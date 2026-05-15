package com.boostdroid.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MenuItem
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import com.boostdroid.app.databinding.ActivityMainBinding
import com.google.android.material.navigation.NavigationView
import java.util.*

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsManager

    override fun attachBaseContext(newBase: Context) {
        val p = PrefsManager.getInstance(newBase)
        val locale = Locale(p.language)
        Locale.setDefault(locale)
        val config = newBase.resources.configuration
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val sharedPrefs = getSharedPreferences("boostdroid_prefs", Context.MODE_PRIVATE)
        val isDark = sharedPrefs.getBoolean("dark_theme", true)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        
        super.onCreate(savedInstanceState)
        prefs = PrefsManager.getInstance(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val toggle = ActionBarDrawerToggle(this, binding.drawerLayout, binding.toolbar, android.R.string.ok, android.R.string.cancel)
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        binding.navigationView.setNavigationItemSelectedListener(this)

        if (savedInstanceState == null) {
            loadFragment(DashboardFragment())
            binding.navigationView.setCheckedItem(R.id.nav_dashboard)
            supportActionBar?.title = getString(R.string.nav_dashboard)
        }

        if (prefs.isFirstLaunch) {
            showPermissionRationale()
            prefs.isFirstLaunch = false
        }
    }

    private fun showPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.perm_rationale_title))
            .setMessage(getString(R.string.perm_rationale_desc))
            .setPositiveButton(getString(R.string.perm_grant)) { _, _ ->
                // Redirect to settings for usage stats
                try {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    })
                } catch (ignored: Exception) {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            }
            .setNegativeButton(getString(R.string.perm_skip), null)
            .show()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val (fragment, title) = when (item.itemId) {
            R.id.nav_dashboard -> Pair(DashboardFragment(), getString(R.string.nav_dashboard))
            R.id.nav_revive -> Pair(ReviveFragment(), getString(R.string.nav_revive))
            R.id.nav_storage -> Pair(StorageFragment(), getString(R.string.nav_storage))
            R.id.nav_features -> Pair(FeaturesFragment(), getString(R.string.nav_features))
            R.id.nav_dns -> Pair(DnsFragment(), getString(R.string.nav_dns))
            R.id.nav_device -> Pair(DeviceInfoFragment(), getString(R.string.nav_device))
            R.id.nav_settings -> Pair(SettingsFragment(), getString(R.string.nav_settings))
            else -> return false
        }
        loadFragment(fragment)
        supportActionBar?.title = title
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.fragmentContainer, fragment).commit()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}