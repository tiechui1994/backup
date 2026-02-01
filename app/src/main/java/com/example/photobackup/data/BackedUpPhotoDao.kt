package com.example.photobackup.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 已备份照片数据访问对象
 */
@Dao
interface BackedUpPhotoDao {
    
    /**
     * 插入或更新已备份照片记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: BackedUpPhoto)
    
    /**
     * 根据 MD5 查询是否已备份
     */
    @Query("SELECT * FROM backed_up_photos WHERE md5 = :md5 LIMIT 1")
    suspend fun findByMd5(md5: String): BackedUpPhoto?
    
    /**
     * 检查照片是否已备份
     */
    @Query("SELECT COUNT(*) > 0 FROM backed_up_photos WHERE md5 = :md5")
    suspend fun isBackedUp(md5: String): Boolean
    
    /**
     * 获取所有已备份照片的 MD5 列表
     */
    @Query("SELECT md5 FROM backed_up_photos")
    suspend fun getAllMd5s(): List<String>
    
    /**
     * 删除已备份记录（用于清理）
     */
    @Query("DELETE FROM backed_up_photos WHERE md5 = :md5")
    suspend fun deleteByMd5(md5: String)
    
    /**
     * 获取备份统计信息
     */
    @Query("SELECT COUNT(*) FROM backed_up_photos")
    suspend fun getBackupCount(): Int

    /**
     * 按类别统计已备份数量
     */
    @Query("SELECT COUNT(*) FROM backed_up_photos WHERE categoryId = :categoryId")
    suspend fun getBackupCountByCategory(categoryId: String): Int

    /**
     * 按类别获取已备份文件列表（用于同步到本地时勾选）
     */
    @Query("SELECT * FROM backed_up_photos WHERE categoryId = :categoryId ORDER BY backupTime DESC")
    suspend fun getByCategoryId(categoryId: String): List<BackedUpPhoto>
}


