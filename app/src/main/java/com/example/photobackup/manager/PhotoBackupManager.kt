package com.example.photobackup.manager

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.photobackup.worker.PhotoBackupWorker
import java.util.concurrent.TimeUnit

/**
 * 照片备份服务管理类
 * 负责配置备份参数、启动定时备份任务
 */
class PhotoBackupManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "PhotoBackupManager"
        private const val WORK_NAME = "photo_backup_work"
        
        @Volatile
        private var INSTANCE: PhotoBackupManager? = null
        
        fun getInstance(context: Context): PhotoBackupManager {
            return INSTANCE ?: synchronized(this) {
                val instance = PhotoBackupManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
    
    /**
     * 备份配置
     */
    data class BackupConfig(
        val backupFolder: String, // 源文件夹（要备份的照片所在目录）
        val backupDestination: String = "", // 备份目标目录（用户自定义）
        val intervalHours: Long = 24, // 默认 24 小时执行一次
        val requiresNetwork: Boolean = false, // 本地备份不需要网络
        val requiresCharging: Boolean = false
    )
    
    /**
     * 设置并启动定时备份任务
     * @param config 备份配置
     */
    fun setupPeriodicBackup(config: BackupConfig) {
        Log.d(TAG, "设置定时备份任务: $config")
        
        // 构建约束条件
        val constraints = Constraints.Builder()
            .apply {
                if (config.requiresNetwork) {
                    setRequiredNetworkType(NetworkType.CONNECTED)
                } else {
                    setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                }
                if (config.requiresCharging) {
                    setRequiresCharging(true)
                }
            }
            .build()
        
        // 构建工作数据
        val inputData = workDataOf(
            PhotoBackupWorker.KEY_BACKUP_FOLDER to config.backupFolder,
            PhotoBackupWorker.KEY_BACKUP_DESTINATION to config.backupDestination
        )
        
        // 创建周期性工作请求
        // 注意：PeriodicWorkRequest 的最小间隔是 15 分钟
        val workRequest = PeriodicWorkRequestBuilder<PhotoBackupWorker>(
            config.intervalHours.coerceAtLeast(1),
            TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInputData(inputData)
            .addTag(WORK_NAME)
            .build()
        
        // 提交工作请求
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )
        
        Log.d(TAG, "定时备份任务已启动，间隔: ${config.intervalHours} 小时")
    }
    
    /**
     * 取消定时备份任务
     */
    fun cancelPeriodicBackup() {
        Log.d(TAG, "取消定时备份任务")
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
    
    /**
     * 立即执行一次备份任务（用于测试）
     */
    fun triggerBackupNow(config: BackupConfig) {
        Log.d(TAG, "立即触发备份任务")
        
        val constraints = Constraints.Builder()
            .apply {
                if (config.requiresNetwork) {
                    setRequiredNetworkType(NetworkType.CONNECTED)
                } else {
                    setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                }
            }
            .build()
        
        val inputData = workDataOf(
            PhotoBackupWorker.KEY_BACKUP_FOLDER to config.backupFolder,
            PhotoBackupWorker.KEY_BACKUP_DESTINATION to config.backupDestination
        )
        
        val workRequest = androidx.work.OneTimeWorkRequestBuilder<PhotoBackupWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .addTag("photo_backup_onetime")
            .build()
        
        WorkManager.getInstance(context).enqueue(workRequest)
    }
}


