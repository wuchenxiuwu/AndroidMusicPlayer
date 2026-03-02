package com.example.androidmusicplayer.utils

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * 文件操作工具类，提供安全的文件读写功能
 */
class FileUtils private constructor() {
    
    companion object {
        @Volatile
        private var INSTANCE: FileUtils? = null
        
        fun getInstance(): FileUtils {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FileUtils().also { INSTANCE = it }
            }
        }
        
        private const val BUFFER_SIZE = 8192
    }
    
    private val errorHandler = ErrorHandler.getInstance()
    
    /**
     * 安全读取文件内容
     */
    suspend fun readFile(filePath: String): FileResult<String> = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            
            if (!file.exists()) {
                return@withContext FileResult.Error(
                    FileError(
                        code = FileErrorCode.FILE_NOT_FOUND,
                        message = "文件不存在: $filePath"
                    )
                )
            }
            
            if (!file.canRead()) {
                return@withContext FileResult.Error(
                    FileError(
                        code = FileErrorCode.PERMISSION_DENIED,
                        message = "没有读取权限: $filePath"
                    )
                )
            }
            
            val content = file.readText()
            FileResult.Success(content)
            
        } catch (e: IOException) {
            errorHandler.handleFileIOError(e, "FileUtils.readFile")
            FileResult.Error(
                FileError(
                    code = FileErrorCode.IO_ERROR,
                    message = "读取文件失败: ${e.message}"
                )
            )
        } catch (e: Exception) {
            errorHandler.handleFileIOError(e, "FileUtils.readFile")
            FileResult.Error(
                FileError(
                    code = FileErrorCode.UNKNOWN_ERROR,
                    message = "未知错误: ${e.message}"
                )
            )
        }
    }
    
    /**
     * 安全写入文件内容
     */
    suspend fun writeFile(filePath: String, content: String, append: Boolean = false): FileResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            
            // 确保父目录存在
            file.parentFile?.let { parentDir ->
                if (!parentDir.exists() && !parentDir.mkdirs()) {
                    return@withContext FileResult.Error(
                        FileError(
                            code = FileErrorCode.PERMISSION_DENIED,
                            message = "无法创建目录: ${parentDir.absolutePath}"
                        )
                    )
                }
            }
            
            if (append) {
                file.appendText(content)
            } else {
                file.writeText(content)
            }
            
            FileResult.Success(Unit)
            
        } catch (e: IOException) {
            errorHandler.handleFileIOError(e, "FileUtils.writeFile")
            FileResult.Error(
                FileError(
                    code = FileErrorCode.IO_ERROR,
                    message = "写入文件失败: ${e.message}"
                )
            )
        } catch (e: Exception) {
            errorHandler.handleFileIOError(e, "FileUtils.writeFile")
            FileResult.Error(
                FileError(
                    code = FileErrorCode.UNKNOWN_ERROR,
                    message = "未知错误: ${e.message}"
                )
            )
        }
    }
    
    /**
     * 安全复制文件
     */
    suspend fun copyFile(sourcePath: String, destinationPath: String): FileResult<Unit> = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        
        try {
            val sourceFile = File(sourcePath)
            val destFile = File(destinationPath)
            
            if (!sourceFile.exists()) {
                return@withContext FileResult.Error(
                    FileError(
                        code = FileErrorCode.FILE_NOT_FOUND,
                        message = "源文件不存在: $sourcePath"
                    )
                )
            }
            
            if (!sourceFile.canRead()) {
                return@withContext FileResult.Error(
                    FileError(
                        code = FileErrorCode.PERMISSION_DENIED,
                        message = "没有读取权限: $sourcePath"
                    )
                )
            }
            
            // 确保目标目录存在
            destFile.parentFile?.let { parentDir ->
                if (!parentDir.exists() && !parentDir.mkdirs()) {
                    return@withContext FileResult.Error(
                        FileError(
                            code = FileErrorCode.PERMISSION_DENIED,
                            message = "无法创建目录: ${parentDir.absolutePath}"
                        )
                    )
                }
            }
            
            inputStream = FileInputStream(sourceFile)
            outputStream = FileOutputStream(destFile)
            
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            
            outputStream.flush()
            FileResult.Success(Unit)
            
        } catch (e: IOException) {
            errorHandler.handleFileIOError(e, "FileUtils.copyFile")
            FileResult.Error(
                FileError(
                    code = FileErrorCode.IO_ERROR,
                    message = "复制文件失败: ${e.message}"
                )
            )
        } catch (e: Exception) {
            errorHandler.handleFileIOError(e, "FileUtils.copyFile")
            FileResult.Error(
                FileError(
                    code = FileErrorCode.UNKNOWN_ERROR,
                    message = "未知错误: ${e.message}"
                )
            )
        } finally {
            try {
                inputStream?.close()
                outputStream?.close()
            } catch (e: IOException) {
                // 忽略关闭流时的异常
            }
        }
    }
    
    /**
     * 安全删除文件
     */
    suspend fun deleteFile(filePath: String): FileResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            
            if (!file.exists()) {
                return@withContext FileResult.Success(Unit) // 文件不存在，认为删除成功
            }
            
            if (file.delete()) {
                FileResult.Success(Unit)
            } else {
                FileResult.Error(
                    FileError(
                        code = FileErrorCode.PERMISSION_DENIED,
                        message = "删除文件失败，可能没有权限: $filePath"
                    )
                )
            }
            
        } catch (e: Exception) {
            errorHandler.handleFileIOError(e, "FileUtils.deleteFile")
            FileResult.Error(
                FileError(
                    code = FileErrorCode.UNKNOWN_ERROR,
                    message = "删除文件失败: ${e.message}"
                )
            )
        }
    }
    
    /**
     * 检查文件是否存在
     */
    fun fileExists(filePath: String): Boolean {
        return try {
            File(filePath).exists()
        } catch (e: Exception) {
            errorHandler.handleFileIOError(e, "FileUtils.fileExists")
            false
        }
    }
    
    /**
     * 获取文件大小
     */
    fun getFileSize(filePath: String): Long {
        return try {
            val file = File(filePath)
            if (file.exists()) file.length() else 0L
        } catch (e: Exception) {
            errorHandler.handleFileIOError(e, "FileUtils.getFileSize")
            0L
        }
    }
    
    /**
     * 检查存储空间是否足够
     */
    fun hasEnoughSpace(requiredBytes: Long, context: Context): Boolean {
        return try {
            val externalDir = context.getExternalFilesDir(null)
            val availableBytes = externalDir?.freeSpace ?: 0L
            availableBytes >= requiredBytes
        } catch (e: Exception) {
            errorHandler.handleFileIOError(e, "FileUtils.hasEnoughSpace")
            false
        }
    }
    
    /**
     * 获取应用缓存目录
     */
    fun getCacheDir(context: Context): String {
        return context.cacheDir.absolutePath
    }
    
    /**
     * 获取应用外部存储目录
     */
    fun getExternalDir(context: Context): String? {
        return context.getExternalFilesDir(null)?.absolutePath
    }
    
    /**
     * 清理缓存目录
     */
    suspend fun clearCache(context: Context): FileResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val cacheDir = context.cacheDir
            deleteDirectoryContents(cacheDir)
            FileResult.Success(Unit)
        } catch (e: Exception) {
            errorHandler.handleFileIOError(e, "FileUtils.clearCache")
            FileResult.Error(
                FileError(
                    code = FileErrorCode.IO_ERROR,
                    message = "清理缓存失败: ${e.message}"
                )
            )
        }
    }
    
    private fun deleteDirectoryContents(directory: File) {
        if (directory.isDirectory) {
            directory.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    deleteDirectoryContents(file)
                }
                file.delete()
            }
        }
    }
    
    enum class FileErrorCode {
        FILE_NOT_FOUND,
        PERMISSION_DENIED,
        IO_ERROR,
        INSUFFICIENT_SPACE,
        UNKNOWN_ERROR
    }
    
    data class FileError(
        val code: FileErrorCode,
        val message: String
    )
    
    sealed class FileResult<out T> {
        data class Success<T>(val data: T) : FileResult<T>()
        data class Error(val error: FileError) : FileResult<Nothing>()
    }
}

