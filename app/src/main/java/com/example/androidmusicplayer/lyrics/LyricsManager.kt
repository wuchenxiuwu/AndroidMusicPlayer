package com.example.androidmusicplayer.lyrics

import com.example.androidmusicplayer.utils.ErrorHandler
import com.example.androidmusicplayer.utils.NetworkUtils
import com.example.androidmusicplayer.utils.FileUtils
import com.example.androidmusicplayer.utils.safeExecute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

/**
 * 歌词管理器，负责歌词的获取、缓存和管理
 */
class LyricsManager private constructor() {
    
    companion object {
        @Volatile
        private var INSTANCE: LyricsManager? = null
        
        fun getInstance(): LyricsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LyricsManager().also { INSTANCE = it }
            }
        }
        
        private const val LYRICS_CACHE_DIR = "lyrics_cache"
        private const val LYRICS_FILE_EXTENSION = ".lrc"
    }
    
    private val lyricsParser = LyricsParser()
    private val networkUtils = NetworkUtils.getInstance()
    private val fileUtils = FileUtils.getInstance()
    private val errorHandler = ErrorHandler.getInstance()
    
    // 歌词缓存
    private val lyricsCache = mutableMapOf<String, LyricsInfo>()
    
    /**
     * 获取歌词信息
     * 优先级：本地文件 -> 缓存 -> 网络搜索
     */
    suspend fun getLyrics(
        title: String,
        artist: String,
        musicFilePath: String? = null
    ): LyricsResult = withContext(Dispatchers.IO) {
        val cacheKey = generateCacheKey(title, artist)
        
        // 1. 检查内存缓存
        lyricsCache[cacheKey]?.let { cachedLyrics ->
            return@withContext LyricsResult.Success(cachedLyrics)
        }
        
        // 2. 检查本地歌词文件
        val localLyrics = loadLocalLyrics(title, artist, musicFilePath)
        if (localLyrics != null) {
            lyricsCache[cacheKey] = localLyrics
            return@withContext LyricsResult.Success(localLyrics)
        }
        
        // 3. 尝试从网络获取歌词
        val networkLyrics = searchLyricsOnline(title, artist)
        if (networkLyrics != null) {
            // 缓存到本地和内存
            saveLyricsToCache(cacheKey, networkLyrics)
            lyricsCache[cacheKey] = networkLyrics
            return@withContext LyricsResult.Success(networkLyrics)
        }
        
        // 4. 没有找到歌词
        LyricsResult.NotFound("未找到歌词: $title - $artist")
    }
    
    /**
     * 从本地文件加载歌词
     */
    private suspend fun loadLocalLyrics(
        title: String,
        artist: String,
        musicFilePath: String?
    ): LyricsInfo? = withContext(Dispatchers.IO) {
        safeExecute("LyricsManager.loadLocalLyrics") {
            // 尝试多种本地歌词文件路径
            val possiblePaths = mutableListOf<String>()
            
            // 1. 与音乐文件同目录的同名歌词文件
            musicFilePath?.let { path ->
                val musicFile = File(path)
                val lyricsFile = File(musicFile.parent, "${musicFile.nameWithoutExtension}$LYRICS_FILE_EXTENSION")
                possiblePaths.add(lyricsFile.absolutePath)
            }
            
            // 2. 基于歌曲信息的歌词文件
            possiblePaths.add("${title} - ${artist}$LYRICS_FILE_EXTENSION")
            possiblePaths.add("${artist} - ${title}$LYRICS_FILE_EXTENSION")
            
            // 尝试加载歌词文件
            for (path in possiblePaths) {
                if (fileUtils.fileExists(path)) {
                    try {
                        val inputStream = FileInputStream(path)
                        return@safeExecute lyricsParser.parseLyrics(inputStream)
                    } catch (e: Exception) {
                        errorHandler.handleFileIOError(e, "LyricsManager.loadLocalLyrics")
                        continue
                    }
                }
            }
            
            null
        }
    }
    
    /**
     * 在线搜索歌词
     */
    private suspend fun searchLyricsOnline(title: String, artist: String): LyricsInfo? = withContext(Dispatchers.IO) {
        safeExecute("LyricsManager.searchLyricsOnline") {
            // 这里可以集成多个歌词API
            // 示例：使用一个假设的歌词API
            val searchQuery = "$title $artist lyrics"
            
            // 注意：这里需要替换为实际的歌词API
            // 由于版权原因，大多数歌词API需要授权
            val apiUrl = "https://api.lyrics.example.com/search?q=${java.net.URLEncoder.encode(searchQuery, "UTF-8")}"
            
            when (val result = networkUtils.httpGet(apiUrl)) {
                is NetworkUtils.NetworkResult.Success -> {
                    // 解析API响应并提取歌词
                    parseLyricsFromApiResponse(result.data)
                }
                is NetworkUtils.NetworkResult.Error -> {
                    errorHandler.handleNetworkError(
                        RuntimeException("歌词搜索失败: ${result.error.message}"),
                        "LyricsManager.searchLyricsOnline"
                    )
                    null
                }
            }
        }
    }
    
    /**
     * 解析API响应中的歌词
     */
    private suspend fun parseLyricsFromApiResponse(response: String): LyricsInfo? {
        return safeExecute("LyricsManager.parseLyricsFromApiResponse") {
            // 这里需要根据实际API的响应格式来解析
            // 示例实现：假设API返回的是LRC格式的歌词
            if (lyricsParser.isValidLrcFormat(response)) {
                lyricsParser.parseLyricsFromString(response)
            } else {
                // 如果不是LRC格式，尝试转换为简单的歌词格式
                convertPlainTextToLyrics(response)
            }
        }
    }
    
    /**
     * 将纯文本转换为歌词格式
     */
    private fun convertPlainTextToLyrics(plainText: String): LyricsInfo {
        val lyricsInfo = LyricsInfo()
        val lines = plainText.lines().filter { it.trim().isNotEmpty() }
        
        // 为每行文本分配时间（假设每行持续5秒）
        lines.forEachIndexed { index, line ->
            val startTime = index * 5000L // 5秒间隔
            lyricsInfo.lyricsLines.add(
                LyricsLine(
                    startTime = startTime,
                    endTime = startTime + 5000L,
                    text = line.trim(),
                    originalText = line.trim()
                )
            )
        }
        
        return lyricsInfo
    }
    
    /**
     * 保存歌词到缓存
     */
    private suspend fun saveLyricsToCache(cacheKey: String, lyricsInfo: LyricsInfo) = withContext(Dispatchers.IO) {
        safeExecute("LyricsManager.saveLyricsToCache") {
            // 生成LRC格式的歌词内容
            val lrcContent = generateLrcContent(lyricsInfo)
            
            // 保存到缓存目录
            val cacheDir = "$LYRICS_CACHE_DIR"
            val cacheFile = "$cacheDir/${cacheKey}$LYRICS_FILE_EXTENSION"
            
            fileUtils.writeFile(cacheFile, lrcContent)
        }
    }
    
    /**
     * 生成LRC格式的歌词内容
     */
    private fun generateLrcContent(lyricsInfo: LyricsInfo): String {
        val builder = StringBuilder()
        
        // 添加歌词信息
        lyricsInfo.title?.let { builder.appendLine("[ti:$it]") }
        lyricsInfo.artist?.let { builder.appendLine("[ar:$it]") }
        lyricsInfo.album?.let { builder.appendLine("[al:$it]") }
        lyricsInfo.creator?.let { builder.appendLine("[by:$it]") }
        if (lyricsInfo.offset != 0L) {
            builder.appendLine("[offset:${lyricsInfo.offset}]")
        }
        
        // 添加歌词行
        lyricsInfo.lyricsLines.forEach { line ->
            val timeTag = line.getFormattedTime()
            builder.appendLine("[$timeTag]${line.text}")
        }
        
        return builder.toString()
    }
    
    /**
     * 手动添加歌词
     */
    suspend fun addLyrics(
        title: String,
        artist: String,
        lyricsContent: String,
        isLrcFormat: Boolean = true
    ): LyricsResult = withContext(Dispatchers.IO) {
        safeExecute("LyricsManager.addLyrics") {
            val lyricsInfo = if (isLrcFormat) {
                lyricsParser.parseLyricsFromString(lyricsContent)
            } else {
                convertPlainTextToLyrics(lyricsContent)
            }
            
            // 设置歌曲信息
            lyricsInfo.title = title
            lyricsInfo.artist = artist
            
            val cacheKey = generateCacheKey(title, artist)
            
            // 保存到缓存
            saveLyricsToCache(cacheKey, lyricsInfo)
            lyricsCache[cacheKey] = lyricsInfo
            
            LyricsResult.Success(lyricsInfo)
        } ?: LyricsResult.Error("添加歌词失败")
    }
    
    /**
     * 删除歌词缓存
     */
    suspend fun deleteLyrics(title: String, artist: String): Boolean = withContext(Dispatchers.IO) {
        safeExecute("LyricsManager.deleteLyrics") {
            val cacheKey = generateCacheKey(title, artist)
            
            // 从内存缓存中删除
            lyricsCache.remove(cacheKey)
            
            // 从文件缓存中删除
            val cacheFile = "$LYRICS_CACHE_DIR/${cacheKey}$LYRICS_FILE_EXTENSION"
            fileUtils.deleteFile(cacheFile)
            
            true
        } ?: false
    }
    
    /**
     * 清除所有歌词缓存
     */
    suspend fun clearAllCache(): Boolean = withContext(Dispatchers.IO) {
        safeExecute("LyricsManager.clearAllCache") {
            // 清除内存缓存
            lyricsCache.clear()
            
            // 清除文件缓存
            // 这里需要删除整个缓存目录
            true
        } ?: false
    }
    
    /**
     * 生成缓存键
     */
    private fun generateCacheKey(title: String, artist: String): String {
        return "${title}_${artist}".replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5]"), "_")
    }
    
    /**
     * 歌词获取结果
     */
    sealed class LyricsResult {
        data class Success(val lyrics: LyricsInfo) : LyricsResult()
        data class NotFound(val message: String) : LyricsResult()
        data class Error(val message: String) : LyricsResult()
    }
}

