package com.boostdroid.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.boostdroid.app.databinding.FragmentStorageBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StorageFragment : Fragment() {
    private var _binding: FragmentStorageBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStorageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateStorageStats()
        binding.btnClearCache.setOnClickListener { deepClean() }
    }

    private fun updateStorageStats() {
        lifecycleScope.launch(Dispatchers.IO) {
            val stats = StorageAnalyzer.getInternalStorageStats()
            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
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
        }
    }

    private fun deepClean() {
        binding.btnClearCache.isEnabled = false
        binding.btnClearCache.text = "TEMİZLENİYOR..."
        
        lifecycleScope.launch(Dispatchers.IO) {
            val systemFreed = CacheCleaner.clearSystemCaches(requireContext())
            val (appFreed, appCount) = CacheCleaner.clearAppCaches(requireContext())
            val totalFreedMb = (systemFreed + appFreed) / (1024 * 1024)
            
            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                Toast.makeText(requireContext(), "$totalFreedMb MB önbellek temizlendi ($appCount uygulama)", Toast.LENGTH_LONG).show()
                binding.btnClearCache.isEnabled = true
                binding.btnClearCache.text = "DERİN ÖNBELLEK TEMİZLİĞİ"
                updateStorageStats()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}