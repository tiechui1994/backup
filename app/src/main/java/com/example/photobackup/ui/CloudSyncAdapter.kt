package com.example.photobackup.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.photobackup.data.BackedUpPhoto
import com.example.photobackup.databinding.ItemCloudSyncFileBinding
import android.view.View

/**
 * 云端备份文件列表适配器，支持勾选；与本地一致的文件禁用勾选
 */
class CloudSyncAdapter(
    private val selectedMd5s: MutableSet<String>,
    private var alreadySyncedMd5s: Set<String>,
    private val onSelectionChanged: () -> Unit
) : ListAdapter<BackedUpPhoto, CloudSyncAdapter.ViewHolder>(DiffCallback) {

    fun updateAlreadySyncedMd5s(set: Set<String>) {
        alreadySyncedMd5s = set
        notifyItemRangeChanged(0, itemCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCloudSyncFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemCloudSyncFileBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: BackedUpPhoto) {
            val isAlreadySynced = item.md5 in alreadySyncedMd5s
            binding.tvFileName.text = item.fileName
            binding.tvFileSize.text = formatFileSize(item.fileSize)
            binding.checkboxSelect.isEnabled = !isAlreadySynced
            binding.checkboxSelect.isChecked = selectedMd5s.contains(item.md5) && !isAlreadySynced
            binding.tvAlreadySynced.visibility = if (isAlreadySynced) View.VISIBLE else View.GONE
            if (isAlreadySynced) selectedMd5s.remove(item.md5)
            binding.root.setOnClickListener {
                if (isAlreadySynced) return@setOnClickListener
                if (selectedMd5s.contains(item.md5)) selectedMd5s.remove(item.md5)
                else selectedMd5s.add(item.md5)
                binding.checkboxSelect.isChecked = selectedMd5s.contains(item.md5)
                onSelectionChanged()
            }
            binding.checkboxSelect.setOnClickListener {
                if (isAlreadySynced) return@setOnClickListener
                if (selectedMd5s.contains(item.md5)) selectedMd5s.remove(item.md5)
                else selectedMd5s.add(item.md5)
                onSelectionChanged()
            }
        }
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        if (bytes < 1024 * 1024) return "%.1f KB".format(bytes / 1024.0)
        return "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }

    private object DiffCallback : DiffUtil.ItemCallback<BackedUpPhoto>() {
        override fun areItemsTheSame(a: BackedUpPhoto, b: BackedUpPhoto) = a.md5 == b.md5
        override fun areContentsTheSame(a: BackedUpPhoto, b: BackedUpPhoto) = a == b
    }
}
