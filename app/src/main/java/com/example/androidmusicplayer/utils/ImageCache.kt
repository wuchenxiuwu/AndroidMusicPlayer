package com.example.androidmusicplayer.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * 图片缓存管理器，用于优化专辑封面加载性能
 */
class ImageCache private constructor() {
    
    companion object {
        @Volatile
        private var INSTANCE: ImageCache? = null
        
        fun getInstance(): ImageCache {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ImageCache().also { INSTANCE = it }
            }
        }
    }
    
    // 内存缓存，使用 LRU 策略
    private val memoryCache: LruCache<String, Bitmap> by lazy {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8 // 使用可用内存的 1/8 作为缓存
        
        object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return bitmap.byteCount / 1024
            }
        }
    }
    
    /**
     * 从缓存获取图片
     */
    fun getBitmapFromCache(key: String): Bitmap? {
        return memoryCache.get(key)
    }
    
    /**
     * 添加图片到缓存
     */
    fun addBitmapToCache(key: String, bitmap: Bitmap) {
        if (getBitmapFromCache(key) == null) {
            memoryCache.put(key, bitmap)
        }
    }
    
    /**
     * 异步加载专辑封面
     */
    suspend fun loadAlbumArt(context: Context, albumArtUri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = albumArtUri.toString()
        
        // 先检查缓存
        getBitmapFromCache(cacheKey)?.let { return@withContext it }
        
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(albumArtUri)
            inputStream?.use { stream ->
                val options = BitmapFactory.Options().apply {
                    // 首先获取图片尺寸
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(stream, null, options)
                
                // 计算合适的采样率
                options.inSampleSize = calculateInSampleSize(options, 300, 300)
                options.inJustDecodeBounds = false
                
                // 重新打开流并解码
                context.contentResolver.openInputStream(albumArtUri)?.use { newStream ->
                    val bitmap = BitmapFactory.decodeStream(newStream, null, options)
                    bitmap?.let { 
                        addBitmapToCache(cacheKey, it)
                        return@withContext it
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        null
    }
    
    /**
     * 计算合适的采样率以减少内存使用
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }
    
    /**
     * 清理缓存
     */
    fun clearCache() {
        memoryCache.evictAll()
    }
    
    /**
     * 获取缓存大小信息
     */
    fun getCacheInfo(): String {
        return "Cache size: ${memoryCache.size()}, Max size: ${memoryCache.maxSize()}"
    }
}

/**
 * Compose 中使用的异步图片加载 Hook
 */
@Composable
fun rememberAsyncImageLoader(
    context: Context,
    imageUri: Uri?,
    placeholder: Bitmap? = null
): Bitmap? {
    var bitmap by remember { mutableStateOf(placeholder) }
    
    LaunchedEffect(imageUri) {
        if (imageUri != null) {
            val loadedBitmap = ImageCache.getInstance().loadAlbumArt(context, imageUri)
            bitmap = loadedBitmap ?: placeholder
        } else {
            bitmap = placeholder
        }
    }
    
    return bitmap
}

