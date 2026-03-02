package com.example.androidmusicplayer.streaming

import com.example.androidmusicplayer.utils.ErrorHandler
import com.example.androidmusicplayer.utils.NetworkUtils
import com.example.androidmusicplayer.utils.safeExecute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 在线音乐搜索和流媒体管理器
 */
class StreamingMusicManager private constructor() {
    
    companion object {
        @Volatile
        private var INSTANCE: StreamingMusicManager? = null
        
        fun getInstance(): StreamingMusicManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StreamingMusicManager().also { INSTANCE = it }
            }
        }
    }
    
    private val networkUtils = NetworkUtils.getInstance()
    private val errorHandler = ErrorHandler.getInstance()
    private val json = Json { ignoreUnknownKeys = true }
    
    // 注册的音乐服务提供商
    private val musicProviders = mutableListOf<MusicProvider>()
    
    init {
        // 注册默认的音乐服务提供商
        registerProvider(FreeMusicProvider())
        // 可以添加更多提供商
        // registerProvider(SpotifyProvider())
        // registerProvider(AppleMusicProvider())
    }
    
    /**
     * 注册音乐服务提供商
     */
    fun registerProvider(provider: MusicProvider) {
        musicProviders.add(provider)
    }
    
    /**
     * 搜索音乐
     */
    suspend fun searchMusic(
        query: String,
        limit: Int = 20,
        offset: Int = 0
    ): SearchResult = withContext(Dispatchers.IO) {
        safeExecute("StreamingMusicManager.searchMusic") {
            val allResults = mutableListOf<OnlineMusic>()
            val errors = mutableListOf<String>()
            
            // 从所有提供商搜索
            for (provider in musicProviders) {
                try {
                    val result = provider.search(query, limit, offset)
                    when (result) {
                        is SearchResult.Success -> {
                            allResults.addAll(result.music)
                        }
                        is SearchResult.Error -> {
                            errors.add("${provider.name}: ${result.message}")
                        }
                        is SearchResult.Empty -> {
                            // 忽略空结果
                        }
                    }
                } catch (e: Exception) {
                    errorHandler.handleNetworkError(e, "StreamingMusicManager.searchMusic.${provider.name}")
                    errors.add("${provider.name}: ${e.message}")
                }
            }
            
            when {
                allResults.isNotEmpty() -> {
                    // 去重并排序
                    val uniqueResults = allResults.distinctBy { "${it.title}_${it.artist}" }
                        .sortedByDescending { it.popularity }
                    SearchResult.Success(uniqueResults)
                }
                errors.isNotEmpty() -> {
                    SearchResult.Error("搜索失败: ${errors.joinToString("; ")}")
                }
                else -> {
                    SearchResult.Empty("未找到相关音乐")
                }
            }
        } ?: SearchResult.Error("搜索过程中发生未知错误")
    }
    
    /**
     * 获取音乐播放URL
     */
    suspend fun getMusicPlayUrl(music: OnlineMusic): PlayUrlResult = withContext(Dispatchers.IO) {
        safeExecute("StreamingMusicManager.getMusicPlayUrl") {
            val provider = musicProviders.find { it.name == music.provider }
            if (provider != null) {
                provider.getPlayUrl(music)
            } else {
                PlayUrlResult.Error("未找到音乐提供商: ${music.provider}")
            }
        } ?: PlayUrlResult.Error("获取播放链接失败")
    }
    
    /**
     * 获取音乐详细信息
     */
    suspend fun getMusicDetails(music: OnlineMusic): MusicDetailsResult = withContext(Dispatchers.IO) {
        safeExecute("StreamingMusicManager.getMusicDetails") {
            val provider = musicProviders.find { it.name == music.provider }
            if (provider != null) {
                provider.getMusicDetails(music)
            } else {
                MusicDetailsResult.Error("未找到音乐提供商: ${music.provider}")
            }
        } ?: MusicDetailsResult.Error("获取音乐详情失败")
    }
    
    /**
     * 获取推荐音乐
     */
    suspend fun getRecommendations(
        genre: String? = null,
        limit: Int = 20
    ): SearchResult = withContext(Dispatchers.IO) {
        safeExecute("StreamingMusicManager.getRecommendations") {
            val allResults = mutableListOf<OnlineMusic>()
            
            for (provider in musicProviders) {
                try {
                    val result = provider.getRecommendations(genre, limit)
                    when (result) {
                        is SearchResult.Success -> {
                            allResults.addAll(result.music)
                        }
                        else -> {
                            // 忽略错误和空结果
                        }
                    }
                } catch (e: Exception) {
                    // 忽略推荐获取失败
                }
            }
            
            if (allResults.isNotEmpty()) {
                val uniqueResults = allResults.distinctBy { "${it.title}_${it.artist}" }
                    .shuffled() // 随机排序
                    .take(limit)
                SearchResult.Success(uniqueResults)
            } else {
                SearchResult.Empty("暂无推荐音乐")
            }
        } ?: SearchResult.Empty("获取推荐失败")
    }
    
    /**
     * 获取热门音乐
     */
    suspend fun getTrendingMusic(limit: Int = 20): SearchResult = withContext(Dispatchers.IO) {
        safeExecute("StreamingMusicManager.getTrendingMusic") {
            val allResults = mutableListOf<OnlineMusic>()
            
            for (provider in musicProviders) {
                try {
                    val result = provider.getTrendingMusic(limit)
                    when (result) {
                        is SearchResult.Success -> {
                            allResults.addAll(result.music)
                        }
                        else -> {
                            // 忽略错误和空结果
                        }
                    }
                } catch (e: Exception) {
                    // 忽略热门获取失败
                }
            }
            
            if (allResults.isNotEmpty()) {
                val uniqueResults = allResults.distinctBy { "${it.title}_${it.artist}" }
                    .sortedByDescending { it.popularity }
                    .take(limit)
                SearchResult.Success(uniqueResults)
            } else {
                SearchResult.Empty("暂无热门音乐")
            }
        } ?: SearchResult.Empty("获取热门音乐失败")
    }
}

/**
 * 音乐服务提供商接口
 */
interface MusicProvider {
    val name: String
    val isAvailable: Boolean
    
    suspend fun search(query: String, limit: Int, offset: Int): SearchResult
    suspend fun getPlayUrl(music: OnlineMusic): PlayUrlResult
    suspend fun getMusicDetails(music: OnlineMusic): MusicDetailsResult
    suspend fun getRecommendations(genre: String?, limit: Int): SearchResult
    suspend fun getTrendingMusic(limit: Int): SearchResult
}

/**
 * 免费音乐提供商实现（示例）
 */
class FreeMusicProvider : MusicProvider {
    override val name = "FreeMusic"
    override val isAvailable = true
    
    private val networkUtils = NetworkUtils.getInstance()
    private val json = Json { ignoreUnknownKeys = true }
    
    override suspend fun search(query: String, limit: Int, offset: Int): SearchResult {
        return try {
            // 这里使用一个假设的免费音乐API
            // 实际使用时需要替换为真实的API
            val apiUrl = "https://api.freemusic.example.com/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&limit=$limit&offset=$offset"
            
            when (val result = networkUtils.httpGet(apiUrl)) {
                is NetworkUtils.NetworkResult.Success -> {
                    val response = json.decodeFromString<FreeMusicSearchResponse>(result.data)
                    val musicList = response.results.map { item ->
                        OnlineMusic(
                            id = item.id,
                            title = item.title,
                            artist = item.artist,
                            album = item.album,
                            duration = item.duration,
                            coverUrl = item.coverUrl,
                            provider = name,
                            popularity = item.playCount,
                            genre = item.genre,
                            year = item.year
                        )
                    }
                    
                    if (musicList.isNotEmpty()) {
                        SearchResult.Success(musicList)
                    } else {
                        SearchResult.Empty("未找到相关音乐")
                    }
                }
                is NetworkUtils.NetworkResult.Error -> {
                    SearchResult.Error("搜索失败: ${result.error.message}")
                }
            }
        } catch (e: Exception) {
            SearchResult.Error("搜索异常: ${e.message}")
        }
    }
    
    override suspend fun getPlayUrl(music: OnlineMusic): PlayUrlResult {
        return try {
            val apiUrl = "https://api.freemusic.example.com/play/${music.id}"
            
            when (val result = networkUtils.httpGet(apiUrl)) {
                is NetworkUtils.NetworkResult.Success -> {
                    val response = json.decodeFromString<FreeMusicPlayResponse>(result.data)
                    PlayUrlResult.Success(
                        playUrl = response.playUrl,
                        quality = response.quality,
                        expiresAt = System.currentTimeMillis() + (response.expiresIn * 1000)
                    )
                }
                is NetworkUtils.NetworkResult.Error -> {
                    PlayUrlResult.Error("获取播放链接失败: ${result.error.message}")
                }
            }
        } catch (e: Exception) {
            PlayUrlResult.Error("获取播放链接异常: ${e.message}")
        }
    }
    
    override suspend fun getMusicDetails(music: OnlineMusic): MusicDetailsResult {
        return try {
            val apiUrl = "https://api.freemusic.example.com/details/${music.id}"
            
            when (val result = networkUtils.httpGet(apiUrl)) {
                is NetworkUtils.NetworkResult.Success -> {
                    val response = json.decodeFromString<FreeMusicDetailsResponse>(result.data)
                    MusicDetailsResult.Success(
                        music.copy(
                            album = response.album,
                            genre = response.genre,
                            year = response.year,
                            description = response.description
                        )
                    )
                }
                is NetworkUtils.NetworkResult.Error -> {
                    MusicDetailsResult.Error("获取音乐详情失败: ${result.error.message}")
                }
            }
        } catch (e: Exception) {
            MusicDetailsResult.Error("获取音乐详情异常: ${e.message}")
        }
    }
    
    override suspend fun getRecommendations(genre: String?, limit: Int): SearchResult {
        return try {
            val apiUrl = if (genre != null) {
                "https://api.freemusic.example.com/recommendations?genre=${java.net.URLEncoder.encode(genre, "UTF-8")}&limit=$limit"
            } else {
                "https://api.freemusic.example.com/recommendations?limit=$limit"
            }
            
            when (val result = networkUtils.httpGet(apiUrl)) {
                is NetworkUtils.NetworkResult.Success -> {
                    val response = json.decodeFromString<FreeMusicSearchResponse>(result.data)
                    val musicList = response.results.map { item ->
                        OnlineMusic(
                            id = item.id,
                            title = item.title,
                            artist = item.artist,
                            album = item.album,
                            duration = item.duration,
                            coverUrl = item.coverUrl,
                            provider = name,
                            popularity = item.playCount,
                            genre = item.genre,
                            year = item.year
                        )
                    }
                    
                    if (musicList.isNotEmpty()) {
                        SearchResult.Success(musicList)
                    } else {
                        SearchResult.Empty("暂无推荐")
                    }
                }
                is NetworkUtils.NetworkResult.Error -> {
                    SearchResult.Error("获取推荐失败: ${result.error.message}")
                }
            }
        } catch (e: Exception) {
            SearchResult.Error("获取推荐异常: ${e.message}")
        }
    }
    
    override suspend fun getTrendingMusic(limit: Int): SearchResult {
        return try {
            val apiUrl = "https://api.freemusic.example.com/trending?limit=$limit"
            
            when (val result = networkUtils.httpGet(apiUrl)) {
                is NetworkUtils.NetworkResult.Success -> {
                    val response = json.decodeFromString<FreeMusicSearchResponse>(result.data)
                    val musicList = response.results.map { item ->
                        OnlineMusic(
                            id = item.id,
                            title = item.title,
                            artist = item.artist,
                            album = item.album,
                            duration = item.duration,
                            coverUrl = item.coverUrl,
                            provider = name,
                            popularity = item.playCount,
                            genre = item.genre,
                            year = item.year
                        )
                    }
                    
                    if (musicList.isNotEmpty()) {
                        SearchResult.Success(musicList)
                    } else {
                        SearchResult.Empty("暂无热门音乐")
                    }
                }
                is NetworkUtils.NetworkResult.Error -> {
                    SearchResult.Error("获取热门音乐失败: ${result.error.message}")
                }
            }
        } catch (e: Exception) {
            SearchResult.Error("获取热门音乐异常: ${e.message}")
        }
    }
}

// API响应数据类
@Serializable
data class FreeMusicSearchResponse(
    val results: List<FreeMusicItem>
)

@Serializable
data class FreeMusicItem(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val duration: Long,
    val coverUrl: String? = null,
    val playCount: Long = 0,
    val genre: String? = null,
    val year: Int? = null
)

@Serializable
data class FreeMusicPlayResponse(
    val playUrl: String,
    val quality: String,
    val expiresIn: Long
)

@Serializable
data class FreeMusicDetailsResponse(
    val album: String? = null,
    val genre: String? = null,
    val year: Int? = null,
    val description: String? = null
)

// 结果数据类
sealed class SearchResult {
    data class Success(val music: List<OnlineMusic>) : SearchResult()
    data class Error(val message: String) : SearchResult()
    data class Empty(val message: String) : SearchResult()
}

sealed class PlayUrlResult {
    data class Success(
        val playUrl: String,
        val quality: String,
        val expiresAt: Long
    ) : PlayUrlResult()
    data class Error(val message: String) : PlayUrlResult()
}

sealed class MusicDetailsResult {
    data class Success(val music: OnlineMusic) : MusicDetailsResult()
    data class Error(val message: String) : MusicDetailsResult()
}

// 在线音乐数据类
data class OnlineMusic(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val duration: Long,
    val coverUrl: String? = null,
    val provider: String,
    val popularity: Long = 0,
    val genre: String? = null,
    val year: Int? = null,
    val description: String? = null
) {
    fun toDisplayString(): String {
        return "$title - $artist"
    }
    
    fun getDurationString(): String {
        val minutes = duration / 60000
        val seconds = (duration % 60000) / 1000
        return String.format("%d:%02d", minutes, seconds)
    }
}

