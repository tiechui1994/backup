package com.example.photobackup.manager

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.photobackup.util.AppLogger
import com.example.photobackup.worker.PhotoBackupWorker
import java.util.concurrent.TimeUnit

/**
 * 照片备份服务管理类
 * 负责配置备份参数、启动定时备份任务
 */
class PhotoBackupManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "PhotoBackupManager"
        private const val WORK_NAME_PERIODIC = "photo_backup_work_periodic"
        private const val WORK_NAME_LOOP = "photo_backup_work_loop"
        
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
        val backupFolders: List<String>, // 源文件夹列表
        val backupDestination: String = "", // 备份目标目录
        val intervalMinutes: Long = 1440, // 默认 1440 分钟（24小时）执行一次
        val requiresNetwork: Boolean = false,
        val requiresCharging: Boolean = false
    )
    
    /**
     * 设置并启动定时备份任务
     * @param config 备份配置
     */
    fun setupPeriodicBackup(config: BackupConfig) {
        try {
            AppLogger.d(TAG, "设置定时备份任务: $config")
            
            // 确保 WorkManager 已初始化
            val workManager = try {
                WorkManager.getInstance(context)
            } catch (e: IllegalStateException) {
                AppLogger.e(TAG, "WorkManager 未初始化", e)
                throw IllegalStateException("WorkManager 未初始化，请稍后重试", e)
            }
            
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
            
            // 构建工作数据 - 将文件夹列表转换为逗号分隔的字符串传递给 Worker
            val foldersString = config.backupFolders.joinToString(",")
            val inputData = workDataOf(
                PhotoBackupWorker.KEY_BACKUP_FOLDERS to foldersString,
                PhotoBackupWorker.KEY_BACKUP_DESTINATION to config.backupDestination,
                PhotoBackupWorker.KEY_INTERVAL_MINUTES to config.intervalMinutes,
                PhotoBackupWorker.KEY_REQUIRES_NETWORK to config.requiresNetwork,
                PhotoBackupWorker.KEY_REQUIRES_CHARGING to config.requiresCharging
            )

            val intervalMinutes = config.intervalMinutes
            if (intervalMinutes in 1..14) {
                // WorkManager 的 PeriodicWorkRequest 最小 15 分钟；为了取消最小间隔限制，
                // interval < 15 时用“循环 OneTimeWork + 初始延迟”来实现任意分钟间隔。
                val workRequest = OneTimeWorkRequestBuilder<PhotoBackupWorker>()
                    .setConstraints(constraints)
                    .setInputData(inputData)
                    .setInitialDelay(intervalMinutes, TimeUnit.MINUTES)
                    .addTag(WORK_NAME_LOOP)
                    .build()

                workManager.enqueueUniqueWork(
                    WORK_NAME_LOOP,
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
                // 同时取消可能存在的 periodic 任务，避免重复执行
                workManager.cancelUniqueWork(WORK_NAME_PERIODIC)

                AppLogger.d(TAG, "定时备份(循环OneTime)已提交，间隔: $intervalMinutes 分钟, 文件夹数量: ${config.backupFolders.size}")
            } else {
                // interval >= 15：使用系统支持的 PeriodicWork
                val safeInterval = if (intervalMinutes <= 0) 15L else intervalMinutes
                val workRequest = PeriodicWorkRequestBuilder<PhotoBackupWorker>(
                    safeInterval,
                    TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .setInputData(inputData)
                    .addTag(WORK_NAME_PERIODIC)
                    .build()

                workManager.enqueueUniquePeriodicWork(
                    WORK_NAME_PERIODIC,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    workRequest
                )
                // 同时取消可能存在的循环任务，避免重复执行
                workManager.cancelUniqueWork(WORK_NAME_LOOP)

                AppLogger.d(TAG, "定时备份(PeriodicWork)已成功提交，间隔: $safeInterval 分钟, 文件夹数量: ${config.backupFolders.size}")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "启动定时备份失败", e)
            throw e
        }
    }
    
    /**
     * 取消定时备份任务
     */
    fun cancelPeriodicBackup() {
        try {
            AppLogger.d(TAG, "收到取消定时备份任务指令")
            val workManager = try {
                WorkManager.getInstance(context)
            } catch (e: IllegalStateException) {
                AppLogger.e(TAG, "WorkManager 未初始化，取消失败", e)
                return
            }
            workManager.cancelUniqueWork(WORK_NAME_PERIODIC)
            workManager.cancelUniqueWork(WORK_NAME_LOOP)
            AppLogger.d(TAG, "定时备份任务已取消")
        } catch (e: Exception) {
            AppLogger.e(TAG, "取消备份任务时发生异常", e)
        }
    }
    
    /**
     * 立即执行一次备份任务（用于测试）
     */
    fun triggerBackupNow(config: BackupConfig) {
        try {
            AppLogger.d(TAG, "手动触发立即备份任务: foldersCount=${config.backupFolders.size}")
            
            // 确保 WorkManager 已初始化
            val workManager = try {
                WorkManager.getInstance(context)
            } catch (e: IllegalStateException) {
                AppLogger.e(TAG, "WorkManager 未初始化，触发失败", e)
                throw IllegalStateException("WorkManager 未初始化，请稍后重试", e)
            }
            
            val constraints = Constraints.Builder()
                .apply {
                    if (config.requiresNetwork) {
                        setRequiredNetworkType(NetworkType.CONNECTED)
                    } else {
                        setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    }
                }
                .build()
            
            val foldersString = config.backupFolders.joinToString(",")
            val inputData = workDataOf(
                PhotoBackupWorker.KEY_BACKUP_FOLDERS to foldersString,
                PhotoBackupWorker.KEY_BACKUP_DESTINATION to config.backupDestination
            )
            
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<PhotoBackupWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .addTag("photo_backup_onetime")
                .build()
            
            workManager.enqueue(workRequest)
            AppLogger.d(TAG, "立即备份任务已提交到队列")
        } catch (e: Exception) {
            AppLogger.e(TAG, "手动触发备份任务失败", e)
            throw e
        }
    }
}


