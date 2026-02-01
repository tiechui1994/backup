package com.example.photobackup.util

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * 文件哈希工具类，用于计算文件的 MD5 值
 */
object FileHashUtil {
    
    /**
     * 计算文件的 MD5 值
     * @param file 要计算的文件
     * @return MD5 字符串，如果计算失败返回 null
     */
    fun calculateMD5(file: File): String? {
        return try {
            val md = MessageDigest.getInstance("MD5")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    md.update(buffer, 0, read)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 计算文件的 SHA1 值（用于云端 API 校验）
     */
    fun calculateSHA1(file: File): String? {
        return try {
            val md = MessageDigest.getInstance("SHA-1")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    md.update(buffer, 0, read)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }
}


