package com.example.photobackup.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSettings()
        refreshTaskStatus()
        refreshStatistics()
        binding.btnSaveSettings.setOnClickListener { saveSettings() }
        binding.btnStartBackup.setOnClickListener { startPeriodicBackup() }
        binding.btnStopBackup.setOnClickListener { stopBackup() }
    }

    private fun loadSettings() {
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
        val categoryRepo = com.example.photobackup.data.CategoryRepository(requireContext())
        val categories = categoryRepo.getCategories()
        val allFolders = categories.flatMap { it.backupFolders }.distinct()
        val allDestinations = categories.flatMap { it.effectiveBackupDestinations() }.distinct()
        if (allFolders.isEmpty()) {
            Toast.makeText(requireContext(), "请先在首页为类别添加备份文件夹", Toast.LENGTH_LONG).show()
            return
        }
        if (allDestinations.isEmpty()) {
            Toast.makeText(requireContext(), "请先在类别详情中通过「编辑」添加备份目标目录", Toast.LENGTH_LONG).show()
            return
        }
        val interval = (binding.etIntervalMinutes.text.toString().toLongOrNull() ?: 1440L).coerceAtLeast(15L)
        val config = PhotoBackupManager.BackupConfig(
            backupFolders = allFolders,
            backupDestinations = allDestinations,
            intervalMinutes = interval,
            requiresNetwork = binding.cbRequiresNetwork.isChecked,
            requiresCharging = binding.cbRequiresCharging.isChecked
        )
        PhotoBackupManager.getInstance(requireContext()).setupPeriodicBackup(config)
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
        refreshTaskStatus()
        refreshStatistics()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scope.cancel()
        _binding = null
    }

    companion object {
        private const val PREFS_NAME = "photo_backup_prefs"
        private const val PREF_INTERVAL_MINUTES = "interval_minutes"
        private const val PREF_SYNC_INTERVAL_MINUTES = "sync_interval_minutes"
        private const val PREF_REQUIRES_NETWORK = "requires_network"
        private const val PREF_REQUIRES_CHARGING = "requires_charging"
    }
}
