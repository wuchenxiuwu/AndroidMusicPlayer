package com.example.androidmusicplayer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.androidmusicplayer.lyrics.LyricsInfo
import com.example.androidmusicplayer.lyrics.LyricsManager
import com.example.androidmusicplayer.ui.components.LyricsDisplay
import com.example.androidmusicplayer.ui.components.LyricsSettingsPanel
import kotlinx.coroutines.launch

/**
 * 歌词页面，参考QQ音乐的歌词显示界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsScreen(
    title: String,
    artist: String,
    currentPosition: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var lyricsInfo by remember { mutableStateOf<LyricsInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    
    // 歌词设置
    var fontSize by remember { mutableFloatStateOf(16f) }
    var showTranslation by remember { mutableStateOf(false) }
    
    val lyricsManager = LyricsManager.getInstance()
    val coroutineScope = rememberCoroutineScope()
    
    // 加载歌词
    LaunchedEffect(title, artist) {
        if (title.isNotBlank() && artist.isNotBlank()) {
            isLoading = true
            errorMessage = null
            
            when (val result = lyricsManager.getLyrics(title, artist)) {
                is LyricsManager.LyricsResult.Success -> {
                    lyricsInfo = result.lyrics
                    isLoading = false
                }
                is LyricsManager.LyricsResult.NotFound -> {
                    lyricsInfo = null
                    errorMessage = result.message
                    isLoading = false
                }
                is LyricsManager.LyricsResult.Error -> {
                    lyricsInfo = null
                    errorMessage = result.message
                    isLoading = false
                }
            }
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        when {
            isLoading -> {
                // 加载状态
                LoadingLyricsDisplay()
            }
            errorMessage != null -> {
                // 错误状态
                ErrorLyricsDisplay(
                    errorMessage = errorMessage!!,
                    onRetry = {
                        coroutineScope.launch {
                            isLoading = true
                            errorMessage = null
                            
                            when (val result = lyricsManager.getLyrics(title, artist)) {
                                is LyricsManager.LyricsResult.Success -> {
                                    lyricsInfo = result.lyrics
                                    isLoading = false
                                }
                                is LyricsManager.LyricsResult.NotFound -> {
                                    lyricsInfo = null
                                    errorMessage = result.message
                                    isLoading = false
                                }
                                is LyricsManager.LyricsResult.Error -> {
                                    lyricsInfo = null
                                    errorMessage = result.message
                                    isLoading = false
                                }
                            }
                        }
                    }
                )
            }
            else -> {
                // 歌词显示
                LyricsDisplay(
                    lyricsInfo = lyricsInfo,
                    currentPosition = currentPosition,
                    onSeek = onSeek,
                    fontSize = fontSize,
                    showTranslation = showTranslation,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        
        // 设置按钮
        FloatingActionButton(
            onClick = { showSettings = !showSettings },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "歌词设置"
            )
        }
        
        // 设置面板
        if (showSettings) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                LyricsSettingsPanel(
                    fontSize = fontSize,
                    onFontSizeChange = { fontSize = it },
                    showTranslation = showTranslation,
                    onShowTranslationChange = { showTranslation = it }
                )
            }
        }
    }
}

/**
 * 加载歌词显示组件
 */
@Composable
fun LoadingLyricsDisplay() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "正在加载歌词...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * 错误歌词显示组件
 */
@Composable
fun ErrorLyricsDisplay(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "😔",
                style = MaterialTheme.typography.displayMedium
            )
            
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("重试")
            }
            
            OutlinedButton(
                onClick = { /* TODO: 打开歌词搜索或手动添加界面 */ }
            ) {
                Text("手动添加歌词")
            }
        }
    }
}

/**
 * 歌词搜索和管理页面
 */
@Composable
fun LyricsManagementScreen(
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<LyricsSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "歌词搜索",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("搜索歌曲或艺术家") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                // TODO: 实现歌词搜索
                                isSearching = true
                            },
                            enabled = searchQuery.isNotBlank() && !isSearching,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isSearching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("搜索")
                            }
                        }
                        
                        OutlinedButton(
                            onClick = { /* TODO: 打开本地歌词文件选择器 */ },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("导入本地歌词")
                        }
                    }
                }
            }
        }
        
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "手动添加歌词",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    var title by remember { mutableStateOf("") }
                    var artist by remember { mutableStateOf("") }
                    var lyricsContent by remember { mutableStateOf("") }
                    
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("歌曲标题") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = artist,
                        onValueChange = { artist = it },
                        label = { Text("艺术家") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = lyricsContent,
                        onValueChange = { lyricsContent = it },
                        label = { Text("歌词内容（支持LRC格式）") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        maxLines = 6
                    )
                    
                    Button(
                        onClick = {
                            // TODO: 保存手动添加的歌词
                        },
                        enabled = title.isNotBlank() && artist.isNotBlank() && lyricsContent.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("保存歌词")
                    }
                }
            }
        }
    }
}

/**
 * 歌词搜索结果数据类
 */
data class LyricsSearchResult(
    val title: String,
    val artist: String,
    val album: String?,
    val duration: Long?,
    val source: String
)

