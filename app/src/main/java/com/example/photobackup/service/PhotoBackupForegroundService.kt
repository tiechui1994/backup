package com.example.photobackup.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.photobackup.MainActivity
import com.example.photobackup.R

/**
 * 前台服务，用于显示备份进度通知
 */
class PhotoBackupForegroundService : Service() {
    
    companion object {
        private const val TAG = "PhotoBackupService"
        const val CHANNEL_ID = "photo_backup_channel"
        const val NOTIFICATION_ID = 1001
        
        /**
         * 启动前台服务
         */
        fun startService(context: Context, title: String = "照片备份中...") {
            val intent = Intent(context, PhotoBackupForegroundService::class.java).apply {
                putExtra("title", title)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        /**
         * 停止前台服务
         */
        fun stopService(context: Context) {
            val intent = Intent(context, PhotoBackupForegroundService::class.java)
            context.stopService(intent)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val title = intent?.getStringExtra("title") ?: "照片备份中..."
            val notification = createNotification(title, "正在备份照片...", 0, 0)
            
            // Android 10+ (API 29+) 建议指定前台服务类型
            // Android 14+ (API 34+) 强制要求指定前台服务类型
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID, 
                    notification, 
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            return START_NOT_STICKY
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service", e)
            stopSelf()
            return START_NOT_STICKY
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    /**
     * 更新通知
     */
    fun updateNotification(title: String, content: String, progress: Int, max: Int) {
        val notification = createNotification(title, content, progress, max)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * 创建通知渠道（Android 8.0+）
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "照片备份服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示照片备份进度"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 创建通知
     */
    private fun createNotification(title: String, content: String, progress: Int, max: Int): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        
        if (max > 0) {
            builder.setProgress(max, progress, false)
        }
        
        return builder.build()
    }
}


