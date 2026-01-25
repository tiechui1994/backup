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
            AppLogger.d("MainActivity", "onCreate started")
            
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            AppLogger.d("MainActivity", "View binding completed")
            
            setupViews()
            AppLogger.d("MainActivity", "Views setup completed")
            
            // 初始化账号同步机制 (拉活保活)
            SyncHelper.setupSync(this)
            AppLogger.d("MainActivity", "SyncHelper setup completed")
            
            // 不在启动时自动检查权限，让用户手动操作
            AppLogger.d("MainActivity", "onCreate completed successfully")
        } catch (e: Exception) {
            AppLogger.e("MainActivity", "Fatal error in onCreate", e)
            e.printStackTrace()
            try {
                Toast.makeText(this, "应用初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
            } catch (toastException: Exception) {
                AppLogger.e("MainActivity", "Failed to show error toast", toastException)
            }
            finish()
        }
    }
    
    private fun setupViews() {
        try {
            binding.btnStartBackup.setOnClickListener {
                checkPermissionAndSetup()
            }
            
            binding.btnStopBackup.setOnClickListener {
                try {
                    backupManager.cancelPeriodicBackup()
                    Toast.makeText(this, "已停止备份任务", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    AppLogger.e("MainActivity", "Error stopping backup", e)
                    Toast.makeText(this, "停止备份失败", Toast.LENGTH_SHORT).show()
                }
            }
            
            binding.btnTestBackup.setOnClickListener {
                if (checkPermission()) {
                    triggerTestBackup()
                } else {
                    requestPermission()
                }
            }
            
            AppLogger.d("MainActivity", "All views setup completed")
        } catch (e: Exception) {
            AppLogger.e("MainActivity", "Error setting up views", e)
            e.printStackTrace()
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
        
        return hasMediaPermission && hasNotificationPermission && hasAllFilesPermission
    }
    
    private fun requestPermission() {
        try {
            // 1. 先请求常规运行时权限 (媒体访问、通知)
            val permissions = PermissionHelper.getRequiredPermissions()
            val missingPermissions = permissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }

            if (missingPermissions.isNotEmpty()) {
                permissionLauncher.launch(missingPermissions.toTypedArray())
            } 
            
            // 2. 如果是 Android 11+，且没有全文件访问权限，引导去设置页
            if (!PermissionHelper.hasAllFilesAccessPermission()) {
                Toast.makeText(this, "由于 Android 系统限制，请授予所有文件访问权限以进行备份", Toast.LENGTH_LONG).show()
                PermissionHelper.requestAllFilesAccessPermission(this)
            }
        } catch (e: Exception) {
            AppLogger.e("MainActivity", "Error requesting permission", e)
            Toast.makeText(this, "请求权限失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupBackupIfReady() {
        try {
            if (!::binding.isInitialized) {
                Toast.makeText(this, "界面未初始化", Toast.LENGTH_SHORT).show()
                return
            }
            
            val backupFolder = binding.etBackupFolder.text.toString().trim()
            val backupDestination = binding.etBackupDestination.text.toString().trim()
            
            if (backupFolder.isEmpty()) {
                Toast.makeText(this, "请输入要备份的照片文件夹路径", Toast.LENGTH_SHORT).show()
                return
            }
            
            if (backupDestination.isEmpty()) {
                Toast.makeText(this, "请输入备份目标目录路径", Toast.LENGTH_SHORT).show()
                return
            }
            
            // 初始化日志
            AppLogger.init(backupDestination)
            
            val intervalHours = binding.etIntervalHours.text.toString().toLongOrNull() ?: 24L
            
            val config = PhotoBackupManager.BackupConfig(
                backupFolder = backupFolder,
                backupDestination = backupDestination,
                intervalHours = intervalHours,
                requiresNetwork = binding.cbRequiresNetwork.isChecked,
                requiresCharging = binding.cbRequiresCharging.isChecked
            )
            
            backupManager.setupPeriodicBackup(config)
            Toast.makeText(this, "定时备份任务已启动", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            AppLogger.e("MainActivity", "Error setting up backup", e)
            Toast.makeText(this, "启动备份失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun triggerTestBackup() {
        try {
            if (!::binding.isInitialized) {
                Toast.makeText(this, "界面未初始化", Toast.LENGTH_SHORT).show()
                return
            }
            
            val backupFolder = binding.etBackupFolder.text.toString().trim()
            val backupDestination = binding.etBackupDestination.text.toString().trim()
            
            if (backupFolder.isEmpty()) {
                Toast.makeText(this, "请输入要备份的照片文件夹路径", Toast.LENGTH_SHORT).show()
                return
            }
            
            if (backupDestination.isEmpty()) {
                Toast.makeText(this, "请输入备份目标目录路径", Toast.LENGTH_SHORT).show()
                return
            }
            
            // 初始化日志
            AppLogger.init(backupDestination)
            
            val config = PhotoBackupManager.BackupConfig(
                backupFolder = backupFolder,
                backupDestination = backupDestination,
                intervalHours = 24,
                requiresNetwork = binding.cbRequiresNetwork.isChecked,
                requiresCharging = false
            )
            
            backupManager.triggerBackupNow(config)
            Toast.makeText(this, "已触发备份任务", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            AppLogger.e("MainActivity", "Error triggering backup", e)
            Toast.makeText(this, "触发备份失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}


