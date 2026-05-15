package com.boostdroid.app

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.boostdroid.app.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()
    private lateinit var prefs: PrefsManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PrefsManager.getInstance(requireContext())
        setupUI()
        setupObservers()
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkPermissions(requireContext())
    }

    private fun setupUI() {
        // Theme
        binding.switchDarkTheme.isChecked = prefs.darkTheme
        binding.switchDarkTheme.setOnCheckedChangeListener { _, checked ->
            prefs.darkTheme = checked
            AppCompatDelegate.setDefaultNightMode(
                if (checked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
            // Delay recreate to let delegate apply
            binding.root.postDelayed({ activity?.recreate() }, 100)
        }

        // Notify
        binding.switchNotifyBoost.isChecked = prefs.notifyBoost
        binding.switchNotifyBoost.setOnCheckedChangeListener { _, c -> prefs.notifyBoost = c }

        // Anim
        binding.switchBoostAnim.isChecked = prefs.boostAnim
        binding.switchBoostAnim.setOnCheckedChangeListener { _, c -> prefs.boostAnim = c }

        // Language
        updateLanguageButtons(prefs.language)
        binding.btnTurkish.setOnClickListener { changeLanguage("tr") }
        binding.btnEnglish.setOnClickListener { changeLanguage("en") }

        // Fix Buttons
        binding.btnFixUsage.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                data = Uri.fromParts("package", requireContext().packageName, null)
            })
        }
        binding.btnFixNotif.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                })
            }
        }
        binding.btnFixAlarm.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.fromParts("package", requireContext().packageName, null)
                })
            }
        }
    }

    private fun setupObservers() {
        viewModel.permissionsStatus.observe(viewLifecycleOwner) { status ->
            updateStatus(binding.tvStatusUsage, binding.btnFixUsage, status["usage"] ?: false)
            updateStatus(binding.tvStatusNotif, binding.btnFixNotif, status["notifications"] ?: false)
            updateStatus(binding.tvStatusAlarm, binding.btnFixAlarm, status["alarm"] ?: false)
        }
    }

    private fun updateStatus(tv: android.widget.TextView, btn: View, granted: Boolean) {
        if (granted) {
            tv.text = getString(R.string.perm_granted)
            tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.colorSuccess))
            btn.visibility = View.GONE
        } else {
            tv.text = getString(R.string.perm_denied)
            tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.colorError))
            btn.visibility = View.VISIBLE
        }
    }

    private fun changeLanguage(lang: String) {
        prefs.language = lang
        updateLanguageButtons(lang)
        
        val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(lang)
        AppCompatDelegate.setApplicationLocales(appLocale)
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            requireActivity().recreate()
            requireActivity().overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    private fun updateLanguageButtons(activeLang: String) {
        val primaryColor = ContextCompat.getColor(requireContext(), R.color.colorPrimary)
        val cardColor = ContextCompat.getColor(requireContext(), R.color.colorCard)
        
        if (activeLang == "tr") {
            binding.btnTurkish.backgroundTintList = ColorStateList.valueOf(primaryColor)
            binding.btnEnglish.backgroundTintList = ColorStateList.valueOf(cardColor)
        } else {
            binding.btnEnglish.backgroundTintList = ColorStateList.valueOf(primaryColor)
            binding.btnTurkish.backgroundTintList = ColorStateList.valueOf(cardColor)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}