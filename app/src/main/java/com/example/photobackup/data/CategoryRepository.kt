package com.example.photobackup.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/**
 * 类别配置持久化：默认照片、音乐、文档，支持添加自定义类别
 */
class CategoryRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val typeToken = object : TypeToken<List<Category>>() {}.type

    fun getCategories(): List<Category> {
        val json = prefs.getString(KEY_CATEGORIES, null) ?: return Category.defaultCategories().also {
            saveCategories(it)
        }
        return try {
            gson.fromJson(json, typeToken) ?: Category.defaultCategories()
        } catch (e: Exception) {
            Category.defaultCategories()
        }
    }

    fun saveCategories(categories: List<Category>) {
        prefs.edit().putString(KEY_CATEGORIES, gson.toJson(categories)).apply()
    }

    fun getCategory(id: String): Category? = getCategories().find { it.id == id }

    fun updateCategory(category: Category) {
        val list = getCategories().toMutableList()
        val index = list.indexOfFirst { it.id == category.id }
        if (index >= 0) list[index] = category else list.add(category)
        saveCategories(list)
    }

    fun addCustomCategory(name: String): Category {
        val id = "${Category.PREFIX_CUSTOM}${UUID.randomUUID().toString().take(8)}"
        val category = Category(id = id, name = name, type = Category.CategoryType.CUSTOM)
        val list = getCategories().toMutableList()
        list.add(category)
        saveCategories(list)
        return category
    }

    fun removeCategory(id: String) {
        if (id == Category.ID_PHOTO || id == Category.ID_MUSIC || id == Category.ID_DOCUMENT) return
        val list = getCategories().filterNot { it.id == id }
        saveCategories(list)
    }

    companion object {
        private const val PREFS_NAME = "category_prefs"
        private const val KEY_CATEGORIES = "categories"
    }
}
