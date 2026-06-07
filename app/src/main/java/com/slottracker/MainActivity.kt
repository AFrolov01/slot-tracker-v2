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
            tvServiceStatus.text = "Сервис активен — отслеживает Blackjackist"
            tvServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.positive))
        } else {
            tvServiceStatus.text = "Сервис не активен — открой настройки Accessibility и включи Slot Tracker"
            tvServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.negative))
        }

        val stats = statsManager.getStats()
        tvTotalSpins.text = "Спинов: ${stats.totalSpins}"
        tvNetProfit.text = "Прибыль: ${if (stats.netProfit >= 0) "+" else ""}${stats.netProfit}"
        tvNetProfit.setTextColor(ContextCompat.getColor(this, if (stats.netProfit >= 0) R.color.positive else R.color.negative))
        tvRoi.text = "ROI: ${if (stats.roi >= 0) "+" else ""}${String.format("%.1f", stats.roi)}%"
        tvRoi.setTextColor(ContextCompat.getColor(this, if (stats.roi >= 0) R.color.positive else R.color.negative))
        tvStrikeRate.text = "Strike Rate: ${String.format("%.1f", stats.strikeRate)}%"
        tvAvgMult.text = "Средний x: ${String.format("%.2f", stats.avgMult)}"
        tvMaxDD.text = "Max DD: ${String.format("%.1f", stats.maxDrawdown)}%"
        tvCurrentBalance.text = "Текущий баланс: ${NumberFormat.getInstance(Locale.getDefault()).format(stats.currentBalance)}"

        tvRecommendation.text = generateRecommendation(stats)

        if (stats.lastSpins.isEmpty()) {
            tvSpinHistory.text = "Нет данных"
        } else {
            val sb = StringBuilder()
            for (sp in stats.lastSpins.reversed()) {
                val emoji = when (sp.type) {
                    "loss" -> "💀"
                    "win" -> "💎"
                    "bonus" -> "🎁"
                    else -> "❓"
                }
                sb.append("${emoji} #${sp.id} | ${sp.type.uppercase()} | Bet:${sp.bet} | Win:${sp.win} | x${String.format("%.1f", sp.multiplier)}
")
            }
            tvSpinHistory.text = sb.toString()
        }
    }

    private fun generateRecommendation(stats: StatsManager.SessionStats): String {
        if (stats.totalSpins == 0) {
            return "Запусти сервис и начни играть — статистика появится автоматически."
        }

        val parts = mutableListOf<String>()
        val roi = stats.roi
        val sr = stats.strikeRate
        val dd = stats.maxDrawdown
        val mult = stats.avgMult

        when {
            roi > 100 -> parts.add("Сессия феноменальная — ROI +${roi.toInt()}%. Это редкая полоса, используй её.")
            roi > 30 -> parts.add("Отличный результат — ROI +${roi.toInt()}%. Баланс растет уверенно.")
            roi > 10 -> parts.add("Хорошая сессия — ROI +${roi.toInt()}%. Тренд твой.")
            roi > 0 -> parts.add("Небольшой плюс — ROI +${roi.toInt()}%. Стабильно.")
            roi > -15 -> parts.add("Просадка ${roi.toInt()}% — в пределах нормы. Это статистический шум.")
            roi > -35 -> parts.add("Глубокая просадка ${roi.toInt()}%. Может быть длинная сухая фаза, а может разворот.")
            else -> parts.add("Критическая просадка ${roi.toInt()}%. Банк на исходе.")
        }

        if (sr < 20 && roi > 20) {
            parts.add("Strike rate всего ${sr.toInt()}% — выигрыши редкие, но огромные. Чистая лотерея.")
        } else if (sr > 40 && roi < 0) {
            parts.add("Strike rate высокий (${sr.toInt()}%), но баланс падает — слот даёт пустые хиты.")
        }

        if (mult > 3.0) {
            parts.add("Средний множитель ${String.format("%.1f", mult)}x высокий — слот сейчас щедрый на крупные выигрыши.")
        } else if (mult < 0.5 && stats.totalSpins > 30) {
            parts.add("Средний x ${String.format("%.1f", mult)}x низкий — большая часть спинов в минусе.")
        }

        if (dd > 25 && roi > 0) {
            parts.add("Была просадка ${dd.toInt()}%, но ты отбился — текущая стратегия выдерживает удары.")
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
