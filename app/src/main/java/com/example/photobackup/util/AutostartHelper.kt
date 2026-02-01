package com.example.photobackup.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.example.photobackup.util.AppLogger

/**
 * 引导用户开启自启动、关联启动和忽略电池优化的工具类
 */
object AutostartHelper {

    private const val TAG = "AutostartHelper"

    /**
     * 检查是否已忽略电池优化
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }

    /**
     * 请求忽略电池优化 (白名单)
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isIgnoringBatteryOptimizations(context)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:${context.packageName}")
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } catch (e: Exception) {
                AppLogger.e(TAG, "请求忽略电池优化失败", e)
            }
        }
    }

    /**
     * 引导用户前往自启动/关联启动设置页面 (针对主流国产手机)
     */
    fun openAutoStartSettings(context: Context) {
        val brand = Build.BRAND.lowercase()
        val intent = Intent()
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        try {
            when {
                // 小米 / 红米 / POCO 共用 MIUI 安全中心
                brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") -> {
                    intent.component = ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                }
                // OPPO / Realme 共用 ColorOS 安全中心
                brand.contains("oppo") || brand.contains("realme") -> {
                    intent.component = ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                }
                // Vivo / iQOO 共用权限管理
                brand.contains("vivo") || brand.contains("iqoo") -> {
                    intent.component = ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    )
                }
                brand.contains("huawei") || brand.contains("honor") -> {
                    intent.component = ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.process.ProtectActivity"
                    )
                }
                else -> {
                    // 通用设置页面
                    intent.action = Settings.ACTION_SETTINGS
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            AppLogger.e(TAG, "跳转自启动设置页面失败: $brand", e)
            try {
                // 如果特定页面跳转失败，尝试跳转到应用详情页
                val detailIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                detailIntent.data = Uri.parse("package:${context.packageName}")
                detailIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(detailIntent)
            } catch (ex: Exception) {
                AppLogger.e(TAG, "跳转应用详情页失败", ex)
            }
        }
    }
}

