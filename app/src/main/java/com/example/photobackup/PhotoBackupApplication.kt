package com.example.photobackup

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager

/**
 * Application 类，用于应用初始化
 */
class PhotoBackupApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        try {
            // 设置全局异常处理器，捕获未处理的异常
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
                Log.e("PhotoBackupApp", "Uncaught exception in thread: ${thread.name}", exception)
                exception.printStackTrace()
                // 调用默认处理器
                defaultHandler?.uncaughtException(thread, exception)
            }
            
            // WorkManager 通过 AndroidManifest 中的 InitializationProvider 自动初始化
            // 这里只做日志记录，不手动初始化以避免冲突
            Log.d("PhotoBackupApp", "Application initialized successfully")
        } catch (e: Exception) {
            Log.e("PhotoBackupApp", "Error initializing application", e)
            e.printStackTrace()
            // 不要抛出异常，让应用继续启动
        }
    }
}

