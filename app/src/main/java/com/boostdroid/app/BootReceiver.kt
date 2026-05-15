package com.boostdroid.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = PrefsManager.getInstance(context)
            if (prefs.autoBoostOnBoot) {
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        val serviceIntent = Intent(context, BoostForegroundService::class.java)
                        context.startForegroundService(serviceIntent)
                    } catch (e: Exception) {}
                }, 5000)
            }
        }
    }
}