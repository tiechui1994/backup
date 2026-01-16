package com.example.photobackup.util

import com.example.photobackup.api.UploadApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import retrofit2.create
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 网络工具类，用于创建 Retrofit 实例和上传 API
 */
object NetworkUtil {
    
    /**
     * 创建 Retrofit 实例
     */
    private fun createRetrofit(baseUrl: String): Retrofit {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
        
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .build()
    }
    
    /**
     * 创建上传 API 实例
     */
    fun createUploadApi(baseUrl: String): UploadApi {
        val retrofit = createRetrofit(baseUrl)
        return retrofit.create()
    }
    
    /**
     * 创建文件 MultipartBody.Part
     */
    fun createFilePart(file: File, partName: String = "file"): MultipartBody.Part {
        val requestFile = file.asRequestBody("image/*".toMediaType())
        return MultipartBody.Part.createFormData(partName, file.name, requestFile)
    }
}


