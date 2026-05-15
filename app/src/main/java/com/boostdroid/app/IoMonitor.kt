package com.boostdroid.app

import java.io.File

data class DiskStats(
    val sectorsRead: Long,
    val sectorsWritten: Long,
    val iosInProgress: Int,
    val timestampMs: Long
)

data class IoStats(
    val readKbps: Long,
    val writeKbps: Long,
    val iosInProgress: Int,
    val isMeasuring: Boolean
)

object IoMonitor {
    private var prevStats: DiskStats? = null

    fun getIoStats(): IoStats {
        val current = readDiskStats()
        val previous = prevStats

        if (current == null) {
            return IoStats(0, 0, 0, false)
        }

        if (previous == null) {
            prevStats = current
            return IoStats(0, 0, current.iosInProgress, true)
        }

        val deltaSectorsRead = current.sectorsRead - previous.sectorsRead
        val deltaSectorsWrite = current.sectorsWritten - previous.sectorsWritten
        val deltaMs = (current.timestampMs - previous.timestampMs).coerceAtLeast(1)

        // KB/s calculation: ((sectors * 512 bytes) / 1024 bytes/KB) * 1000 ms/s / deltaMs
        val readKbps = ((deltaSectorsRead * 512) / 1024) * 1000 / deltaMs
        val writeKbps = ((deltaSectorsWrite * 512) / 1024) * 1000 / deltaMs

        prevStats = current

        return IoStats(
            readKbps = readKbps,
            writeKbps = writeKbps,
            iosInProgress = current.iosInProgress,
            isMeasuring = false
        )
    }

    private fun readDiskStats(): DiskStats? {
        return try {
            val file = File("/proc/diskstats")
            if (!file.exists() || !file.canRead()) return null
            
            val lines = file.readLines()
            // Find mmcblk0 (exact match for the parent device)
            val line = lines.firstOrNull {
                val parts = it.trim().split("\\s+".toRegex())
                parts.size > 2 && parts[2] == "mmcblk0"
            } ?: return null

            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size < 14) return null

            DiskStats(
                sectorsRead = parts[5].toLongOrNull() ?: 0L,
                sectorsWritten = parts[9].toLongOrNull() ?: 0L,
                iosInProgress = parts[11].toIntOrNull() ?: 0,
                timestampMs = System.currentTimeMillis()
            )
        } catch (ignored: Exception) {
            null
        }
    }
}