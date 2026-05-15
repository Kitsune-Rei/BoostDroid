package com.boostdroid.app

import android.os.Environment
import android.os.StatFs
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

data class StorageStats(
    val totalBytes: Long,
    val availableBytes: Long,
    val usedBytes: Long,
    val fsType: String,
    val isEMMC: Boolean,
    val queueDepth: Int
)

object StorageAnalyzer {

    fun getInternalStorageStats(): StorageStats {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong

        val total = totalBlocks * blockSize
        val available = availableBlocks * blockSize
        val used = total - available

        return StorageStats(
            totalBytes = total,
            availableBytes = available,
            usedBytes = used,
            fsType = detectFsType("/data"),
            isEMMC = File("/sys/block/mmcblk0").exists(),
            queueDepth = readQueueDepth()
        )
    }

    private fun detectFsType(mountPoint: String): String {
        try {
            BufferedReader(FileReader("/proc/mounts")).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val parts = line!!.split(Regex("\\s+"))
                    if (parts.size >= 3 && parts[1] == mountPoint) {
                        return parts[2]
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "unknown"
    }

    private fun readQueueDepth(): Int {
        val path = "/sys/block/mmcblk0/queue/nr_requests"
        val file = File(path)
        if (!file.exists()) return 0
        return try {
            BufferedReader(FileReader(file)).use { it.readLine()?.trim()?.toIntOrNull() ?: 0 }
        } catch (e: Exception) {
            0
        }
    }
}