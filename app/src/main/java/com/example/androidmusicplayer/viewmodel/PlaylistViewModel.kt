package com.example.androidmusicplayer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.androidmusicplayer.data.MusicFile
import com.example.androidmusicplayer.data.PlaylistRepository
import com.example.androidmusicplayer.data.PlaylistWithSongs
import com.example.androidmusicplayer.data.database.MusicDatabase
import com.example.androidmusicplayer.data.database.PlaylistEntity
import kotlinx.coroutines.launch

class PlaylistViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = MusicDatabase.getDatabase(application)
    private val playlistRepository = PlaylistRepository(database)
    
    val playlists: LiveData<List<PlaylistEntity>> = playlistRepository.getAllPlaylists().asLiveData()
    
    private val _currentPlaylistWithSongs = MutableLiveData<PlaylistWithSongs?>()
    val currentPlaylistWithSongs: LiveData<PlaylistWithSongs?> = _currentPlaylistWithSongs
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            try {
                playlistRepository.createPlaylist(name)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch {
            try {
                playlistRepository.deletePlaylist(playlist)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun renamePlaylist(playlist: PlaylistEntity, newName: String) {
        viewModelScope.launch {
            try {
                playlistRepository.renamePlaylist(playlist, newName)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun addSongToPlaylist(playlistId: Long, musicFile: MusicFile) {
        viewModelScope.launch {
            try {
                playlistRepository.addSongToPlaylist(playlistId, musicFile)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun removeSongFromPlaylist(playlistId: Long, musicFileId: Long) {
        viewModelScope.launch {
            try {
                playlistRepository.removeSongFromPlaylist(playlistId, musicFileId)
                // 刷新当前播放列表
                loadPlaylistWithSongs(playlistId, emptyList()) // 需要传入所有音乐文件
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun loadPlaylistWithSongs(playlistId: Long, allMusicFiles: List<MusicFile>) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val playlistWithSongs = playlistRepository.getPlaylistWithSongs(playlistId, allMusicFiles)
                _currentPlaylistWithSongs.value = playlistWithSongs
            } catch (e: Exception) {
                _currentPlaylistWithSongs.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun reorderPlaylistSongs(playlistId: Long, fromPosition: Int, toPosition: Int) {
        viewModelScope.launch {
            try {
                playlistRepository.reorderPlaylistSongs(playlistId, fromPosition, toPosition)
                // 刷新当前播放列表
                loadPlaylistWithSongs(playlistId, emptyList()) // 需要传入所有音乐文件
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}

