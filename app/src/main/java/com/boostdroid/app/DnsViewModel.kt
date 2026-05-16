package com.boostdroid.app

import android.content.Context
import android.provider.Settings
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress

class DnsViewModel : ViewModel() {

    private val _dnsStatus = MutableLiveData<String>()
    val dnsStatus: LiveData<String> = _dnsStatus

    private val _adbSetupRequired = MutableLiveData<Boolean>()
    val adbSetupRequired: LiveData<Boolean> = _adbSetupRequired

    fun applyDns(context: Context, address: String) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    Settings.Global.putString(appContext.contentResolver, "private_dns_mode", "hostname")
                    Settings.Global.putString(appContext.contentResolver, "private_dns_specifier", address)
                }
                _adbSetupRequired.value = false
                PrefsManager.getInstance(appContext).dnsAdbGranted = true
                validateDns()
            } catch (e: SecurityException) {
                _adbSetupRequired.value = true
                PrefsManager.getInstance(appContext).dnsAdbGranted = false
            }
        }
    }

    private fun validateDns() {
        _dnsStatus.value = "validating"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val address = InetAddress.getByName("dns.google")
                if (address != null) {
                    _dnsStatus.postValue("success")
                } else {
                    _dnsStatus.postValue("fail")
                }
            } catch (e: Exception) {
                _dnsStatus.postValue("fail")
            }
        }
    }

    fun checkInitialState(context: Context) {
        val appContext = context.applicationContext
        val current = Settings.Global.getString(appContext.contentResolver, "private_dns_specifier")
        if (!current.isNullOrBlank()) {
            validateDns()
        }
        _adbSetupRequired.value = !PrefsManager.getInstance(appContext).dnsAdbGranted
    }
}