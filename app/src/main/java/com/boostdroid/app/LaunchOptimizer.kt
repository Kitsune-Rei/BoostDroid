package com.boostdroid.app

import android.app.ActivityManager
import android.content.Context

object LaunchOptimizer {

    fun optimizeForLaunch(context: Context): Boolean {
        val memInfo = MemInfoReader.readMemInfo()
        if (memInfo.availableMb < 300) {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningProcs = am.runningAppProcesses ?: return true
            
            for (proc in runningProcs) {
                if (proc.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED) {
                    for (pkg in proc.pkgList) {
                        am.killBackgroundProcesses(pkg)
                    }
                }
            }
            
            Thread.sleep(500)
            Runtime.getRuntime().gc()
            System.gc()
            
            val memInfoAfter = MemInfoReader.readMemInfo()
            return memInfoAfter.availableMb >= 200
        }
        return true
    }
}