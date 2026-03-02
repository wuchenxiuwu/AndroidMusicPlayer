package com.example.androidmusicplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.androidmusicplayer.audio.AudioEqualizerManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    equalizerManager: AudioEqualizerManager = AudioEqualizerManager.getInstance()
) {
    val isEnabled by equalizerManager.isEnabled.collectAsState()
    val currentPreset by equalizerManager.currentPreset.collectAsState()
    val bandLevels by equalizerManager.bandLevels.collectAsState()
    val bassBoostStrength by equalizerManager.bassBoostStrength.collectAsState()
    val virtualizerStrength by equalizerManager.virtualizerStrength.collectAsState()
    val reverbPreset by equalizerManager.reverbPreset.collectAsState()
    
    val bandInfo = remember { equalizerManager.getBandInfo() }
    val bandLevelRange = remember { equalizerManager.getBandLevelRange() }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // 标题和总开关
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "音频均衡器",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { equalizerManager.setEnabled(it) }
                        )
                    }
                    
                    if (isEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "调整音频效果以获得最佳听觉体验",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        if (isEnabled) {
            item {
                // 预设选择
                PresetSelector(
                    currentPreset = currentPreset,
                    onPresetSelected = { equalizerManager.applyPreset(it) }
                )
            }
            
            item {
                // 频段均衡器
                EqualizerBands(
                    bandInfo = bandInfo,
                    bandLevels = bandLevels,
                    bandLevelRange = bandLevelRange,
                    onBandLevelChanged = { band, level ->
                        equalizerManager.setBandLevel(band, level)
                    }
                )
            }
            
            item {
                // 音效增强
                AudioEffects(
                    bassBoostStrength = bassBoostStrength,
                    virtualizerStrength = virtualizerStrength,
                    reverbPreset = reverbPreset,
                    onBassBoostChanged = { equalizerManager.setBassBoostStrength(it) },
                    onVirtualizerChanged = { equalizerManager.setVirtualizerStrength(it) },
                    onReverbChanged = { equalizerManager.setReverbPreset(it) }
                )
            }
        }
    }
}

@Composable
fun PresetSelector(
    currentPreset: AudioEqualizerManager.EqualizerPreset?,
    onPresetSelected: (AudioEqualizerManager.EqualizerPreset) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "预设",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            val presets = listOf(
                AudioEqualizerManager.EqualizerPreset.NORMAL to "正常",
                AudioEqualizerManager.EqualizerPreset.ROCK to "摇滚",
                AudioEqualizerManager.EqualizerPreset.POP to "流行",
                AudioEqualizerManager.EqualizerPreset.JAZZ to "爵士",
                AudioEqualizerManager.EqualizerPreset.CLASSICAL to "古典",
                AudioEqualizerManager.EqualizerPreset.BASS_BOOST to "低音增强",
                AudioEqualizerManager.EqualizerPreset.VOCAL to "人声",
                AudioEqualizerManager.EqualizerPreset.CUSTOM to "自定义"
            )
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(presets) { (preset, name) ->
                    FilterChip(
                        onClick = { onPresetSelected(preset) },
                        label = { Text(name) },
                        selected = currentPreset == preset
                    )
                }
            }
        }
    }
}

@Composable
fun EqualizerBands(
    bandInfo: List<AudioEqualizerManager.BandInfo>,
    bandLevels: List<Int>,
    bandLevelRange: Pair<Int, Int>,
    onBandLevelChanged: (Int, Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "频段调节",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                bandInfo.forEachIndexed { index, band ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        // 频率标签
                        Text(
                            text = band.getFrequencyLabel(),
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 垂直滑块
                        Box(
                            modifier = Modifier
                                .height(120.dp)
                                .width(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // 背景轨道
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            )
                            
                            // 滑块
                            val level = if (index < bandLevels.size) bandLevels[index] else 0
                            val normalizedLevel = (level - bandLevelRange.first).toFloat() / 
                                    (bandLevelRange.second - bandLevelRange.first).toFloat()
                            
                            VerticalSlider(
                                value = normalizedLevel,
                                onValueChange = { normalizedValue ->
                                    val actualLevel = (normalizedValue * (bandLevelRange.second - bandLevelRange.first) + bandLevelRange.first).toInt()
                                    onBandLevelChanged(index, actualLevel)
                                },
                                modifier = Modifier.height(120.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 数值显示
                        val level = if (index < bandLevels.size) bandLevels[index] else 0
                        Text(
                            text = "${level / 100}dB",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AudioEffects(
    bassBoostStrength: Int,
    virtualizerStrength: Int,
    reverbPreset: AudioEqualizerManager.ReverbPreset,
    onBassBoostChanged: (Int) -> Unit,
    onVirtualizerChanged: (Int) -> Unit,
    onReverbChanged: (AudioEqualizerManager.ReverbPreset) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "音效增强",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 低音增强
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "低音增强",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "${bassBoostStrength / 10}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Slider(
                    value = bassBoostStrength.toFloat(),
                    onValueChange = { onBassBoostChanged(it.toInt()) },
                    valueRange = 0f..1000f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 虚拟化器
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "3D音效",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "${virtualizerStrength / 10}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Slider(
                    value = virtualizerStrength.toFloat(),
                    onValueChange = { onVirtualizerChanged(it.toInt()) },
                    valueRange = 0f..1000f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 混响效果
            Column {
                Text(
                    text = "混响效果",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                val reverbPresets = listOf(
                    AudioEqualizerManager.ReverbPreset.NONE to "无",
                    AudioEqualizerManager.ReverbPreset.SMALL_ROOM to "小房间",
                    AudioEqualizerManager.ReverbPreset.MEDIUM_ROOM to "中房间",
                    AudioEqualizerManager.ReverbPreset.LARGE_ROOM to "大房间",
                    AudioEqualizerManager.ReverbPreset.MEDIUM_HALL to "中大厅",
                    AudioEqualizerManager.ReverbPreset.LARGE_HALL to "大大厅",
                    AudioEqualizerManager.ReverbPreset.PLATE to "金属板"
                )
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(reverbPresets) { (preset, name) ->
                        FilterChip(
                            onClick = { onReverbChanged(preset) },
                            label = { Text(name, fontSize = 12.sp) },
                            selected = reverbPreset == preset
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f
) {
    // 这是一个简化的垂直滑块实现
    // 在实际项目中，你可能需要使用更复杂的自定义组件
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = modifier
    )
}

