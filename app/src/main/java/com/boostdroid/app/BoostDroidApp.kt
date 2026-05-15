package com.boostdroid.app

import android.app.Application

class BoostDroidApp : Application() {

    // 6.7 App-wide memory trim logic
    override fun onLowMemory() {
        super.onLowMemory()
        IconCache.clear()
        Runtime.getRuntime().gc()
        System.gc()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE) {
            IconCache.clear()
            Runtime.getRuntime().gc()
        }
    }
}