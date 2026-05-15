package com.boostdroid.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.util.*

class FeaturesViewModel : ViewModel() {

    private val _scheduledTimesText = MutableLiveData<String>()
    val scheduledTimesText: LiveData<String> = _scheduledTimesText

    fun updateScheduledTimes(context: Context) {
        val prefs = PrefsManager.getInstance(context)
        val times = prefs.scheduledTimes
        _scheduledTimesText.value = if (times.isEmpty()) "" else times.replace(",", "  •  ")
    }

    fun addTime(context: Context, hour: Int, minute: Int) {
        val prefs = PrefsManager.getInstance(context)
        val time = String.format(Locale.US, "%02d:%02d", hour, minute)
        val current = if (prefs.scheduledTimes.isEmpty()) emptyList() else prefs.scheduledTimes.split(",")
        
        if (time !in current) {
            val updated = current + time
            prefs.scheduledTimes = updated.joinToString(",")
            scheduleAlarm(context, hour, minute)
            updateScheduledTimes(context)
        }
    }

    fun clearAll(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = PrefsManager.getInstance(context)
        
        prefs.scheduledTimes.split(",").forEach { time ->
            if (time.contains(":")) {
                val parts = time.split(":")
                val h = parts[0].toIntOrNull() ?: return@forEach
                val m = parts[1].toIntOrNull() ?: return@forEach
                
                val intent = Intent(context, ScheduledBoostReceiver::class.java)
                val pi = PendingIntent.getBroadcast(
                    context, h * 60 + m, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                am.cancel(pi)
            }
        }
        
        prefs.scheduledTimes = ""
        updateScheduledTimes(context)
    }

    private fun scheduleAlarm(context: Context, hour: Int, minute: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DAY_OF_YEAR, 1)
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