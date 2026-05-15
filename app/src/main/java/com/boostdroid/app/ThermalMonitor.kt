package com.boostdroid.app

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

data class ThermalStats(
    val cpuTemp: Int,
    val currentFreqMhz: Int,
    val maxFreqMhz: Int,
    val isThrottled: Boolean
)

object ThermalMonitor {
    private const val CUR_FREQ_PATH = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq"
    private const val MAX_FREQ_PATH = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq"
    private const val HW_MAX_FREQ_PATH = "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq"

    fun getThermalStats(context: Context): ThermalStats {
        val temp = readCpuTemperature(context)
        val curFreq = readFileAsInt(CUR_FREQ_PATH) / 1000
        val maxFreq = readFileAsInt(MAX_FREQ_PATH) / 1000
        val hwMaxFreq = readFileAsInt(HW_MAX_FREQ_PATH) / 1000

        val isThrottled = maxFreq > 0 && hwMaxFreq > 0 && maxFreq < (hwMaxFreq * 0.7)

        return ThermalStats(
            cpuTemp = temp,
            currentFreqMhz = curFreq,
            maxFreqMhz = maxFreq,
            isThrottled = isThrottled
        )
    }

    fun readCpuTemperature(context: Context): Int {
        val prefs = PrefsManager.getInstance(context)
        val cachedPath = prefs.workingThermalPath
        
        if (cachedPath != null) {
            val temp = readFromPath(cachedPath)
            if (temp != -1) return temp
        }

        // Standard paths
        val paths = listOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone1/temp",
            "/sys/class/thermal/thermal_zone2/temp",
            "/sys/class/thermal/thermal_zone3/temp",
            "/sys/devices/virtual/thermal/thermal_zone0/temp",
            "/sys/devices/virtual/thermal/thermal_zone1/temp"
        )
        for (path in paths) {
            val temp = readFromPath(path)
            if (temp != -1) {
                prefs.workingThermalPath = path
                return temp
            }
        }

        // Scan ALL thermal zones 0-9
        for (i in 0..9) {
            val path = "/sys/class/thermal/thermal_zone$i/temp"
            val temp = readFromPath(path)
            if (temp != -1) {
                prefs.workingThermalPath = path
                return temp
            }
        }

        // Fallback: MediaTek specific
        try {
            val process = Runtime.getRuntime().exec("cat /sys/class/thermal/thermal_zone0/temp")
            val result = process.inputStream.bufferedReader().readLine()?.trim()?.toLongOrNull()
            if (result != null && result > 0) {
                val temp = if (result > 1000) (result / 1000).toInt() else result.toInt()
                if (temp in 20..120) return temp
            }
        } catch (e: Exception) {}

        return -1 // -1 means unavailable
    }

    private fun readFromPath(path: String): Int {
        try {
            val file = File(path)
            if (!file.exists() || !file.canRead()) return -1
            val raw = file.readText().trim().toLongOrNull() ?: return -1
            if (raw == 0L) return -1
            
            // Most drivers report millidegrees (e.g. 42000 = 42°C)
            // Some report degrees directly (e.g. 42)
            val celsius = when {
                raw > 1000 -> (raw / 1000).toInt()   // millidegrees
                raw > 200  -> return -1              // garbage value
                raw > 0    -> raw.toInt()              // already degrees
                else       -> return -1
            }
            return if (celsius in 20..120) celsius else -1 // sanity check
        } catch (e: Exception) {
            return -1
        }
    }

    private fun readFileAsInt(path: String): Int {
        val file = File(path)
        if (!file.exists()) return 0
        return try {
            BufferedReader(FileReader(file)).use { it.readLine()?.trim()?.toIntOrNull() ?: 0 }
        } catch (e: Exception) {
            0
        }
    }
}