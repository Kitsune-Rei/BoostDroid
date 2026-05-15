package com.boostdroid.app

import android.content.Context
import java.io.File

data class CacheResult(
    val bytesCleared: Long,
    val appsCleared: Int,
    val accessErrors: Int
)

object CacheCleaner {

    fun clearAppCaches(context: Context): CacheResult {
        var totalBytesCleared = 0L
        var appsCleared = 0
        var accessErrors = 0
        
        val pm = context.packageManager
        
        // PATH 1: Our own app cache (always accessible)
        totalBytesCleared += clearDirectory(context.cacheDir)
        context.codeCacheDir?.let { totalBytesCleared += clearDirectory(it) }
        
        // PATH 2: External storage Android/data/{pkg}/cache
        // These are accessible without root on Android 12
        // (they are on the shared external storage)
        val externalDataDir = context.getExternalFilesDir(null)
            ?.parentFile?.parentFile // /sdcard/Android/data/
        
        if (externalDataDir != null && externalDataDir.exists()) {
            try {
                val installedPackages = pm.getInstalledPackages(0)
                    .map { it.packageName }
                
                for (pkg in installedPackages) {
                    val cacheDir = File(
                        externalDataDir, "$pkg/cache"
                    )
                    if (cacheDir.exists() && cacheDir.isDirectory) {
                        try {
                            val cleared = clearDirectory(cacheDir)
                            if (cleared > 0) {
                                totalBytesCleared += cleared
                                appsCleared++
                            }
                        } catch (e: SecurityException) {
                            accessErrors++
                        } catch (e: Exception) {
                            accessErrors++
                        }
                    }
                }
            } catch (e: Exception) { /* package list failed */ }
        }
        
        // PATH 3: Our app's external cache
        context.externalCacheDir?.let { 
            totalBytesCleared += clearDirectory(it) 
        }
        
        // PATH 4: Additional external cache dirs (multi-volume)
        context.externalCacheDirs?.forEach { dir ->
            if (dir != null) totalBytesCleared += clearDirectory(dir)
        }
        
        return CacheResult(
            bytesCleared = totalBytesCleared,
            appsCleared = appsCleared,
            accessErrors = accessErrors
        )
    }

    private fun clearDirectory(dir: File): Long {
        if (!dir.exists() || !dir.isDirectory) return 0L
        var cleared = 0L
        try {
            dir.listFiles()?.forEach { file ->
                try {
                    if (file.isFile) {
                        val size = file.length()
                        if (file.delete()) cleared += size
                    } else if (file.isDirectory) {
                        cleared += clearDirectory(file) // recursive
                        file.delete() // remove empty dir
                    }
                } catch (e: Exception) { /* skip unreadable file */ }
            }
        } catch (e: SecurityException) { /* no permission for this dir */ }
        return cleared
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024L * 1024 * 1024 -> 
                String.format("%.1f GB", bytes / (1024f * 1024 * 1024))
            bytes >= 1024L * 1024 -> 
                String.format("%.1f MB", bytes / (1024f * 1024))
            bytes >= 1024L -> 
                "${bytes / 1024} KB"
            else -> 
                "$bytes B"
        }
    }

    // Previous implementation compatibility
    fun clearSystemCaches(context: Context): Long {
        return clearAppCaches(context).bytesCleared
    }
}
