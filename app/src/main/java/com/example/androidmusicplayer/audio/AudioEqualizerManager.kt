package com.example.androidmusicplayer.audio

import android.content.Context
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.BassBoost
import android.media.audiofx.Virtualizer
import com.example.androidmusicplayer.utils.ErrorHandler
import com.example.androidmusicplayer.utils.safeExecute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 音频均衡器管理器
 */
class AudioEqualizerManager private constructor() {
    
    companion object {
        @Volatile
        private var INSTANCE: AudioEqualizerManager? = null
        
        fun getInstance(): AudioEqualizerManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AudioEqualizerManager().also { INSTANCE = it }
            }
        }
    }
    
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var presetReverb: PresetReverb? = null
    
    private val errorHandler = ErrorHandler.getInstance()
    
    // 均衡器状态
    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()
    
    private val _currentPreset = MutableStateFlow<EqualizerPreset?>(null)
    val currentPreset: StateFlow<EqualizerPreset?> = _currentPreset.asStateFlow()
    
    private val _bandLevels = MutableStateFlow<List<Int>>(emptyList())
    val bandLevels: StateFlow<List<Int>> = _bandLevels.asStateFlow()
    
    private val _bassBoostStrength = MutableStateFlow(0)
    val bassBoostStrength: StateFlow<Int> = _bassBoostStrength.asStateFlow()
    
    private val _virtualizerStrength = MutableStateFlow(0)
    val virtualizerStrength: StateFlow<Int> = _virtualizerStrength.asStateFlow()
    
    private val _reverbPreset = MutableStateFlow(ReverbPreset.NONE)
    val reverbPreset: StateFlow<ReverbPreset> = _reverbPreset.asStateFlow()
    
    /**
     * 初始化音频效果器
     */
    fun initialize(audioSessionId: Int): Boolean {
        return safeExecute("AudioEqualizerManager.initialize") {
            try {
                // 初始化均衡器
                equalizer = Equalizer(0, audioSessionId).apply {
                    enabled = false
                }
                
                // 初始化低音增强
                bassBoost = BassBoost(0, audioSessionId).apply {
                    enabled = false
                }
                
                // 初始化虚拟化器
                virtualizer = Virtualizer(0, audioSessionId).apply {
                    enabled = false
                }
                
                // 初始化混响
                presetReverb = PresetReverb(0, audioSessionId).apply {
                    enabled = false
                }
                
                // 初始化频段级别
                val numberOfBands = equalizer?.numberOfBands?.toInt() ?: 0
                _bandLevels.value = List(numberOfBands) { 0 }
                
                true
            } catch (e: Exception) {
                errorHandler.handleError(
                    ErrorHandler.ErrorInfo(
                        type = ErrorHandler.ErrorType.MEDIA_PLAYBACK_ERROR,
                        message = "初始化音频效果器失败",
                        throwable = e,
                        context = "AudioEqualizerManager.initialize"
                    )
                )
                false
            }
        } ?: false
    }
    
    /**
     * 启用/禁用均衡器
     */
    fun setEnabled(enabled: Boolean) {
        safeExecute("AudioEqualizerManager.setEnabled") {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
            virtualizer?.enabled = enabled
            presetReverb?.enabled = enabled
            
            _isEnabled.value = enabled
        }
    }
    
    /**
     * 应用预设
     */
    fun applyPreset(preset: EqualizerPreset) {
        safeExecute("AudioEqualizerManager.applyPreset") {
            equalizer?.let { eq ->
                when (preset) {
                    EqualizerPreset.NORMAL -> {
                        // 重置所有频段
                        for (i in 0 until eq.numberOfBands) {
                            eq.setBandLevel(i, 0)
                        }
                    }
                    EqualizerPreset.ROCK -> {
                        // 摇滚预设：增强低频和高频
                        applyBandLevels(listOf(800, 400, -200, -400, -200, 400, 800, 1000, 1000, 1000))
                    }
                    EqualizerPreset.POP -> {
                        // 流行预设：增强中频
                        applyBandLevels(listOf(-200, 400, 700, 800, 500, 0, -200, -200, -200, -200))
                    }
                    EqualizerPreset.JAZZ -> {
                        // 爵士预设：增强低频和高频，减少中频
                        applyBandLevels(listOf(400, 200, 0, 200, -200, -200, 0, 200, 400, 500))
                    }
                    EqualizerPreset.CLASSICAL -> {
                        // 古典预设：增强高频
                        applyBandLevels(listOf(0, 0, 0, 0, 0, 0, -200, -200, -200, -500))
                    }
                    EqualizerPreset.BASS_BOOST -> {
                        // 低音增强预设
                        applyBandLevels(listOf(700, 500, 300, 100, 0, 0, 0, 0, 0, 0))
                    }
                    EqualizerPreset.VOCAL -> {
                        // 人声预设：增强中频
                        applyBandLevels(listOf(-200, -100, 200, 400, 400, 400, 200, 100, 0, 0))
                    }
                    EqualizerPreset.CUSTOM -> {
                        // 自定义预设，不做任何改变
                    }
                }
                
                _currentPreset.value = preset
            }
        }
    }
    
    /**
     * 应用频段级别
     */
    private fun applyBandLevels(levels: List<Int>) {
        equalizer?.let { eq ->
            val numberOfBands = eq.numberOfBands.toInt()
            val adjustedLevels = mutableListOf<Int>()
            
            for (i in 0 until numberOfBands) {
                val level = if (i < levels.size) levels[i] else 0
                val clampedLevel = level.coerceIn(
                    eq.bandLevelRange[0].toInt(),
                    eq.bandLevelRange[1].toInt()
                )
                eq.setBandLevel(i.toShort(), clampedLevel.toShort())
                adjustedLevels.add(clampedLevel)
            }
            
            _bandLevels.value = adjustedLevels
        }
    }
    
    /**
     * 设置单个频段级别
     */
    fun setBandLevel(band: Int, level: Int) {
        safeExecute("AudioEqualizerManager.setBandLevel") {
            equalizer?.let { eq ->
                val clampedLevel = level.coerceIn(
                    eq.bandLevelRange[0].toInt(),
                    eq.bandLevelRange[1].toInt()
                )
                eq.setBandLevel(band.toShort(), clampedLevel.toShort())
                
                // 更新状态
                val currentLevels = _bandLevels.value.toMutableList()
                if (band < currentLevels.size) {
                    currentLevels[band] = clampedLevel
                    _bandLevels.value = currentLevels
                }
                
                // 切换到自定义预设
                _currentPreset.value = EqualizerPreset.CUSTOM
            }
        }
    }
    
    /**
     * 设置低音增强强度
     */
    fun setBassBoostStrength(strength: Int) {
        safeExecute("AudioEqualizerManager.setBassBoostStrength") {
            bassBoost?.let { bass ->
                val clampedStrength = strength.coerceIn(0, 1000)
                bass.setStrength(clampedStrength.toShort())
                _bassBoostStrength.value = clampedStrength
            }
        }
    }
    
    /**
     * 设置虚拟化器强度
     */
    fun setVirtualizerStrength(strength: Int) {
        safeExecute("AudioEqualizerManager.setVirtualizerStrength") {
            virtualizer?.let { virt ->
                val clampedStrength = strength.coerceIn(0, 1000)
                virt.setStrength(clampedStrength.toShort())
                _virtualizerStrength.value = clampedStrength
            }
        }
    }
    
    /**
     * 设置混响预设
     */
    fun setReverbPreset(preset: ReverbPreset) {
        safeExecute("AudioEqualizerManager.setReverbPreset") {
            presetReverb?.let { reverb ->
                when (preset) {
                    ReverbPreset.NONE -> reverb.enabled = false
                    ReverbPreset.SMALL_ROOM -> {
                        reverb.preset = PresetReverb.PRESET_SMALLROOM
                        reverb.enabled = true
                    }
                    ReverbPreset.MEDIUM_ROOM -> {
                        reverb.preset = PresetReverb.PRESET_MEDIUMROOM
                        reverb.enabled = true
                    }
                    ReverbPreset.LARGE_ROOM -> {
                        reverb.preset = PresetReverb.PRESET_LARGEROOM
                        reverb.enabled = true
                    }
                    ReverbPreset.MEDIUM_HALL -> {
                        reverb.preset = PresetReverb.PRESET_MEDIUMHALL
                        reverb.enabled = true
                    }
                    ReverbPreset.LARGE_HALL -> {
                        reverb.preset = PresetReverb.PRESET_LARGEHALL
                        reverb.enabled = true
                    }
                    ReverbPreset.PLATE -> {
                        reverb.preset = PresetReverb.PRESET_PLATE
                        reverb.enabled = true
                    }
                }
                _reverbPreset.value = preset
            }
        }
    }
    
    /**
     * 获取频段信息
     */
    fun getBandInfo(): List<BandInfo> {
        return safeExecute("AudioEqualizerManager.getBandInfo") {
            equalizer?.let { eq ->
                val bandInfoList = mutableListOf<BandInfo>()
                for (i in 0 until eq.numberOfBands) {
                    val centerFreq = eq.getCenterFreq(i.toShort())
                    val bandLevel = eq.getBandLevel(i.toShort())
                    bandInfoList.add(
                        BandInfo(
                            index = i,
                            centerFrequency = centerFreq.toInt(),
                            level = bandLevel.toInt()
                        )
                    )
                }
                bandInfoList
            }
        } ?: emptyList()
    }
    
    /**
     * 获取频段级别范围
     */
    fun getBandLevelRange(): Pair<Int, Int> {
        return safeExecute("AudioEqualizerManager.getBandLevelRange") {
            equalizer?.let { eq ->
                Pair(eq.bandLevelRange[0].toInt(), eq.bandLevelRange[1].toInt())
            }
        } ?: Pair(-1500, 1500)
    }
    
    /**
     * 释放资源
     */
    fun release() {
        safeExecute("AudioEqualizerManager.release") {
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()
            presetReverb?.release()
            
            equalizer = null
            bassBoost = null
            virtualizer = null
            presetReverb = null
        }
    }
    
    /**
     * 均衡器预设
     */
    enum class EqualizerPreset {
        NORMAL,
        ROCK,
        POP,
        JAZZ,
        CLASSICAL,
        BASS_BOOST,
        VOCAL,
        CUSTOM
    }
    
    /**
     * 混响预设
     */
    enum class ReverbPreset {
        NONE,
        SMALL_ROOM,
        MEDIUM_ROOM,
        LARGE_ROOM,
        MEDIUM_HALL,
        LARGE_HALL,
        PLATE
    }
    
    /**
     * 频段信息
     */
    data class BandInfo(
        val index: Int,
        val centerFrequency: Int,
        val level: Int
    ) {
        fun getFrequencyLabel(): String {
            return when {
                centerFrequency < 1000 -> "${centerFrequency}Hz"
                centerFrequency < 1000000 -> "${centerFrequency / 1000}kHz"
                else -> "${centerFrequency / 1000000}MHz"
            }
        }
    }
}

