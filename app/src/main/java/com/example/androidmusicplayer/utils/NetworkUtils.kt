package com.example.androidmusicplayer.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * 网络工具类，提供网络状态检查和HTTP请求功能
 */
class NetworkUtils private constructor() {
    
    companion object {
        @Volatile
        private var INSTANCE: NetworkUtils? = null
        
        fun getInstance(): NetworkUtils {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NetworkUtils().also { INSTANCE = it }
            }
        }
        
        private const val DEFAULT_TIMEOUT = 30000L // 30秒
        private const val CONNECT_TIMEOUT = 10000L // 10秒
    }
    
    private val errorHandler = ErrorHandler.getInstance()
    
    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.MILLISECONDS)
            .readTimeout(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS)
            .writeTimeout(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
    
    /**
     * 检查网络连接状态
     */
    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                networkInfo?.isConnected == true
            }
        } catch (e: Exception) {
            errorHandler.handleError(
                ErrorHandler.ErrorInfo(
                    type = ErrorHandler.ErrorType.NETWORK_ERROR,
                    message = "检查网络状态失败",
                    throwable = e,
                    context = "NetworkUtils.isNetworkAvailable"
                )
            )
            false
        }
    }
    
    /**
     * 获取网络类型
     */
    fun getNetworkType(context: Context): NetworkType {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return NetworkType.NONE
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkType.NONE
                
                when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
                    else -> NetworkType.OTHER
                }
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                when (networkInfo?.type) {
                    ConnectivityManager.TYPE_WIFI -> NetworkType.WIFI
                    ConnectivityManager.TYPE_MOBILE -> NetworkType.CELLULAR
                    ConnectivityManager.TYPE_ETHERNET -> NetworkType.ETHERNET
                    else -> if (networkInfo?.isConnected == true) NetworkType.OTHER else NetworkType.NONE
                }
            }
        } catch (e: Exception) {
            errorHandler.handleError(
                ErrorHandler.ErrorInfo(
                    type = ErrorHandler.ErrorType.NETWORK_ERROR,
                    message = "获取网络类型失败",
                    throwable = e,
                    context = "NetworkUtils.getNetworkType"
                )
            )
            NetworkType.NONE
        }
    }
    
    /**
     * 执行HTTP GET请求
     */
    suspend fun httpGet(url: String, timeoutMs: Long = DEFAULT_TIMEOUT): NetworkResult<String> = withContext(Dispatchers.IO) {
        try {
            withTimeout(timeoutMs) {
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                
                val response = okHttpClient.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    NetworkResult.Success(body)
                } else {
                    val error = NetworkError(
                        code = response.code,
                        message = "HTTP ${response.code}: ${response.message}",
                        type = NetworkErrorType.HTTP_ERROR
                    )
                    errorHandler.handleNetworkError(
                        RuntimeException("HTTP请求失败: ${response.code}"),
                        "NetworkUtils.httpGet"
                    )
                    NetworkResult.Error(error)
                }
            }
        } catch (e: Exception) {
            val networkError = when (e) {
                is SocketTimeoutException -> NetworkError(
                    code = -1,
                    message = "请求超时",
                    type = NetworkErrorType.TIMEOUT
                )
                is UnknownHostException -> NetworkError(
                    code = -2,
                    message = "无法连接到服务器",
                    type = NetworkErrorType.NO_CONNECTION
                )
                is IOException -> NetworkError(
                    code = -3,
                    message = "网络IO错误",
                    type = NetworkErrorType.IO_ERROR
                )
                else -> NetworkError(
                    code = -999,
                    message = e.message ?: "未知网络错误",
                    type = NetworkErrorType.UNKNOWN
                )
            }
            
            errorHandler.handleNetworkError(e, "NetworkUtils.httpGet")
            NetworkResult.Error(networkError)
        }
    }
    
    /**
     * 执行HTTP POST请求
     */
    suspend fun httpPost(url: String, body: String, contentType: String = "application/json", timeoutMs: Long = DEFAULT_TIMEOUT): NetworkResult<String> = withContext(Dispatchers.IO) {
        try {
            withTimeout(timeoutMs) {
                val requestBody = okhttp3.RequestBody.create(
                    okhttp3.MediaType.parse(contentType),
                    body
                )
                
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()
                
                val response = okHttpClient.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    NetworkResult.Success(responseBody)
                } else {
                    val error = NetworkError(
                        code = response.code,
                        message = "HTTP ${response.code}: ${response.message}",
                        type = NetworkErrorType.HTTP_ERROR
                    )
                    errorHandler.handleNetworkError(
                        RuntimeException("HTTP POST请求失败: ${response.code}"),
                        "NetworkUtils.httpPost"
                    )
                    NetworkResult.Error(error)
                }
            }
        } catch (e: Exception) {
            val networkError = when (e) {
                is SocketTimeoutException -> NetworkError(
                    code = -1,
                    message = "请求超时",
                    type = NetworkErrorType.TIMEOUT
                )
                is UnknownHostException -> NetworkError(
                    code = -2,
                    message = "无法连接到服务器",
                    type = NetworkErrorType.NO_CONNECTION
                )
                is IOException -> NetworkError(
                    code = -3,
                    message = "网络IO错误",
                    type = NetworkErrorType.IO_ERROR
                )
                else -> NetworkError(
                    code = -999,
                    message = e.message ?: "未知网络错误",
                    type = NetworkErrorType.UNKNOWN
                )
            }
            
            errorHandler.handleNetworkError(e, "NetworkUtils.httpPost")
            NetworkResult.Error(networkError)
        }
    }
    
    /**
     * 测试网络连接
     */
    suspend fun testConnection(url: String = "https://www.google.com"): Boolean {
        return when (val result = httpGet(url, 5000)) {
            is NetworkResult.Success -> true
            is NetworkResult.Error -> false
        }
    }
    
    enum class NetworkType {
        WIFI, CELLULAR, ETHERNET, OTHER, NONE
    }
    
    enum class NetworkErrorType {
        TIMEOUT, NO_CONNECTION, HTTP_ERROR, IO_ERROR, UNKNOWN
    }
    
    data class NetworkError(
        val code: Int,
        val message: String,
        val type: NetworkErrorType
    )
    
    sealed class NetworkResult<out T> {
        data class Success<T>(val data: T) : NetworkResult<T>()
        data class Error(val error: NetworkError) : NetworkResult<Nothing>()
    }
}

