package com.boostdroid.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.boostdroid.app.databinding.FragmentStorageBinding

class StorageFragment : Fragment() {
    private var _binding: FragmentStorageBinding? = null
    private val binding get() = _binding!!
    private val viewModel: StorageViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStorageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupObservers()
        viewModel.updateStorageStats()
        binding.btnClearCache.setOnClickListener { viewModel.deepClean(requireContext()) }
    }

    private fun setupObservers() {
        viewModel.storageStats.observe(viewLifecycleOwner) { stats ->
            val totalGb = stats.totalBytes / (1024 * 1024 * 1024)
            val usedGb = stats.usedBytes / (1024 * 1024 * 1024)
            val pct = if (totalGb > 0) (usedGb * 100 / totalGb).toInt() else 0

            binding.tvStoragePercent.text = "$pct%"
            binding.tvStorageDetails.text = "$usedGb GB / $totalGb GB"
            binding.tvFsType.text = stats.fsType.uppercase()
            binding.tvStorageType.text = if (stats.isEMMC) "eMMC" else "UFS / Diğer"
            binding.tvQueueDepth.text = "I/O Kuyruk Derinliği: ${stats.queueDepth}"

            val healthColor = if (stats.queueDepth > 10) R.color.colorWarning else R.color.colorSuccess
            binding.tvStorageHealth.text = if (stats.queueDepth > 10) "Yoğun" else "İyi"
            binding.tvStorageHealth.setTextColor(ContextCompat.getColor(requireContext(), healthColor))
        }

        viewModel.isCleaning.observe(viewLifecycleOwner) { isCleaning ->
            binding.btnClearCache.isEnabled = !isCleaning
            binding.btnClearCache.text = if (isCleaning) "TEMİZLENİYOR..." else "DERİN ÖNBELLEK TEMİZLİĞİ"
        }

        viewModel.cleanResult.observe(viewLifecycleOwner) { result ->
            Toast.makeText(requireContext(), result, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
