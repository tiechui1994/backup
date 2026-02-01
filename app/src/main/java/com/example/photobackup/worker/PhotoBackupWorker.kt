package com.example.photobackup.worker

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.photobackup.api.FileSyncBackend
import com.example.photobackup.api.SyncBackendProvider
import com.example.photobackup.data.BackedUpPhoto
import com.example.photobackup.ui.SettingsFragment
import com.example.photobackup.data.PhotoBackupDatabase
import com.example.photobackup.service.PhotoBackupForegroundService
import com.example.photobackup.util.AppLogger
import com.example.photobackup.util.FileHashUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * WorkManager Worker，执行照片备份任务
 * 支持增量备份、异常重试
 */
class PhotoBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "PhotoBackupWorker"
        const val KEY_BACKUP_FOLDERS = "backup_folders"
        const val KEY_BACKUP_DESTINATION = "backup_destination"
        const val KEY_INTERVAL_MINUTES = "interval_minutes"
        const val KEY_REQUIRES_NETWORK = "requires_network"
        const val KEY_REQUIRES_CHARGING = "requires_charging"
        const val KEY_CATEGORY_ID = "category_id"
        const val KEY_CATEGORY_NAME = "category_name"
    }
    
    private val database = PhotoBackupDatabase.getDatabase(applicationContext)
    private val dao = database.backedUpPhotoDao()
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // 获取备份配置
            val foldersString = inputData.getString(KEY_BACKUP_FOLDERS)
                ?: return@withContext Result.failure(
                    workDataOf("error" to "备份文件夹未配置")
                )
            
            val backupFolders = foldersString.split(",").filter { it.isNotEmpty() }
            val destinationsString = inputData.getString(KEY_BACKUP_DESTINATION) ?: ""
            val backupDestinations = destinationsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val categoryId = inputData.getString(KEY_CATEGORY_ID)
            val categoryName = inputData.getString(KEY_CATEGORY_NAME)
            val firstDestination = backupDestinations.firstOrNull().orEmpty().trim()
            val isCloudMode = SyncBackendProvider.isCloudTarget(firstDestination)
            AppLogger.d(TAG, "备份目标: firstDestination=[$firstDestination], isCloudMode=$isCloudMode, categoryName=$categoryName")

            // 初始化日志落地：根目录/logs 或应用目录（云端模式）
            if (isCloudMode) {
                applicationContext.getExternalFilesDir(null)?.absolutePath?.let { AppLogger.init(it) }
            } else {
                backupDestinations.firstOrNull()?.let { File(it).parent }?.let { AppLogger.init(it) }
            }
            
            AppLogger.d(TAG, "开始执行备份任务，涉及 ${backupFolders.size} 个文件夹, 目标目录: ${backupDestinations.size} 个")
            
            // 启动前台服务
            PhotoBackupForegroundService.startService(applicationContext, "正在备份照片...")
            
            val allImageFiles = mutableListOf<File>()
            backupFolders.forEach { folderPath ->
                val folder = File(folderPath)
                if (folder.exists() && folder.isDirectory) {
                    val files = getImageFiles(folder)
                    allImageFiles.addAll(files)
                    AppLogger.d(TAG, "扫描文件夹: $folderPath, 找到图片数量: ${files.size}")
                } else {
                    AppLogger.w(TAG, "文件夹不存在或无效，跳过: $folderPath")
                }
            }
            
            AppLogger.d(TAG, "所有待处理图片总数: ${allImageFiles.size}")
            
            var successCount = 0
            var skipCount = 0
            var failCount = 0

            // 遍历处理每个图片文件
            allImageFiles.forEachIndexed { index, file ->
                try {
                    // 计算 MD5
                    val md5 = FileHashUtil.calculateMD5(file)
                    if (md5 == null) {
                        AppLogger.w(TAG, "无法计算文件 MD5，跳过: ${file.absolutePath}")
                        failCount++
                        return@forEachIndexed
                    }
                    
                    // 检查是否已备份
                    val isBackedUp = dao.isBackedUp(md5)
                    if (isBackedUp) {
                        skipCount++
                        return@forEachIndexed
                    }
                    
                    // 通过统一后端上传/备份（本地或云端由 SyncBackendProvider 决定）
                    var backupSuccess = backupDestinations.isEmpty()
                    var backupTargetForRecord = backupDestinations.firstOrNull() ?: ""
                    var remoteIdForRecord: String? = null
                    if (!backupSuccess) {
                        if (isCloudMode && categoryName != null) {
                            val backend = SyncBackendProvider.get(applicationContext, "cloud", categoryName)
                            val result = backend.upload(file, categoryName)
                            backupSuccess = result.success
                            remoteIdForRecord = result.remoteId
                            backupTargetForRecord = "cloud"
                        } else {
                            backupSuccess = true
                            for (dest in backupDestinations) {
                                val backend = SyncBackendProvider.get(applicationContext, dest.trim(), categoryName)
                                val result = backend.upload(file, categoryName)
                                if (!result.success) {
                                    backupSuccess = false
                                    break
                                }
                            }
                            backupTargetForRecord = backupDestinations.firstOrNull() ?: ""
                        }
                    }

                    if (backupSuccess) {
                        val backedUpPhoto = BackedUpPhoto(
                            md5 = md5,
                            filePath = file.absolutePath,
                            fileName = file.name,
                            fileSize = file.length(),
                            backupTarget = backupTargetForRecord,
                            categoryId = categoryId,
                            remoteId = remoteIdForRecord
                        )
                        dao.insert(backedUpPhoto)
                        successCount++
                    } else {
                        failCount++
                        AppLogger.w(TAG, "备份失败 [${index + 1}/${allImageFiles.size}]: ${file.name}")
                    }
                    
                    // 更新通知进度
                    updateNotification(
                        "正在备份照片...",
                        "已处理 ${index + 1}/${allImageFiles.size} (成功:$successCount, 失败:$failCount)",
                        index + 1,
                        allImageFiles.size
                    )
                    
                } catch (e: Exception) {
                    AppLogger.e(TAG, "处理文件时发生意外异常: ${file.absolutePath}", e)
                    failCount++
                }
            }
            
            // 更新最终通知
            updateNotification(
                "备份任务结束",
                "完成处理 ${allImageFiles.size} 张照片。成功:$successCount, 跳过:$skipCount, 失败:$failCount",
                0,
                0
            )
            
            AppLogger.d(TAG, "备份工作执行完毕 - 最终统计: 成功=$successCount, 跳过=$skipCount, 失败=$failCount")
            saveLastRunStatus(true, successCount, skipCount, failCount)
            kotlinx.coroutines.delay(3000)
            PhotoBackupForegroundService.stopService(applicationContext)
            
            Result.success(
                workDataOf(
                    "success_count" to successCount,
                    "skip_count" to skipCount,
                    "fail_count" to failCount
                )
            )
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "备份任务严重错误导致中断", e)
            saveLastRunStatus(false, 0, 0, 0, e.message)
            PhotoBackupForegroundService.stopService(applicationContext)
            
            if (isRetryableError(e)) {
                AppLogger.d(TAG, "任务标记为可重试")
                Result.retry()
            } else {
                Result.failure(workDataOf("error" to e.message))
            }
        }
    }
    
    /**
     * 获取文件夹中的所有图片文件
     */
    private fun getImageFiles(folder: File): List<File> {
        val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
        val files = mutableListOf<File>()
        
        fun traverseDirectory(dir: File) {
            try {
                dir.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        traverseDirectory(file)
                    } else if (file.isFile) {
                        val extension = file.extension.lowercase()
                        if (imageExtensions.contains(extension)) {
                            files.add(file)
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "遍历目录失败: ${dir.absolutePath}", e)
            }
        }
        
        traverseDirectory(folder)
        return files
    }
    
    /**
     * 判断错误是否可重试
     * 对于本地文件备份，IO 错误和权限错误通常不可重试
     */
    private fun isRetryableError(e: Exception): Boolean {
        val message = e.message?.lowercase() ?: ""
        // 本地备份通常不需要重试，除非是临时性的 IO 错误
        // 可以根据实际需求调整重试策略
        return message.contains("temporary") || 
               message.contains("retry")
    }
    
    private fun saveLastRunStatus(success: Boolean, successCount: Int, skipCount: Int, failCount: Int, error: String? = null) {
        try {
            applicationContext.getSharedPreferences("photo_backup_prefs", android.content.Context.MODE_PRIVATE)
                .edit()
                .putBoolean("last_run_success", success)
                .putLong("last_run_time", System.currentTimeMillis())
                .putInt("last_success_count", successCount)
                .putInt("last_skip_count", skipCount)
                .putInt("last_fail_count", failCount)
                .putString("last_run_error", error)
                .apply()
        } catch (_: Exception) {}
    }

    /**
     * 更新通知
     */
    private fun updateNotification(title: String, content: String, progress: Int, max: Int) {
        try {
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager != null) {
                val builder = NotificationCompat.Builder(applicationContext, PhotoBackupForegroundService.CHANNEL_ID)
                    .setContentTitle(title)
                    .setContentText(content)
                    .setSmallIcon(android.R.drawable.ic_menu_upload)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                
                if (max > 0) {
                    builder.setProgress(max, progress, false)
                }
                
                notificationManager.notify(PhotoBackupForegroundService.NOTIFICATION_ID, builder.build())
                AppLogger.d(TAG, "通知已更新: $title - $content ($progress/$max)")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "更新通知失败", e)
        }
    }
}

