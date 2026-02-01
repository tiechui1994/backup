package com.example.photobackup.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 已备份照片记录实体
 * 使用 MD5 值作为唯一标识，实现增量备份
 */
@Entity(tableName = "backed_up_photos")
data class BackedUpPhoto(
    @PrimaryKey
    val md5: String,
    val filePath: String,
    val fileName: String,
    val fileSize: Long,
    val backupTime: Long = System.currentTimeMillis(),
    val uploadUrl: String? = null,
    val categoryId: String? = null
)


