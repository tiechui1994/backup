package com.example.photobackup.data

/**
 * 备份类别：照片、音乐、文档或自定义
 */
data class Category(
    val id: String,
    val name: String,
    val type: CategoryType,
    val backupFolders: List<String> = emptyList(),
    val backupDestination: String = ""
) {
    enum class CategoryType {
        PHOTO,
        MUSIC,
        DOCUMENT,
        CUSTOM
    }

    companion object {
        const val ID_PHOTO = "photo"
        const val ID_MUSIC = "music"
        const val ID_DOCUMENT = "document"
        const val PREFIX_CUSTOM = "custom_"

        fun defaultCategories(): List<Category> = listOf(
            Category(ID_PHOTO, "照片", CategoryType.PHOTO),
            Category(ID_MUSIC, "音乐", CategoryType.MUSIC),
            Category(ID_DOCUMENT, "文档", CategoryType.DOCUMENT)
        )
    }
}
