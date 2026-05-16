package com.boostdroid.app

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SmartBoostEngine {
    private var lastBoostTime = 0L
    private var highPressureCount = 0

    suspend fun checkAndBoost(context: Context): Int = withContext(Dispatchers.IO) {
        val prefs = PrefsManager.getInstance(context)
        if (!prefs.smartBoostEnabled) return@withContext 0

        val currentTime = System.currentTimeMillis()
        if ((currentTime - lastBoostTime) < 10 * 60 * 1000) return@withContext 0 // Rate limit 10m

        if (isUserActivelyGaming(context)) return@withContext 0

        val memInfo = MemInfoReader.readMemInfo()
        val pressure = (memInfo.usedMb.toDouble() / memInfo.totalMb.toDouble()) * 100
        val threshold = prefs.smartBoostThreshold

        if (pressure > threshold) {
            highPressureCount++
        } else {
            highPressureCount = 0
        }

        if (highPressureCount >= 2) { // 2 consecutive checks (approx 60s if checked every 30s)
            // Realism: Skip if no cached apps exist
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val hasCachedApps = am.runningAppProcesses?.any { 
                it.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED 
            } ?: false
            
            if (!hasCachedApps) {
                highPressureCount = 0
                return@withContext 0
            }

            val freed = performGentleBoost(context)
            lastBoostTime = currentTime
            highPressureCount = 0
            return@withContext freed
        }

        return@withContext 0
    }

    private fun isUserActivelyGaming(context: Context): Boolean {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 30 * 1000
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        
        if (stats == null || stats.isEmpty()) return false
        
        val latestApp = stats.maxByOrNull { it.lastTimeUsed }
        val packageName = latestApp?.packageName ?: return false
        
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_GAME
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun performGentleBoost(context: Context): Int {
        val memBefore = MemInfoReader.readMemInfo().availableMb
        MemoryUtils.killBackgroundApps(context, "gentle")
        val memAfter = MemInfoReader.readMemInfo().availableMb
        return (memAfter - memBefore).coerceAtLeast(0).toInt()
    }
}
