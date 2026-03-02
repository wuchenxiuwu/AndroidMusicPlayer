package com.example.androidmusicplayer.utils

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * 内存管理工具类，用于监控和优化内存使用
 */
class MemoryManager private constructor() {
    
    companion object {
        @Volatile
        private var INSTANCE: MemoryManager? = null
        
        fun getInstance(): MemoryManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MemoryManager().also { INSTANCE = it }
            }
        }
        
        private const val MEMORY_CHECK_INTERVAL = 30000L // 30秒检查一次内存
        private const val LOW_MEMORY_THRESHOLD = 0.85 // 内存使用率超过85%时触发清理
    }
    
    private var memoryMonitorJob: Job? = null
    private val memoryListeners = mutableListOf<WeakReference<MemoryListener>>()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    interface MemoryListener {
        fun onLowMemory()
        fun onMemoryWarning(usagePercentage: Float)
    }
    
    /**
     * 开始内存监控
     */
    fun startMemoryMonitoring(context: Context) {
        stopMemoryMonitoring()
        
        memoryMonitorJob = coroutineScope.launch {
            while (isActive) {
                checkMemoryUsage(context)
                delay(MEMORY_CHECK_INTERVAL)
            }
        }
    }
    
    /**
     * 停止内存监控
     */
    fun stopMemoryMonitoring() {
        memoryMonitorJob?.cancel()
        memoryMonitorJob = null
    }
    
    /**
     * 添加内存监听器
     */
    fun addMemoryListener(listener: MemoryListener) {
        memoryListeners.add(WeakReference(listener))
    }
    
    /**
     * 移除内存监听器
     */
    fun removeMemoryListener(listener: MemoryListener) {
        memoryListeners.removeAll { it.get() == listener || it.get() == null }
    }
    
    /**
     * 检查内存使用情况
     */
    private fun checkMemoryUsage(context: Context) {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val usagePercentage = usedMemory.toFloat() / maxMemory.toFloat()
        
        // 清理无效的监听器引用
        memoryListeners.removeAll { it.get() == null }
        
        if (usagePercentage > LOW_MEMORY_THRESHOLD) {
            // 触发低内存警告
            memoryListeners.forEach { ref ->
                ref.get()?.onLowMemory()
            }
            
            // 执行内存清理
            performMemoryCleanup()
        } else if (usagePercentage > 0.7f) {
            // 内存使用率超过70%时发出警告
            memoryListeners.forEach { ref ->
                ref.get()?.onMemoryWarning(usagePercentage)
            }
        }
    }
    
    /**
     * 执行内存清理
     */
    private fun performMemoryCleanup() {
        // 清理图片缓存
        ImageCache.getInstance().clearCache()
        
        // 建议系统进行垃圾回收
        System.gc()
    }
    
    /**
     * 获取当前内存使用信息
     */
    fun getMemoryInfo(): MemoryInfo {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val freeMemory = maxMemory - usedMemory
        val usagePercentage = usedMemory.toFloat() / maxMemory.toFloat()
        
        return MemoryInfo(
            usedMemory = usedMemory,
            freeMemory = freeMemory,
            maxMemory = maxMemory,
            usagePercentage = usagePercentage
        )
    }
    
    /**
     * 手动触发内存清理
     */
    fun forceMemoryCleanup() {
        performMemoryCleanup()
    }
    
    /**
     * 销毁内存管理器
     */
    fun destroy() {
        stopMemoryMonitoring()
        memoryListeners.clear()
        coroutineScope.cancel()
    }
    
    data class MemoryInfo(
        val usedMemory: Long,
        val freeMemory: Long,
        val maxMemory: Long,
        val usagePercentage: Float
    ) {
        fun getUsedMemoryMB(): Float = usedMemory / (1024f * 1024f)
        fun getFreeMemoryMB(): Float = freeMemory / (1024f * 1024f)
        fun getMaxMemoryMB(): Float = maxMemory / (1024f * 1024f)
    }
}

