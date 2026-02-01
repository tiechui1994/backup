package com.example.photobackup.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.photobackup.databinding.ActivityCloudBackupConfigBinding
import com.example.photobackup.manager.PhotoBackupManager

/**
 * 云端备份配置：请求域名、用户 ID
 * 保存后设为当前生效的备份方式（与「备份目标根目录」二选一）
 */
class CloudBackupConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCloudBackupConfigBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCloudBackupConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(com.example.photobackup.R.string.cloud_backup_config)

        val prefs = getSharedPreferences(SettingsFragment.PREFS_NAME, MODE_PRIVATE)
        binding.etCloudBaseUrl.setText(prefs.getString(SettingsFragment.PREF_CLOUD_BASE_URL, "") ?: "")
        binding.etCloudUserId.setText(prefs.getString(SettingsFragment.PREF_CLOUD_USER_ID, "") ?: "")

        binding.btnSaveCloudConfig.setOnClickListener {
            val baseUrl = binding.etCloudBaseUrl.text.toString().trim()
            val userid = binding.etCloudUserId.text.toString().trim()
            if (baseUrl.isEmpty() || userid.isEmpty()) {
                Toast.makeText(this, getString(com.example.photobackup.R.string.please_set_cloud_config), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // 同步目标变动：保存所有云端相关设置，并立即切换底层
            prefs.edit()
                .putString(SettingsFragment.PREF_CLOUD_BASE_URL, baseUrl)
                .putString(SettingsFragment.PREF_CLOUD_USER_ID, userid)
                .putString(SettingsFragment.PREF_BACKUP_MODE, SettingsFragment.BACKUP_MODE_CLOUD)
                .apply()
            PhotoBackupManager.getInstance(this).reapplyPeriodicBackupFromCurrentSettings()
            Toast.makeText(this, "云端备份配置已保存并设为当前生效，设置已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
