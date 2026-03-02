package com.example.androidmusicplayer.data

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.example.androidmusicplayer.utils.PerformanceMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MusicFile(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val path: String,
    val albumId: Long
)

class MusicScanner(private val context: Context) {
    
    private val performanceMonitor = PerformanceMonitor.getInstance()
    
    suspend fun scanMusicFiles(): List<MusicFile> = withContext(Dispatchers.IO) {
        performanceMonitor.measureTime("music_scan") {
            val musicFiles = mutableListOf<MusicFile>()
            val contentResolver: ContentResolver = context.contentResolver
            
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.ALBUM_ID
            )
            
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} = 1 AND ${MediaStore.Audio.Media.DURATION} > 30000" // 过滤掉小于30秒的文件
            val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
            
            val cursor: Cursor? = contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )
            
            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val pathColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val albumIdColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                
                // 批量处理，避免频繁的内存分配
                val batchSize = 100
                var count = 0
                
                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val title = it.getString(titleColumn) ?: "Unknown Title"
                    val artist = it.getString(artistColumn) ?: "Unknown Artist"
                    val album = it.getString(albumColumn) ?: "Unknown Album"
                    val duration = it.getLong(durationColumn)
                    val path = it.getString(pathColumn) ?: ""
                    val albumId = it.getLong(albumIdColumn)
                    
                    // 验证文件路径有效性
                    if (path.isNotEmpty()) {
                        musicFiles.add(
                            MusicFile(
                                id = id,
                                title = title,
                                artist = artist,
                                album = album,
                                duration = duration,
                                path = path,
                                albumId = albumId
                            )
                        )
                    }
                    
                    count++
                    // 每处理一批数据后让出线程，避免阻塞
                    if (count % batchSize == 0) {
                        kotlinx.coroutines.yield()
                    }
                }
            }
            
            musicFiles
        }
    }
    
    fun getAlbumArtUri(albumId: Long): Uri {
        return Uri.parse("content://media/external/audio/albumart/$albumId")
    }
    
    /**
     * 增量扫描，只扫描新增或修改的音乐文件
     */
    suspend fun scanMusicFilesIncremental(lastScanTime: Long): List<MusicFile> = withContext(Dispatchers.IO) {
        performanceMonitor.measureTime("music_scan_incremental") {
            val musicFiles = mutableListOf<MusicFile>()
            val contentResolver: ContentResolver = context.contentResolver
            
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DATE_MODIFIED
            )
            
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} = 1 AND " +
                    "${MediaStore.Audio.Media.DURATION} > 30000 AND " +
                    "${MediaStore.Audio.Media.DATE_MODIFIED} > ?"
            val selectionArgs = arrayOf((lastScanTime / 1000).toString()) // MediaStore uses seconds
            val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
            
            val cursor: Cursor? = contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )
            
            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val pathColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val albumIdColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                
                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val title = it.getString(titleColumn) ?: "Unknown Title"
                    val artist = it.getString(artistColumn) ?: "Unknown Artist"
                    val album = it.getString(albumColumn) ?: "Unknown Album"
                    val duration = it.getLong(durationColumn)
                    val path = it.getString(pathColumn) ?: ""
                    val albumId = it.getLong(albumIdColumn)
                    
                    if (path.isNotEmpty()) {
                        musicFiles.add(
                            MusicFile(
                                id = id,
                                title = title,
                                artist = artist,
                                album = album,
                                duration = duration,
                                path = path,
                                albumId = albumId
                            )
                        )
                    }
                }
            }
            
            musicFiles
        }
    }
}

