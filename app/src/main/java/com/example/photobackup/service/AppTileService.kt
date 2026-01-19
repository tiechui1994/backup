package com.example.photobackup.service

import android.content.Intent
import android.service.quicksettings.TileService
import com.example.photobackup.MainActivity

/**
 * 快捷设置开关服务，允许用户从下拉通知栏打开应用
 */
class AppTileService : TileService() {
    
    override fun onClick() {
        super.onClick()
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            // 在 Android 10+ 中，推荐使用 startActivityAndCollapse
            // 它会自动收起下拉通知栏并启动 Activity
            startActivityAndCollapse(intent)
        } catch (e: Exception) {
            android.util.Log.e("AppTileService", "Failed to start MainActivity", e)
        }
    }
    
    override fun onStartListening() {
        super.onStartListening()
        // 可以在这里更新 Tile 的状态（如启用/禁用）
        val tile = qsTile
        if (tile != null) {
            tile.state = android.service.quicksettings.Tile.STATE_ACTIVE
            tile.updateTile()
        }
    }
}

