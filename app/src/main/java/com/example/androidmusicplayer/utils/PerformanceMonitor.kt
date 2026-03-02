package com.example.androidmusicplayer.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 性能监控工具，用于监控应用性能指标
 */
class PerformanceMonitor private constructor() {
    
    companion object {
        @Volatile
        private var INSTANCE: PerformanceMonitor? = null
        
        fun getInstance(): PerformanceMonitor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PerformanceMonitor().also { INSTANCE = it }
            }
        }
        
        private const val PERFORMANCE_LOG_INTERVAL = 60000L // 1分钟记录一次性能数据
    }
    
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitoringJob: Job? = null
    private val performanceData = ConcurrentHashMap<String, PerformanceMetric>()
    private val mutex = Mutex()
    
    data class PerformanceMetric(
        val name: String,
        val totalTime: AtomicLong = AtomicLong(0),
        val callCount: AtomicLong = AtomicLong(0),
        val maxTime: AtomicLong = AtomicLong(0),
        val minTime: AtomicLong = AtomicLong(Long.MAX_VALUE)
    ) {
        fun getAverageTime(): Long {
            val count = callCount.get()
            return if (count > 0) totalTime.get() / count else 0
        }
        
        fun addMeasurement(timeMs: Long) {
            totalTime.addAndGet(timeMs)
            callCount.incrementAndGet()
            
            // 更新最大值
            var currentMax = maxTime.get()
            while (timeMs > currentMax && !maxTime.compareAndSet(currentMax, timeMs)) {
                currentMax = maxTime.get()
            }
            
            // 更新最小值
            var currentMin = minTime.get()
            while (timeMs < currentMin && !minTime.compareAndSet(currentMin, timeMs)) {
                currentMin = minTime.get()
            }
        }
    }
    
    /**
     * 开始性能监控
     */
    fun startMonitoring() {
        stopMonitoring()
        
        monitoringJob = coroutineScope.launch {
            while (isActive) {
                delay(PERFORMANCE_LOG_INTERVAL)
                logPerformanceData()
            }
        }
    }
    
    /**
     * 停止性能监控
     */
    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }
    
    /**
     * 测量方法执行时间
     */
    suspend inline fun <T> measureTime(operationName: String, operation: () -> T): T {
        val startTime = System.currentTimeMillis()
        return try {
            operation()
        } finally {
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            recordPerformance(operationName, duration)
        }
    }
    
    /**
     * 记录性能数据
     */
    private suspend fun recordPerformance(operationName: String, timeMs: Long) {
        mutex.withLock {
            val metric = performanceData.getOrPut(operationName) {
                PerformanceMetric(operationName)
            }
            metric.addMeasurement(timeMs)
        }
    }
    
    /**
     * 获取性能报告
     */
    suspend fun getPerformanceReport(): Map<String, PerformanceMetric> {
        return mutex.withLock {
            performanceData.toMap()
        }
    }
    
    /**
     * 清除性能数据
     */
    suspend fun clearPerformanceData() {
        mutex.withLock {
            performanceData.clear()
        }
    }
    
    /**
     * 记录性能数据到日志
     */
    private suspend fun logPerformanceData() {
        val report = getPerformanceReport()
        if (report.isNotEmpty()) {
            println("=== Performance Report ===")
            report.forEach { (name, metric) ->
                println("$name: avg=${metric.getAverageTime()}ms, " +
                        "min=${metric.minTime.get()}ms, " +
                        "max=${metric.maxTime.get()}ms, " +
                        "calls=${metric.callCount.get()}")
            }
            println("========================")
        }
    }
    
    /**
     * 获取慢操作列表（平均时间超过阈值的操作）
     */
    suspend fun getSlowOperations(thresholdMs: Long = 1000): List<PerformanceMetric> {
        return getPerformanceReport().values.filter { 
            it.getAverageTime() > thresholdMs 
        }.sortedByDescending { it.getAverageTime() }
    }
    
    /**
     * 销毁性能监控器
     */
    fun destroy() {
        stopMonitoring()
        coroutineScope.cancel()
        performanceData.clear()
    }
}

/**
 * 性能测量注解，用于标记需要监控的方法
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Measured(val operationName: String = "")

