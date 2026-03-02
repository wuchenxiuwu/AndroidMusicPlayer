package com.example.androidmusicplayer

data class SearchResponse(
    val result: SearchResult?,
    val code: Int
)

data class SearchResult(
    val songs: List<Song>?,
    val songCount: Int
)

data class Song(
    val id: Long,
    val name: String?,
    val artists: List<Artist>?,
    val album: Album?,
    val duration: Long
)

data class Artist(
    val id: Long,
    val name: String?
)

data class Album(
    val id: Long,
    val name: String?,
    val picUrl: String?
) : java.io.Serializable
