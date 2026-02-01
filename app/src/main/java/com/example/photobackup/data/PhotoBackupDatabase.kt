package com.example.photobackup.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room 数据库，用于存储已备份照片记录
 */
@Database(
    entities = [BackedUpPhoto::class],
    version = 3,
    exportSchema = false
)
abstract class PhotoBackupDatabase : RoomDatabase() {
    
    abstract fun backedUpPhotoDao(): BackedUpPhotoDao
    
    companion object {
        @Volatile
        private var INSTANCE: PhotoBackupDatabase? = null
        
        fun getDatabase(context: Context): PhotoBackupDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PhotoBackupDatabase::class.java,
                    "photo_backup_database"
                ).fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}


