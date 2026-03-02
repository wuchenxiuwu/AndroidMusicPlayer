package com.example.androidmusicplayer.streaming

import android.content.Context
import com.example.androidmusicplayer.utils.ErrorHandler
import com.example.androidmusicplayer.utils.FileUtils
import com.example.androidmusicplayer.utils.NetworkUtils
import com.example.androidmusicplayer.utils.safeExecute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URL

/**
 * 流媒体缓存管理器
 */
class StreamingCacheManager private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: StreamingCacheManager? = null
        
        fun getInstance(context: Context): StreamingCacheManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StreamingCacheManager(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        private const val CACHE_DIR_NAME = "streaming_cache"
        private const val MAX_CACHE_SIZE = 500 * 1024 * 1024L // 500MB
        private const val CACHE_EXPIRY_TIME = 7 * 24 * 60 * 60 * 1000L // 7天
    }
    
    private val networkUtils = NetworkUtils.getInstance()
    private val fileUtils = FileUtils.getInstance()
    private val errorHandler = ErrorHandler.getInstance()
    
    private val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
    
    // 缓存状态
    private val _cacheSize = MutableStateFlow(0L)
    val cacheSize: StateFlow<Long> = _cacheSize.asStateFlow()
    
    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()
    
    init {
        // 确保缓存目录存在
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        
        // 计算当前缓存大小
        updateCacheSize()
        
        // 清理过期缓存
        cleanExpiredCache()
    }
    
    /**
     * 获取缓存的音乐文件
     */
    suspend fun getCachedMusic(music: OnlineMusic): File? = withContext(Dispatchers.IO) {
        safeExecute("StreamingCacheManager.getCachedMusic") {
            val cacheFile = getCacheFile(music)
            if (cacheFile.exists() && cacheFile.length() > 0) {
                // 更新访问时间
                cacheFile.setLastModified(System.currentTimeMillis())
                cacheFile
            } else {
                null
            }
        }
    }
    
    /**
     * 缓存音乐文件
     */
    suspend fun cacheMusic(music: OnlineMusic, playUrl: String): CacheResult = withContext(Dispatchers.IO) {
        safeExecute("StreamingCacheManager.cacheMusic") {
            val cacheFile = getCacheFile(music)
            
            // 如果已经缓存，直接返回
            if (cacheFile.exists() && cacheFile.length() > 0) {
                return@safeExecute CacheResult.Success(cacheFile)
            }
            
            try {
                // 检查缓存空间
                if (!hasEnoughSpace(music.duration)) {
                    cleanOldCache()
                }
                
                // 下载文件
                val downloadResult = downloadFile(playUrl, cacheFile, music.id)
                when (downloadResult) {
                    is DownloadResult.Success -> {
                        updateCacheSize()
                        CacheResult.Success(cacheFile)
                    }
                    is DownloadResult.Error -> {
                        CacheResult.Error(downloadResult.message)
                    }
                }
            } catch (e: Exception) {
                errorHandler.handleError(
                    ErrorHandler.ErrorInfo(
                        type = ErrorHandler.ErrorType.FILE_IO_ERROR,
                        message = "缓存音乐文件失败",
                        throwable = e,
                        context = "StreamingCacheManager.cacheMusic"
                    )
                )
                CacheResult.Error("缓存失败: ${e.message}")
            }
        } ?: CacheResult.Error("缓存过程中发生未知错误")
    }
    
    /**
     * 下载文件
     */
    private suspend fun downloadFile(url: String, targetFile: File, musicId: String): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection()
            connection.connect()
            
            val fileLength = connection.contentLength
            val inputStream: InputStream = connection.getInputStream()
            val outputStream = FileOutputStream(targetFile)
            
            val buffer = ByteArray(8192)
            var totalBytesRead = 0L
            var bytesRead: Int
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                
                // 更新下载进度
                if (fileLength > 0) {
                    val progress = totalBytesRead.toFloat() / fileLength.toFloat()
                    updateDownloadProgress(musicId, progress)
                }
            }
            
            outputStream.close()
            inputStream.close()
            
            // 移除下载进度
            removeDownloadProgress(musicId)
            
            DownloadResult.Success(targetFile)
        } catch (e: Exception) {
            // 删除不完整的文件
            if (targetFile.exists()) {
                targetFile.delete()
            }
            
            // 移除下载进度
            removeDownloadProgress(musicId)
            
            DownloadResult.Error("下载失败: ${e.message}")
        }
    }
    
    /**
     * 更新下载进度
     */
    private fun updateDownloadProgress(musicId: String, progress: Float) {
        val currentProgress = _downloadProgress.value.toMutableMap()
        currentProgress[musicId] = progress
        _downloadProgress.value = currentProgress
    }
    
    /**
     * 移除下载进度
     */
    private fun removeDownloadProgress(musicId: String) {
        val currentProgress = _downloadProgress.value.toMutableMap()
        currentProgress.remove(musicId)
        _downloadProgress.value = currentProgress
    }
    
    /**
     * 获取缓存文件
     */
    private fun getCacheFile(music: OnlineMusic): File {
        val fileName = "${music.provider}_${music.id}.mp3"
        return File(cacheDir, fileName)
    }
    
    /**
     * 检查是否有足够的缓存空间
     */
    private fun hasEnoughSpace(estimatedSize: Long): Boolean {
        val currentSize = calculateCacheSize()
        val estimatedFileSize = estimatedSize * 128 // 假设128kbps
        return (currentSize + estimatedFileSize) <= MAX_CACHE_SIZE
    }
    
    /**
     * 清理旧缓存
     */
    private suspend fun cleanOldCache() = withContext(Dispatchers.IO) {
        safeExecute("StreamingCacheManager.cleanOldCache") {
            val files = cacheDir.listFiles() ?: return@safeExecute
            
            // 按最后修改时间排序，删除最旧的文件
            val sortedFiles = files.sortedBy { it.lastModified() }
            
            var currentSize = calculateCacheSize()
            for (file in sortedFiles) {
                if (currentSize <= MAX_CACHE_SIZE * 0.8) { // 清理到80%
                    break
                }
                
                currentSize -= file.length()
                file.delete()
            }
            
            updateCacheSize()
        }
    }
    
    /**
     * 清理过期缓存
     */
    private suspend fun cleanExpiredCache() = withContext(Dispatchers.IO) {
        safeExecute("StreamingCacheManager.cleanExpiredCache") {
            val files = cacheDir.listFiles() ?: return@safeExecute
            val currentTime = System.currentTimeMillis()
            
            for (file in files) {
                if (currentTime - file.lastModified() > CACHE_EXPIRY_TIME) {
                    file.delete()
                }
            }
            
            updateCacheSize()
        }
    }
    
    /**
     * 计算缓存大小
     */
    private fun calculateCacheSize(): Long {
        val files = cacheDir.listFiles() ?: return 0L
        return files.sumOf { it.length() }
    }
    
    /**
     * 更新缓存大小状态
     */
    private fun updateCacheSize() {
        _cacheSize.value = calculateCacheSize()
    }
    
    /**
     * 清空所有缓存
     */
    suspend fun clearAllCache(): Boolean = withContext(Dispatchers.IO) {
        safeExecute("StreamingCacheManager.clearAllCache") {
            val files = cacheDir.listFiles() ?: return@safeExecute true
            
            var success = true
            for (file in files) {
                if (!file.delete()) {
                    success = false
                }
            }
            
            updateCacheSize()
            success
        } ?: false
    }
    
    /**
     * 获取缓存统计信息
     */
    fun getCacheStats(): CacheStats {
        val files = cacheDir.listFiles() ?: emptyArray()
        return CacheStats(
            totalSize = calculateCacheSize(),
            fileCount = files.size,
            maxSize = MAX_CACHE_SIZE,
            usagePercentage = (calculateCacheSize().toFloat() / MAX_CACHE_SIZE.toFloat() * 100).toInt()
        )
    }
    
    /**
     * 缓存结果
     */
    sealed class CacheResult {
        data class Success(val file: File) : CacheResult()
        data class Error(val message: String) : CacheResult()
    }
    
    /**
     * 下载结果
     */
    private sealed class DownloadResult {
        data class Success(val file: File) : DownloadResult()
        data class Error(val message: String) : DownloadResult()
    }
    
    /**
     * 缓存统计信息
     */
    data class CacheStats(
        val totalSize: Long,
        val fileCount: Int,
        val maxSize: Long,
        val usagePercentage: Int
    ) {
        fun getFormattedSize(): String {
            return when {
                totalSize < 1024 -> "${totalSize}B"
                totalSize < 1024 * 1024 -> "${totalSize / 1024}KB"
                totalSize < 1024 * 1024 * 1024 -> "${totalSize / (1024 * 1024)}MB"
                else -> "${totalSize / (1024 * 1024 * 1024)}GB"
            }
        }
        
        fun getFormattedMaxSize(): String {
            return when {
                maxSize < 1024 -> "${maxSize}B"
                maxSize < 1024 * 1024 -> "${maxSize / 1024}KB"
                maxSize < 1024 * 1024 * 1024 -> "${maxSize / (1024 * 1024)}MB"
                else -> "${maxSize / (1024 * 1024 * 1024)}GB"
            }
        }
    }
}

