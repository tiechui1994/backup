package com.example.photobackup.sync

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import com.example.photobackup.util.AppLogger

/**
 * 账号同步辅助类，负责创建账号和配置同步频率
 */
object SyncHelper {
    private const val TAG = "SyncHelper"
    private const val ACCOUNT_TYPE = "com.example.photobackup.account"
    private const val AUTHORITY = "com.example.photobackup.provider"
    private const val ACCOUNT_NAME = "照片备份服务"

    /**
     * 初始化同步账号
     * @param intervalMinutes 同步间隔（分钟）
     */
    fun setupSync(context: Context, intervalMinutes: Long = 60) {
        try {
            AppLogger.d(TAG, "初始化账号同步机制，间隔: $intervalMinutes 分钟")
            val account = Account(ACCOUNT_NAME, ACCOUNT_TYPE)
            val accountManager = context.getSystemService(Context.ACCOUNT_SERVICE) as AccountManager

            // 1. 添加账号
            val added = accountManager.addAccountExplicitly(account, null, null)
            if (added) {
                AppLogger.d(TAG, "成功创建系统账号: $ACCOUNT_NAME")
            } else {
                AppLogger.d(TAG, "系统账号已存在: $ACCOUNT_NAME")
            }
            
            setupSyncSettings(account, intervalMinutes)
        } catch (e: Exception) {
            AppLogger.e(TAG, "配置账号同步失败", e)
        }
    }

    private fun setupSyncSettings(account: Account, intervalMinutes: Long) {
        // 2. 启用同步
        ContentResolver.setIsSyncable(account, AUTHORITY, 1)
        ContentResolver.setSyncAutomatically(account, AUTHORITY, true)

        // 避免重复添加导致“时间改了但不生效”（系统会保留旧的 periodic sync）
        try {
            ContentResolver.removePeriodicSync(account, AUTHORITY, Bundle.EMPTY)
        } catch (e: Exception) {
            AppLogger.w(TAG, "removePeriodicSync 失败（可忽略）: ${e.message}")
        }

        // 3. 设置定期同步 (单位为秒)
        // 即使应用被杀死，系统也会尝试按此频率拉起同步服务
        val intervalSeconds = intervalMinutes * 60
        ContentResolver.addPeriodicSync(
            account,
            AUTHORITY,
            Bundle.EMPTY,
            intervalSeconds
        )
        AppLogger.d(TAG, "定期同步已设置: 间隔 $intervalMinutes 分钟 ($intervalSeconds 秒)")
    }
}

