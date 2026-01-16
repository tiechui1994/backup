package com.example.photobackup

import android.app.Application
import android.util.Log

/**
 * Application 类，用于应用初始化
 */
class PhotoBackupApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        try {
            // 预初始化数据库（可选，用于提前创建数据库文件）
            // PhotoBackupDatabase.getDatabase(this)
            Log.d("PhotoBackupApp", "Application initialized")
        } catch (e: Exception) {
            Log.e("PhotoBackupApp", "Error initializing application", e)
        }
    }
}

