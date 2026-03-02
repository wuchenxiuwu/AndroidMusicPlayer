package com.example.androidmusicplayer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.androidmusicplayer.data.MusicFile
import com.example.androidmusicplayer.data.MusicScanner
import com.example.androidmusicplayer.utils.MemoryManager
import com.example.androidmusicplayer.utils.PerformanceMonitor
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MusicViewModel(application: Application) : AndroidViewModel(application), MemoryManager.MemoryListener {
    
    private val musicScanner = MusicScanner(application)
    private val memoryManager = MemoryManager.getInstance()
    private val performanceMonitor = PerformanceMonitor.getInstance()
    
    private val _musicFiles = MutableLiveData<List<MusicFile>>()
    val musicFiles: LiveData<List<MusicFile>> = _musicFiles
    
    private val _currentMusicFile = MutableLiveData<MusicFile?>()
    val currentMusicFile: LiveData<MusicFile?> = _currentMusicFile
    
    private val _isPlaying = MutableLiveData<Boolean>()
    val isPlaying: LiveData<Boolean> = _isPlaying
    
    private val _currentPosition = MutableLiveData<Long>()
    val currentPosition: LiveData<Long> = _currentPosition
    
    private val _duration = MutableLiveData<Long>()
    val duration: LiveData<Long> = _duration
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    
    // 使用 StateFlow 来优化搜索性能
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _filteredMusicFiles = MutableStateFlow<List<MusicFile>>(emptyList())
    val filteredMusicFiles: StateFlow<List<MusicFile>> = _filteredMusicFiles.asStateFlow()
    
    // 缓存数据以提高性能
    private var cachedArtists: List<String>? = null
    private var cachedAlbums: List<String>? = null
    private var lastScanTime: Long = 0
    
    init {
        // 注册内存监听器
        memoryManager.addMemoryListener(this)
        
        // 启动性能监控
        performanceMonitor.startMonitoring()
        
        loadMusicFiles()
        
        // 监听搜索查询变化
        viewModelScope.launch {
            searchQuery.collect { query ->
                performanceMonitor.measureTime("search_music") {
                    updateFilteredMusicFiles(query)
                }
            }
        }
    }
    
    fun loadMusicFiles(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                val files = if (forceRefresh || lastScanTime == 0L) {
                    // 完整扫描
                    musicScanner.scanMusicFiles()
                } else {
                    // 增量扫描
                    val incrementalFiles = musicScanner.scanMusicFilesIncremental(lastScanTime)
                    val existingFiles = _musicFiles.value ?: emptyList()
                    (existingFiles + incrementalFiles).distinctBy { it.id }
                }
                
                _musicFiles.value = files
                lastScanTime = System.currentTimeMillis()
                
                // 清除缓存，强制重新计算
                cachedArtists = null
                cachedAlbums = null
                
                // 更新过滤结果
                updateFilteredMusicFiles(_searchQuery.value)
                
            } catch (e: Exception) {
                _errorMessage.value = "加载音乐文件失败: ${e.message}"
                _musicFiles.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun setCurrentMusicFile(musicFile: MusicFile) {
        _currentMusicFile.value = musicFile
    }
    
    fun setIsPlaying(playing: Boolean) {
        _isPlaying.value = playing
    }
    
    fun setCurrentPosition(position: Long) {
        _currentPosition.value = position
    }
    
    fun setDuration(duration: Long) {
        _duration.value = duration
    }
    
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    private fun updateFilteredMusicFiles(query: String) {
        val allFiles = _musicFiles.value ?: emptyList()
        val filtered = if (query.isEmpty()) {
            allFiles
        } else {
            allFiles.filter { musicFile ->
                musicFile.title.contains(query, ignoreCase = true) ||
                musicFile.artist.contains(query, ignoreCase = true) ||
                musicFile.album.contains(query, ignoreCase = true)
            }
        }
        _filteredMusicFiles.value = filtered
    }
    
    fun searchMusic(query: String): List<MusicFile> {
        updateSearchQuery(query)
        return _filteredMusicFiles.value
    }
    
    fun getArtists(): List<String> {
        return cachedArtists ?: run {
            val allFiles = _musicFiles.value ?: emptyList()
            val artists = allFiles.map { it.artist }.distinct().sorted()
            cachedArtists = artists
            artists
        }
    }
    
    fun getAlbums(): List<String> {
        return cachedAlbums ?: run {
            val allFiles = _musicFiles.value ?: emptyList()
            val albums = allFiles.map { it.album }.distinct().sorted()
            cachedAlbums = albums
            albums
        }
    }
    
    fun getMusicByArtist(artist: String): List<MusicFile> {
        val allFiles = _musicFiles.value ?: emptyList()
        return allFiles.filter { it.artist == artist }
    }
    
    fun getMusicByAlbum(album: String): List<MusicFile> {
        val allFiles = _musicFiles.value ?: emptyList()
        return allFiles.filter { it.album == album }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
    
    // MemoryManager.MemoryListener 实现
    override fun onLowMemory() {
        // 清除缓存数据
        cachedArtists = null
        cachedAlbums = null
        
        // 如果不在播放状态，可以清除当前音乐文件
        if (_isPlaying.value != true) {
            _currentMusicFile.value = null
        }
    }
    
    override fun onMemoryWarning(usagePercentage: Float) {
        // 内存警告时的处理逻辑
        if (usagePercentage > 0.8f) {
            // 清除部分缓存
            cachedArtists = null
            cachedAlbums = null
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        
        // 移除内存监听器
        memoryManager.removeMemoryListener(this)
        
        // 清理资源
        cachedArtists = null
        cachedAlbums = null
    }
}

