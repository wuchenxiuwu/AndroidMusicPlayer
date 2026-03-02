package com.example.androidmusicplayer

data class PlaylistResponse(
    val playlist: Playlist?,
    val code: Int
)

data class Playlist(
    val id: Long,
    val name: String?,
    val coverImgUrl: String?,
    val description: String?,
    val tracks: List<Song>?,
    val trackCount: Int,
    val playCount: Long,
    val creator: User?
) : java.io.Serializable

data class User(
    val userId: Long,
    val nickname: String?,
    val avatarUrl: String?
)
