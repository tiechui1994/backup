package com.example.photobackup.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.photobackup.data.Category
import com.example.photobackup.data.CategoryRepository
import com.example.photobackup.databinding.FragmentHomeBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var adapter: CategoryAdapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        categoryRepository = CategoryRepository(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = CategoryAdapter(
            requireContext(),
            categoryRepository,
            onCategoryClick = { category -> openCategoryDetail(category) }
        )
        binding.recyclerCategories.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerCategories.adapter = adapter
        binding.btnAddCategory.setOnClickListener { showAddCategoryDialog() }
        loadCategories()
    }

    private fun loadCategories() {
        scope.launch {
            val list = withContext(Dispatchers.IO) { categoryRepository.getCategories() }
            adapter.submitList(list)
        }
    }

    private fun openCategoryDetail(category: Category) {
        startActivity(Intent(requireContext(), CategoryDetailActivity::class.java).apply {
            putExtra(CategoryDetailActivity.EXTRA_CATEGORY_ID, category.id)
        })
    }

    private fun showAddCategoryDialog() {
        val input = android.widget.EditText(requireContext()).apply {
            hint = "自定义类别名称"
            setPadding(48, 48, 48, 48)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("添加自定义类别")
            .setView(input)
            .setPositiveButton("添加") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), "请输入类别名称", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                categoryRepository.addCustomCategory(name)
                loadCategories()
                Toast.makeText(requireContext(), "已添加", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        loadCategories()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scope.cancel()
        _binding = null
    }
}
