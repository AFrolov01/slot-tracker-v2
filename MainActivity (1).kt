package com.slottracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var statsManager: StatsManager
    private lateinit var tvServiceStatus: TextView
    private lateinit var tvTotalSpins: TextView
    private lateinit var tvNetProfit: TextView
    private lateinit var tvRoi: TextView
    private lateinit var tvStrikeRate: TextView
    private lateinit var tvAvgMult: TextView
    private lateinit var tvMaxDD: TextView
    private lateinit var tvCurrentBalance: TextView
    private lateinit var tvRecommendation: TextView
    private lateinit var tvSpinHistory: TextView
    private lateinit var btnOpenAccessibility: Button
    private lateinit var btnSaveSettings: Button
    private lateinit var btnReset: Button

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshStats()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statsManager = StatsManager(this)
        initViews()
        setupListeners()
        refreshStats()
    }

    private fun initViews() {
        tvServiceStatus = findViewById(R.id.tvServiceStatus)
        tvTotalSpins = findViewById(R.id.tvTotalSpins)
        tvNetProfit = findViewById(R.id.tvNetProfit)
        tvRoi = findViewById(R.id.tvRoi)
        tvStrikeRate = findViewById(R.id.tvStrikeRate)
        tvAvgMult = findViewById(R.id.tvAvgMult)
        tvMaxDD = findViewById(R.id.tvMaxDD)
        tvCurrentBalance = findViewById(R.id.tvCurrentBalance)
        tvRecommendation = findViewById(R.id.tvRecommendation)
        tvSpinHistory = findViewById(R.id.tvSpinHistory)
        btnOpenAccessibility = findViewById(R.id.btnOpenAccessibility)
        btnSaveSettings = findViewById(R.id.btnSaveSettings)
        btnReset = findViewById(R.id.btnReset)
    }

    private fun setupListeners() {
        btnOpenAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        btnSaveSettings.setOnClickListener {
            val startBal = findViewById<TextView>(R.id.etStartBalance).text.toString().toIntOrNull() ?: 140000
            val bet = findViewById<TextView>(R.id.etBetSize).text.toString().toIntOrNull() ?: 500
            statsManager.startBalance = startBal
            statsManager.betSize = bet
            refreshStats()
        }
        btnReset.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Сбросить статистику?")
                .setMessage("Все спины будут удалены.")
                .setPositiveButton("Да") { _, _ ->
                    statsManager.clearSpins()
                    refreshStats()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    private fun refreshStats() {
        if (BlackjackistTrackerService.isRunning) {
            tvServiceStatus.text = "Сервис активен"
            tvServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.positive))
        } else {
            tvServiceStatus.text = "Сервис не активен"
            tvServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.negative))
        }

        val stats = statsManager.getStats()
        tvTotalSpins.text = "Спинов: " + stats.totalSpins.toString()
        tvNetProfit.text = "Прибыль: " + stats.netProfit.toString()
        tvRoi.text = "ROI: " + stats.roi.toString()
        tvStrikeRate.text = "SR: " + stats.strikeRate.toString()
        tvAvgMult.text = "Avg: " + stats.avgMult.toString()
        tvMaxDD.text = "DD: " + stats.maxDrawdown.toString()
        tvCurrentBalance.text = "Баланс: " + stats.currentBalance.toString()
        tvRecommendation.text = "Статистика обновлена"
        tvSpinHistory.text = "Спинов в истории: " + stats.lastSpins.size.toString()
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(updateReceiver, IntentFilter("com.slottracker.UPDATE"),
            ContextCompat.RECEIVER_NOT_EXPORTED)
        refreshStats()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(updateReceiver)
    }
}
