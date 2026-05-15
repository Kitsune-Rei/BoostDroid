package com.boostdroid.app

import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.boostdroid.app.databinding.FragmentFeaturesBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class FeaturesFragment : Fragment() {
    private var _binding: FragmentFeaturesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FeaturesViewModel by viewModels()
    private lateinit var prefs: PrefsManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFeaturesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PrefsManager.getInstance(requireContext())
        setupUI()
        setupObservers()
        viewModel.updateScheduledTimes(requireContext())
        
        binding.tvCacheClearNote?.text = getString(R.string.cache_clear_note_text)
        binding.tvCacheClearNote?.visibility = View.VISIBLE
        
        applyEntranceAnimations()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionIndicators()
    }

    private fun setupUI() {
        binding.switchKillApps.isChecked = prefs.killApps
        binding.switchKillAll.isChecked = prefs.boostIntensity == "aggressive"
        binding.switchClearClipboard.isChecked = prefs.clearClipboard
        binding.switchAutoBootBoot.isChecked = prefs.autoBoostOnBoot
        binding.switchAutoBoostScreen.isChecked = prefs.autoBoostScreenOff
        binding.switchOptimizeLaunch.isChecked = prefs.optimizeBeforeLaunch
        binding.switchSmartBoost.isChecked = prefs.smartBoostEnabled

        binding.switchKillApps.setOnCheckedChangeListener { _, c -> prefs.killApps = c }
        binding.switchKillAll.setOnCheckedChangeListener { _, c ->
            prefs.boostIntensity = if (c) "aggressive" else "normal"
        }
        binding.switchClearClipboard.setOnCheckedChangeListener { _, c -> prefs.clearClipboard = c }
        binding.switchAutoBootBoot.setOnCheckedChangeListener { _, c -> prefs.autoBoostOnBoot = c }
        binding.switchAutoBoostScreen.setOnCheckedChangeListener { _, c -> prefs.autoBoostScreenOff = c }
        binding.switchOptimizeLaunch.setOnCheckedChangeListener { _, c -> prefs.optimizeBeforeLaunch = c }
        binding.switchSmartBoost.setOnCheckedChangeListener { _, c -> 
            prefs.smartBoostEnabled = c
            if (c) BoostForegroundService.startService(requireContext())
        }

        binding.btnAddBoostTime.setOnClickListener { checkAlarmPermissionAndShowPicker() }
        binding.btnClearTimes.setOnClickListener { viewModel.clearAll(requireContext()) }

        binding.btnHelpKillApps.setOnClickListener { showPermissionHelp(getString(R.string.perm_help_usage_title), getString(R.string.perm_help_usage_desc)) }
        binding.btnHelpKillAll.setOnClickListener { showPermissionHelp(getString(R.string.perm_help_usage_title), getString(R.string.perm_help_usage_desc)) }
        binding.btnHelpSmartBoost.setOnClickListener { showPermissionHelp(getString(R.string.perm_help_usage_title), getString(R.string.perm_help_usage_desc)) }
        binding.btnHelpAlarm.setOnClickListener { showPermissionHelp(getString(R.string.perm_help_alarm_title), getString(R.string.perm_help_alarm_desc)) }

        binding.btnDeepCacheClear.setOnClickListener {
            binding.btnDeepCacheClear.isEnabled = false
            binding.btnDeepCacheClear.text = getString(R.string.cleaning_msg)
            
            viewLifecycleOwner.lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    CacheCleaner.clearAppCaches(requireContext())
                }
                
                val message = buildString {
                    append(getString(R.string.cache_cleared_toast, CacheCleaner.formatBytes(result.bytesCleared)))
                    if (result.appsCleared > 0) {
                        append(" ")
                        append(getString(R.string.apps_cleared_toast, result.appsCleared))
                    }
                    if (result.accessErrors > 0) {
                        append("\n")
                        append(getString(R.string.access_errors_toast, result.accessErrors))
                    }
                }
                
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                
                binding.btnDeepCacheClear.isEnabled = true
                binding.btnDeepCacheClear.text = getString(R.string.deep_cache_clear_btn)
            }
        }
    }

    private fun updatePermissionIndicators() {
        val hasUsage = MemoryUtils.hasUsageStatsPermission(requireContext())
        var hasAlarm = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = requireContext().getSystemService(AlarmManager::class.java)
            hasAlarm = am.canScheduleExactAlarms()
        }

        // Usage Stats Features
        binding.switchKillApps.isEnabled = hasUsage
        binding.switchKillAll.isEnabled = hasUsage
        binding.switchSmartBoost.isEnabled = hasUsage
        
        binding.tvPermKillApps.visibility = if (hasUsage) View.GONE else View.VISIBLE
        binding.tvPermKillAll.visibility = if (hasUsage) View.GONE else View.VISIBLE
        binding.tvPermSmartBoost.visibility = if (hasUsage) View.GONE else View.VISIBLE
        
        binding.btnHelpKillApps.visibility = if (hasUsage) View.GONE else View.VISIBLE
        binding.btnHelpKillAll.visibility = if (hasUsage) View.GONE else View.VISIBLE
        binding.btnHelpSmartBoost.visibility = if (hasUsage) View.GONE else View.VISIBLE

        // Alarm Features
        binding.tvPermAlarm.visibility = if (hasAlarm) View.GONE else View.VISIBLE
        binding.btnHelpAlarm.visibility = if (hasAlarm) View.GONE else View.VISIBLE
        
        // Gray out if disabled
        val alpha = 0.5f
        binding.switchKillApps.alpha = if (hasUsage) 1f else alpha
        binding.switchKillAll.alpha = if (hasUsage) 1f else alpha
        binding.switchSmartBoost.alpha = if (hasUsage) 1f else alpha
    }

    private fun showPermissionHelp(title: String, message: String) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.perm_grant) { _, _ ->
                if (title == getString(R.string.perm_help_usage_title)) {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                        data = Uri.fromParts("package", requireContext().packageName, null)
                    })
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.fromParts("package", requireContext().packageName, null)
                        })
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setupObservers() {
        viewModel.scheduledTimesText.observe(viewLifecycleOwner) { text ->
            binding.tvScheduledTimes.text = if (text.isEmpty()) getString(R.string.no_scheduled) else getString(R.string.scheduled_at, text)
        }
    }

    private fun checkAlarmPermissionAndShowPicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = requireContext().getSystemService(AlarmManager::class.java)
            if (!am.canScheduleExactAlarms()) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.fromParts("package", requireContext().packageName, null)
                    },
                )
                return
            }
        }
        showTimePicker()
    }

    private fun showTimePicker() {
        val cal = Calendar.getInstance()
        TimePickerDialog(
            requireContext(),
            { _, h, m ->
                viewModel.addTime(requireContext(), h, m)
            },
            cal[Calendar.HOUR_OF_DAY],
            cal[Calendar.MINUTE],
            true,
        ).show()
    }

    private fun applyEntranceAnimations() {
        val cards = listOf(
            binding.switchKillApps.parent.parent as View,
            binding.switchAutoBootBoot.parent.parent as View,
            binding.tvScheduledTimes.parent.parent as View
        )
        cards.forEachIndexed { i, v ->
            v.alpha = 0f
            v.translationY = 20f
            v.animate().alpha(1f).translationY(0f).setDuration(400).setStartDelay(i * 80L).start()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}