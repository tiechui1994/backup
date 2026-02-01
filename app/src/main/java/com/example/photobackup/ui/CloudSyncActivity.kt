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
import com.example.photobackup.api.CloudBackupApi
import com.example.photobackup.api.UploadApi
import com.example.photobackup.data.CategoryRepository
import com.example.photobackup.data.PhotoBackupDatabase
import com.example.photobackup.databinding.ActivityCloudSyncBinding
import com.example.photobackup.ui.SettingsFragment
import com.example.photobackup.util.FileHashUtil
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
    private var alreadySyncedMd5s: Set<String> = emptySet()

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
        val cid = categoryId
        val categoryName = if (cid != null) CategoryRepository(this).getCategory(cid)?.name else null
        val prefs = getSharedPreferences(SettingsFragment.PREFS_NAME, MODE_PRIVATE)
        val baseUrl = prefs.getString(SettingsFragment.PREF_CLOUD_BASE_URL, null).orEmpty().trim()
        val userid = prefs.getString(SettingsFragment.PREF_CLOUD_USER_ID, null).orEmpty().trim()

        lifecycleScope.launch {
            var successCount = 0
            var failCount = 0
            withContext(Dispatchers.IO) {
                selected.forEach { photo ->
                    val ok = when {
                        photo.uploadUrl == "cloud" -> {
                            if (baseUrl.isEmpty() || userid.isEmpty() || categoryName == null) false
                            else {
                                val bytes = CloudBackupApi.download(baseUrl, userid, categoryName, photo.fileName)
                                if (bytes != null) {
                                    val outFile = File(destDir, photo.fileName)
                                    val finalFile = if (outFile.exists()) {
                                        val base = photo.fileName.substringBeforeLast('.', photo.fileName)
                                        val ext = photo.fileName.substringAfterLast('.', "")
                                        val name = if (ext.isEmpty()) "${base}_${System.currentTimeMillis()}" else "${base}_${System.currentTimeMillis()}.$ext"
                                        File(destDir, name)
                                    } else outFile
                                    java.io.FileOutputStream(finalFile).use { it.write(bytes) }
                                    true
                                } else false
                            }
                        }
                        else -> {
                            val backupDir = photo.uploadUrl ?: ""
                            val file = UploadApi.findBackupFile(backupDir, photo.fileName)
                            if (file != null) UploadApi.copyFromBackupToLocal(file, destDir) else false
                        }
                    }
                    if (ok) successCount++ else failCount++
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
        cloudSyncAdapter = CloudSyncAdapter(selectedMd5s, alreadySyncedMd5s) { invalidateOptionsMenu() }
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
        val syncableCount = cloudList.count { it.md5 !in alreadySyncedMd5s }
        val allSyncableSelected = syncableCount > 0 && selectedMd5s.size == syncableCount
        menu.findItem(com.example.photobackup.R.id.action_select_all)?.title =
            getString(if (allSyncableSelected) com.example.photobackup.R.string.deselect_all else com.example.photobackup.R.string.select_all)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            com.example.photobackup.R.id.action_select_all -> {
                val syncableMd5s = cloudList.map { it.md5 }.filter { it !in alreadySyncedMd5s }.toSet()
                if (selectedMd5s.size == syncableMd5s.size) {
                    selectedMd5s.clear()
                } else {
                    selectedMd5s.clear()
                    selectedMd5s.addAll(syncableMd5s)
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
            val prefs = getSharedPreferences(SettingsFragment.PREFS_NAME, MODE_PRIVATE)
            val backupRoot = prefs.getString(SettingsFragment.PREF_BACKUP_ROOT_DIRECTORY, null).orEmpty().trim()
            val categoryRepo = CategoryRepository(this@CloudSyncActivity)
            val categoryName = categoryRepo.getCategory(cid)?.name

            cloudList = withContext(Dispatchers.IO) {
                PhotoBackupDatabase.getDatabase(this@CloudSyncActivity).backedUpPhotoDao().getByCategoryId(cid)
            }

            alreadySyncedMd5s = if (backupRoot.isNotEmpty() && categoryName != null) {
                withContext(Dispatchers.IO) {
                    val categoryDir = File(backupRoot, categoryName)
                    cloudList.filter { photo ->
                        val localFile = File(categoryDir, photo.fileName)
                        localFile.exists() && FileHashUtil.calculateMD5(localFile) == photo.md5
                    }.map { it.md5 }.toSet()
                }
            } else {
                emptySet()
            }
            selectedMd5s.removeAll(alreadySyncedMd5s)

            cloudSyncAdapter.updateAlreadySyncedMd5s(alreadySyncedMd5s)
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
