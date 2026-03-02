package com.example.androidmusicplayer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.androidmusicplayer.streaming.OnlineMusic
import com.example.androidmusicplayer.streaming.SearchResult
import com.example.androidmusicplayer.streaming.StreamingMusicManager
import kotlinx.coroutines.launch

/**
 * 在线音乐搜索和浏览页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineMusicScreen(
    onMusicSelected: (OnlineMusic) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<OnlineMusic>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    
    // 推荐和热门音乐
    var recommendedMusic by remember { mutableStateOf<List<OnlineMusic>>(emptyList()) }
    var trendingMusic by remember { mutableStateOf<List<OnlineMusic>>(emptyList()) }
    var isLoadingRecommended by remember { mutableStateOf(false) }
    var isLoadingTrending by remember { mutableStateOf(false) }
    
    val streamingManager = StreamingMusicManager.getInstance()
    val coroutineScope = rememberCoroutineScope()
    
    // 加载推荐和热门音乐
    LaunchedEffect(Unit) {
        // 加载推荐音乐
        isLoadingRecommended = true
        when (val result = streamingManager.getRecommendations()) {
            is SearchResult.Success -> {
                recommendedMusic = result.music
            }
            else -> {
                // 忽略错误
            }
        }
        isLoadingRecommended = false
        
        // 加载热门音乐
        isLoadingTrending = true
        when (val result = streamingManager.getTrendingMusic()) {
            is SearchResult.Success -> {
                trendingMusic = result.music
            }
            else -> {
                // 忽略错误
            }
        }
        isLoadingTrending = false
    }
    
    Column(modifier = modifier.fillMaxSize()) {
        // 搜索栏
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("搜索音乐、艺术家或专辑") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "清除")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Button(
                    onClick = {
                        if (searchQuery.isNotBlank()) {
                            coroutineScope.launch {
                                isSearching = true
                                errorMessage = null
                                
                                when (val result = streamingManager.searchMusic(searchQuery)) {
                                    is SearchResult.Success -> {
                                        searchResults = result.music
                                        selectedTab = 0 // 切换到搜索结果标签
                                    }
                                    is SearchResult.Error -> {
                                        errorMessage = result.message
                                        searchResults = emptyList()
                                    }
                                    is SearchResult.Empty -> {
                                        errorMessage = result.message
                                        searchResults = emptyList()
                                    }
                                }
                                
                                isSearching = false
                            }
                        }
                    },
                    enabled = searchQuery.isNotBlank() && !isSearching,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("搜索")
                }
            }
        }
        
        // 标签页
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("搜索结果") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("推荐") }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("热门") }
            )
        }
        
        // 内容区域
        when (selectedTab) {
            0 -> {
                // 搜索结果
                if (errorMessage != null) {
                    ErrorDisplay(
                        message = errorMessage!!,
                        onRetry = {
                            if (searchQuery.isNotBlank()) {
                                coroutineScope.launch {
                                    isSearching = true
                                    errorMessage = null
                                    
                                    when (val result = streamingManager.searchMusic(searchQuery)) {
                                        is SearchResult.Success -> {
                                            searchResults = result.music
                                        }
                                        is SearchResult.Error -> {
                                            errorMessage = result.message
                                            searchResults = emptyList()
                                        }
                                        is SearchResult.Empty -> {
                                            errorMessage = result.message
                                            searchResults = emptyList()
                                        }
                                    }
                                    
                                    isSearching = false
                                }
                            }
                        }
                    )
                } else if (searchResults.isEmpty() && searchQuery.isBlank()) {
                    EmptySearchDisplay()
                } else {
                    MusicList(
                        musicList = searchResults,
                        onMusicSelected = onMusicSelected
                    )
                }
            }
            1 -> {
                // 推荐音乐
                if (isLoadingRecommended) {
                    LoadingDisplay("正在加载推荐音乐...")
                } else {
                    MusicList(
                        musicList = recommendedMusic,
                        onMusicSelected = onMusicSelected
                    )
                }
            }
            2 -> {
                // 热门音乐
                if (isLoadingTrending) {
                    LoadingDisplay("正在加载热门音乐...")
                } else {
                    MusicList(
                        musicList = trendingMusic,
                        onMusicSelected = onMusicSelected
                    )
                }
            }
        }
    }
}

/**
 * 音乐列表组件
 */
@Composable
fun MusicList(
    musicList: List<OnlineMusic>,
    onMusicSelected: (OnlineMusic) -> Unit,
    modifier: Modifier = Modifier
) {
    if (musicList.isEmpty()) {
        EmptyMusicListDisplay()
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(musicList) { music ->
                MusicItem(
                    music = music,
                    onMusicSelected = onMusicSelected
                )
            }
        }
    }
}

/**
 * 音乐项组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicItem(
    music: OnlineMusic,
    onMusicSelected: (OnlineMusic) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = { onMusicSelected(music) },
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 封面图片
            AsyncImage(
                model = music.coverUrl,
                contentDescription = "专辑封面",
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
                placeholder = {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                error = {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 音乐信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = music.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = music.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (music.album != null) {
                    Text(
                        text = music.album,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // 时长和来源
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = music.getDurationString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = music.provider,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 空搜索显示组件
 */
@Composable
fun EmptySearchDisplay() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            
            Text(
                text = "搜索在线音乐",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = "输入歌曲名、艺术家或专辑名进行搜索",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * 空音乐列表显示组件
 */
@Composable
fun EmptyMusicListDisplay() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.MusicOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            
            Text(
                text = "暂无音乐",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 错误显示组件
 */
@Composable
fun ErrorDisplay(
    message: String,
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
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Button(onClick = onRetry) {
                Text("重试")
            }
        }
    }
}

/**
 * 加载显示组件
 */
@Composable
fun LoadingDisplay(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp)
            )
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

