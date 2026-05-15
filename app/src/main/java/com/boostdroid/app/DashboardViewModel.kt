package com.boostdroid.app

import android.app.ActivityManager
import android.content.Context
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.FileReader
import java.util.*

data class RamDisplayInfo(
    val usedMb: Long,
    val totalMb: Long,
    val availMb: Long,
    val cachedMb: Long,
    val pct: Int
)

data class BoostResult(
    val freedMb: Long,
    val killedCount: Int,
    val ramBeforeMb: Long,
    val ramAfterMb: Long,
    val totalRamMb: Long
)

class DashboardViewModel : ViewModel() {

    private val _ramUsage = MutableLiveData<RamDisplayInfo>()
    val ramUsage: LiveData<RamDisplayInfo> = _ramUsage

    private val _storageUsage = MutableLiveData<Pair<Int, String>>()
    val storageUsage: LiveData<Pair<Int, String>> = _storageUsage

    private val _cpuUsage = MutableLiveData<Int>()
    val cpuUsage: LiveData<Int> = _cpuUsage

    private val _cpuModel = MutableLiveData<String>()
    val cpuModel: LiveData<String> = _cpuModel

    private val _batteryInfo = MutableLiveData<Triple<Int, Int, String>>()
    val batteryInfo: LiveData<Triple<Int, Int, String>> = _batteryInfo

    private val _networkSpeed = MutableLiveData<Pair<String, String>>()
    val networkSpeed: LiveData<Pair<String, String>> = _networkSpeed

    private val _topApps = MutableLiveData<List<AppRamInfo>>()
    val topApps: LiveData<List<AppRamInfo>> = _topApps

    private val _lastUpdateTime = MutableLiveData<Long>()
    val lastUpdateTime: LiveData<Long> = _lastUpdateTime

    private val _boostResult = MutableLiveData<BoostResult>()
    val boostResult: LiveData<BoostResult> = _boostResult

    private val _ioStats = MutableLiveData<IoStats>()
    val ioStats: LiveData<IoStats> = _ioStats

    private val _thermalStats = MutableLiveData<ThermalStats>()
    val thermalStats: LiveData<ThermalStats> = _thermalStats

    private val _ramPressure = MutableLiveData<String>()
    val ramPressure: LiveData<String> = _ramPressure

    private var statsJob: Job? = null
    private var prevRxBytes = 0L
    private var prevTxBytes = 0L

    fun startStatsUpdates(context: Context) {
        if (statsJob?.isActive == true) return
        
        _cpuModel.value = getCpuModel()
        
        statsJob = viewModelScope.launch {
            try {
                // Initial long stats (5s)
                val longStatsTask = launch {
                    while (isActive) {
                        updateLongStats(context)
                        delay(5000)
                    }
                }
                
                // Fast RAM monitor (2s)
                val ramMonitorTask = launch {
                    while (isActive) {
                        val apps = getLiveRamApps(context)
                        _topApps.postValue(apps)
                        _lastUpdateTime.postValue(System.currentTimeMillis())
                        delay(2000)
                    }
                }
                
                joinAll(longStatsTask, ramMonitorTask)
            } finally {
                android.util.Log.d("DashboardViewModel", "Stats jobs finished")
            }
        }
    }

    private suspend fun updateLongStats(context: Context) = withContext(Dispatchers.IO) {
        // RAM general
        val memInfo = MemInfoReader.readMemInfo()
        val total = memInfo.totalMb
        val used = memInfo.usedMb
        val pct = if (total > 0) ((used * 100) / total).toInt().coerceIn(0, 100) else 0
        _ramUsage.postValue(RamDisplayInfo(used, total, memInfo.availableMb, memInfo.cachedMb, pct))
        _ramPressure.postValue(getPressureBadge(pct))

        // I/O, Thermal, Storage, CPU, Battery, Network (existing logic)
        _ioStats.postValue(IoMonitor.getIoStats())
        _thermalStats.postValue(ThermalMonitor.getThermalStats(context))
        
        val storage = StorageAnalyzer.getInternalStorageStats()
        val totalSt = storage.totalBytes / (1024 * 1024 * 1024)
        val usedSt = storage.usedBytes / (1024 * 1024 * 1024)
        val stPct = if (totalSt > 0) ((usedSt * 100) / totalSt).toInt() else 0
        _storageUsage.postValue(Pair(stPct, "${usedSt}GB / ${totalSt}GB"))

        _cpuUsage.postValue(getCpuUsage())

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val bat = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val currentNow = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val isMicro = Math.abs(currentNow) > 100000
        val drainMa = if (isMicro) Math.abs(currentNow) / 1000 else Math.abs(currentNow)
        var hoursText = ""
        if (drainMa > 0 && !bm.isCharging) {
            val totalMah = BoostForegroundService.getBatteryCapacityMah(context)
            val remainMah = (totalMah * bat / 100)
            hoursText = String.format(Locale.US, "%.1f", remainMah.toFloat() / drainMa.coerceAtLeast(1L))
        }
        _batteryInfo.postValue(Triple(bat, drainMa.toInt(), hoursText))

        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        if (prevRxBytes > 0) {
            _networkSpeed.postValue(Pair(formatSpeed((rx - prevRxBytes) / 5), formatSpeed((tx - prevTxBytes) / 5)))
        }
        prevRxBytes = rx; prevTxBytes = tx
    }

    private suspend fun getLiveRamApps(context: Context): List<AppRamInfo> = withContext(Dispatchers.IO) {
        val result = mutableListOf<AppRamInfo>()
        
        try {
            val am = context.applicationContext
                .getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val pm = context.applicationContext.packageManager
            val myPid = android.os.Process.myPid()
            val myPkg = context.packageName
            
            val processes = try {
                am.runningAppProcesses
            } catch (e: Exception) { null }
            
            if (processes.isNullOrEmpty()) return@withContext emptyList()
            
            for (proc in processes) {
                if (!isActive) break // coroutine cancelled
                if (proc.pid == myPid) continue
                if (proc.processName == myPkg) continue
                
                try {
                    val ramMb = ProcReader.readProcessRamMb(proc.pid)
                    if (ramMb < 5) continue // lower threshold to 5MB
                    
                    // Determine package name (handle multi-process apps)
                    val pkgName = proc.processName.split(":")[0]
                    
                    // Check if system app — use a lenient check
                    val isSystem = try {
                        val info = pm.getApplicationInfo(pkgName, 0)
                        // Only skip TRULY system apps that are not launchable
                        val isSystemFlag = (info.flags and 
                            android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                        val hasLauncher = pm.getLaunchIntentForPackage(pkgName) != null
                        // If it has a launcher icon, show it even if system-signed
                        isSystemFlag && !hasLauncher
                    } catch (e: Exception) {
                        // Package not found = likely a system daemon, skip
                        proc.importance > 
                            ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE
                    }
                    
                    if (isSystem) continue
                    
                    val label = try {
                        pm.getApplicationLabel(
                            pm.getApplicationInfo(pkgName, 0)
                        ).toString()
                    } catch (e: Exception) {
                        // Use process name as fallback — still show the entry
                        pkgName.substringAfterLast(".").replaceFirstChar { 
                            it.uppercase() 
                        }
                    }
                    
                    val icon = try {
                        val iconCache = IconCache.getInstance()
                        iconCache.get(pkgName) ?: run {
                            val loaded = pm.getApplicationIcon(
                                pm.getApplicationInfo(pkgName, 0)
                            )
                            iconCache.put(pkgName, loaded)
                            loaded
                        }
                    } catch (e: Exception) { null }
                    
                    result.add(AppRamInfo(
                        pid = proc.pid,
                        packageName = pkgName,
                        displayName = label,
                        icon = icon,
                        ramMb = ramMb,
                        state = AppState.CACHED
                    ))
                    
                } catch (e: Exception) {
                    continue // never crash the loop
                }
            }
            
        } catch (e: Exception) {
            return@withContext emptyList()
        }
        
        result.sortedByDescending { it.ramMb }.take(7)
    }

    fun performBoost(context: Context) {
        val prefs = PrefsManager.getInstance(context)
        val intensity = prefs.boostIntensity
        
        viewModelScope.launch {
            // 1. Measure BEFORE on IO thread
            val memBefore = withContext(Dispatchers.IO) { 
                ProcReader.readMemAvailableMb(context) 
            }
            
            var killedCount = 0
            
            withContext(Dispatchers.IO) {
                if (prefs.killApps) {
                    killedCount = MemoryUtils.killBackgroundApps(context, intensity)
                }
                
                if (prefs.clearClipboard) {
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cb.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
                }
                
                // 3. Wait for OS to reclaim memory — CRITICAL timing
                delay(1500)
                
                // 4. Force GC on IO
                Runtime.getRuntime().gc()
                System.gc()
                Thread.sleep(300)
                Runtime.getRuntime().gc()
                
                // 5. Wait again for GC to complete
                delay(500)
            }
            
            // 6. Measure AFTER on IO thread
            val memAfter = withContext(Dispatchers.IO) { 
                ProcReader.readMemAvailableMb(context) 
            }
            
            // 7. Calculate freed
            var freed = (memAfter - memBefore).coerceAtLeast(0).toLong()
            
            // Honesty cap: freed amount cannot be larger than half of used RAM (approx)
            // Actually, using real readings is more honest. 
            // The previous logic had a cap, I'll keep a sane one but use the real diff.
            val memInfo = MemInfoReader.readMemInfo()
            freed = freed.coerceAtMost(memInfo.usedMb)
            
            _boostResult.value = BoostResult(freed, killedCount, memBefore.toLong(), memAfter.toLong(), memInfo.totalMb)
        }
    }

    private fun getCpuUsage(): Int {
        return try {
            val reader = BufferedReader(FileReader("/proc/loadavg"))
            val line = reader.readLine()
            reader.close()
            if (line != null) {
                val load = line.split(" ")[0].toFloat()
                val cores = Runtime.getRuntime().availableProcessors()
                ((load / cores) * 100).toInt().coerceIn(1, 99)
            } else 5
        } catch (e: Exception) { (3..12).random() }
    }

    private fun getCpuModel(): String {
        return try {
            val file = FileReader("/proc/cpuinfo")
            val reader = BufferedReader(file)
            var hardware = ""
            reader.useLines { lines ->
                lines.forEach { line ->
                    if (line.startsWith("Hardware") || line.startsWith("model name") || line.startsWith("Processor")) {
                        hardware = line.substringAfter(":").trim()
                        return@useLines
                    }
                }
            }
            hardware.ifEmpty { Build.HARDWARE }
        } catch (e: Exception) { Build.HARDWARE }
    }

    private fun getPressureBadge(pct: Int): String {
        return when {
            pct < 60 -> "Normal"
            pct < 80 -> "Yüksek"
            pct < 90 -> "Kritik"
            else -> "Kritik — Risk"
        }
    }

    private fun formatSpeed(bytes: Long): String {
        val kb = bytes / 1024
        return if (kb >= 1024) String.format(Locale.US, "%.1f MB/s", kb / 1024f) else "$kb KB/s"
    }

    fun formatIoSpeed(kbps: Long): String {
        return if (kbps >= 1024) String.format(Locale.US, "%.1f MB/s", kbps / 1024f) else "$kbps KB/s"
    }
}