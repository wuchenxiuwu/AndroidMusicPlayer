package com.example.androidmusicplayer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.androidmusicplayer.MainActivity
import com.example.androidmusicplayer.R
import com.example.androidmusicplayer.data.MusicFile
import com.example.androidmusicplayer.utils.ErrorHandler
import com.example.androidmusicplayer.utils.safeExecute

class MusicService : Service(), ErrorHandler.ErrorListener {
    
    private val binder = MusicBinder()
    private lateinit var exoPlayer: ExoPlayer
    private var currentMusicFile: MusicFile? = null
    private val errorHandler = ErrorHandler.getInstance()
    
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "music_playback_channel"
    }
    
    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // 注册错误监听器
        errorHandler.addErrorListener(this)
        
        safeExecute("MusicService.onCreate") {
            initializePlayer()
            createNotificationChannel()
        }
    }
    
    private fun initializePlayer() {
        try {
            exoPlayer = ExoPlayer.Builder(this).build()
            
            exoPlayer.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    safeExecute("Player.onPlaybackStateChanged") {
                        updateNotification()
                        
                        // 处理播放状态变化
                        when (playbackState) {
                            Player.STATE_IDLE -> {
                                // 播放器空闲
                            }
                            Player.STATE_BUFFERING -> {
                                // 缓冲中
                            }
                            Player.STATE_READY -> {
                                // 准备就绪
                            }
                            Player.STATE_ENDED -> {
                                // 播放结束
                                onPlaybackEnded()
                            }
                        }
                    }
                }
                
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    safeExecute("Player.onIsPlayingChanged") {
                        updateNotification()
                    }
                }
                
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    errorHandler.handleMediaPlaybackError(
                        error,
                        "ExoPlayer播放错误: ${currentMusicFile?.title}"
                    )
                    
                    // 尝试恢复播放
                    safeExecute("Player.onPlayerError.recovery") {
                        exoPlayer.prepare()
                    }
                }
            })
        } catch (e: Exception) {
            errorHandler.handleError(
                ErrorHandler.ErrorInfo(
                    type = ErrorHandler.ErrorType.MEDIA_PLAYBACK_ERROR,
                    message = "初始化播放器失败",
                    throwable = e,
                    context = "MusicService.initializePlayer",
                    isCritical = true
                )
            )
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            safeExecute("MusicService.createNotificationChannel") {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Music Playback",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows currently playing music"
                    setShowBadge(false)
                }
                
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.createNotificationChannel(channel)
            }
        }
    }
    
    fun playMusic(musicFile: MusicFile) {
        safeExecute("MusicService.playMusic") {
            currentMusicFile = musicFile
            
            try {
                val mediaItem = MediaItem.fromUri(musicFile.path)
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.play()
                
                startForeground(NOTIFICATION_ID, createNotification())
            } catch (e: Exception) {
                errorHandler.handleMediaPlaybackError(
                    e,
                    "播放音乐失败: ${musicFile.title}"
                )
            }
        }
    }
    
    fun pauseMusic() {
        safeExecute("MusicService.pauseMusic") {
            exoPlayer.pause()
            updateNotification()
        }
    }
    
    fun resumeMusic() {
        safeExecute("MusicService.resumeMusic") {
            exoPlayer.play()
            updateNotification()
        }
    }
    
    fun stopMusic() {
        safeExecute("MusicService.stopMusic") {
            exoPlayer.stop()
            stopForeground(true)
        }
    }
    
    fun isPlaying(): Boolean {
        return safeExecute("MusicService.isPlaying") {
            exoPlayer.isPlaying
        } ?: false
    }
    
    fun getCurrentPosition(): Long {
        return safeExecute("MusicService.getCurrentPosition") {
            exoPlayer.currentPosition
        } ?: 0L
    }
    
    fun getDuration(): Long {
        return safeExecute("MusicService.getDuration") {
            exoPlayer.duration
        } ?: 0L
    }
    
    fun seekTo(position: Long) {
        safeExecute("MusicService.seekTo") {
            exoPlayer.seekTo(position)
        }
    }
    
    private fun onPlaybackEnded() {
        // 播放结束时的处理逻辑
        // 可以在这里实现自动播放下一首等功能
        safeExecute("MusicService.onPlaybackEnded") {
            stopForeground(false)
            updateNotification()
        }
    }
    
    private fun createNotification(): Notification? {
        return safeExecute("MusicService.createNotification") {
            val intent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val playPauseAction = if (exoPlayer.isPlaying) {
                NotificationCompat.Action(
                    android.R.drawable.ic_media_pause,
                    "Pause",
                    createPendingIntent("PAUSE")
                )
            } else {
                NotificationCompat.Action(
                    android.R.drawable.ic_media_play,
                    "Play",
                    createPendingIntent("PLAY")
                )
            }
            
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(currentMusicFile?.title ?: "Unknown Title")
                .setContentText(currentMusicFile?.artist ?: "Unknown Artist")
                .setSmallIcon(R.drawable.ic_music_note)
                .setContentIntent(pendingIntent)
                .addAction(
                    NotificationCompat.Action(
                        android.R.drawable.ic_media_previous,
                        "Previous",
                        createPendingIntent("PREVIOUS")
                    )
                )
                .addAction(playPauseAction)
                .addAction(
                    NotificationCompat.Action(
                        android.R.drawable.ic_media_next,
                        "Next",
                        createPendingIntent("NEXT")
                    )
                )
                .setStyle(
                    androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0, 1, 2)
                )
                .setOngoing(exoPlayer.isPlaying)
                .build()
        }
    }
    
    private fun createPendingIntent(action: String): PendingIntent? {
        return safeExecute("MusicService.createPendingIntent") {
            val intent = Intent(this, MusicService::class.java).apply {
                this.action = action
            }
            PendingIntent.getService(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
    
    private fun updateNotification() {
        safeExecute("MusicService.updateNotification") {
            val notification = createNotification()
            if (notification != null) {
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        safeExecute("MusicService.onStartCommand") {
            when (intent?.action) {
                "PLAY" -> resumeMusic()
                "PAUSE" -> pauseMusic()
                "PREVIOUS" -> {
                    // TODO: Implement previous track logic
                }
                "NEXT" -> {
                    // TODO: Implement next track logic
                }
            }
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        safeExecute("MusicService.onDestroy") {
            // 移除错误监听器
            errorHandler.removeErrorListener(this)
            
            // 释放播放器资源
            if (::exoPlayer.isInitialized) {
                exoPlayer.release()
            }
        }
    }
    
    // ErrorHandler.ErrorListener 实现
    override fun onError(errorInfo: ErrorHandler.ErrorInfo) {
        // 处理一般错误
        when (errorInfo.type) {
            ErrorHandler.ErrorType.MEDIA_PLAYBACK_ERROR -> {
                // 媒体播放错误的特殊处理
                pauseMusic()
            }
            else -> {
                // 其他错误的处理
            }
        }
    }
    
    override fun onCriticalError(errorInfo: ErrorHandler.ErrorInfo) {
        // 处理严重错误
        when (errorInfo.type) {
            ErrorHandler.ErrorType.MEDIA_PLAYBACK_ERROR -> {
                // 严重的播放错误，停止播放
                stopMusic()
            }
            else -> {
                // 其他严重错误的处理
            }
        }
    }
}

