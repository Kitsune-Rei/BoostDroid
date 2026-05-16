package com.boostdroid.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReviveViewModel : ViewModel() {

    private val _appList = MutableLiveData<List<ReviveAppInfo>>()
    val appList: LiveData<List<ReviveAppInfo>> = _appList

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _reviveResult = MutableLiveData<ReviveResult>()
    val reviveResult: LiveData<ReviveResult> = _reviveResult

    fun loadApps(context: Context) {
        _isLoading.value = true
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            val pm = appContext.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val iconCache = IconCache.getInstance()

            val appList = packages.filter { 
                (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 
            }.map { app ->
                val label = app.loadLabel(pm).toString()
                var icon = iconCache.get(app.packageName)
                if (icon == null) {
                    icon = app.loadIcon(pm)
                    iconCache.put(app.packageName, icon)
                }
                val state = ProcReader.getAppState(appContext, app.packageName)
                val pid = getProcessId(appContext, app.packageName)
                ReviveAppInfo(
                    packageName = app.packageName,
                    label = label,
                    icon = icon,
                    state = state,
                    estimatedRam = ProcReader.readProcessRamMb(pid)
                )
            }.sortedBy { it.label }

            withContext(Dispatchers.Main) {
                _appList.value = appList
                _isLoading.value = false
            }
        }
    }

    private fun getProcessId(context: Context, packageName: String): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        return am.runningAppProcesses?.firstOrNull { it.processName.startsWith(packageName) }?.pid ?: 0
    }

    fun reviveApp(context: Context, packageName: String) {
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            val result = QuickReviveManager.revive(appContext, packageName)
            withContext(Dispatchers.Main) {
                _reviveResult.value = result
                loadApps(appContext) // Refresh list
            }
        }
    }
}
