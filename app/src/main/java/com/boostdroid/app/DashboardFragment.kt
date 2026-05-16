package com.boostdroid.app

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.boostdroid.app.databinding.FragmentDashboardBinding
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.*

class DashboardFragment : Fragment() {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()
    private lateinit var prefs: PrefsManager
    private var boostPulseAnimator: ObjectAnimator? = null
    private var liveDotAnimator: ObjectAnimator? = null
    private val topAppsAdapter = TopAppsAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun startLiveBadgeTimer() {
        viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                viewModel.lastUpdateTime.value?.let { updateLiveBadge(it) }
                delay(1000)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PrefsManager.getInstance(requireContext())
        setupRecyclerView()
        setupObservers()
        setupAnimations()
        startLiveBadgeTimer()
        binding.btnBoost.setOnClickListener { startBoost() }
        viewModel.startStatsUpdates(requireContext())
        
        applyEntranceAnimations()
    }

    private fun setupRecyclerView() {
        binding.rvTopApps.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = topAppsAdapter
            itemAnimator = null // CRITICAL: disable default item animator 
        }
    }

    private fun setupAnimations() {
        liveDotAnimator = ObjectAnimator.ofFloat(binding.vLiveDot, "alpha", 1f, 0f).apply {
            duration = 800
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun setupObservers() {
        viewModel.ramUsage.observe(viewLifecycleOwner) { info ->
            binding.tvRamBadge.text = getString(R.string.ram_usage_pct, info.pct)
            binding.tvRamUsed.text = getString(R.string.app_mem_text, info.usedMb)
            binding.tvRamTotal.text = getString(R.string.ram_total, info.totalMb)
            binding.tvRamDetails.text = getString(R.string.ram_details, info.availMb, info.cachedMb)
            
            animateProgress(binding.progressRam, info.pct)
            pulseBadge(binding.tvRamBadge)
        }

        viewModel.ioStats.observe(viewLifecycleOwner) { stats ->
            if (stats.isMeasuring) {
                binding.tvIoBadge.text = "..."
                return@observe
            }
            
            val isBusy = stats.iosInProgress > 0
            binding.tvIoBadge.text = if (isBusy) getString(R.string.io_active) else getString(R.string.io_idle)
            binding.tvIoBadge.setTextColor(
                ContextCompat.getColor(requireContext(), if (isBusy) R.color.colorGreen else R.color.colorTextSecondary),
            )
            binding.tvIoBadge.backgroundTintList = ContextCompat.getColorStateList(
                requireContext(), if (isBusy) R.color.colorGreenDim else R.color.colorSurfaceHigh,
            )
            
            binding.tvIoReadValue.text = viewModel.formatIoSpeed(stats.readKbps)
            binding.tvIoWriteValue.text = viewModel.formatIoSpeed(stats.writeKbps)

            // Estimate progress for I/O
            animateProgress(binding.progressIoRead, (stats.readKbps / 500).toInt().coerceIn(0, 100))
            animateProgress(binding.progressIoWrite, (stats.writeKbps / 500).toInt().coerceIn(0, 100))
        }

        viewModel.networkSpeed.observe(viewLifecycleOwner) { (down, up) ->
            binding.tvNetDown.text = getString(R.string.net_down, down)
            binding.tvNetUp.text = getString(R.string.net_up, up)
            binding.tvNetStatus.text = getString(R.string.net_connected)
            binding.vNetStatusDot.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.colorGreen)
        }

        viewModel.thermalStats.observe(viewLifecycleOwner) { stats ->
            val tempText = if (stats.cpuTemp > 0) "${stats.cpuTemp}°C" else ""
            binding.tvCpuDetails.text = if (tempText.isNotEmpty()) "$tempText  ·  Çekirdek: 8" else "Çekirdek: 8"
        }

        viewModel.storageUsage.observe(viewLifecycleOwner) { (pct, _) ->
            binding.tvStorageBadge.text = "%$pct"
            // Split storage text if possible, or just use defaults for now
            // Assuming storage text is "Used / Total"
            animateProgress(binding.progressStorage, pct)
        }
        
        // Storage details from ViewModel might need formatting
        viewModel.storageUsage.observe(viewLifecycleOwner) { (_, text) ->
            // text usually looks like "36.2 GB / 103.1 GB"
            val parts = text.split("/")
            if (parts.size == 2) {
                binding.tvStorageUsed.text = getString(R.string.storage_used_text, parts[0].trim())
                binding.tvStorageTotal.text = getString(R.string.storage_total_text, parts[1].trim())
            }
        }

        viewModel.cpuUsage.observe(viewLifecycleOwner) { pct ->
            binding.tvCpuBadge.text = "%$pct"
            animateProgress(binding.progressCpu, pct)
            pulseBadge(binding.tvCpuBadge)
        }

        viewModel.cpuModel.observe(viewLifecycleOwner) { model ->
            binding.tvCpuModel.text = model
        }

        viewModel.topApps.observe(viewLifecycleOwner) { apps ->
            if (apps.isNotEmpty()) {
                topAppsAdapter.updateList(apps)
                binding.tvLiveStatus.text = getString(R.string.live_just_now)
            } else {
                binding.tvLiveStatus.text = getString(R.string.live_no_apps)
            }
        }

        viewModel.batteryInfo.observe(viewLifecycleOwner) { (pct, _, hours) ->
            binding.tvBatteryPercent.text = "$pct%"
            animateProgress(binding.progressBattery, pct)
            binding.tvBatteryRemaining.text = if (hours.isNotEmpty()) getString(R.string.battery_remaining, hours) else "--"
            binding.tvBatteryMah.text = getString(R.string.battery_health)
        }

        viewModel.boostResult.observe(viewLifecycleOwner) { result ->
            val resultMessage = when {
                result.freedMb > 0 && result.killedCount > 0 -> 
                    getString(R.string.boost_ram_freed, result.freedMb) + " · " + getString(R.string.boost_apps_cleaned, result.killedCount)
                result.freedMb > 0 -> getString(R.string.boost_ram_freed, result.freedMb)
                result.killedCount > 0 -> getString(R.string.boost_apps_cleaned, result.killedCount) + " · " + getString(R.string.boost_ram_returned)
                else -> getString(R.string.boost_already_optimized)
            }

            binding.btnBoost.text = getString(R.string.btn_boost_done)
            Toast.makeText(requireContext(), resultMessage, Toast.LENGTH_LONG).show()
            showBoostResultCard(result)
            stopBoostAnimation()

            viewLifecycleOwner.lifecycleScope.launch {
                delay(2500)
                _binding?.let { binding ->
                    binding.btnBoost.text = getString(R.string.btn_boost_idle)
                    binding.btnBoost.isEnabled = true
                }
            }
        }
    }

    private fun showBoostResultCard(result: BoostResult) {
        binding.cardBoostResult.visibility = View.VISIBLE
        binding.cardBoostResult.translationY = -20f
        binding.cardBoostResult.alpha = 0f
        binding.cardBoostResult.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(300)
            .start()

        binding.tvBoostResultDetail.text = getString(R.string.boost_result_detail, result.killedCount)
        
        binding.cardBoostResult.postDelayed({
            binding.cardBoostResult.animate()
                .translationY(-20f)
                .alpha(0f)
                .setDuration(300)
                .withEndAction { binding.cardBoostResult.visibility = View.GONE }
                .start()
        }, 4000)
    }

    private fun animateProgress(pb: ProgressBar, value: Int) {
        ObjectAnimator.ofInt(pb, "progress", pb.progress, value).apply {
            duration = 500
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private fun pulseBadge(view: View) {
        view.animate().scaleX(1.15f).scaleY(1.15f).setDuration(100)
            .withEndAction { view.animate().scaleX(1f).scaleY(1f).duration = 100 }
    }

    private fun startBoost() {
        binding.btnBoost.isEnabled = false
        binding.btnBoost.text = getString(R.string.boosting)
        startBoostAnimation()
        vibrate()
        viewModel.performBoost(requireContext())
    }

    private fun startBoostAnimation() {
        boostPulseAnimator = ObjectAnimator.ofFloat(binding.btnBoost, "alpha", 1f, 0.75f).apply {
            duration = 600
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun updateLiveBadge(lastUpdate: Long) {
        val diff = System.currentTimeMillis() - lastUpdate
        val seconds = diff / 1000
        
        when {
            seconds < 8 -> {
                binding.layoutLiveBadge.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.colorRedDim)
                binding.tvLiveStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.colorRed))
                binding.vLiveDot.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.colorRed)
                binding.tvLiveStatus.text = when {
                    seconds < 5 -> getString(R.string.live)
                    else -> getString(R.string.live_ago, seconds)
                }
            }
            else -> {
                binding.layoutLiveBadge.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.colorAmberDim)
                binding.tvLiveStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.colorAmber))
                binding.vLiveDot.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.colorAmber)
                binding.tvLiveStatus.text = getString(R.string.live_updating)
            }
        }
    }

    private fun stopBoostAnimation() {
        boostPulseAnimator?.cancel()
        binding.btnBoost.alpha = 1f
    }

    private fun applyEntranceAnimations() {
        val views = listOf(
            binding.btnBoost.parent as View,
            binding.cardRam,
            binding.cardCpu,
            binding.cardStorage,
            binding.cardBattery.parent as View,
            binding.cardIo,
            binding.cardTopApps
        )
        
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 16f * resources.displayMetrics.density
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .setStartDelay(index * 60L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun vibrate() {
        val ms = 60L
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {}
    }

    override fun onDestroyView() {
        super.onDestroyView()
        boostPulseAnimator?.cancel()
        liveDotAnimator?.cancel()
        _binding = null
    }
}