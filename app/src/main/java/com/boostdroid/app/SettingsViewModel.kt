package com.boostdroid.app

import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SettingsViewModel : ViewModel() {

    private val _permissionsStatus = MutableLiveData<Map<String, Boolean>>()
    val permissionsStatus: LiveData<Map<String, Boolean>> = _permissionsStatus

    fun checkPermissions(context: Context) {
        val status = mutableMapOf<String, Boolean>()
        
        // Usage Stats
        status["usage"] = hasUsageStatsPermission(context)
        
        // Notifications (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            status["notifications"] = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            status["notifications"] = true
        }
        
        // Exact Alarm (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            status["alarm"] = am.canScheduleExactAlarms()
        } else {
            status["alarm"] = true
        }
        
        _permissionsStatus.value = status
    }

    private fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }
}