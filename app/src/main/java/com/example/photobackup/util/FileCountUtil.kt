package com.example.photobackup.util

import com.example.photobackup.data.Category
import java.io.File

/**
 * 按类别统计本地文件夹中的文件数量
 */
object FileCountUtil {

    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
    private val musicExtensions = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "wma")
    private val documentExtensions = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md")

    fun getExtensionsForType(type: Category.CategoryType): Set<String> = when (type) {
        Category.CategoryType.PHOTO -> imageExtensions
        Category.CategoryType.MUSIC -> musicExtensions
        Category.CategoryType.DOCUMENT -> documentExtensions
        Category.CategoryType.CUSTOM -> imageExtensions + musicExtensions + documentExtensions
    }

    /**
     * 统计指定文件夹列表中符合类别类型的文件数量
     */
    fun countLocalFiles(folderPaths: List<String>, type: Category.CategoryType): Int {
        val extensions = getExtensionsForType(type)
        var count = 0
        folderPaths.forEach { path ->
            val folder = File(path)
            if (folder.exists() && folder.isDirectory) {
                count += countFilesInDir(folder, extensions)
            }
        }
        return count
    }

    private fun countFilesInDir(dir: File, extensions: Set<String>): Int {
        var n = 0
        try {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    n += countFilesInDir(file, extensions)
                } else if (file.isFile && extensions.contains(file.extension.lowercase())) {
                    n++
                }
            }
        } catch (e: Exception) {
            AppLogger.w("FileCountUtil", "countFilesInDir error: ${e.message}")
        }
        return n
    }
}
