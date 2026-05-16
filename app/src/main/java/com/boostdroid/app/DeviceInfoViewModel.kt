package com.boostdroid.app

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.view.WindowManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FileReader
import java.util.*

class DeviceInfoViewModel : ViewModel() {

    private val _deviceInfo = MutableLiveData<Map<String, String>>()
    val deviceInfo: LiveData<Map<String, String>> = _deviceInfo

    fun loadDeviceInfo(context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            val info = mutableMapOf<String, String>()
            
            try {
                // Brand & Model
                info["brand"] = Build.MANUFACTURER?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } ?: "Unknown"
                info["model"] = Build.MODEL ?: "Unknown"
                
                // Processor
                info["cpu"] = getCpuModel()
                info["cores"] = "${Runtime.getRuntime().availableProcessors()} cores"
                
                // SOC (API 31+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        info["soc"] = "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}"
                    } catch (_: Exception) {
                        info["soc"] = "N/A"
                    }
                }
                
                // RAM
                val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val memInfo = ActivityManager.MemoryInfo()
                am?.getMemoryInfo(memInfo)
                info["ram"] = String.format(Locale.US, "%.1f GB", memInfo.totalMem / (1024.0 * 1024 * 1024))
                
                // Storage
                try {
                    val stat = StatFs(Environment.getDataDirectory().path)
                    info["storage"] = String.format(Locale.US, "%.0f GB", stat.totalBytes.toDouble() / (1024.0 * 1024 * 1024))
                } catch (e: Exception) {
                    info["storage"] = "N/A"
                }
                info["storage_type"] = getStorageType()
                
                // Software
                info["android"] = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
                info["patch"] = try { Build.VERSION.SECURITY_PATCH ?: "N/A" } catch (_: Exception) { "N/A" }
                
                // Display
                val dm = appContext.resources.displayMetrics
                info["resolution"] = "${dm.widthPixels} x ${dm.heightPixels} px"
                info["dpi"] = "${dm.densityDpi} dpi"
                info["refresh_rate"] = getRefreshRate(appContext)
                
                // GPU
                info["gpu"] = getGpuInfo()
            } catch (e: Exception) {
                // Fallback for extreme cases
                info["brand"] = info["brand"] ?: "Unknown"
                info["model"] = info["model"] ?: "Unknown"
            }

            withContext(Dispatchers.Main) {
                _deviceInfo.value = info
            }
        }
    }

    private fun getCpuModel(): String {
        return try {
            BufferedReader(FileReader("/proc/cpuinfo")).use { r ->
                r.lineSequence()
                    .firstOrNull { it.startsWith("Hardware") || it.startsWith("model name") || it.startsWith("Processor") }
                    ?.substringAfter(":")?.trim() ?: Build.HARDWARE
            }
        } catch (_: Exception) {
            Build.HARDWARE
        }
    }

    private fun getStorageType(): String {
        return try {
            val rotational = BufferedReader(FileReader("/sys/block/mmcblk0/queue/rotational")).readLine()
            if (rotational == "0") "Flash (UFS/eMMC)" else "HDD"
        } catch (ignored: Exception) {
            "Flash"
        }
    }

    private fun getRefreshRate(context: Context): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val display = context.display
            String.format(Locale.US, "%.0f Hz", display.refreshRate)
        } else {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            @Suppress("DEPRECATION")
            String.format(Locale.US, "%.0f Hz", wm.defaultDisplay.refreshRate)
        }
    }

    private fun getGpuInfo(): String {
        return try {
            BufferedReader(FileReader("/sys/class/kgsl/kgsl-3d0/gpu_model")).readLine() ?: "Unknown"
        } catch (ignored: Exception) {
            "Adreno / Mali"
        }
    }
}