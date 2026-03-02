package com.example.androidmusicplayer.data

import com.example.androidmusicplayer.data.database.MusicDatabase
import com.example.androidmusicplayer.data.database.PlaylistEntity
import com.example.androidmusicplayer.data.database.PlaylistSongEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class PlaylistWithSongs(
    val playlist: PlaylistEntity,
    val songs: List<MusicFile>
)

class PlaylistRepository(private val database: MusicDatabase) {
    
    private val playlistDao = database.playlistDao()
    
    fun getAllPlaylists(): Flow<List<PlaylistEntity>> {
        return playlistDao.getAllPlaylists()
    }
    
    suspend fun createPlaylist(name: String): Long {
        val playlist = PlaylistEntity(name = name)
        return playlistDao.insertPlaylist(playlist)
    }
    
    suspend fun deletePlaylist(playlist: PlaylistEntity) {
        playlistDao.deleteAllPlaylistSongs(playlist.id)
        playlistDao.deletePlaylist(playlist)
    }
    
    suspend fun renamePlaylist(playlist: PlaylistEntity, newName: String) {
        val updatedPlaylist = playlist.copy(name = newName)
        playlistDao.updatePlaylist(updatedPlaylist)
    }
    
    suspend fun addSongToPlaylist(playlistId: Long, musicFile: MusicFile) {
        val existingSongs = playlistDao.getPlaylistSongs(playlistId)
        val nextPosition = existingSongs.size
        
        val playlistSong = PlaylistSongEntity(
            playlistId = playlistId,
            musicFileId = musicFile.id,
            position = nextPosition
        )
        playlistDao.insertPlaylistSong(playlistSong)
    }
    
    suspend fun removeSongFromPlaylist(playlistId: Long, musicFileId: Long) {
        val playlistSongs = playlistDao.getPlaylistSongs(playlistId)
        val songToRemove = playlistSongs.find { it.musicFileId == musicFileId }
        
        songToRemove?.let { song ->
            playlistDao.deletePlaylistSong(song)
            
            // 重新排序剩余歌曲
            val remainingSongs = playlistSongs.filter { it.id != song.id }
            remainingSongs.forEachIndexed { index, playlistSong ->
                playlistDao.updateSongPosition(playlistSong.id, index)
            }
        }
    }
    
    suspend fun getPlaylistWithSongs(playlistId: Long, allMusicFiles: List<MusicFile>): PlaylistWithSongs? {
        val playlist = playlistDao.getPlaylistById(playlistId) ?: return null
        val playlistSongs = playlistDao.getPlaylistSongs(playlistId)
        
        val songs = playlistSongs.mapNotNull { playlistSong ->
            allMusicFiles.find { it.id == playlistSong.musicFileId }
        }
        
        return PlaylistWithSongs(playlist, songs)
    }
    
    suspend fun reorderPlaylistSongs(playlistId: Long, fromPosition: Int, toPosition: Int) {
        val playlistSongs = playlistDao.getPlaylistSongs(playlistId).toMutableList()
        
        if (fromPosition < playlistSongs.size && toPosition < playlistSongs.size) {
            val movedSong = playlistSongs.removeAt(fromPosition)
            playlistSongs.add(toPosition, movedSong)
            
            // 更新所有歌曲的位置
            playlistSongs.forEachIndexed { index, playlistSong ->
                playlistDao.updateSongPosition(playlistSong.id, index)
            }
        }
    }
}

