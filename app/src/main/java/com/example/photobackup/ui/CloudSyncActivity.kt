package com.example.photobackup.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.photobackup.api.UploadApi
import com.example.photobackup.data.PhotoBackupDatabase
import com.example.photobackup.databinding.ActivityCloudSyncBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 云端备份文件列表：勾选后同步到本地
 */
class CloudSyncActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCloudSyncBinding
    private lateinit var cloudSyncAdapter: CloudSyncAdapter
    private val selectedMd5s = mutableSetOf<String>()
    private var categoryId: String? = null
    private var cloudList: List<com.example.photobackup.data.BackedUpPhoto> = emptyList()

    private val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        val destPath = resolveTreeUriToPath(uri) ?: run {
            Toast.makeText(this, "请选择主存储下的文件夹", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        val destDir = File(destPath)
        if (!destDir.exists() || !destDir.isDirectory) {
            Toast.makeText(this, "所选路径不存在或不是目录", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        val selected = cloudList.filter { selectedMd5s.contains(it.md5) }
        if (selected.isEmpty()) {
            Toast.makeText(this, getString(com.example.photobackup.R.string.please_select_files), Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        lifecycleScope.launch {
            var successCount = 0
            var failCount = 0
            withContext(Dispatchers.IO) {
                selected.forEach { photo ->
                    val backupDir = photo.uploadUrl ?: ""
                    val file = UploadApi.findBackupFile(backupDir, photo.fileName)
                    if (file != null) {
                        val ok = UploadApi.copyFromBackupToLocal(file, destDir)
                        if (ok) successCount++ else failCount++
                    } else {
                        failCount++
                    }
                }
            }
            if (successCount > 0) {
                Toast.makeText(this@CloudSyncActivity, getString(com.example.photobackup.R.string.sync_success_count, successCount), Toast.LENGTH_SHORT).show()
            }
            if (failCount > 0) {
                Toast.makeText(this@CloudSyncActivity, getString(com.example.photobackup.R.string.sync_fail_count, failCount), Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCloudSyncBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        categoryId = intent.getStringExtra(EXTRA_CATEGORY_ID)

        binding.recyclerCloudFiles.layoutManager = LinearLayoutManager(this)
        cloudSyncAdapter = CloudSyncAdapter(selectedMd5s) { invalidateOptionsMenu() }
        binding.recyclerCloudFiles.adapter = cloudSyncAdapter

        binding.btnSyncToLocal.setOnClickListener {
            if (selectedMd5s.isEmpty()) {
                Toast.makeText(this, getString(com.example.photobackup.R.string.please_select_files), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            folderPickerLauncher.launch(null)
        }

        loadCloudList()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(com.example.photobackup.R.menu.cloud_sync_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val allSelected = cloudList.isNotEmpty() && selectedMd5s.size == cloudList.size
        menu.findItem(com.example.photobackup.R.id.action_select_all)?.title =
            getString(if (allSelected) com.example.photobackup.R.string.deselect_all else com.example.photobackup.R.string.select_all)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            com.example.photobackup.R.id.action_select_all -> {
                if (selectedMd5s.size == cloudList.size) {
                    selectedMd5s.clear()
                } else {
                    selectedMd5s.clear()
                    selectedMd5s.addAll(cloudList.map { it.md5 })
                }
                cloudSyncAdapter.notifyItemRangeChanged(0, cloudSyncAdapter.itemCount)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadCloudList() {
        val cid = categoryId ?: return
        lifecycleScope.launch {
            cloudList = withContext(Dispatchers.IO) {
                PhotoBackupDatabase.getDatabase(this@CloudSyncActivity).backedUpPhotoDao().getByCategoryId(cid)
            }
            cloudSyncAdapter.submitList(cloudList)
            if (cloudList.isEmpty()) {
                Toast.makeText(this@CloudSyncActivity, getString(com.example.photobackup.R.string.no_cloud_files), Toast.LENGTH_SHORT).show()
            }
        }
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

    companion object {
        const val EXTRA_CATEGORY_ID = "category_id"
    }
}
