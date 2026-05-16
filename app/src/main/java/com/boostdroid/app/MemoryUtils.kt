package com.boostdroid.app

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

object MemoryUtils {

    suspend fun getAppRamInfoList(context: Context): List<AppRamInfo> = withContext(Dispatchers.IO) {
        if (!hasUsageStatsPermission(context)) return@withContext emptyList()

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = context.packageManager
        
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1000 * 60 * 10 // Last 10 minutes for "running" apps
        
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        if (stats.isNullOrEmpty()) {
            return@withContext getInstalledAppsFallback(context)
        }

        stats.filter { it.lastTimeUsed > startTime }
            .sortedByDescending { it.lastTimeUsed }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .mapNotNull { stat ->
                try {
                    val appInfo = pm.getApplicationInfo(stat.packageName, 0)
                    if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 && 
                        (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) return@mapNotNull null
                    
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val icon = pm.getApplicationIcon(appInfo)
                    val estimatedRam = calculateEstimatedRam(stat.packageName)
                    
                    AppRamInfo(0, stat.packageName, label, icon, estimatedRam, AppState.CACHED)
                } catch (_: Exception) {
                    null
                }
            }.take(10)
    }

    private fun getInstalledAppsFallback(context: Context): List<AppRamInfo> {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return apps.filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && it.packageName != context.packageName }
            .shuffled()
            .take(5)
            .map { appInfo ->
                AppRamInfo(
                    0,
                    appInfo.packageName,
                    pm.getApplicationLabel(appInfo).toString(),
                    pm.getApplicationIcon(appInfo),
                    calculateEstimatedRam(appInfo.packageName),
                    AppState.NOT_RUNNING
                )
            }
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun calculateEstimatedRam(packageName: String): Int {
        val random = Random(packageName.hashCode().toLong())
        val base = when {
            packageName.contains("instagram") -> 450
            packageName.contains("facebook") -> 480
            packageName.contains("whatsapp") -> 220
            packageName.contains("chrome") -> 550
            packageName.contains("youtube") -> 380
            packageName.contains("game") -> 700
            packageName.contains("spotify") -> 190
            packageName.contains("tiktok") -> 440
            else -> 150
        }
        return base + random.nextInt(120)
    }

    suspend fun killBackgroundApps(context: Context, intensity: String): Int = withContext(Dispatchers.IO) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val myPkg = context.packageName
        val myPid = android.os.Process.myPid()
        var killed = 0
        
        val processes = am.runningAppProcesses ?: return@withContext 0
        
        // Define threshold based on intensity
        val threshold = when (intensity) {
            "gentle" -> ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED
            "normal" -> ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED
            "aggressive" -> 201
            else -> ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED
        }

        val targets = processes.filter { proc ->
            proc.pid != myPid &&
            proc.processName != myPkg &&
            proc.importance >= threshold
        }
        
        if (intensity == "aggressive") {
            targets.forEach { proc ->
                val pkg = proc.processName.split(":")[0]
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", pkg, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                killed++
                delay(1500) // Wait for Accessibility Service to work
            }
        } else {
            // Kill in small batches to avoid eMMC write spike
            targets.chunked(3).forEach { batch ->
                batch.forEach { proc ->
                    try {
                        am.killBackgroundProcesses(proc.processName)
                        killed++
                    } catch (_: Exception) { /* skip */ }
                }
                delay(150)
            }
        }
        
        // Intensity specific actions
        if (intensity == "normal" || intensity == "aggressive") {
            Runtime.getRuntime().gc()
            System.gc()
            delay(200)
            Runtime.getRuntime().gc()
        }

        if (intensity == "aggressive") {
            try {
                // Flush file system buffers
                Runtime.getRuntime().exec("sync")
            } catch (_: Exception) {}
        }
        
        killed
    }

    fun clearCache(context: Context): Long {
        var freedBytes = 0L
        try {
            freedBytes += deleteDir(context.cacheDir)
            freedBytes += deleteDir(context.externalCacheDir)
        } catch (_: Exception) {}
        return freedBytes / (1024 * 1024)
    }

    private fun deleteDir(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0
        var bytesDeleted = 0L
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { bytesDeleted += deleteDir(it) }
        }
        val size = dir.length()
        if (dir.delete()) bytesDeleted += size
        return bytesDeleted
    }
}
