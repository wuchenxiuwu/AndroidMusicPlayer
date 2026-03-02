package com.example.androidmusicplayer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.androidmusicplayer.lyrics.LyricsInfo
import com.example.androidmusicplayer.lyrics.LyricsLine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 歌词显示组件，参考QQ音乐的歌词显示效果
 */
@Composable
fun LyricsDisplay(
    lyricsInfo: LyricsInfo?,
    currentPosition: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    showTranslation: Boolean = false,
    fontSize: Float = 16f,
    highlightColor: Color = MaterialTheme.colorScheme.primary,
    normalColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
    backgroundColor: Color = Color.Transparent
) {
    if (lyricsInfo == null || !lyricsInfo.hasLyrics()) {
        // 显示无歌词状态
        NoLyricsDisplay(modifier = modifier)
        return
    }
    
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // 当前歌词行索引
    val currentIndex = remember(currentPosition) {
        lyricsInfo.getCurrentLyricsIndex(currentPosition)
    }
    
    // 自动滚动到当前歌词
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            coroutineScope.launch {
                listState.animateScrollToItem(
                    index = maxOf(0, currentIndex - 2), // 保持当前歌词在中间位置
                    scrollOffset = 0
                )
            }
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(vertical = 100.dp), // 上下留白，让歌词居中显示
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(lyricsInfo.lyricsLines) { index, lyricsLine ->
                LyricsLineItem(
                    lyricsLine = lyricsLine,
                    isCurrentLine = index == currentIndex,
                    currentPosition = currentPosition,
                    fontSize = fontSize,
                    highlightColor = highlightColor,
                    normalColor = normalColor,
                    onSeek = onSeek
                )
            }
        }
        
        // 歌词进度指示器
        LyricsProgressIndicator(
            lyricsInfo = lyricsInfo,
            currentPosition = currentPosition,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
        )
    }
}

/**
 * 单行歌词组件
 */
@Composable
fun LyricsLineItem(
    lyricsLine: LyricsLine,
    isCurrentLine: Boolean,
    currentPosition: Long,
    fontSize: Float,
    highlightColor: Color,
    normalColor: Color,
    onSeek: (Long) -> Unit
) {
    val animatedFontSize by animateFloatAsState(
        targetValue = if (isCurrentLine) fontSize + 2f else fontSize,
        animationSpec = tween(durationMillis = 300),
        label = "fontSize"
    )
    
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isCurrentLine) 1f else 0.6f,
        animationSpec = tween(durationMillis = 300),
        label = "alpha"
    )
    
    // 计算当前行的播放进度（用于卡拉OK效果）
    val progress = if (isCurrentLine && lyricsLine.isInTimeRange(currentPosition)) {
        val lineProgress = (currentPosition - lyricsLine.startTime).toFloat() / 
                          (lyricsLine.endTime - lyricsLine.startTime).toFloat()
        lineProgress.coerceIn(0f, 1f)
    } else if (currentPosition >= lyricsLine.endTime) {
        1f
    } else {
        0f
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(lyricsLine) {
                detectDragGestures { _, _ ->
                    // 点击歌词行跳转到对应时间
                    onSeek(lyricsLine.startTime)
                }
            }
    ) {
        if (lyricsLine.text.isNotBlank()) {
            // 卡拉OK效果的歌词显示
            KaraokeText(
                text = lyricsLine.text,
                progress = progress,
                fontSize = animatedFontSize.sp,
                highlightColor = if (isCurrentLine) highlightColor else normalColor,
                normalColor = normalColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(animatedAlpha)
            )
        } else {
            // 空歌词行，显示音乐符号
            Text(
                text = "♪",
                fontSize = animatedFontSize.sp,
                color = normalColor,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(animatedAlpha)
            )
        }
    }
}

/**
 * 卡拉OK效果的文本组件
 */
@Composable
fun KaraokeText(
    text: String,
    progress: Float,
    fontSize: androidx.compose.ui.unit.TextUnit,
    highlightColor: Color,
    normalColor: Color,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // 背景文本（未播放部分）
        Text(
            text = text,
            fontSize = fontSize,
            color = normalColor,
            textAlign = TextAlign.Center,
            fontWeight = if (progress > 0) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.fillMaxWidth()
        )
        
        // 前景文本（已播放部分）
        if (progress > 0) {
            Text(
                text = text,
                fontSize = fontSize,
                color = highlightColor,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(
                            topStart = 0.dp,
                            topEnd = if (progress >= 1f) 0.dp else 50.dp,
                            bottomStart = 0.dp,
                            bottomEnd = if (progress >= 1f) 0.dp else 50.dp
                        )
                    )
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent
                            ),
                            startX = 0f,
                            endX = progress * 1000f
                        )
                    )
            )
        }
    }
}

/**
 * 歌词进度指示器
 */
@Composable
fun LyricsProgressIndicator(
    lyricsInfo: LyricsInfo,
    currentPosition: Long,
    modifier: Modifier = Modifier
) {
    val totalDuration = lyricsInfo.getTotalDuration()
    val progress = if (totalDuration > 0) {
        (currentPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    
    Canvas(
        modifier = modifier
            .width(4.dp)
            .height(200.dp)
    ) {
        drawLyricsProgress(progress)
    }
}

/**
 * 绘制歌词进度
 */
private fun DrawScope.drawLyricsProgress(progress: Float) {
    val trackColor = Color.Gray.copy(alpha = 0.3f)
    val progressColor = Color.Blue
    
    // 绘制轨道
    drawLine(
        color = trackColor,
        start = Offset(size.width / 2, 0f),
        end = Offset(size.width / 2, size.height),
        strokeWidth = size.width
    )
    
    // 绘制进度
    val progressHeight = size.height * progress
    drawLine(
        color = progressColor,
        start = Offset(size.width / 2, size.height - progressHeight),
        end = Offset(size.width / 2, size.height),
        strokeWidth = size.width
    )
}

/**
 * 无歌词显示组件
 */
@Composable
fun NoLyricsDisplay(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 音乐符号动画
            val infiniteTransition = rememberInfiniteTransition(label = "musicNote")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 3000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "rotation"
            )
            
            Text(
                text = "♪",
                fontSize = 48.sp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.graphicsLayer {
                    rotationZ = rotation
                }
            )
            
            Text(
                text = "暂无歌词",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            
            Text(
                text = "享受纯音乐的美妙",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

/**
 * 歌词设置面板
 */
@Composable
fun LyricsSettingsPanel(
    fontSize: Float,
    onFontSizeChange: (Float) -> Unit,
    showTranslation: Boolean,
    onShowTranslationChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "歌词设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            // 字体大小调节
            Column {
                Text(
                    text = "字体大小",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("A", fontSize = 12.sp)
                    
                    Slider(
                        value = fontSize,
                        onValueChange = onFontSizeChange,
                        valueRange = 12f..24f,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Text("A", fontSize = 18.sp)
                }
                
                Text(
                    text = "${fontSize.toInt()}sp",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 翻译显示开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "显示翻译",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Switch(
                    checked = showTranslation,
                    onCheckedChange = onShowTranslationChange
                )
            }
        }
    }
}

