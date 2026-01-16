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
import com.example.photobackup.util.PermissionHelper
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val backupManager = PhotoBackupManager.getInstance(this)
    
    // 权限请求启动器
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show()
            setupBackupIfReady()
        } else {
            Toast.makeText(this, "需要权限才能备份照片", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupViews()
        checkPermissionAndSetup()
    }
    
    private fun setupViews() {
        binding.btnStartBackup.setOnClickListener {
            checkPermissionAndSetup()
        }
        
        binding.btnStopBackup.setOnClickListener {
            backupManager.cancelPeriodicBackup()
            Toast.makeText(this, "已停止备份任务", Toast.LENGTH_SHORT).show()
        }
        
        binding.btnTestBackup.setOnClickListener {
            if (checkPermission()) {
                triggerTestBackup()
            } else {
                requestPermission()
            }
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
        return PermissionHelper.hasReadMediaImagesPermission(this)
    }
    
    private fun requestPermission() {
        val permissions = PermissionHelper.getRequiredPermissions()
        permissions.forEach { permission ->
            permissionLauncher.launch(permission)
        }
    }
    
    private fun setupBackupIfReady() {
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
    }
    
    private fun triggerTestBackup() {
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
        
        val config = PhotoBackupManager.BackupConfig(
            backupFolder = backupFolder,
            backupDestination = backupDestination,
            intervalHours = 24,
            requiresNetwork = binding.cbRequiresNetwork.isChecked,
            requiresCharging = false
        )
        
        backupManager.triggerBackupNow(config)
        Toast.makeText(this, "已触发备份任务", Toast.LENGTH_SHORT).show()
    }
}


