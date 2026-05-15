package com.boostdroid.app

import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException

data class MemInfoResult(
    val totalMb: Long = 0,
    val availableMb: Long = 0,
    val usedMb: Long = 0,
    val freeMb: Long = 0,
    val cachedMb: Long = 0,
    val buffersMb: Long = 0,
    val sReclaimableMb: Long = 0,
    val swapTotalMb: Long = 0,
    val swapUsedMb: Long = 0
)

object MemInfoReader {
    private const val MEMINFO_PATH = "/proc/meminfo"

    fun readMemInfo(): MemInfoResult {
        val map = mutableMapOf<String, Long>()
        try {
            BufferedReader(FileReader(MEMINFO_PATH)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val parts = line!!.split(Regex("\\s+"))
                    if (parts.size >= 2) {
                        val key = parts[0].replace(":", "")
                        val value = parts[1].toLongOrNull() ?: 0L
                        map[key] = value / 1024 // Convert KB to MB
                    }
                }
            }
        } catch (ignored: IOException) {
            return MemInfoResult()
        }

        val total = map["MemTotal"] ?: 0L
        val free = map["MemFree"] ?: 0L
        val available = map["MemAvailable"] ?: free
        val buffers = map["Buffers"] ?: 0L
        val cached = map["Cached"] ?: 0L
        val sReclaimable = map["SReclaimable"] ?: 0L
        val swapTotal = map["SwapTotal"] ?: 0L
        val swapFree = map["SwapFree"] ?: 0L

        // Android's honest "Used RAM" is Total - Available.
        // Available RAM includes MemFree + some reclaimable caches.
        val used = (total - available).coerceAtLeast(0)
        val swapUsed = (swapTotal - swapFree).coerceAtLeast(0)

        return MemInfoResult(
            totalMb = total,
            availableMb = available,
            usedMb = used,
            freeMb = free,
            cachedMb = cached,
            buffersMb = buffers,
            sReclaimableMb = sReclaimable,
            swapTotalMb = swapTotal,
            swapUsedMb = swapUsed
        )
    }
}