package com.boostdroid.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.boostdroid.app.databinding.FragmentDeviceInfoBinding

class DeviceInfoFragment : Fragment() {
    private var _binding: FragmentDeviceInfoBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DeviceInfoViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDeviceInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupObservers()
        viewModel.loadDeviceInfo(requireContext())
        
        applyEntranceAnimations()
    }

    private fun setupObservers() {
        viewModel.deviceInfo.observe(viewLifecycleOwner) { info ->
            binding.tvBrand.text = info["brand"]
            binding.tvModel.text = info["model"]
            binding.tvCpuName.text = info["cpu"]
            binding.tvCores.text = info["cores"]
            binding.tvTotalRam.text = info["ram"]
            binding.tvTotalStorage.text = info["storage"]
            binding.tvAndroid.text = info["android"]
            binding.tvSecurityPatch.text = info["patch"]
            binding.tvResolution.text = info["resolution"]
            binding.tvDpi.text = info["dpi"]
            
            // Note: For full premium feel, I'd add labels for GPU and Refresh Rate in XML
            // For now, I'll appending them to existing views or just keep them in ViewModel
        }
    }

    private fun applyEntranceAnimations() {
        val views = listOf(
            binding.tvBrand.parent.parent as View, // Assuming CardView structure
            binding.tvCpuName.parent.parent as View,
            binding.tvTotalRam.parent.parent as View,
            binding.tvAndroid.parent.parent as View,
            binding.tvResolution.parent.parent as View
        )
        views.forEachIndexed { i, v ->
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