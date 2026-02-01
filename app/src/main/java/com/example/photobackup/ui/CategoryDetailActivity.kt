package com.example.photobackup.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.Chip
import com.example.photobackup.data.Category
import com.example.photobackup.data.CategoryRepository
import com.example.photobackup.data.PhotoBackupDatabase
import com.example.photobackup.databinding.ActivityCategoryDetailBinding
import com.example.photobackup.manager.PhotoBackupManager
import com.example.photobackup.util.AppLogger
import com.example.photobackup.util.FileCountUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class CategoryDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCategoryDetailBinding
    private lateinit var categoryRepository: CategoryRepository
    private var category: Category? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategoryDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        categoryRepository = CategoryRepository(this)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val categoryId = intent.getStringExtra(EXTRA_CATEGORY_ID) ?: run {
            Toast.makeText(this, "无效的类别", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        category = categoryRepository.getCategory(categoryId) ?: run {
            Toast.makeText(this, "类别不存在", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.tvCategoryTitle.text = category!!.name
        renderFolderChips()
        renderDestinationChips()
        refreshStats()

        binding.btnEditFolders.setOnClickListener { folderPickerLauncher.launch(null) }
        binding.btnEditDestinations.setOnClickListener { destinationPickerLauncher.launch(null) }
        binding.btnSyncFromCloud.setOnClickListener { showSyncFromCloudDialog() }
        binding.btnBackupNow.setOnClickListener { triggerBackupNow() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun renderFolderChips() {
        binding.chipgroupFolders.removeAllViews()
        category?.backupFolders?.forEach { folder ->
            val chip = Chip(this).apply {
                text = folder
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    removeFolder(folder)
                }
            }
            binding.chipgroupFolders.addView(chip)
        }
    }

    private fun renderDestinationChips() {
        binding.chipgroupDestinations.removeAllViews()
        category?.effectiveBackupDestinations()?.forEach { path ->
            val chip = Chip(this).apply {
                text = path
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    removeDestination(path)
                }
            }
            binding.chipgroupDestinations.addView(chip)
        }
    }

    private fun removeDestination(path: String) {
        val c = category ?: return
        val list = c.backupDestinations.filterNot { it == path }
        val clearLegacy = c.backupDestination == path
        category = c.copy(backupDestinations = list, backupDestination = if (clearLegacy) "" else c.backupDestination)
        categoryRepository.updateCategory(category!!)
        renderDestinationChips()
        refreshStats()
    }

    private fun removeFolder(folder: String) {
        val c = category ?: return
        val list = c.backupFolders.filterNot { it == folder }
        category = c.copy(backupFolders = list)
        categoryRepository.updateCategory(category!!)
        renderFolderChips()
        refreshStats()
    }

    private fun refreshStats() {
        val c = category ?: return
        scope.launch {
            val backedUp = withContext(Dispatchers.IO) {
                PhotoBackupDatabase.getDatabase(this@CategoryDetailActivity).backedUpPhotoDao()
                    .getBackupCountByCategory(c.id)
            }
            val localCount = FileCountUtil.countLocalFiles(c.backupFolders, c.type)
            binding.tvBackedUp.text = getString(com.example.photobackup.R.string.backed_up_count, backedUp)
            binding.tvLocalCount.text = getString(com.example.photobackup.R.string.local_file_count, localCount)
        }
    }

    private val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        } catch (_: Exception) {}
        val path = resolveTreeUriToPath(uri)
        if (path.isNullOrBlank()) {
            Toast.makeText(this, "请选择主存储下的文件夹", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        if (!File(path).exists() || !File(path).isDirectory) {
            Toast.makeText(this, "所选路径不存在或不是目录，请重新选择", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        val c = category ?: return@registerForActivityResult
        val list = (c.backupFolders + path).distinct()
        category = c.copy(backupFolders = list)
        categoryRepository.updateCategory(category!!)
        renderFolderChips()
        refreshStats()
    }

    private val destinationPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        } catch (_: Exception) {}
        val path = resolveTreeUriToPath(uri)
        if (path.isNullOrBlank()) {
            Toast.makeText(this, "请选择主存储下的文件夹", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) {
            Toast.makeText(this, "所选路径不存在或不是目录，请重新选择", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        val c = category ?: return@registerForActivityResult
        val list = (c.backupDestinations + path).distinct()
        category = c.copy(backupDestinations = list, backupDestination = "")
        categoryRepository.updateCategory(category!!)
        renderDestinationChips()
        refreshStats()
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
            AppLogger.w("CategoryDetail", "resolveTreeUriToPath: ${e.message}")
            null
        }
    }

    private fun showSyncFromCloudDialog() {
        val cid = category?.id ?: return
        startActivity(Intent(this, CloudSyncActivity::class.java).apply {
            putExtra(CloudSyncActivity.EXTRA_CATEGORY_ID, cid)
        })
    }

    private fun triggerBackupNow() {
        val c = category ?: return
        if (c.backupFolders.isEmpty()) {
            Toast.makeText(this, "请先添加要备份的文件夹", Toast.LENGTH_SHORT).show()
            return
        }
        val dests = c.effectiveBackupDestinations()
        if (dests.isEmpty()) {
            Toast.makeText(this, "请先添加备份目标目录（通过编辑选择真实存在的文件夹）", Toast.LENGTH_SHORT).show()
            return
        }
        val prefs = getSharedPreferences("photo_backup_prefs", MODE_PRIVATE)
        val config = PhotoBackupManager.BackupConfig(
            backupFolders = c.backupFolders,
            backupDestinations = dests,
            intervalMinutes = 15,
            requiresNetwork = prefs.getBoolean("requires_network", false),
            requiresCharging = false
        )
        PhotoBackupManager.getInstance(this).triggerBackupNow(config, c.id)
        Toast.makeText(this, "已触发备份任务", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CATEGORY_ID = "category_id"
    }
}
