package com.example.photobackup.api

import com.example.photobackup.util.AppLogger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 云端备份 API：PUT 上传、GET 下载
 * PUT /api/file/upload  Header: userid, category, sha1sum  Body: 文件内容
 * GET /api/file/download  Header: userid, category, filename  响应: 文件内容
 */
object CloudBackupApi {
    private const val TAG = "CloudBackupApi"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun normalizeBaseUrl(baseUrl: String): String {
        return baseUrl.trim().removeSuffix("/")
    }

    /**
     * 上传文件到云端
     * @return 是否成功
     */
    fun upload(baseUrl: String, userid: String, category: String, file: File, sha1sum: String): Boolean {
        if (baseUrl.isBlank() || userid.isBlank()) {
            AppLogger.e(TAG, "baseUrl 或 userid 为空")
            return false
        }
        return try {
            val url = "${normalizeBaseUrl(baseUrl)}/api/file/upload"
            val body = file.readBytes().toRequestBody("application/octet-stream".toMediaType())
            val request = Request.Builder()
                .url(url)
                .put(body)
                .addHeader("userid", userid)
                .addHeader("category", category)
                .addHeader("sha1sum", sha1sum)
                .build()
            val response = client.newCall(request).execute()
            val ok = response.isSuccessful
            if (!ok) {
                AppLogger.e(TAG, "上传失败: ${file.name} code=${response.code} body=${response.body?.string()}")
            } else {
                AppLogger.d(TAG, "上传成功: ${file.name}")
            }
            ok
        } catch (e: Exception) {
            AppLogger.e(TAG, "上传异常: ${file.absolutePath}", e)
            false
        }
    }

    /**
     * 从云端下载文件
     * @return 文件内容，失败返回 null
     */
    fun download(baseUrl: String, userid: String, category: String, filename: String): ByteArray? {
        if (baseUrl.isBlank() || userid.isBlank()) {
            AppLogger.e(TAG, "baseUrl 或 userid 为空")
            return null
        }
        return try {
            val url = "${normalizeBaseUrl(baseUrl)}/api/file/download"
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("userid", userid)
                .addHeader("category", category)
                .addHeader("filename", filename)
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                AppLogger.e(TAG, "下载失败: $filename code=${response.code}")
                return null
            }
            response.body?.bytes()
        } catch (e: Exception) {
            AppLogger.e(TAG, "下载异常: $filename", e)
            null
        }
    }
}
