package com.example.androidmusicplayer.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 全局错误处理管理器
 */
class ErrorHandler private constructor() {
    
    companion object {
        @Volatile
        private var INSTANCE: ErrorHandler? = null
        
        fun getInstance(): ErrorHandler {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ErrorHandler().also { INSTANCE = it }
            }
        }
        
        private const val TAG = "ErrorHandler"
        private const val MAX_ERROR_HISTORY = 100
    }
    
    private val errorListeners = mutableListOf<WeakReference<ErrorListener>>()
    private val errorHistory = ConcurrentLinkedQueue<ErrorInfo>()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    interface ErrorListener {
        fun onError(errorInfo: ErrorInfo)
        fun onCriticalError(errorInfo: ErrorInfo)
    }
    
    data class ErrorInfo(
        val type: ErrorType,
        val message: String,
        val throwable: Throwable? = null,
        val timestamp: Long = System.currentTimeMillis(),
        val context: String? = null,
        val isCritical: Boolean = false
    ) {
        fun getStackTrace(): String? {
            return throwable?.let {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                it.printStackTrace(pw)
                sw.toString()
            }
        }
    }
    
    enum class ErrorType {
        NETWORK_ERROR,
        FILE_IO_ERROR,
        MEDIA_PLAYBACK_ERROR,
        DATABASE_ERROR,
        PERMISSION_ERROR,
        UNKNOWN_ERROR,
        MEMORY_ERROR,
        PERFORMANCE_ERROR
    }
    
    /**
     * 创建协程异常处理器
     */
    fun createCoroutineExceptionHandler(context: String = ""): CoroutineExceptionHandler {
        return CoroutineExceptionHandler { _, exception ->
            handleError(
                ErrorInfo(
                    type = ErrorType.UNKNOWN_ERROR,
                    message = "协程异常: ${exception.message}",
                    throwable = exception,
                    context = context,
                    isCritical = true
                )
            )
        }
    }
    
    /**
     * 处理错误
     */
    fun handleError(errorInfo: ErrorInfo) {
        // 记录到日志
        if (errorInfo.isCritical) {
            Log.e(TAG, "Critical Error: ${errorInfo.message}", errorInfo.throwable)
        } else {
            Log.w(TAG, "Error: ${errorInfo.message}", errorInfo.throwable)
        }
        
        // 添加到历史记录
        addToHistory(errorInfo)
        
        // 通知监听器
        coroutineScope.launch {
            notifyListeners(errorInfo)
        }
    }
    
    /**
     * 处理网络错误
     */
    fun handleNetworkError(exception: Throwable, context: String = "") {
        val errorInfo = ErrorInfo(
            type = ErrorType.NETWORK_ERROR,
            message = getNetworkErrorMessage(exception),
            throwable = exception,
            context = context
        )
        handleError(errorInfo)
    }
    
    /**
     * 处理文件IO错误
     */
    fun handleFileIOError(exception: Throwable, context: String = "") {
        val errorInfo = ErrorInfo(
            type = ErrorType.FILE_IO_ERROR,
            message = "文件操作失败: ${exception.message}",
            throwable = exception,
            context = context
        )
        handleError(errorInfo)
    }
    
    /**
     * 处理媒体播放错误
     */
    fun handleMediaPlaybackError(exception: Throwable, context: String = "") {
        val errorInfo = ErrorInfo(
            type = ErrorType.MEDIA_PLAYBACK_ERROR,
            message = "媒体播放错误: ${exception.message}",
            throwable = exception,
            context = context,
            isCritical = true
        )
        handleError(errorInfo)
    }
    
    /**
     * 处理数据库错误
     */
    fun handleDatabaseError(exception: Throwable, context: String = "") {
        val errorInfo = ErrorInfo(
            type = ErrorType.DATABASE_ERROR,
            message = "数据库操作失败: ${exception.message}",
            throwable = exception,
            context = context
        )
        handleError(errorInfo)
    }
    
    /**
     * 处理权限错误
     */
    fun handlePermissionError(permission: String, context: String = "") {
        val errorInfo = ErrorInfo(
            type = ErrorType.PERMISSION_ERROR,
            message = "缺少权限: $permission",
            context = context,
            isCritical = true
        )
        handleError(errorInfo)
    }
    
    /**
     * 添加错误监听器
     */
    fun addErrorListener(listener: ErrorListener) {
        errorListeners.add(WeakReference(listener))
    }
    
    /**
     * 移除错误监听器
     */
    fun removeErrorListener(listener: ErrorListener) {
        errorListeners.removeAll { it.get() == listener || it.get() == null }
    }
    
    /**
     * 获取错误历史
     */
    fun getErrorHistory(): List<ErrorInfo> {
        return errorHistory.toList()
    }
    
    /**
     * 清除错误历史
     */
    fun clearErrorHistory() {
        errorHistory.clear()
    }
    
    /**
     * 获取用户友好的错误消息
     */
    fun getUserFriendlyMessage(errorInfo: ErrorInfo): String {
        return when (errorInfo.type) {
            ErrorType.NETWORK_ERROR -> "网络连接失败，请检查网络设置"
            ErrorType.FILE_IO_ERROR -> "文件读取失败，请检查存储权限"
            ErrorType.MEDIA_PLAYBACK_ERROR -> "音频播放失败，文件可能已损坏"
            ErrorType.DATABASE_ERROR -> "数据保存失败，请重试"
            ErrorType.PERMISSION_ERROR -> "需要相关权限才能继续操作"
            ErrorType.MEMORY_ERROR -> "内存不足，请关闭其他应用后重试"
            ErrorType.PERFORMANCE_ERROR -> "操作超时，请重试"
            ErrorType.UNKNOWN_ERROR -> "发生未知错误，请重试"
        }
    }
    
    private fun addToHistory(errorInfo: ErrorInfo) {
        errorHistory.offer(errorInfo)
        
        // 保持历史记录数量在限制内
        while (errorHistory.size > MAX_ERROR_HISTORY) {
            errorHistory.poll()
        }
    }
    
    private fun notifyListeners(errorInfo: ErrorInfo) {
        // 清理无效的监听器引用
        errorListeners.removeAll { it.get() == null }
        
        errorListeners.forEach { ref ->
            ref.get()?.let { listener ->
                try {
                    if (errorInfo.isCritical) {
                        listener.onCriticalError(errorInfo)
                    } else {
                        listener.onError(errorInfo)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error notifying listener", e)
                }
            }
        }
    }
    
    private fun getNetworkErrorMessage(exception: Throwable): String {
        return when {
            exception.message?.contains("timeout", ignoreCase = true) == true -> "网络连接超时"
            exception.message?.contains("host", ignoreCase = true) == true -> "无法连接到服务器"
            exception.message?.contains("network", ignoreCase = true) == true -> "网络不可用"
            else -> "网络请求失败: ${exception.message}"
        }
    }
}

/**
 * 安全执行代码块，自动处理异常
 */
inline fun <T> safeExecute(
    context: String = "",
    onError: ((ErrorHandler.ErrorInfo) -> Unit)? = null,
    block: () -> T
): T? {
    return try {
        block()
    } catch (e: Exception) {
        val errorInfo = ErrorHandler.ErrorInfo(
            type = ErrorHandler.ErrorType.UNKNOWN_ERROR,
            message = "执行失败: ${e.message}",
            throwable = e,
            context = context
        )
        
        onError?.invoke(errorInfo) ?: ErrorHandler.getInstance().handleError(errorInfo)
        null
    }
}

/**
 * 安全执行挂起函数
 */
suspend inline fun <T> safeExecuteSuspend(
    context: String = "",
    onError: ((ErrorHandler.ErrorInfo) -> Unit)? = null,
    block: suspend () -> T
): T? {
    return try {
        block()
    } catch (e: Exception) {
        val errorInfo = ErrorHandler.ErrorInfo(
            type = ErrorHandler.ErrorType.UNKNOWN_ERROR,
            message = "异步执行失败: ${e.message}",
            throwable = e,
            context = context
        )
        
        onError?.invoke(errorInfo) ?: ErrorHandler.getInstance().handleError(errorInfo)
        null
    }
}

