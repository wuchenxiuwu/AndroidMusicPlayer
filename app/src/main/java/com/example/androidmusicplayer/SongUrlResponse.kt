package com.example.androidmusicplayer

data class SongUrlResponse(
    val data: List<SongUrl>?,
    val code: Int
)

data class SongUrl(
    val id: Long,
    val url: String?,
    val size: Long,
    val type: String?,
    val level: String?
)
