package com.example.photobackup.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.photobackup.data.PhotoBackupDatabase
import com.example.photobackup.databinding.FragmentSettingsBinding
import com.example.photobackup.manager.PhotoBackupManager
import com.example.photobackup.sync.SyncHelper
import com.example.photobackup.util.AutostartHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs by lazy {
        requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    private val backupRootPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            requireContext().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        } catch (_: Exception) {}
        val path = resolveTreeUriToPath(uri)
        if (path.isNullOrBlank()) {
            Toast.makeText(requireContext(), "请选择主存储下的文件夹", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) {
            Toast.makeText(requireContext(), "所选路径不存在或不是目录", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        prefs.edit()
            .putString(PREF_BACKUP_ROOT_DIRECTORY, path)
            .putString(PREF_BACKUP_MODE, BACKUP_MODE_LOCAL)
            .apply()
        binding.tvBackupRoot.text = path
        refreshBackupModeBadges()
        Toast.makeText(requireContext(), "备份目标根目录已设置并设为当前生效", Toast.LENGTH_SHORT).show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSettings()
        refreshTaskStatus()
        refreshStatistics()
        binding.btnSelectBackupRoot.setOnClickListener { backupRootPickerLauncher.launch(null) }
        binding.btnCloudBackupConfig.setOnClickListener {
            startActivity(Intent(requireContext(), CloudBackupConfigActivity::class.java))
        }
        binding.btnSaveSettings.setOnClickListener { saveSettings() }
        binding.btnStartBackup.setOnClickListener { startPeriodicBackup() }
        binding.btnStopBackup.setOnClickListener { stopBackup() }
    }

    private fun resolveTreeUriToPath(uri: Uri): String? {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            when {
                docId.startsWith("primary:") -> {
                    val rel = docId.removePrefix("primary:").trimStart('/')
                    if (rel.isEmpty()) "/storage/emulated/0" else "/storage/emulated/0/$rel"
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun loadSettings() {
        val root = prefs.getString(PREF_BACKUP_ROOT_DIRECTORY, null).orEmpty()
        binding.tvBackupRoot.text = if (root.isEmpty()) getString(com.example.photobackup.R.string.backup_root_not_set) else root
        refreshBackupModeBadges()
        refreshCloudConfigSummary()
        val interval = prefs.getLong(PREF_INTERVAL_MINUTES, 1440L).coerceAtLeast(15L)
        val syncInterval = prefs.getLong(PREF_SYNC_INTERVAL_MINUTES, 60L).coerceAtLeast(15L)
        binding.etIntervalMinutes.setText(interval.toString())
        binding.etSyncIntervalMinutes.setText(syncInterval.toString())
        binding.cbRequiresNetwork.isChecked = prefs.getBoolean(PREF_REQUIRES_NETWORK, false)
        binding.cbRequiresCharging.isChecked = prefs.getBoolean(PREF_REQUIRES_CHARGING, false)
    }

    private fun saveSettings() {
        val interval = (binding.etIntervalMinutes.text.toString().toLongOrNull() ?: 1440L).coerceAtLeast(15L)
        val syncInterval = (binding.etSyncIntervalMinutes.text.toString().toLongOrNull() ?: 60L).coerceAtLeast(15L)
        prefs.edit()
            .putLong(PREF_INTERVAL_MINUTES, interval)
            .putLong(PREF_SYNC_INTERVAL_MINUTES, syncInterval)
            .putBoolean(PREF_REQUIRES_NETWORK, binding.cbRequiresNetwork.isChecked)
            .putBoolean(PREF_REQUIRES_CHARGING, binding.cbRequiresCharging.isChecked)
            .apply()
        SyncHelper.setupSync(requireContext(), syncInterval)
        Toast.makeText(requireContext(), "设置已保存", Toast.LENGTH_SHORT).show()
    }

    private fun refreshBackupModeBadges() {
        val mode = prefs.getString(PREF_BACKUP_MODE, BACKUP_MODE_LOCAL) ?: BACKUP_MODE_LOCAL
        val isLocalActive = mode == BACKUP_MODE_LOCAL
        val activeColor = ContextCompat.getColor(requireContext(), com.example.photobackup.R.color.backup_section_active)
        val inactiveColor = ContextCompat.getColor(requireContext(), com.example.photobackup.R.color.backup_section_inactive)
        binding.tvBackupRootTitle.setTextColor(if (isLocalActive) activeColor else inactiveColor)
        binding.tvCloudConfigTitle.setTextColor(if (isLocalActive) inactiveColor else activeColor)
    }

    private fun refreshCloudConfigSummary() {
        val baseUrl = prefs.getString(PREF_CLOUD_BASE_URL, null).orEmpty().trim()
        val userid = prefs.getString(PREF_CLOUD_USER_ID, null).orEmpty().trim()
        if (baseUrl.isNotEmpty() || userid.isNotEmpty()) {
            binding.tvCloudConfigSummary.visibility = View.VISIBLE
            binding.tvCloudConfigSummary.text = buildString {
                append(getString(com.example.photobackup.R.string.cloud_base_url))
                append("：")
                append(if (baseUrl.isEmpty()) getString(com.example.photobackup.R.string.backup_root_not_set) else baseUrl)
                append("\n")
                append(getString(com.example.photobackup.R.string.cloud_user_id))
                append("：")
                append(if (userid.isEmpty()) getString(com.example.photobackup.R.string.backup_root_not_set) else userid)
            }
        } else {
            binding.tvCloudConfigSummary.visibility = View.GONE
        }
    }

    private fun startPeriodicBackup() {
        if (!AutostartHelper.isIgnoringBatteryOptimizations(requireContext())) {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("需要后台保活权限")
                .setMessage("为保证定时备份稳定执行，请允许应用忽略电池优化并开启自启动权限。")
                .setPositiveButton("前往设置") { _, _ ->
                    AutostartHelper.requestIgnoreBatteryOptimizations(requireContext())
                    AutostartHelper.openAutoStartSettings(requireContext())
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }
        val mode = prefs.getString(PREF_BACKUP_MODE, BACKUP_MODE_LOCAL) ?: BACKUP_MODE_LOCAL
        if (mode == BACKUP_MODE_LOCAL) {
            val backupRoot = prefs.getString(PREF_BACKUP_ROOT_DIRECTORY, null).orEmpty().trim()
            if (backupRoot.isEmpty()) {
                Toast.makeText(requireContext(), getString(com.example.photobackup.R.string.please_set_backup_root), Toast.LENGTH_LONG).show()
                return
            }
        } else {
            val baseUrl = prefs.getString(PREF_CLOUD_BASE_URL, null).orEmpty().trim()
            val userid = prefs.getString(PREF_CLOUD_USER_ID, null).orEmpty().trim()
            if (baseUrl.isEmpty() || userid.isEmpty()) {
                Toast.makeText(requireContext(), getString(com.example.photobackup.R.string.please_set_cloud_config), Toast.LENGTH_LONG).show()
                return
            }
        }
        val categoryRepo = com.example.photobackup.data.CategoryRepository(requireContext())
        val categories = categoryRepo.getCategories().filter { it.backupFolders.isNotEmpty() }
        if (categories.isEmpty()) {
            Toast.makeText(requireContext(), "请先在首页为类别添加备份文件夹", Toast.LENGTH_LONG).show()
            return
        }
        val interval = (binding.etIntervalMinutes.text.toString().toLongOrNull() ?: 1440L).coerceAtLeast(15L)
        val backupRoot = if (mode == BACKUP_MODE_LOCAL) {
            prefs.getString(PREF_BACKUP_ROOT_DIRECTORY, null).orEmpty().trim()
        } else {
            ""
        }
        PhotoBackupManager.getInstance(requireContext()).setupPeriodicBackupFromCategories(
            backupRoot = backupRoot,
            categories = categories,
            intervalMinutes = interval,
            requiresNetwork = binding.cbRequiresNetwork.isChecked,
            requiresCharging = binding.cbRequiresCharging.isChecked,
            useCloudApi = mode == BACKUP_MODE_CLOUD
        )
        val syncInterval = (binding.etSyncIntervalMinutes.text.toString().toLongOrNull() ?: 60L).coerceAtLeast(15L)
        SyncHelper.setupSync(requireContext(), syncInterval)
        saveSettings()
        Toast.makeText(requireContext(), "定时备份已启动", Toast.LENGTH_SHORT).show()
    }

    private fun stopBackup() {
        PhotoBackupManager.getInstance(requireContext()).cancelPeriodicBackup()
        Toast.makeText(requireContext(), "已停止备份任务", Toast.LENGTH_SHORT).show()
    }

    private fun refreshTaskStatus() {
        val time = prefs.getLong("last_run_time", 0L)
        val success = prefs.getBoolean("last_run_success", false)
        val successCount = prefs.getInt("last_success_count", 0)
        val skipCount = prefs.getInt("last_skip_count", 0)
        val failCount = prefs.getInt("last_fail_count", 0)
        val error = prefs.getString("last_run_error", null)
        val timeStr = if (time > 0) {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(time))
        } else "暂无"
        val status = if (success) "成功 · 成功:$successCount 跳过:$skipCount 失败:$failCount" else (error ?: "失败")
        binding.tvTaskStatus.text = "上次执行：$timeStr\n状态：$status"
    }

    private fun refreshStatistics() {
        scope.launch {
            val total = withContext(Dispatchers.IO) {
                PhotoBackupDatabase.getDatabase(requireContext()).backedUpPhotoDao().getBackupCount()
            }
            binding.tvStatistics.text = "总已备份：$total 个"
        }
    }

    override fun onResume() {
        super.onResume()
        refreshBackupModeBadges()
        refreshCloudConfigSummary()
        refreshTaskStatus()
        refreshStatistics()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scope.cancel()
        _binding = null
    }

    companion object {
        const val PREFS_NAME = "photo_backup_prefs"
        const val PREF_BACKUP_ROOT_DIRECTORY = "backup_root_directory"
        const val PREF_BACKUP_MODE = "backup_mode"
        const val BACKUP_MODE_LOCAL = "local"
        const val BACKUP_MODE_CLOUD = "cloud"
        const val PREF_CLOUD_BASE_URL = "cloud_base_url"
        const val PREF_CLOUD_USER_ID = "cloud_user_id"
        private const val PREF_INTERVAL_MINUTES = "interval_minutes"
        private const val PREF_SYNC_INTERVAL_MINUTES = "sync_interval_minutes"
        private const val PREF_REQUIRES_NETWORK = "requires_network"
        private const val PREF_REQUIRES_CHARGING = "requires_charging"
    }
}
