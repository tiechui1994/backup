package com.example.photobackup.api

import com.example.photobackup.util.AppLogger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * 云端备份 API
 * PUT /api/file/upload  Header: userid, category, sha1sum  Body: 文件内容  响应: { "fileId": "xxx" }
 * GET /api/file/download?fileId=xxx  Header: userid, category, filename  响应: 文件内容
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

    /** OkHttp 要求 header 值为 ASCII，对含中文等字符进行 UTF-8 URL 编码 */
    private fun encodeHeaderValue(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    }

    /**
     * 上传文件到云端
     * @return 成功时返回 fileId（sha1），失败返回 null
     */
    fun upload(baseUrl: String, userid: String, category: String, file: File, sha1sum: String): String? {
        if (baseUrl.isBlank() || userid.isBlank()) {
            AppLogger.e(TAG, "baseUrl 或 userid 为空")
            return null
        }
        return try {
            val url = "${normalizeBaseUrl(baseUrl)}/api/file/upload"
            val body = file.readBytes().toRequestBody("application/octet-stream".toMediaType())
            val request = Request.Builder()
                .url(url)
                .put(body)
                .addHeader("userid", userid)
                .addHeader("category", encodeHeaderValue(category))
                .addHeader("sha1sum", sha1sum)
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                AppLogger.e(TAG, "上传失败: ${file.name} code=${response.code} body=${response.body?.string()}")
                return null
            }
            val bodyStr = response.body?.string().orEmpty()
            val fileId = JSONObject(bodyStr).optString("fileId", "").ifBlank { sha1sum }
            AppLogger.d(TAG, "上传成功: ${file.name} fileId=$fileId")
            fileId
        } catch (e: Exception) {
            AppLogger.e(TAG, "上传异常: ${file.absolutePath}", e)
            null
        }
    }

    /**
     * 从云端下载文件
     * @param fileId 上传接口返回的 fileId（sha1）
     * @param filename 用于 Content-Disposition 的文件名
     * @return 文件内容，失败返回 null
     */
    fun download(baseUrl: String, userid: String, category: String, fileId: String, filename: String): ByteArray? {
        if (baseUrl.isBlank() || userid.isBlank()) {
            AppLogger.e(TAG, "baseUrl 或 userid 为空")
            return null
        }
        return try {
            val url = "${normalizeBaseUrl(baseUrl)}/api/file/download?fileId=${java.net.URLEncoder.encode(fileId, "UTF-8")}"
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("userid", userid)
                .addHeader("category", encodeHeaderValue(category))
                .addHeader("filename", encodeHeaderValue(filename))
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                AppLogger.e(TAG, "下载失败: fileId=$fileId code=${response.code}")
                return null
            }
            response.body?.bytes()
        } catch (e: Exception) {
            AppLogger.e(TAG, "下载异常: fileId=$fileId", e)
            null
        }
    }
}
