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
            AppLogger.d("AppTileService", "Tile clicked, attempting to start MainActivity")
            
            val intent = Intent(this, MainActivity::class.java).apply {
                // 必须的 flags，用于从后台服务启动 Activity
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // 如果 Activity 已经在运行，将其带到前台而不是创建新实例
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                // 配合 singleTask launchMode，确保正确的行为
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                // 确保 Activity 被带到前台（某些定制系统需要）
                addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            
            // 尝试使用 startActivityAndCollapse（Android 7.0+）
            // 它会自动收起下拉通知栏并启动 Activity
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    startActivityAndCollapse(intent)
                    AppLogger.d("AppTileService", "Successfully started MainActivity via startActivityAndCollapse")
                } else {
                    // Android 7.0 以下使用 startActivity
                    startActivity(intent)
                    AppLogger.d("AppTileService", "Successfully started MainActivity via startActivity")
                }
            } catch (e: SecurityException) {
                // 某些定制系统可能抛出 SecurityException
                AppLogger.w("AppTileService", "SecurityException with startActivityAndCollapse, trying fallback", e)
                try {
                    // Fallback: 直接使用 startActivity
                    startActivity(intent)
                    AppLogger.d("AppTileService", "Successfully started MainActivity via fallback startActivity")
                } catch (e2: Exception) {
                    AppLogger.e("AppTileService", "Failed to start MainActivity with fallback", e2)
                    // 最后的尝试：使用更简单的 Intent
                    try {
                        val simpleIntent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(simpleIntent)
                        AppLogger.d("AppTileService", "Successfully started MainActivity with simple intent")
                    } catch (e3: Exception) {
                        AppLogger.e("AppTileService", "All attempts to start MainActivity failed", e3)
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("AppTileService", "Failed to start MainActivity", e)
                // 尝试 fallback
                try {
                    startActivity(intent)
                    AppLogger.d("AppTileService", "Successfully started MainActivity via exception fallback")
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
                AppLogger.d("AppTileService", "Tile state updated to ACTIVE")
            }
        } catch (e: Exception) {
            AppLogger.e("AppTileService", "Error updating tile state", e)
        }
    }
    
    override fun onStopListening() {
        super.onStopListening()
        AppLogger.d("AppTileService", "Tile stopped listening")
    }
}

