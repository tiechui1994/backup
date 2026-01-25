package com.example.photobackup.api

import com.example.photobackup.util.AppLogger
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * 照片备份 API 接口
 * 测试版本：将文件备份到本地自定义目录
 */
object UploadApi {
    
    private const val TAG = "UploadApi"
    
    /**
     * 备份照片文件到指定目录
     * @param sourceFile 源文件
     * @param backupDestination 备份目标目录路径
     * @return 备份是否成功
     */
    suspend fun backupPhoto(sourceFile: File, backupDestination: String): Boolean {
        return try {
            if (backupDestination.isEmpty()) {
                AppLogger.d(TAG, "未配置备份目标目录，仅记录到本地数据库: ${sourceFile.name}")
                return true
            }
            
            val destDir = File(backupDestination)
            
            // 确保目标目录存在
            if (!destDir.exists()) {
                val created = destDir.mkdirs()
                if (!created) {
                    AppLogger.e(TAG, "无法创建备份目录: $backupDestination")
                    return false
                }
            }
            
            if (!destDir.isDirectory) {
                AppLogger.e(TAG, "备份目标路径不是目录: $backupDestination")
                return false
            }
            
            // 检查是否有写入权限
            if (!destDir.canWrite()) {
                AppLogger.e(TAG, "备份目录无写入权限: $backupDestination")
                return false
            }
            
            // 创建目标文件路径（保持原文件名，如果已存在则添加时间戳）
            val destFile = File(destDir, sourceFile.name)
            val finalDestFile = if (destFile.exists()) {
                // 如果文件已存在，添加时间戳
                val nameWithoutExt = sourceFile.nameWithoutExtension
                val ext = sourceFile.extension
                val timestamp = System.currentTimeMillis()
                File(destDir, "${nameWithoutExt}_${timestamp}.$ext")
            } else {
                destFile
            }
            
            // 复制文件
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(finalDestFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            AppLogger.d(TAG, "备份成功: ${sourceFile.name} -> ${finalDestFile.absolutePath}")
            true
            
        } catch (e: java.io.FileNotFoundException) {
            AppLogger.e(TAG, "文件未找到: ${sourceFile.absolutePath}", e)
            false
        } catch (e: java.io.IOException) {
            AppLogger.e(TAG, "IO 错误: ${sourceFile.absolutePath}", e)
            false
        } catch (e: SecurityException) {
            AppLogger.e(TAG, "权限错误: ${sourceFile.absolutePath}", e)
            false
        } catch (e: Exception) {
            AppLogger.e(TAG, "备份文件失败: ${sourceFile.absolutePath}", e)
            false
        }
    }
}


