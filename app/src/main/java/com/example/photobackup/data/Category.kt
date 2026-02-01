package com.example.photobackup.data

/**
 * 备份类别：照片、音乐、文档或自定义
 */
data class Category(
    val id: String,
    val name: String,
    val type: CategoryType,
    val backupFolders: List<String> = emptyList(),
    /** @deprecated 请使用 backupDestinations，保留用于兼容旧配置 */
    val backupDestination: String = "",
    /** 备份目标目录列表，通过系统文件夹选择器添加，确认真实存在 */
    val backupDestinations: List<String> = emptyList()
) {
    /** 实际使用的备份目标目录列表（兼容旧单路径配置） */
    fun effectiveBackupDestinations(): List<String> =
        if (backupDestinations.isNotEmpty()) backupDestinations
        else if (backupDestination.isNotEmpty()) listOf(backupDestination)
        else emptyList()
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
