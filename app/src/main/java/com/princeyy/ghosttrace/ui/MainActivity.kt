package com.princeyy.ghosttrace.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.princeyy.ghosttrace.R
import com.princeyy.ghosttrace.databinding.ActivityMainBinding
import com.princeyy.ghosttrace.model.AppRecord
import com.princeyy.ghosttrace.utils.FormatUtils
import com.princeyy.ghosttrace.utils.UsageScanner
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: AppListAdapter
    private var allRecords: List<AppRecord> = emptyList()
    private var bgRecords: List<AppRecord> = emptyList()

    // Filter state
    private var filterUsed = false
    private var sortByFg = false
    private var gamesOnly = false
    private var bgMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupChipListeners()
        setupActionButtons()
        loadData()
    }

    private fun setupRecyclerView() {
        adapter = AppListAdapter()
        binding.rvApps.layoutManager = LinearLayoutManager(this)
        binding.rvApps.adapter = adapter
    }

    private fun setupChipListeners() {
        binding.chipAll.setOnClickListener {
            filterUsed = false; gamesOnly = false; bgMode = false
            updateChipStates(); applyFilters()
        }
        binding.chipUsed.setOnClickListener {
            filterUsed = true; gamesOnly = false; bgMode = false
            updateChipStates(); applyFilters()
        }
        binding.chipGames.setOnClickListener {
            gamesOnly = !gamesOnly; filterUsed = false; bgMode = false
            updateChipStates(); applyFilters()
        }
        binding.chipBg.setOnClickListener {
            bgMode = !bgMode; filterUsed = false; gamesOnly = false
            updateChipStates(); applyFilters()
        }
        binding.chipSort.setOnClickListener {
            sortByFg = !sortByFg
            binding.chipSort.text = if (sortByFg) "Sort: FG time" else "Sort: Last active"
            applyFilters()
        }
    }

    private fun updateChipStates() {
        binding.chipAll.isChecked = !filterUsed && !gamesOnly && !bgMode
        binding.chipBg.isChecked = bgMode
        binding.chipUsed.isChecked = filterUsed
        binding.chipGames.isChecked = gamesOnly
    }

    private fun setupActionButtons() {
        binding.btnCopy.setOnClickListener {
            val txt = FormatUtils.exportTxt(getFilteredList())
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("GhostTrace", txt))
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        binding.btnSave.setOnClickListener {
            val txt = FormatUtils.exportTxt(getFilteredList())
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val fname = "ghosttrace_${sdf.format(Date())}.txt"
            val file = File(getExternalFilesDir(null), fname)
            file.writeText(txt)
            Toast.makeText(this, "Saved: $fname", Toast.LENGTH_SHORT).show()
        }

        binding.btnShare.setOnClickListener {
            val txt = FormatUtils.exportTxt(getFilteredList())
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val file = File(cacheDir, "ghosttrace_${sdf.format(Date())}.txt")
            file.writeText(txt)
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "GhostTrace Report — by @princeyy")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share via"))
        }
    }

    private fun loadData() {
        binding.tvScanStatus.text = "Scanning..."
        Thread {
            val result = UsageScanner.scanAll(this)
            val records = result.ghosts
            allRecords = records
            bgRecords = result.backgroundOnly
            runOnUiThread {
                updateStats(records)
                applyFilters()
                setupChart(records)
            }
        }.start()
    }

    private fun updateStats(records: List<AppRecord>) {
        binding.tvGhostCount.text = records.size.toString()
        binding.tvTotalFg.text = FormatUtils.totalFgFormatted(records)
        val top = records.maxByOrNull { it.sessions }
        binding.tvTopSessions.text = top?.sessions?.toString() ?: "0"
        binding.tvTopPkg.text = top?.packageName?.split(".")?.last() ?: "-"
        val oldestMs = records.minOfOrNull { it.firstSeen } ?: System.currentTimeMillis()
        val oldestDate = SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(oldestMs))
        binding.tvLogWindow.text = "7d / $oldestDate"
        binding.tvScanStatus.text = "${records.size} ghosts, ${bgRecords.size} background-only"
    }

    private fun applyFilters() {
        val list = getFilteredList()
        adapter.submitList(list)
        val noun = if (bgMode) "background app" else "ghost"
        binding.tvListCount.text = "${list.size} $noun${if (list.size != 1) "s" else ""}"
    }

    private fun getFilteredList(): List<AppRecord> {
        var list = if (bgMode) bgRecords else allRecords
        if (filterUsed) list = list.filter { it.fgMillis > 0 }
        if (gamesOnly) list = list.filter { it.isGame }
        list = if (sortByFg) list.sortedByDescending { if (bgMode) it.svcMillis else it.fgMillis }
               else list.sortedByDescending { if (bgMode) it.lastSvc else it.lastSeen }
        return list
    }

    private fun setupChart(records: List<AppRecord>) {
        val top = records.filter { it.fgMillis > 0 }
            .sortedByDescending { it.fgMillis }
            .take(6)
        if (top.isEmpty()) return

        val entries = top.mapIndexed { i, r ->
            BarEntry(i.toFloat(), r.fgMillis / 60000f)
        }
        val labels = top.map { it.packageName.split(".").last().take(8) }

        val colors = listOf(
            0xFF00E5FF.toInt(), 0xFF7C3AED.toInt(), 0xFF00E676.toInt(),
            0xFFFFD740.toInt(), 0xFFFF6E40.toInt(), 0xFFF06292.toInt()
        )

        val dataSet = BarDataSet(entries, "FG time (min)").apply {
            setColors(colors)
            valueTextColor = 0xFF6E7681.toInt()
            valueTextSize = 9f
        }

        val barData = BarData(dataSet).apply { barWidth = 0.6f }
        binding.barChart.apply {
            data = barData
            description.isEnabled = false
            legend.isEnabled = false
            setBackgroundColor(0xFF111419.toInt())
            setDrawGridBackground(false)
            axisLeft.apply {
                textColor = 0xFF6E7681.toInt()
                gridColor = 0xFF1E2530.toInt()
                axisLineColor = 0xFF1E2530.toInt()
            }
            axisRight.isEnabled = false
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = 0xFF6E7681.toInt()
                gridColor = 0xFF1E2530.toInt()
                axisLineColor = 0xFF1E2530.toInt()
                valueFormatter = IndexAxisValueFormatter(labels)
                granularity = 1f
                labelCount = labels.size
            }
            animateY(800)
            invalidate()
        }
    }
}
