package com.example.photobackup

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.photobackup.databinding.ActivityMainBinding
import com.example.photobackup.manager.PhotoBackupManager
import com.example.photobackup.sync.SyncHelper
import com.example.photobackup.util.AppLogger
import com.example.photobackup.util.AutostartHelper
import com.example.photobackup.util.PermissionHelper
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val backupManager: PhotoBackupManager by lazy {
        PhotoBackupManager.getInstance(this)
    }
    
    // 权限请求启动器
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        try {
            val allGranted = permissions.all { it.value }
            if (allGranted) {
                Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "需要权限才能备份照片", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            AppLogger.e("MainActivity", "Error in permission callback", e)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            AppLogger.d("MainActivity", "应用启动: onCreate")
            
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            setupViews()
            
            // 初始化账号同步机制 (默认 60 分钟)
            SyncHelper.setupSync(this, 60)
            
            // 检查自启动和关联启动权限
            checkAutostartAndBattery()
            
            AppLogger.d("MainActivity", "界面初始化完成")
        } catch (e: Exception) {
            AppLogger.e("MainActivity", "onCreate 发生严重错误", e)
            e.printStackTrace()
            try {
                Toast.makeText(this, "应用初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
            } catch (toastException: Exception) {
                AppLogger.e("MainActivity", "无法显示错误提示 Toast", toastException)
            }
            finish()
        }
    }
    
    private fun checkAutostartAndBattery() {
        if (!AutostartHelper.isIgnoringBatteryOptimizations(this)) {
            AppLogger.d("MainActivity", "显示自启动引导弹窗")
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("需要后台保活权限")
                .setMessage("为了保证备份任务在后台正常运行，请允许应用“忽略电池优化”并开启“自启动/关联启动”权限。")
                .setPositiveButton("前往设置") { _, _ ->
                    AppLogger.d("MainActivity", "用户点击前往设置")
                    AutostartHelper.requestIgnoreBatteryOptimizations(this)
                    AutostartHelper.openAutoStartSettings(this)
                }
                .setNegativeButton("稍后再说") { _, _ ->
                    AppLogger.d("MainActivity", "用户点击稍后再说")
                }
                .show()
        }
    }

    private fun setupViews() {
        try {
            binding.btnStartBackup.setOnClickListener {
                AppLogger.d("MainActivity", "点击 [启动定时备份] 按钮")
                checkPermissionAndSetup()
            }
            
            binding.btnStopBackup.setOnClickListener {
                AppLogger.d("MainActivity", "点击 [停止备份任务] 按钮")
                try {
                    backupManager.cancelPeriodicBackup()
                    Toast.makeText(this, "已停止备份任务", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    AppLogger.e("MainActivity", "停止备份任务失败", e)
                    Toast.makeText(this, "停止备份失败", Toast.LENGTH_SHORT).show()
                }
            }
            
            binding.btnTestBackup.setOnClickListener {
                AppLogger.d("MainActivity", "点击 [测试立即备份] 按钮")
                if (checkPermission()) {
                    triggerTestBackup()
                } else {
                    requestPermission()
                }
            }
            
            AppLogger.d("MainActivity", "按钮事件绑定完成")
        } catch (e: Exception) {
            AppLogger.e("MainActivity", "setupViews 失败", e)
        }
    }

    private fun checkPermissionAndSetup() {
        if (checkPermission()) {
            setupBackupIfReady()
        } else {
            requestPermission()
        }
    }
    
    private fun checkPermission(): Boolean {
        val hasMediaPermission = PermissionHelper.hasReadMediaImagesPermission(this)
        val hasNotificationPermission = PermissionHelper.hasNotificationPermission(this)
        val hasAllFilesPermission = PermissionHelper.hasAllFilesAccessPermission()
        
        AppLogger.d("MainActivity", "权限检查结果: 媒体=$hasMediaPermission, 通知=$hasNotificationPermission, 所有文件访问=$hasAllFilesPermission")
        return hasMediaPermission && hasNotificationPermission && hasAllFilesPermission
    }
    
    private fun requestPermission() {
        try {
            AppLogger.d("MainActivity", "正在请求必要的运行时权限")
            // 1. 先请求常规运行时权限 (媒体访问、通知)
            val permissions = PermissionHelper.getRequiredPermissions()
            val missingPermissions = permissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }

            if (missingPermissions.isNotEmpty()) {
                AppLogger.d("MainActivity", "缺失运行时权限: ${missingPermissions.joinToString(",")}")
                permissionLauncher.launch(missingPermissions.toTypedArray())
            } 
            
            // 2. 如果是 Android 11+，且没有全文件访问权限，引导去设置页
            if (!PermissionHelper.hasAllFilesAccessPermission()) {
                AppLogger.d("MainActivity", "缺失所有文件访问权限，引导去设置页面")
                Toast.makeText(this, "由于 Android 系统限制，请授予所有文件访问权限以进行备份", Toast.LENGTH_LONG).show()
                PermissionHelper.requestAllFilesAccessPermission(this)
            }
        } catch (e: Exception) {
            AppLogger.e("MainActivity", "请求权限流程发生异常", e)
            Toast.makeText(this, "请求权限失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupBackupIfReady() {
        try {
            if (!::binding.isInitialized) {
                AppLogger.e("MainActivity", "binding 未初始化，无法继续设置备份")
                Toast.makeText(this, "界面未初始化", Toast.LENGTH_SHORT).show()
                return
            }
            
            val backupFoldersStr = binding.etBackupFolder.text.toString().trim()
            val backupDestination = binding.etBackupDestination.text.toString().trim()
            
            if (backupFoldersStr.isEmpty()) {
                Toast.makeText(this, "请输入要备份的照片文件夹路径", Toast.LENGTH_SHORT).show()
                return
            }
            
            if (backupDestination.isEmpty()) {
                Toast.makeText(this, "请输入备份目标目录路径", Toast.LENGTH_SHORT).show()
                return
            }
            
            // 初始化日志
            AppLogger.init(backupDestination)
            
            // 解析多文件夹路径
            val backupFolders = backupFoldersStr.split(Regex("[,，]")).map { it.trim() }.filter { it.isNotEmpty() }
            if (backupFolders.isEmpty()) {
                AppLogger.w("MainActivity", "解析后的文件夹列表为空")
                Toast.makeText(this, "请输入有效的文件夹路径", Toast.LENGTH_SHORT).show()
                return
            }
            
            AppLogger.d("MainActivity", "解析备份文件夹成功: $backupFolders, 目标: $backupDestination")
            
            val intervalMinutes = binding.etIntervalHours.text.toString().toLongOrNull() ?: 1440L
            
            val config = PhotoBackupManager.BackupConfig(
                backupFolders = backupFolders,
                backupDestination = backupDestination,
                intervalMinutes = intervalMinutes,
                requiresNetwork = binding.cbRequiresNetwork.isChecked,
                requiresCharging = binding.cbRequiresCharging.isChecked
            )
            
            backupManager.setupPeriodicBackup(config)
            
            // 同步账号的间隔也跟备份间隔保持一致（或设为固定值）
            SyncHelper.setupSync(this, intervalMinutes)
            
            Toast.makeText(this, "定时备份任务已启动", Toast.LENGTH_SHORT).show()
            AppLogger.d("MainActivity", "定时备份任务与账号同步已配置完毕")
        } catch (e: Exception) {
            AppLogger.e("MainActivity", "设置备份任务失败", e)
            Toast.makeText(this, "启动备份失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun triggerTestBackup() {
        try {
            if (!::binding.isInitialized) {
                AppLogger.e("MainActivity", "binding 未初始化，无法触发测试备份")
                Toast.makeText(this, "界面未初始化", Toast.LENGTH_SHORT).show()
                return
            }
            
            val backupFoldersStr = binding.etBackupFolder.text.toString().trim()
            val backupDestination = binding.etBackupDestination.text.toString().trim()
            
            if (backupFoldersStr.isEmpty()) {
                Toast.makeText(this, "请输入要备份的照片文件夹路径", Toast.LENGTH_SHORT).show()
                return
            }
            
            if (backupDestination.isEmpty()) {
                Toast.makeText(this, "请输入备份目标目录路径", Toast.LENGTH_SHORT).show()
                return
            }
            
            // 初始化日志
            AppLogger.init(backupDestination)
            
            val backupFolders = backupFoldersStr.split(Regex("[,，]")).map { it.trim() }.filter { it.isNotEmpty() }
            
            val config = PhotoBackupManager.BackupConfig(
                backupFolders = backupFolders,
                backupDestination = backupDestination,
                intervalMinutes = 15, // 测试备份间隔设为 15 分钟（仅作为 config 占位，OneTimeWork 会立即执行）
                requiresNetwork = binding.cbRequiresNetwork.isChecked,
                requiresCharging = false
            )
            
            backupManager.triggerBackupNow(config)
            Toast.makeText(this, "已触发单次备份任务", Toast.LENGTH_SHORT).show()
            AppLogger.d("MainActivity", "单次测试备份任务已触发")
        } catch (e: Exception) {
            AppLogger.e("MainActivity", "触发单次备份失败", e)
            Toast.makeText(this, "触发备份失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}


