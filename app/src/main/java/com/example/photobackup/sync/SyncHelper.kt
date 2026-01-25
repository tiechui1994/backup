package com.example.photobackup.sync

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.os.Bundle

/**
 * 账号同步辅助类，负责创建账号和配置同步频率
 */
object SyncHelper {
    private const val ACCOUNT_TYPE = "com.example.photobackup.account"
    private const val AUTHORITY = "com.example.photobackup.provider"
    private const val ACCOUNT_NAME = "照片备份服务"

    /**
     * 初始化同步账号
     */
    fun setupSync(context: Context) {
        val account = Account(ACCOUNT_NAME, ACCOUNT_TYPE)
        val accountManager = context.getSystemService(Context.ACCOUNT_SERVICE) as AccountManager

        // 1. 添加账号
        if (accountManager.addAccountExplicitly(account, null, null)) {
            // 账号创建成功
            setupSyncSettings(account)
        } else {
            // 账号已存在或创建失败
            setupSyncSettings(account)
        }
    }

    private fun setupSyncSettings(account: Account) {
        // 2. 启用同步
        ContentResolver.setIsSyncable(account, AUTHORITY, 1)
        ContentResolver.setSyncAutomatically(account, AUTHORITY, true)

        // 3. 设置定期同步 (例如每 1 小时同步一次，单位为秒)
        // 即使应用被杀死，系统也会尝试按此频率拉起同步服务
        ContentResolver.addPeriodicSync(
            account,
            AUTHORITY,
            Bundle.EMPTY,
            60 * 60 // 1 hour
        )
    }
}

