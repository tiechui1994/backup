package com.example.photobackup.api

import android.content.Context
import com.example.photobackup.data.BackedUpPhoto
import com.example.photobackup.ui.SettingsFragment
import com.example.photobackup.util.FileHashUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 文件同步后端抽象：统一本地文件备份与云端服务两种方式的同步能力。
 * 上层（Worker、UI）仅依赖本接口，不区分具体实现。
 */
interface FileSyncBackend {
    suspend fun upload(sourceFile: File, categoryName: String?): UploadResult
    suspend fun download(record: BackedUpPhoto, destDir: File): Boolean
    data class UploadResult(val success: Boolean, val remoteId: String? = null)
}

/**
 * 根据备份目标返回对应的同步后端。目标为 "cloud" 时返回云端后端，否则返回本地目录后端。
 */
object SyncBackendProvider {
    private const val TARGET_CLOUD = "cloud"

    fun get(context: Context, destination: String, categoryName: String? = null): FileSyncBackend =
        if (destination.trim() == TARGET_CLOUD) {
            requireNotNull(categoryName) { "云端模式需要提供 categoryName" }
            CloudFileSyncBackend(context, categoryName)
        } else {
            LocalFileSyncBackend(destination.trim())
        }

    fun isCloudTarget(destination: String): Boolean = destination.trim() == TARGET_CLOUD
}

/** 基于本地文件目录的同步后端。 */
private class LocalFileSyncBackend(private val backupDirPath: String) : FileSyncBackend {
    override suspend fun upload(sourceFile: File, categoryName: String?) =
        withContext(Dispatchers.IO) {
            val ok = LocalBackupApi.backupPhoto(sourceFile, backupDirPath)
            FileSyncBackend.UploadResult(success = ok, remoteId = null)
        }

    override suspend fun download(record: BackedUpPhoto, destDir: File): Boolean {
        val dir = record.backupTarget ?: return false
        if (dir.isEmpty()) return false
        return withContext(Dispatchers.IO) {
            val backupFile = LocalBackupApi.findBackupFile(dir, record.fileName)
            backupFile != null && LocalBackupApi.copyFromBackupToLocal(backupFile, destDir)
        }
    }
}

/** 基于云端服务的同步后端。 */
private class CloudFileSyncBackend(
    private val context: Context,
    private val categoryName: String
) : FileSyncBackend {
    private val prefs = context.getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE)
    private val baseUrl get() = prefs.getString(SettingsFragment.PREF_CLOUD_BASE_URL, null).orEmpty().trim()
    private val userid get() = prefs.getString(SettingsFragment.PREF_CLOUD_USER_ID, null).orEmpty().trim()

    override suspend fun upload(sourceFile: File, categoryName: String?): FileSyncBackend.UploadResult {
        if (baseUrl.isBlank() || userid.isBlank()) return FileSyncBackend.UploadResult(success = false, remoteId = null)
        return withContext(Dispatchers.IO) {
            val sha1 = FileHashUtil.calculateSHA1(sourceFile) ?: return@withContext FileSyncBackend.UploadResult(success = false, remoteId = null)
            val cat = categoryName ?: this@CloudFileSyncBackend.categoryName
            val fileId = CloudBackupApi.upload(baseUrl, userid, cat, sourceFile, sha1)
            FileSyncBackend.UploadResult(success = fileId != null, remoteId = fileId)
        }
    }

    override suspend fun download(record: BackedUpPhoto, destDir: File): Boolean {
        val fileId = record.remoteId ?: return false
        if (baseUrl.isBlank() || userid.isBlank()) return false
        return withContext(Dispatchers.IO) {
            val bytes = CloudBackupApi.download(baseUrl, userid, categoryName, fileId, record.fileName) ?: return@withContext false
            val outFile = File(destDir, record.fileName)
            val finalFile = if (outFile.exists()) {
                val base = record.fileName.substringBeforeLast('.', record.fileName)
                val ext = record.fileName.substringAfterLast('.', "")
                File(destDir, if (ext.isEmpty()) "${base}_${System.currentTimeMillis()}" else "${base}_${System.currentTimeMillis()}.$ext")
            } else outFile
            java.io.FileOutputStream(finalFile).use { it.write(bytes) }
            true
        }
    }
}
