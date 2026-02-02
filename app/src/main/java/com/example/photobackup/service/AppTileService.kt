package com.example.photobackup.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import com.example.photobackup.MainActivity
import com.example.photobackup.util.AppLogger

/**
 * 快捷设置开关服务，允许用户从下拉通知栏打开应用
 * 适配 vivo、OPPO、小米等定制系统
 */
class AppTileService : TileService() {
    
    override fun onClick() {
        super.onClick()
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                // 从 TileService 启动必须带 NEW_TASK
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // 若应用已在后台，将已有任务带到前台而不是新建
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                // 收起最近任务中本应用之上的其他任务，确保本应用在前台
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            
            // unlockAndRun：锁屏时会先解锁再启动，未锁屏时直接执行，利于从后台带到前台
            val doStart = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    startActivityAndCollapse(intent)
                } else {
                    startActivity(intent)
                }
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    unlockAndRun(doStart)
                } else {
                    doStart()
                }
            } catch (e: SecurityException) {
                AppLogger.e("AppTileService", "SecurityException, trying fallback", e)
                try {
                    startActivity(intent)
                } catch (e2: Exception) {
                    AppLogger.e("AppTileService", "Fallback failed", e2)
                }
            } catch (e: Exception) {
                AppLogger.e("AppTileService", "Failed to start MainActivity", e)
                try {
                    startActivity(intent)
                } catch (e2: Exception) {
                    AppLogger.e("AppTileService", "Fallback also failed", e2)
                }
            }
        } catch (e: Exception) {
            AppLogger.e("AppTileService", "Unexpected error in onClick", e)
        }
    }
    
    override fun onStartListening() {
        super.onStartListening()
        try {
            // 更新 Tile 的状态（如启用/禁用）
            val tile = qsTile
            if (tile != null) {
                tile.state = android.service.quicksettings.Tile.STATE_ACTIVE
                tile.updateTile()
            }
        } catch (e: Exception) {
            AppLogger.e("AppTileService", "Error updating tile state", e)
        }
    }
    
    override fun onStopListening() {
        super.onStopListening()
    }
}

