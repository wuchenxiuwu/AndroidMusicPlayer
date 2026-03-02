package com.example.androidmusicplayer

data class SongDetailResponse(
    val songs: List<SongDetail>?,
    val code: Int
)

data class SongDetail(
    val id: Long,
    val name: String?,
    val artists: List<Artist>?,
    val album: AlbumDetail?,
    val duration: Long,
    val mvId: Long?
)

data class AlbumDetail(
    val id: Long,
    val name: String?,
    val picUrl: String?,
    val description: String?
) : java.io.Serializable

data class Mv(
    val id: Long,
    val name: String?,
    val artistName: String?,
    val cover: String?
)
