package com.example.photobackup.manager

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.photobackup.data.Category
import com.example.photobackup.util.AppLogger
import com.example.photobackup.worker.PhotoBackupWorker
import java.io.File
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
        val backupDestinations: List<String> = emptyList(), // 备份目标目录列表（多选，通过文件夹选择器确认真实存在）
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
            
            // 构建工作数据 - 将文件夹列表与目标目录列表转换为逗号分隔的字符串传递给 Worker
            val foldersString = config.backupFolders.joinToString(",")
            val destinationsString = config.backupDestinations.joinToString(",")
            val inputData = workDataOf(
                PhotoBackupWorker.KEY_BACKUP_FOLDERS to foldersString,
                PhotoBackupWorker.KEY_BACKUP_DESTINATION to destinationsString,
                PhotoBackupWorker.KEY_INTERVAL_MINUTES to config.intervalMinutes,
                PhotoBackupWorker.KEY_REQUIRES_NETWORK to config.requiresNetwork,
                PhotoBackupWorker.KEY_REQUIRES_CHARGING to config.requiresCharging
            )

            // PeriodicWorkRequest 最小 15 分钟：这里统一限制为 15 分钟
            val intervalMinutes = config.intervalMinutes.coerceAtLeast(15L)
            val workRequest = PeriodicWorkRequestBuilder<PhotoBackupWorker>(
                intervalMinutes,
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
            // 清理旧版本可能残留的循环任务
            workManager.cancelUniqueWork(WORK_NAME_LOOP)

            AppLogger.d(TAG, "定时备份(PeriodicWork)已成功提交，间隔: $intervalMinutes 分钟, 文件夹数量: ${config.backupFolders.size}")
        } catch (e: Exception) {
            AppLogger.e(TAG, "启动定时备份失败", e)
            throw e
        }
    }

    /** 备份目标路径：根目录/类别名 */
    private fun categoryDestination(root: String, category: Category): String {
        return File(root, category.name).absolutePath
    }

    /**
     * 按类别设置定时备份：每个类别的备份目标为 根目录/类别名
     */
    fun setupPeriodicBackupFromCategories(
        backupRoot: String,
        categories: List<Category>,
        intervalMinutes: Long,
        requiresNetwork: Boolean = false,
        requiresCharging: Boolean = false,
        useCloudApi: Boolean = false
    ) {
        try {
            val workManager = try {
                WorkManager.getInstance(context)
            } catch (e: IllegalStateException) {
                AppLogger.e(TAG, "WorkManager 未初始化", e)
                throw IllegalStateException("WorkManager 未初始化，请稍后重试", e)
            }
            val constraints = Constraints.Builder()
                .apply {
                    if (requiresNetwork) setRequiredNetworkType(NetworkType.CONNECTED)
                    else setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    if (requiresCharging) setRequiresCharging(true)
                }
                .build()
            val interval = intervalMinutes.coerceAtLeast(15L)
            categories.forEach { category ->
                val dest = if (useCloudApi) "cloud" else categoryDestination(backupRoot, category)
                val foldersString = category.backupFolders.joinToString(",")
                val inputData = if (useCloudApi) {
                    workDataOf(
                        PhotoBackupWorker.KEY_BACKUP_FOLDERS to foldersString,
                        PhotoBackupWorker.KEY_BACKUP_DESTINATION to dest,
                        PhotoBackupWorker.KEY_INTERVAL_MINUTES to interval,
                        PhotoBackupWorker.KEY_REQUIRES_NETWORK to requiresNetwork,
                        PhotoBackupWorker.KEY_REQUIRES_CHARGING to requiresCharging,
                        PhotoBackupWorker.KEY_CATEGORY_ID to category.id,
                        PhotoBackupWorker.KEY_CATEGORY_NAME to category.name
                    )
                } else {
                    workDataOf(
                        PhotoBackupWorker.KEY_BACKUP_FOLDERS to foldersString,
                        PhotoBackupWorker.KEY_BACKUP_DESTINATION to dest,
                        PhotoBackupWorker.KEY_INTERVAL_MINUTES to interval,
                        PhotoBackupWorker.KEY_REQUIRES_NETWORK to requiresNetwork,
                        PhotoBackupWorker.KEY_REQUIRES_CHARGING to requiresCharging,
                        PhotoBackupWorker.KEY_CATEGORY_ID to category.id
                    )
                }
                val workRequest = PeriodicWorkRequestBuilder<PhotoBackupWorker>(interval, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .setInputData(inputData)
                    .addTag(WORK_NAME_PERIODIC)
                    .build()
                val workName = "${WORK_NAME_PERIODIC}_${category.id}"
                workManager.enqueueUniquePeriodicWork(workName, ExistingPeriodicWorkPolicy.UPDATE, workRequest)
            }
            workManager.cancelUniqueWork(WORK_NAME_PERIODIC)
            workManager.cancelUniqueWork(WORK_NAME_LOOP)
            AppLogger.d(TAG, "定时备份已按类别提交，共 ${categories.size} 个类别，间隔: $interval 分钟")
        } catch (e: Exception) {
            AppLogger.e(TAG, "启动定时备份失败", e)
            throw e
        }
    }

    /**
     * 取消定时备份任务（包括所有按类别提交的任务）
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
            val categories = com.example.photobackup.data.CategoryRepository(context).getCategories()
            categories.forEach { category ->
                workManager.cancelUniqueWork("${WORK_NAME_PERIODIC}_${category.id}")
            }
            AppLogger.d(TAG, "定时备份任务已取消")
        } catch (e: Exception) {
            AppLogger.e(TAG, "取消备份任务时发生异常", e)
        }
    }
    
    /**
     * 立即执行一次备份任务（用于测试）
     * @param categoryId 可选，从类别详情页触发时传入，用于统计已备份数量
     */
    fun triggerBackupNow(config: BackupConfig, categoryId: String? = null) {
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
            val destinationsString = config.backupDestinations.joinToString(",")
            val isCloud = config.backupDestinations.firstOrNull() == "cloud"
            val pairs = mutableListOf<Pair<String, Any>>(
                PhotoBackupWorker.KEY_BACKUP_FOLDERS to foldersString,
                PhotoBackupWorker.KEY_BACKUP_DESTINATION to destinationsString
            )
            if (categoryId != null) {
                pairs.add(PhotoBackupWorker.KEY_CATEGORY_ID to categoryId)
                if (isCloud) {
                    val categoryName = com.example.photobackup.data.CategoryRepository(context).getCategory(categoryId)?.name ?: ""
                    pairs.add(PhotoBackupWorker.KEY_CATEGORY_NAME to categoryName)
                }
            }
            val inputData = workDataOf(*pairs.toTypedArray())
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


