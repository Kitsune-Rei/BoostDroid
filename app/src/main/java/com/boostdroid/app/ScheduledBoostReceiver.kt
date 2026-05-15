package com.boostdroid.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import java.util.*

class ScheduledBoostReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BoostDroid:ScheduledBoost")
        
        try {
            wakeLock.acquire(10 * 60 * 1000L /*10 mins max*/)
            
            val killed = BoostForegroundService.killBackgroundApps(context)
            MemoryUtils.clearCache(context)
            
            val prefs = PrefsManager.getInstance(context)
            if (prefs.notifyBoost) {
                BoostForegroundService.showBoostNotification(context, context.getString(R.string.optimized))
            }
            
            // Reschedule for next day
            val hour = intent.getIntExtra("hour", -1)
            val minute = intent.getIntExtra("minute", -1)
            if (hour >= 0 && minute >= 0) {
                reschedule(context, hour, minute)
            }
            
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    private fun reschedule(context: Context, hour: Int, minute: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }
        
        val intent = Intent(context, ScheduledBoostReceiver::class.java).apply {
            putExtra("hour", hour)
            putExtra("minute", minute)
        }
        
        val pi = PendingIntent.getBroadcast(
            context, hour * 60 + minute, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val canSchedule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else true
        
        if (canSchedule) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pi)
        }
    }
}