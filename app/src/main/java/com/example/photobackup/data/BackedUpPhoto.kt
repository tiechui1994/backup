package com.example.photobackup.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 已备份照片记录实体（与当前模式无关，统一存储）。
 * 使用 MD5 作为唯一标识实现增量备份。
 * backupTarget：备份目标（本地为目录路径，云端为 "cloud"）。
 * remoteId：远程文件标识（云端为 fileId，本地为 null）。
 */
@Entity(tableName = "backed_up_photos")
data class BackedUpPhoto(
    @PrimaryKey
    val md5: String,
    val filePath: String,
    val fileName: String,
    val fileSize: Long,
    val backupTime: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "uploadUrl")
    val backupTarget: String? = null,
    val categoryId: String? = null,
    @ColumnInfo(name = "cloudFileId")
    val remoteId: String? = null
)
