package com.boostdroid.app

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StorageViewModel : ViewModel() {

    private val _storageStats = MutableLiveData<StorageStats>()
    val storageStats: LiveData<StorageStats> = _storageStats

    private val _isCleaning = MutableLiveData<Boolean>()
    val isCleaning: LiveData<Boolean> = _isCleaning

    private val _cleanResult = MutableLiveData<String>()
    val cleanResult: LiveData<String> = _cleanResult

    fun updateStorageStats() {
        viewModelScope.launch(Dispatchers.IO) {
            val stats = StorageAnalyzer.getInternalStorageStats()
            _storageStats.postValue(stats)
        }
    }

    fun deepClean(context: Context) {
        _isCleaning.value = true
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            val systemFreed = CacheCleaner.clearSystemCaches(appContext)
            val (appFreed, appCount) = CacheCleaner.clearAppCaches(appContext)
            val totalFreedMb = (systemFreed + appFreed) / (1024 * 1024)
            
            withContext(Dispatchers.Main) {
                _cleanResult.value = "$totalFreedMb MB önbellek temizlendi ($appCount uygulama)"
                _isCleaning.value = false
                updateStorageStats()
            }
        }
    }
}
