package com.example.photobackup

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.example.photobackup.databinding.ActivityMainBinding
import com.example.photobackup.manager.PhotoBackupManager
import com.example.photobackup.sync.SyncHelper
import com.example.photobackup.util.AppLogger
import com.example.photobackup.util.AutostartHelper
import com.example.photobackup.util.PermissionHelper

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val backupManager: PhotoBackupManager by lazy {
        PhotoBackupManager.getInstance(this)
    }

    private val prefs by lazy {
        getSharedPreferences("photo_backup_prefs", Context.MODE_PRIVATE)
    }

    private val selectedBackupFolders = mutableListOf<String>()

    private companion object {
        private const val PREF_BACKUP_FOLDERS = "backup_folders"
        private const val PREF_BACKUP_DESTINATION = "backup_destination"
        private const val PREF_INTERVAL_MINUTES = "interval_minutes"
        private const val PREF_REQUIRES_NETWORK = "requires_network"
        private const val PREF_REQUIRES_CHARGING = "requires_charging"
        private const val PREF_SYNC_INTERVAL_MINUTES = "sync_interval_minutes"
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

            restoreUiState()
            setupFolderUi()
            setupViews()

            // 初始化账号同步机制（使用上次设置；没有则默认 60 分钟）
            // 不再对最小间隔做 15 分钟限制（由系统自行调度/裁剪）
            val syncInterval = prefs.getLong(PREF_SYNC_INTERVAL_MINUTES, 60L)
            SyncHelper.setupSync(this, syncInterval)
            
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

    private fun restoreUiState() {
        try {
            val savedFolders = prefs.getString(PREF_BACKUP_FOLDERS, "") ?: ""
            val folders = parseFolders(savedFolders)
            setSelectedBackupFolders(folders)

            binding.etBackupDestination.setText(prefs.getString(PREF_BACKUP_DESTINATION, binding.etBackupDestination.text?.toString() ?: "") ?: "")
            val intervalMinutes = prefs.getLong(PREF_INTERVAL_MINUTES, 1440L)
            binding.etIntervalMinutes.setText(intervalMinutes.toString())

            binding.cbRequiresNetwork.isChecked = prefs.getBoolean(PREF_REQUIRES_NETWORK, false)
            binding.cbRequiresCharging.isChecked = prefs.getBoolean(PREF_REQUIRES_CHARGING, false)

            val syncIntervalMinutes = prefs.getLong(PREF_SYNC_INTERVAL_MINUTES, 60L)
            binding.etSyncIntervalMinutes.setText(syncIntervalMinutes.toString())
        } catch (e: Exception) {
            AppLogger.e("MainActivity", "恢复界面状态失败", e)
        }
    }

    private fun saveUiState(
        backupFolders: List<String>,
        backupDestination: String,
        intervalMinutes: Long,
        requiresNetwork: Boolean,
        requiresCharging: Boolean,
        syncIntervalMinutes: Long
    ) {
        prefs.edit()
            .putString(PREF_BACKUP_FOLDERS, backupFolders.joinToString("\n"))
            .putString(PREF_BACKUP_DESTINATION, backupDestination)
            .putLong(PREF_INTERVAL_MINUTES, intervalMinutes)
            .putBoolean(PREF_REQUIRES_NETWORK, requiresNetwork)
            .putBoolean(PREF_REQUIRES_CHARGING, requiresCharging)
            .putLong(PREF_SYNC_INTERVAL_MINUTES, syncIntervalMinutes)
            .apply()
    }

    private fun setupFolderUi() {
        binding.btnAddBackupFolder.setOnClickListener {
            folderPickerLauncher.launch(null)
        }
    }

    // 文件夹选择器（系统文件管理器）
    private val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        try {
            if (uri == null) return@registerForActivityResult

            // 尝试持久化权限（避免下次失效）
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, flags)
            } catch (e: Exception) {
                AppLogger.w("MainActivity", "takePersistableUriPermission 失败（可忽略）: ${e.message}")
            }

            val path = resolveTreeUriToPath(uri)
            if (path.isNullOrBlank()) {
                Toast.makeText(this, "暂不支持该位置，请选择主存储(内部存储)的文件夹", Toast.LENGTH_LONG).show()
                AppLogger.w("MainActivity", "无法将 TreeUri 转换为真实路径: $uri")
                return@registerForActivityResult
            }

            val next = (selectedBackupFolders + path).distinct()
            setSelectedBackupFolders(next)
        } catch (e: Exception) {
            AppLogger.e("MainActivity", "处理文件夹选择结果失败", e)
            Toast.makeText(this, "选择文件夹失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 将 SAF TreeUri 尽可能转换为真实文件路径（只支持主存储 primary:xxx）
     * 例：content://.../tree/primary%3ADCIM%2FCamera -> /storage/emulated/0/DCIM/Camera
     */
    private fun resolveTreeUriToPath(uri: Uri): String? {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(uri) // e.g. "primary:DCIM/Camera"
            when {
                docId.startsWith("primary:") -> {
                    val rel = docId.removePrefix("primary:").trimStart('/')
                    if (rel.isEmpty()) "/storage/emulated/0" else "/storage/emulated/0/$rel"
                }
                else -> null
            }
        } catch (e: Exception) {
            AppLogger.w("MainActivity", "resolveTreeUriToPath 异常: ${e.message}")
            null
        }
    }

    private fun parseFolders(raw: String): List<String> {
        return raw
            .split(Regex("[,，\\n;；]"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun setSelectedBackupFolders(folders: List<String>) {
        selectedBackupFolders.clear()
        selectedBackupFolders.addAll(folders)
        // 同步到输入框，便于用户复制/粘贴、也便于旧逻辑继续工作
        binding.etBackupFolder.setText(selectedBackupFolders.joinToString("\n"))
        renderFolderChips()
    }

    private fun renderFolderChips() {
        try {
            binding.chipgroupBackupFolders.removeAllViews()
            selectedBackupFolders.forEach { folder ->
                val chip = Chip(this).apply {
                    text = folder
                    isCloseIconVisible = true
                    setOnCloseIconClickListener {
                        val next = selectedBackupFolders.filterNot { it == folder }
                        setSelectedBackupFolders(next)
                    }
                }
                binding.chipgroupBackupFolders.addView(chip)
            }
        } catch (e: Exception) {
            AppLogger.e("MainActivity", "渲染文件夹 Chip 失败", e)
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
            val backupFolders = parseFolders(backupFoldersStr)
            if (backupFolders.isEmpty()) {
                AppLogger.w("MainActivity", "解析后的文件夹列表为空")
                Toast.makeText(this, "请输入有效的文件夹路径", Toast.LENGTH_SHORT).show()
                return
            }

            // 规范化并回显到 chips / 输入框
            setSelectedBackupFolders(backupFolders)
            
            AppLogger.d("MainActivity", "解析备份文件夹成功: $backupFolders, 目标: $backupDestination")
            
            // 注意：界面输入的是“分钟”
            val intervalMinutes = (binding.etIntervalMinutes.text.toString().toLongOrNull() ?: 1440L)
                .coerceAtLeast(15L)

            // 账号同步间隔（分钟）；留空则跟随备份间隔
            val syncIntervalText = binding.etSyncIntervalMinutes.text?.toString()?.trim().orEmpty()
            val syncIntervalMinutes = (syncIntervalText.toLongOrNull() ?: intervalMinutes).coerceAtLeast(15L)
            
            val config = PhotoBackupManager.BackupConfig(
                backupFolders = backupFolders,
                backupDestination = backupDestination,
                intervalMinutes = intervalMinutes,
                requiresNetwork = binding.cbRequiresNetwork.isChecked,
                requiresCharging = binding.cbRequiresCharging.isChecked
            )
            
            backupManager.setupPeriodicBackup(config)
            
            // 同步账号的间隔可单独设置
            SyncHelper.setupSync(this, syncIntervalMinutes)

            saveUiState(
                backupFolders = backupFolders,
                backupDestination = backupDestination,
                intervalMinutes = intervalMinutes,
                requiresNetwork = binding.cbRequiresNetwork.isChecked,
                requiresCharging = binding.cbRequiresCharging.isChecked,
                syncIntervalMinutes = syncIntervalMinutes
            )
            
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
            
            val backupFolders = parseFolders(backupFoldersStr)
            if (backupFolders.isEmpty()) {
                Toast.makeText(this, "请输入有效的文件夹路径", Toast.LENGTH_SHORT).show()
                return
            }
            setSelectedBackupFolders(backupFolders)
            
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


