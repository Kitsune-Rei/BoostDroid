package com.boostdroid.app

import android.graphics.drawable.Drawable

enum class AppState {
    NOT_RUNNING,       // no process found — cold start needed
    CACHED,            // in memory but cached — revivable
    ACTIVE,            // foreground or visible — healthy, no revive needed  
    STUCK              // has a process but importance suggests frozen state
}

data class AppRamInfo(
    val pid: Int,
    val packageName: String,
    val displayName: String,
    val icon: Drawable?,
    val ramMb: Int,
    val state: AppState
)

data class ReviveAppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val state: AppState,
    val estimatedRam: Int
)
