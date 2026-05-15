package com.boostdroid.app

import android.content.Context
import android.content.SharedPreferences

class PrefsManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "boostdroid_prefs"
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_BOOST_INTENSITY = "boost_intensity"
        private const val KEY_NOTIFY_BOOST = "notify_boost"
        private const val KEY_BOOST_ANIM = "boost_anim"
        private const val KEY_KILL_APPS = "kill_apps"
        private const val KEY_CLEAR_CLIPBOARD = "clear_clipboard"
        private const val KEY_TOGGLE_WIFI = "toggle_wifi"
        private const val KEY_TOGGLE_BLUETOOTH = "toggle_bluetooth"
        private const val KEY_BATTERY_SAVER = "battery_saver"
        private const val KEY_AUTO_BOOST_BOOT = "auto_boost_on_boot"
        private const val KEY_AUTO_BOOST_SCREEN = "auto_boost_screen_off"
        private const val KEY_SCHEDULED_TIMES = "scheduled_times"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_DNS_ADB_GRANTED = "dns_adb_granted"
        private const val KEY_SMART_BOOST = "smart_boost_enabled"
        private const val KEY_SMART_BOOST_THRESHOLD = "smart_boost_threshold"
        private const val KEY_IO_SYNC_ENABLED = "io_sync_enabled"
        private const val KEY_OPTIMIZE_BEFORE_LAUNCH = "optimize_before_launch"
        private const val KEY_DEEP_CACHE_CLEAN = "deep_cache_clean"
        private const val KEY_AUTO_BOOST_NOTIFICATION = "auto_boost_notification"
        private const val KEY_WORKING_THERMAL_PATH = "working_thermal_path"
        private const val KEY_REVIVE_EXPLANATION_SHOWN = "revive_explanation_shown"

        @Volatile
        private var INSTANCE: PrefsManager? = null

        fun getInstance(context: Context): PrefsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PrefsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    var darkTheme: Boolean
        get() = prefs.getBoolean(KEY_DARK_THEME, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK_THEME, value).apply()

    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "en") ?: "en"
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    var boostIntensity: String
        get() = prefs.getString(KEY_BOOST_INTENSITY, "normal") ?: "normal"
        set(value) = prefs.edit().putString(KEY_BOOST_INTENSITY, value).apply()

    var notifyBoost: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_BOOST, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFY_BOOST, value).apply()

    var boostAnim: Boolean
        get() = prefs.getBoolean(KEY_BOOST_ANIM, true)
        set(value) = prefs.edit().putBoolean(KEY_BOOST_ANIM, value).apply()

    var killApps: Boolean
        get() = prefs.getBoolean(KEY_KILL_APPS, true)
        set(value) = prefs.edit().putBoolean(KEY_KILL_APPS, value).apply()

    var clearClipboard: Boolean
        get() = prefs.getBoolean(KEY_CLEAR_CLIPBOARD, true)
        set(value) = prefs.edit().putBoolean(KEY_CLEAR_CLIPBOARD, value).apply()

    var toggleWifi: Boolean
        get() = prefs.getBoolean(KEY_TOGGLE_WIFI, false)
        set(value) = prefs.edit().putBoolean(KEY_TOGGLE_WIFI, value).apply()

    var toggleBluetooth: Boolean
        get() = prefs.getBoolean(KEY_TOGGLE_BLUETOOTH, false)
        set(value) = prefs.edit().putBoolean(KEY_TOGGLE_BLUETOOTH, value).apply()

    var batterySaver: Boolean
        get() = prefs.getBoolean(KEY_BATTERY_SAVER, false)
        set(value) = prefs.edit().putBoolean(KEY_BATTERY_SAVER, value).apply()

    var autoBoostOnBoot: Boolean
        get() = prefs.getBoolean(KEY_AUTO_BOOST_BOOT, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_BOOST_BOOT, value).apply()

    var autoBoostScreenOff: Boolean
        get() = prefs.getBoolean(KEY_AUTO_BOOST_SCREEN, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_BOOST_SCREEN, value).apply()

    var scheduledTimes: String
        get() = prefs.getString(KEY_SCHEDULED_TIMES, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SCHEDULED_TIMES, value).apply()

    var isFirstLaunch: Boolean
        get() = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_LAUNCH, value).apply()
        
    var dnsAdbGranted: Boolean
        get() = prefs.getBoolean(KEY_DNS_ADB_GRANTED, false)
        set(value) = prefs.edit().putBoolean(KEY_DNS_ADB_GRANTED, value).apply()

    var smartBoostEnabled: Boolean
        get() = prefs.getBoolean(KEY_SMART_BOOST, false)
        set(value) = prefs.edit().putBoolean(KEY_SMART_BOOST, value).apply()

    var smartBoostThreshold: Int
        get() = prefs.getInt(KEY_SMART_BOOST_THRESHOLD, 85)
        set(value) = prefs.edit().putInt(KEY_SMART_BOOST_THRESHOLD, value).apply()

    var ioSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_IO_SYNC_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_IO_SYNC_ENABLED, value).apply()

    var optimizeBeforeLaunch: Boolean
        get() = prefs.getBoolean(KEY_OPTIMIZE_BEFORE_LAUNCH, true)
        set(value) = prefs.edit().putBoolean(KEY_OPTIMIZE_BEFORE_LAUNCH, value).apply()

    var deepCacheClean: Boolean
        get() = prefs.getBoolean(KEY_DEEP_CACHE_CLEAN, false)
        set(value) = prefs.edit().putBoolean(KEY_DEEP_CACHE_CLEAN, value).apply()

    var autoBoostNotification: Boolean
        get() = prefs.getBoolean(KEY_AUTO_BOOST_NOTIFICATION, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_BOOST_NOTIFICATION, value).apply()

    var workingThermalPath: String?
        get() = prefs.getString(KEY_WORKING_THERMAL_PATH, null)
        set(value) = prefs.edit().putString(KEY_WORKING_THERMAL_PATH, value).apply()

    var reviveExplanationShown: Boolean
        get() = prefs.getBoolean(KEY_REVIVE_EXPLANATION_SHOWN, false)
        set(value) = prefs.edit().putBoolean(KEY_REVIVE_EXPLANATION_SHOWN, value).apply()
}