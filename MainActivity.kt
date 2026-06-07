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
import java.text.NumberFormat
import java.util.Locale

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
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
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
                .setMessage("Все спины будут удалены. Это необратимо.")
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
        tvTotalSpins.text = "Спинов: " + stats.totalSpins
        tvNetProfit.text = "Прибыль: " + (if (stats.netProfit >= 0) "+" else "") + stats.netProfit
        tvNetProfit.setTextColor(ContextCompat.getColor(this, if (stats.netProfit >= 0) R.color.positive else R.color.negative))
        tvRoi.text = "ROI: " + (if (stats.roi >= 0) "+" else "") + String.format("%.1f", stats.roi) + "%"
        tvRoi.setTextColor(ContextCompat.getColor(this, if (stats.roi >= 0) R.color.positive else R.color.negative))
        tvStrikeRate.text = "Strike Rate: " + String.format("%.1f", stats.strikeRate) + "%"
        tvAvgMult.text = "Средний x: " + String.format("%.2f", stats.avgMult)
        tvMaxDD.text = "Max DD: " + String.format("%.1f", stats.maxDrawdown) + "%"
        tvCurrentBalance.text = "Баланс: " + NumberFormat.getInstance(Locale.getDefault()).format(stats.currentBalance)

        tvRecommendation.text = generateRecommendation(stats)

        if (stats.lastSpins.isEmpty()) {
            tvSpinHistory.text = "Нет данных"
        } else {
            val sb = StringBuilder()
            for (sp in stats.lastSpins.reversed()) {
                val emoji = when (sp.type) {
                    "loss" -> "X"
                    "win" -> "W"
                    "bonus" -> "B"
                    else -> "?"
                }
                sb.append(emoji)
                sb.append(" #")
                sb.append(sp.id)
                sb.append(" ")
                sb.append(sp.type.uppercase())
                sb.append(" Bet:")
                sb.append(sp.bet)
                sb.append(" Win:")
                sb.append(sp.win)
                sb.append(" x")
                sb.append(String.format("%.1f", sp.multiplier))
                sb.append("
")
            }
            tvSpinHistory.text = sb.toString()
        }
    }

    private fun generateRecommendation(stats: StatsManager.SessionStats): String {
        if (stats.totalSpins == 0) {
            return "Запусти сервис и начни играть."
        }

        val parts = mutableListOf<String>()
        val roi = stats.roi
        val sr = stats.strikeRate
        val dd = stats.maxDrawdown
        val mult = stats.avgMult

        when {
            roi > 100 -> parts.add("Сессия феноменальная — ROI +" + roi.toInt() + "%. Это редкая полоса.")
            roi > 30 -> parts.add("Отличный результат — ROI +" + roi.toInt() + "%. Баланс растет.")
            roi > 10 -> parts.add("Хорошая сессия — ROI +" + roi.toInt() + "%.")
            roi > 0 -> parts.add("Небольшой плюс — ROI +" + roi.toInt() + "%.")
            roi > -15 -> parts.add("Просадка " + roi.toInt() + "% — в пределах нормы.")
            roi > -35 -> parts.add("Глубокая просадка " + roi.toInt() + "%.")
            else -> parts.add("Критическая просадка " + roi.toInt() + "%.")
        }

        if (sr < 20 && roi > 20) {
            parts.add("Strike rate " + sr.toInt() + "% — выигрыши редкие, но огромные.")
        } else if (sr > 40 && roi < 0) {
            parts.add("Strike rate высокий, но баланс падает — пустые хиты.")
        }

        if (mult > 3.0) {
            parts.add("Средний множитель высокий — слот щедрый.")
        } else if (mult < 0.5 && stats.totalSpins > 30) {
            parts.add("Средний x низкий — большая часть спинов в минусе.")
        }

        if (dd > 25 && roi > 0) {
            parts.add("Была просадка " + dd.toInt() + "%, но отбился.")
        }

        return parts.joinToString(" | ")
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
