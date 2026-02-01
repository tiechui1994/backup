package com.example.photobackup.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.photobackup.data.Category
import com.example.photobackup.data.CategoryRepository
import com.example.photobackup.data.PhotoBackupDatabase
import com.example.photobackup.databinding.ItemCategoryBinding
import com.example.photobackup.util.FileCountUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CategoryAdapter(
    private val context: Context,
    private val categoryRepository: CategoryRepository,
    private val onCategoryClick: (Category) -> Unit
) : ListAdapter<Category, CategoryAdapter.ViewHolder>(DiffCallback) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = getItem(position)
        holder.bind(category)
        holder.itemView.setOnClickListener { onCategoryClick(category) }
    }

    inner class ViewHolder(private val binding: ItemCategoryBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(category: Category) {
            binding.tvCategoryName.text = category.name
            binding.tvCategorySummary.text = "${category.backupFolders.size} 个文件夹"
            scope.launch {
                val backedUp = withContext(Dispatchers.IO) {
                    PhotoBackupDatabase.getDatabase(context).backedUpPhotoDao()
                        .getBackupCountByCategory(category.id)
                }
                val localCount = FileCountUtil.countLocalFiles(category.backupFolders, category.type)
                binding.tvCategorySummary.text = "${category.backupFolders.size} 个文件夹 · 已备份 $backedUp · 本地 $localCount"
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<Category>() {
        override fun areItemsTheSame(old: Category, new: Category) = old.id == new.id
        override fun areContentsTheSame(old: Category, new: Category) = old == new
    }
}
