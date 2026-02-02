package com.example.photobackup

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.photobackup.databinding.ActivityMainBinding
import com.example.photobackup.sync.SyncHelper
import com.example.photobackup.ui.HomeFragment
import com.example.photobackup.ui.SettingsFragment
import com.example.photobackup.util.AppLogger
import com.example.photobackup.util.AutostartHelper
import com.example.photobackup.util.PermissionHelper

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy {
        getSharedPreferences("photo_backup_prefs", Context.MODE_PRIVATE)
    }

    private enum class PendingAction { NONE, CONFIGURE_SYNC }
    private var pendingAction: PendingAction = PendingAction.NONE

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        try {
            if (permissions.all { it.value }) {
                Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "需要权限才能备份", Toast.LENGTH_LONG).show()
            }
            handlePendingAction()
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

            supportActionBar?.title = getString(R.string.app_name)
            setupBottomNav()
            if (savedInstanceState == null) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.container_main, HomeFragment())
                    .commit()
            }

            pendingAction = PendingAction.CONFIGURE_SYNC
            handlePendingAction()
            checkAutostartAndBattery()
            AppLogger.d("MainActivity", "界面初始化完成")
        } catch (e: Exception) {
            AppLogger.e("MainActivity", "onCreate 发生严重错误", e)
            e.printStackTrace()
            Toast.makeText(this, "应用初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> return@setOnItemSelectedListener false
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.container_main, fragment)
                .commit()
            true
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        AppLogger.d("MainActivity", "onNewIntent called - Activity brought to foreground from Tile")
        // 当从 Tile 启动时，如果 Activity 已经存在，会调用 onNewIntent
        // 确保 Activity 被带到前台并刷新状态
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        handlePendingAction()
    }

    private fun checkAutostartAndBattery() {
        if (!AutostartHelper.isIgnoringBatteryOptimizations(this)) {
            AlertDialog.Builder(this)
                .setTitle("需要后台保活权限")
                .setMessage("为了保证备份任务在后台正常运行，请允许应用“忽略电池优化”并开启“自启动/关联启动”权限。")
                .setPositiveButton("前往设置") { _, _ ->
                    AutostartHelper.requestIgnoreBatteryOptimizations(this)
                    AutostartHelper.openAutoStartSettings(this)
                }
                .setNegativeButton("稍后再说") { _, _ -> }
                .show()
        }
    }

    private fun ensurePrerequisites(): Boolean {
        if (!checkPermission()) {
            requestPermission()
            return false
        }
        if (!AutostartHelper.isIgnoringBatteryOptimizations(this)) {
            AlertDialog.Builder(this)
                .setTitle("需要后台保活权限")
                .setMessage("为保证定时同步/备份稳定执行，请完成：1) 忽略电池优化 2) 开启自启动。")
                .setPositiveButton("前往设置") { _, _ ->
                    AutostartHelper.requestIgnoreBatteryOptimizations(this)
                    AutostartHelper.openAutoStartSettings(this)
                }
                .setNegativeButton("取消") { _, _ -> pendingAction = PendingAction.NONE }
                .show()
            return false
        }
        return true
    }

    private fun handlePendingAction() {
        if (pendingAction == PendingAction.NONE) return
        if (!ensurePrerequisites()) return
        when (pendingAction) {
            PendingAction.CONFIGURE_SYNC -> {
                val syncInterval = prefs.getLong("sync_interval_minutes", 60L).coerceAtLeast(15L)
                SyncHelper.setupSync(this, syncInterval)
            }
            PendingAction.NONE -> Unit
        }
        pendingAction = PendingAction.NONE
    }

    private fun checkPermission(): Boolean {
        return PermissionHelper.hasReadMediaImagesPermission(this) &&
            PermissionHelper.hasNotificationPermission(this) &&
            PermissionHelper.hasWriteExternalStoragePermission(this) &&
            PermissionHelper.hasAllFilesAccessPermission()
    }

    private fun requestPermission() {
        val permissions = PermissionHelper.getRequiredPermissions()
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !PermissionHelper.hasAllFilesAccessPermission()) {
            Toast.makeText(this, "请授予「所有文件访问权限」以备份到自定义目录及写入云端日志 (PhotoBackup/logs)", Toast.LENGTH_LONG).show()
            PermissionHelper.requestAllFilesAccessPermission(this)
        }
    }
}
