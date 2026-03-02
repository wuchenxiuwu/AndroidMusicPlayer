package com.example.androidmusicplayer.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>
    
    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Long): PlaylistEntity?
    
    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long
    
    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)
    
    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)
    
    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position")
    suspend fun getPlaylistSongs(playlistId: Long): List<PlaylistSongEntity>
    
    @Insert
    suspend fun insertPlaylistSong(playlistSong: PlaylistSongEntity)
    
    @Delete
    suspend fun deletePlaylistSong(playlistSong: PlaylistSongEntity)
    
    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun deleteAllPlaylistSongs(playlistId: Long)
    
    @Query("UPDATE playlist_songs SET position = :newPosition WHERE id = :songId")
    suspend fun updateSongPosition(songId: Long, newPosition: Int)
}

