package com.boostdroid.app

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class BoostForegroundService : Service() {

    private var screenOffReceiver: BroadcastReceiver? = null
    private val handler = Handler(Looper.getMainLooper())
    private val monitorRunnable = object : Runnable {
        override fun run() {
            val freed = SmartBoostEngine.checkAndBoost(this@BoostForegroundService)
            if (freed > 0 && PrefsManager.getInstance(this@BoostForegroundService).autoBoostNotification) {
                showBoostNotification(this@BoostForegroundService, "Otomatik boost yapıldı — $freed MB boşaltıldı")
            }
            updateNotification()
            handler.postDelayed(this, 30000) // 30 seconds
        }
    }

    companion object {
        const val CHANNEL_ID = "BoostDroidChannel"
        const val NOTIFICATION_ID = 1

        fun killBackgroundApps(context: Context): Int {
            val intensity = PrefsManager.getInstance(context).boostIntensity
            return MemoryUtils.killBackgroundApps(context, intensity)
        }

        fun killAllApps(context: Context): Int {
            return MemoryUtils.killBackgroundApps(context, "aggressive")
        }

        fun getBatteryCapacityMah(context: Context): Int {
            return try {
                val cls = Class.forName("com.android.internal.os.PowerProfile")
                val profile = cls.getConstructor(Context::class.java).newInstance(context)
                (cls.getMethod("getBatteryCapacity").invoke(profile) as Double).toInt()
            } catch (e: Exception) {
                try {
                    val f = java.io.File("/sys/class/power_supply/battery/charge_full")
                    if (f.exists()) (f.readText().trim().toLong() / 1000).toInt() else 0
                } catch (e2: Exception) { 0 }
            }
        }

        fun showBoostNotification(context: Context, message: String) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(CHANNEL_ID, "BoostDroid", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(channel)
            
            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("BoostDroid")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true)
                .build()
            nm.notify(2, notif)
        }

        fun startService(context: Context) {
            val intent = Intent(context, BoostForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, BoostForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerScreenOffReceiver()
        handler.post(monitorRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Sistem izleniyor..."))
        return START_STICKY
    }

    private fun registerScreenOffReceiver() {
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        screenOffReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val prefs = PrefsManager.getInstance(context)
                if (prefs.autoBoostScreenOff) {
                    killBackgroundApps(context)
                    if (prefs.notifyBoost) {
                        showBoostNotification(context, "Ekran kapalı: Optimize edildi")
                    }
                }
            }
        }
        registerReceiver(screenOffReceiver, filter)
    }

    private fun updateNotification() {
        val memInfo = MemInfoReader.readMemInfo()
        val total = memInfo.totalMb.coerceAtLeast(1)
        val pressure = ((memInfo.usedMb.toDouble() / total.toDouble()) * 100).toInt()
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification("RAM Kullanımı: %$pressure"))
    }

    override fun onDestroy() {
        screenOffReceiver?.let { unregisterReceiver(it) }
        handler.removeCallbacks(monitorRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, "BoostDroid Monitoring", NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(channel)
        
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BoostDroid")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}