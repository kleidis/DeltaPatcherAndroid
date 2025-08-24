// Copyright (C) 2025 Innixunix


package io.github.innixunix.deltapatcher.ui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

class SettingsEntries(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("delta_patcher_settings", Context.MODE_PRIVATE)

    // Boolean settings
    var useMonet by mutableStateOf(prefs.getBoolean("use_monet", true))
        private set
    var useChecksum by mutableStateOf(prefs.getBoolean("use_checksum", true))
        private set

    // Int settings
    var compressionLevel by mutableIntStateOf(prefs.getInt("compression_level", 5))
        private set

    var secondaryCompression by mutableIntStateOf(prefs.getInt("secondary_compression", 3))
        private set

    var srcWindowSize by mutableIntStateOf(prefs.getInt("src_window_size", 0))
        private set

    fun updateUseMonet(value: Boolean) {
        useMonet = value
        prefs.edit { putBoolean("use_monet", value) }
    }

    fun updateUseChecksum(value: Boolean) {
        useChecksum = value
        prefs.edit { putBoolean("use_checksum", value) }
    }

    fun updateCompressionLevel(value: Int) {
        compressionLevel = value
        prefs.edit { putInt("compression_level", value) }
    }

    fun updateSecondaryCompression(value: Int) {
        secondaryCompression = value
        prefs.edit().putInt("secondary_compression", value).apply()
    }

    fun updateSrcWindowSize(value: Int) {
        srcWindowSize = value
        prefs.edit { putInt("src_window_size", value) }
    }

    companion object {
        val COMPRESSION_LEVELS = listOf(
            0 to "0",
            1 to "1",
            2 to "2",
            3 to "3",
            4 to "4",
            5 to "5",
            6 to "6",
            7 to "7",
            8 to "8",
            9 to "9"
        )

        val SECONDARY_COMPRESSIONS = listOf(
            0 to "LZMA",
            1 to "DJW",
            2 to "FGK",
            3 to "None"
        )

        val SRC_WINDOW_SIZES = listOf(
            0 to "Auto",
            1 to "8 MB",
            2 to "16 MB",
            3 to "32 MB",
            4 to "64 MB",
            5 to "128 MB",
            6 to "256 MB",
            7 to "512 MB",
            8 to "1024 MB"
        )
    }
}
