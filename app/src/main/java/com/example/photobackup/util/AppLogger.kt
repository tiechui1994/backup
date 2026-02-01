package com.example.photobackup.util

import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日志落地工具类
 * 将日志同时输出到 Logcat 和指定的本地文件
 */
object AppLogger {
    private const val DEFAULT_TAG = "PhotoBackup"
    private var logDir: String? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val fileNameFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /**
     * 初始化日志目录
     * @param root 备份根目录，日志将存放在 根目录/logs
     */
    fun init(root: String) {
        try {
            val dir = File(root, "logs")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            logDir = dir.absolutePath
            d("AppLogger", "日志系统初始化成功，路径: $logDir")
        } catch (e: Exception) {
            Log.e("AppLogger", "初始化日志目录失败", e)
        }
    }

    fun d(tag: String = DEFAULT_TAG, message: String) {
        Log.d(tag, message)
        writeToFile("DEBUG", tag, message)
    }

    fun i(tag: String = DEFAULT_TAG, message: String) {
        Log.i(tag, message)
        writeToFile("INFO", tag, message)
    }

    fun w(tag: String = DEFAULT_TAG, message: String) {
        Log.w(tag, message)
        writeToFile("WARN", tag, message)
    }

    fun e(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        val fullMessage = if (throwable != null) {
            "$message\n${Log.getStackTraceString(throwable)}"
        } else {
            message
        }
        writeToFile("ERROR", tag, fullMessage)
    }

    private fun writeToFile(level: String, tag: String, message: String) {
        val currentLogDir = logDir ?: return
        
        try {
            val fileName = "backup_${fileNameFormat.format(Date())}.log"
            val logFile = File(currentLogDir, fileName)
            val timeStamp = dateFormat.format(Date())
            
            FileWriter(logFile, true).use { fw ->
                PrintWriter(fw).use { out ->
                    out.println("$timeStamp [$level] $tag: $message")
                }
            }
        } catch (e: Exception) {
            // 如果写文件失败，只打印 logcat 以防递归
            Log.e("AppLogger", "写入日志文件失败", e)
        }
    }
}

