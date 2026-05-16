package com.boostdroid.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.fragment.app.viewModels
import com.boostdroid.app.databinding.FragmentReviveBinding
import com.boostdroid.app.databinding.ItemReviveAppBinding
import com.google.android.material.bottomsheet.BottomSheetDialog

class ReviveFragment : Fragment() {
    private var _binding: FragmentReviveBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ReviveViewModel by viewModels()
    private lateinit var adapter: ReviveAdapter
    private var allApps = listOf<ReviveAppInfo>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReviveBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PrefsManager.getInstance(requireContext())
        setupRecyclerView()
        setupSearchView()
        setupHeader()
        setupObservers()
        viewModel.loadApps(requireContext())
    }

    private lateinit var prefs: PrefsManager

    private fun setupObservers() {
        viewModel.appList.observe(viewLifecycleOwner) { apps ->
            allApps = apps
            adapter.submitList(apps)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.reviveResult.observe(viewLifecycleOwner) { result ->
            Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun setupHeader() {
        binding.btnSystemSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_SETTINGS))
        }

        if (!prefs.reviveExplanationShown) {
            binding.cardExplanation.visibility = View.VISIBLE
        } else {
            binding.cardExplanation.visibility = View.GONE
        }

        binding.btnCloseExplanation.setOnClickListener {
            binding.cardExplanation.visibility = View.GONE
            prefs.reviveExplanationShown = true
        }
    }

    private fun setupRecyclerView() {
        adapter = ReviveAdapter { app ->
            reviveApp(app)
        }
        binding.rvApps.layoutManager = LinearLayoutManager(requireContext())
        binding.rvApps.adapter = adapter
        
        binding.rvApps.setHasFixedSize(true)
        binding.rvApps.setItemViewCacheSize(0)
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterApps(newText ?: "")
                return true
            }
        })
    }

    private fun filterApps(query: String) {
        val filtered = if (query.isEmpty()) {
            allApps
        } else {
            allApps.filter { it.label.contains(query, ignoreCase = true) }
        }
        adapter.submitList(filtered)
    }

    private fun reviveApp(app: ReviveAppInfo) {
        if (app.state == AppState.STUCK) {
            showStuckBottomSheet(app)
            return
        }
        viewModel.reviveApp(requireContext(), app.packageName)
    }

    private fun showStuckBottomSheet(app: ReviveAppInfo) {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.sheet_stuck_app, null)
        
        view.findViewById<android.widget.TextView>(R.id.tvStuckTitle).text = "${app.label} yanıt vermiyor"
        view.findViewById<android.widget.Button>(R.id.btnGoToSettings).setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", app.packageName, null)
            }
            startActivity(intent)
            dialog.dismiss()
        }
        
        dialog.setContentView(view)
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class ReviveAdapter(private val onRevive: (ReviveAppInfo) -> Unit) :
        ListAdapter<ReviveAppInfo, ReviveAdapter.ViewHolder>(DiffCallback) {

        inner class ViewHolder(private val binding: ItemReviveAppBinding) :
            RecyclerView.ViewHolder(binding.root) {
            fun bind(app: ReviveAppInfo) {
                binding.tvAppName.text = app.label
                binding.tvAppMem.text = if (app.estimatedRam > 0) "${app.estimatedRam} MB" else "Kapalı"
                binding.ivAppIcon.setImageDrawable(app.icon)
                
                updateStateUI(app)
                
                binding.btnRevive.setOnClickListener { onRevive(app) }
            }

            private fun updateStateUI(app: ReviveAppInfo) {
                val ctx = itemView.context
                binding.tvStateBadge.visibility = View.VISIBLE
                
                when (app.state) {
                    AppState.NOT_RUNNING -> {
                        binding.tvStateBadge.visibility = View.GONE
                        binding.btnRevive.text = "Başlat"
                        binding.btnRevive.isEnabled = true
                    }
                    AppState.CACHED -> {
                        binding.tvStateBadge.text = "Bellekte"
                        binding.tvStateBadge.backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.colorBlueDim)
                        binding.tvStateBadge.setTextColor(ContextCompat.getColor(ctx, R.color.colorBlue))
                        binding.btnRevive.text = "Optimize Et"
                        binding.btnRevive.isEnabled = true
                    }
                    AppState.ACTIVE -> {
                        binding.tvStateBadge.text = "Açık"
                        binding.tvStateBadge.backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.colorGreenDim)
                        binding.tvStateBadge.setTextColor(ContextCompat.getColor(ctx, R.color.colorGreen))
                        binding.btnRevive.text = "Çalışıyor"
                        binding.btnRevive.isEnabled = false
                    }
                    AppState.STUCK -> {
                        binding.tvStateBadge.text = "Takılı"
                        binding.tvStateBadge.backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.colorRedDim)
                        binding.tvStateBadge.setTextColor(ContextCompat.getColor(ctx, R.color.colorRed))
                        binding.btnRevive.text = "Yardım"
                        binding.btnRevive.isEnabled = true
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(ItemReviveAppBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        companion object {
            private val DiffCallback = object : DiffUtil.ItemCallback<ReviveAppInfo>() {
                override fun areItemsTheSame(oldItem: ReviveAppInfo, newItem: ReviveAppInfo): Boolean =
                    oldItem.packageName == newItem.packageName

                override fun areContentsTheSame(oldItem: ReviveAppInfo, newItem: ReviveAppInfo): Boolean =
                    oldItem == newItem
            }
        }
    }
}