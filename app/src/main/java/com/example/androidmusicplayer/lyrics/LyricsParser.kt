package com.example.androidmusicplayer.lyrics

import com.example.androidmusicplayer.utils.ErrorHandler
import com.example.androidmusicplayer.utils.safeExecute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.regex.Pattern

/**
 * 歌词解析器，支持LRC格式歌词解析
 */
class LyricsParser {
    
    companion object {
        // LRC歌词时间标签正则表达式 [mm:ss.xx] 或 [mm:ss]
        private val TIME_PATTERN = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?\\]")
        
        // 歌词信息标签正则表达式
        private val INFO_PATTERN = Pattern.compile("\\[([a-zA-Z]+):([^\\]]+)\\]")
    }
    
    private val errorHandler = ErrorHandler.getInstance()
    
    /**
     * 从输入流解析LRC歌词
     */
    suspend fun parseLyrics(inputStream: InputStream): LyricsInfo = withContext(Dispatchers.IO) {
        safeExecute("LyricsParser.parseLyrics") {
            val lyricsInfo = LyricsInfo()
            val bufferedReader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            
            try {
                bufferedReader.useLines { lines ->
                    lines.forEach { line ->
                        parseLine(line.trim(), lyricsInfo)
                    }
                }
                
                // 排序歌词行
                lyricsInfo.lyricsLines.sortBy { it.startTime }
                
                // 计算每行歌词的结束时间
                calculateEndTimes(lyricsInfo.lyricsLines)
                
            } catch (e: Exception) {
                errorHandler.handleError(
                    ErrorHandler.ErrorInfo(
                        type = ErrorHandler.ErrorType.FILE_IO_ERROR,
                        message = "解析歌词文件失败",
                        throwable = e,
                        context = "LyricsParser.parseLyrics"
                    )
                )
            }
            
            lyricsInfo
        } ?: LyricsInfo()
    }
    
    /**
     * 从字符串解析LRC歌词
     */
    suspend fun parseLyricsFromString(lyricsContent: String): LyricsInfo = withContext(Dispatchers.IO) {
        safeExecute("LyricsParser.parseLyricsFromString") {
            val lyricsInfo = LyricsInfo()
            
            lyricsContent.lines().forEach { line ->
                parseLine(line.trim(), lyricsInfo)
            }
            
            // 排序歌词行
            lyricsInfo.lyricsLines.sortBy { it.startTime }
            
            // 计算每行歌词的结束时间
            calculateEndTimes(lyricsInfo.lyricsLines)
            
            lyricsInfo
        } ?: LyricsInfo()
    }
    
    /**
     * 解析单行歌词
     */
    private fun parseLine(line: String, lyricsInfo: LyricsInfo) {
        if (line.isEmpty()) return
        
        // 检查是否是信息标签
        val infoMatcher = INFO_PATTERN.matcher(line)
        if (infoMatcher.find()) {
            val tag = infoMatcher.group(1)?.lowercase()
            val value = infoMatcher.group(2)
            
            when (tag) {
                "ti" -> lyricsInfo.title = value
                "ar" -> lyricsInfo.artist = value
                "al" -> lyricsInfo.album = value
                "by" -> lyricsInfo.creator = value
                "offset" -> {
                    try {
                        lyricsInfo.offset = value?.toLong() ?: 0L
                    } catch (e: NumberFormatException) {
                        // 忽略无效的偏移值
                    }
                }
            }
            return
        }
        
        // 解析时间标签和歌词内容
        val timeMatcher = TIME_PATTERN.matcher(line)
        val timeStamps = mutableListOf<Long>()
        var lastEnd = 0
        
        while (timeMatcher.find()) {
            val minutes = timeMatcher.group(1)?.toInt() ?: 0
            val seconds = timeMatcher.group(2)?.toInt() ?: 0
            val milliseconds = timeMatcher.group(3)?.let { ms ->
                // 处理不同长度的毫秒部分
                when (ms.length) {
                    1 -> ms.toInt() * 100
                    2 -> ms.toInt() * 10
                    3 -> ms.toInt()
                    else -> 0
                }
            } ?: 0
            
            val timeInMs = (minutes * 60 + seconds) * 1000L + milliseconds
            timeStamps.add(timeInMs)
            lastEnd = timeMatcher.end()
        }
        
        // 获取歌词文本
        val lyricsText = if (lastEnd < line.length) {
            line.substring(lastEnd).trim()
        } else {
            ""
        }
        
        // 为每个时间戳创建歌词行
        timeStamps.forEach { timeStamp ->
            val adjustedTime = timeStamp + lyricsInfo.offset
            lyricsInfo.lyricsLines.add(
                LyricsLine(
                    startTime = adjustedTime,
                    text = lyricsText,
                    originalText = lyricsText
                )
            )
        }
    }
    
    /**
     * 计算每行歌词的结束时间
     */
    private fun calculateEndTimes(lyricsLines: MutableList<LyricsLine>) {
        for (i in lyricsLines.indices) {
            val currentLine = lyricsLines[i]
            val nextLine = if (i + 1 < lyricsLines.size) lyricsLines[i + 1] else null
            
            currentLine.endTime = nextLine?.startTime ?: (currentLine.startTime + 5000L) // 默认5秒
        }
    }
    
    /**
     * 验证LRC格式
     */
    fun isValidLrcFormat(content: String): Boolean {
        return safeExecute("LyricsParser.isValidLrcFormat") {
            val lines = content.lines()
            var hasTimeTag = false
            
            for (line in lines) {
                if (line.trim().isEmpty()) continue
                
                // 检查是否包含时间标签
                if (TIME_PATTERN.matcher(line).find()) {
                    hasTimeTag = true
                    break
                }
                
                // 检查是否包含信息标签
                if (INFO_PATTERN.matcher(line).find()) {
                    continue
                }
                
                // 如果既不是时间标签也不是信息标签，可能不是有效的LRC格式
                if (line.startsWith("[") && line.contains("]")) {
                    continue
                }
                
                // 纯文本行，可能不是LRC格式
                return@safeExecute false
            }
            
            hasTimeTag
        } ?: false
    }
}

/**
 * 歌词信息数据类
 */
data class LyricsInfo(
    var title: String? = null,
    var artist: String? = null,
    var album: String? = null,
    var creator: String? = null,
    var offset: Long = 0L, // 时间偏移，单位毫秒
    val lyricsLines: MutableList<LyricsLine> = mutableListOf()
) {
    /**
     * 根据当前播放时间获取当前歌词行
     */
    fun getCurrentLyricsLine(currentTime: Long): LyricsLine? {
        return lyricsLines.find { line ->
            currentTime >= line.startTime && currentTime < line.endTime
        }
    }
    
    /**
     * 根据当前播放时间获取当前歌词行索引
     */
    fun getCurrentLyricsIndex(currentTime: Long): Int {
        return lyricsLines.indexOfFirst { line ->
            currentTime >= line.startTime && currentTime < line.endTime
        }
    }
    
    /**
     * 获取下一行歌词
     */
    fun getNextLyricsLine(currentTime: Long): LyricsLine? {
        val currentIndex = getCurrentLyricsIndex(currentTime)
        return if (currentIndex >= 0 && currentIndex + 1 < lyricsLines.size) {
            lyricsLines[currentIndex + 1]
        } else {
            null
        }
    }
    
    /**
     * 获取上一行歌词
     */
    fun getPreviousLyricsLine(currentTime: Long): LyricsLine? {
        val currentIndex = getCurrentLyricsIndex(currentTime)
        return if (currentIndex > 0) {
            lyricsLines[currentIndex - 1]
        } else {
            null
        }
    }
    
    /**
     * 检查是否有歌词
     */
    fun hasLyrics(): Boolean {
        return lyricsLines.isNotEmpty()
    }
    
    /**
     * 获取歌词总时长
     */
    fun getTotalDuration(): Long {
        return if (lyricsLines.isNotEmpty()) {
            lyricsLines.last().endTime
        } else {
            0L
        }
    }
}

/**
 * 歌词行数据类
 */
data class LyricsLine(
    val startTime: Long, // 开始时间，毫秒
    var endTime: Long = 0L, // 结束时间，毫秒
    val text: String, // 歌词文本
    val originalText: String, // 原始歌词文本
    var isHighlighted: Boolean = false // 是否高亮显示
) {
    /**
     * 检查指定时间是否在此歌词行的时间范围内
     */
    fun isInTimeRange(time: Long): Boolean {
        return time >= startTime && time < endTime
    }
    
    /**
     * 获取格式化的时间字符串
     */
    fun getFormattedTime(): String {
        val totalSeconds = startTime / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val milliseconds = (startTime % 1000) / 10
        return String.format("%02d:%02d.%02d", minutes, seconds, milliseconds)
    }
    
    /**
     * 检查是否为空歌词行
     */
    fun isEmpty(): Boolean {
        return text.isBlank()
    }
}

