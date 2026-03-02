package com.example.androidmusicplayer.data

import android.content.Context
import android.content.SharedPreferences
import com.example.androidmusicplayer.audio.AudioEqualizerManager
import com.example.androidmusicplayer.utils.ErrorHandler
import com.example.androidmusicplayer.utils.safeExecute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 均衡器设置存储管理器
 */
class EqualizerPreferences private constructor(context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: EqualizerPreferences? = null
        
        fun getInstance(context: Context): EqualizerPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: EqualizerPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        private const val PREFS_NAME = "equalizer_preferences"
        private const val KEY_ENABLED = "equalizer_enabled"
        private const val KEY_PRESET = "equalizer_preset"
        private const val KEY_BAND_LEVELS = "band_levels"
        private const val KEY_BASS_BOOST = "bass_boost_strength"
        private const val KEY_VIRTUALIZER = "virtualizer_strength"
        private const val KEY_REVERB = "reverb_preset"
    }
    
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val errorHandler = ErrorHandler.getInstance()
    
    /**
     * 保存均衡器设置
     */
    suspend fun saveEqualizerSettings(
        enabled: Boolean,
        preset: AudioEqualizerManager.EqualizerPreset,
        bandLevels: List<Int>,
        bassBoostStrength: Int,
        virtualizerStrength: Int,
        reverbPreset: AudioEqualizerManager.ReverbPreset
    ) = withContext(Dispatchers.IO) {
        safeExecute("EqualizerPreferences.saveEqualizerSettings") {
            sharedPreferences.edit().apply {
                putBoolean(KEY_ENABLED, enabled)
                putString(KEY_PRESET, preset.name)
                putString(KEY_BAND_LEVELS, bandLevels.joinToString(","))
                putInt(KEY_BASS_BOOST, bassBoostStrength)
                putInt(KEY_VIRTUALIZER, virtualizerStrength)
                putString(KEY_REVERB, reverbPreset.name)
                apply()
            }
        }
    }
    
    /**
     * 加载均衡器设置
     */
    suspend fun loadEqualizerSettings(): EqualizerSettings = withContext(Dispatchers.IO) {
        safeExecute("EqualizerPreferences.loadEqualizerSettings") {
            val enabled = sharedPreferences.getBoolean(KEY_ENABLED, false)
            
            val presetName = sharedPreferences.getString(KEY_PRESET, AudioEqualizerManager.EqualizerPreset.NORMAL.name)
            val preset = try {
                AudioEqualizerManager.EqualizerPreset.valueOf(presetName ?: AudioEqualizerManager.EqualizerPreset.NORMAL.name)
            } catch (e: IllegalArgumentException) {
                AudioEqualizerManager.EqualizerPreset.NORMAL
            }
            
            val bandLevelsString = sharedPreferences.getString(KEY_BAND_LEVELS, "")
            val bandLevels = if (bandLevelsString.isNullOrEmpty()) {
                emptyList()
            } else {
                try {
                    bandLevelsString.split(",").map { it.toInt() }
                } catch (e: NumberFormatException) {
                    emptyList()
                }
            }
            
            val bassBoostStrength = sharedPreferences.getInt(KEY_BASS_BOOST, 0)
            val virtualizerStrength = sharedPreferences.getInt(KEY_VIRTUALIZER, 0)
            
            val reverbName = sharedPreferences.getString(KEY_REVERB, AudioEqualizerManager.ReverbPreset.NONE.name)
            val reverbPreset = try {
                AudioEqualizerManager.ReverbPreset.valueOf(reverbName ?: AudioEqualizerManager.ReverbPreset.NONE.name)
            } catch (e: IllegalArgumentException) {
                AudioEqualizerManager.ReverbPreset.NONE
            }
            
            EqualizerSettings(
                enabled = enabled,
                preset = preset,
                bandLevels = bandLevels,
                bassBoostStrength = bassBoostStrength,
                virtualizerStrength = virtualizerStrength,
                reverbPreset = reverbPreset
            )
        } ?: EqualizerSettings()
    }
    
    /**
     * 重置均衡器设置
     */
    suspend fun resetEqualizerSettings() = withContext(Dispatchers.IO) {
        safeExecute("EqualizerPreferences.resetEqualizerSettings") {
            sharedPreferences.edit().clear().apply()
        }
    }
    
    /**
     * 保存自定义预设
     */
    suspend fun saveCustomPreset(name: String, bandLevels: List<Int>) = withContext(Dispatchers.IO) {
        safeExecute("EqualizerPreferences.saveCustomPreset") {
            val key = "custom_preset_$name"
            sharedPreferences.edit().apply {
                putString(key, bandLevels.joinToString(","))
                apply()
            }
        }
    }
    
    /**
     * 加载自定义预设
     */
    suspend fun loadCustomPreset(name: String): List<Int>? = withContext(Dispatchers.IO) {
        safeExecute("EqualizerPreferences.loadCustomPreset") {
            val key = "custom_preset_$name"
            val bandLevelsString = sharedPreferences.getString(key, null)
            
            if (bandLevelsString != null) {
                try {
                    bandLevelsString.split(",").map { it.toInt() }
                } catch (e: NumberFormatException) {
                    null
                }
            } else {
                null
            }
        }
    }
    
    /**
     * 获取所有自定义预设名称
     */
    suspend fun getCustomPresetNames(): List<String> = withContext(Dispatchers.IO) {
        safeExecute("EqualizerPreferences.getCustomPresetNames") {
            sharedPreferences.all.keys
                .filter { it.startsWith("custom_preset_") }
                .map { it.removePrefix("custom_preset_") }
                .sorted()
        } ?: emptyList()
    }
    
    /**
     * 删除自定义预设
     */
    suspend fun deleteCustomPreset(name: String) = withContext(Dispatchers.IO) {
        safeExecute("EqualizerPreferences.deleteCustomPreset") {
            val key = "custom_preset_$name"
            sharedPreferences.edit().remove(key).apply()
        }
    }
    
    /**
     * 均衡器设置数据类
     */
    data class EqualizerSettings(
        val enabled: Boolean = false,
        val preset: AudioEqualizerManager.EqualizerPreset = AudioEqualizerManager.EqualizerPreset.NORMAL,
        val bandLevels: List<Int> = emptyList(),
        val bassBoostStrength: Int = 0,
        val virtualizerStrength: Int = 0,
        val reverbPreset: AudioEqualizerManager.ReverbPreset = AudioEqualizerManager.ReverbPreset.NONE
    )
}

