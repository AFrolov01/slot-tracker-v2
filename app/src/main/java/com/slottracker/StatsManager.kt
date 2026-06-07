package com.slottracker

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class StatsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("slot_tracker", Context.MODE_PRIVATE)
    private val gson = Gson()

    var startBalance: Int
        get() = prefs.getInt("start_balance", 140000)
        set(value) = prefs.edit().putInt("start_balance", value).apply()

    var betSize: Int
        get() = prefs.getInt("bet_size", 500)
        set(value) = prefs.edit().putInt("bet_size", value).apply()

    fun addSpin(spin: SpinData) {
        val spins = getSpins().toMutableList()
        spins.add(spin)
        saveSpins(spins)
    }

    fun getSpins(): List<SpinData> {
        val json = prefs.getString("spins", "[]") ?: "[]"
        val type = object : TypeToken<List<SpinData>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun clearSpins() {
        prefs.edit().remove("spins").apply()
    }

    fun getStats(): SessionStats {
        val spins = getSpins()
        if (spins.isEmpty()) return SessionStats()

        val totalSpins = spins.size
        val totalBet = spins.sumOf { it.bet }
        val totalWin = spins.sumOf { it.win }
        val netProfit = totalWin - totalBet
        val roi = if (totalBet > 0) netProfit.toDouble() / totalBet * 100 else 0.0
        val winningSpins = spins.count { it.win > 0 }
        val strikeRate = winningSpins.toDouble() / totalSpins * 100
        val avgMult = spins.map { it.multiplier }.average()

        var peak = startBalance.toDouble()
        var maxDD = 0.0
        var bal = startBalance.toDouble()
        for (sp in spins) {
            bal = bal - sp.bet + sp.win
            if (bal > peak) peak = bal
            val dd = (peak - bal) / peak * 100
            if (dd > maxDD) maxDD = dd
        }

        return SessionStats(
            totalSpins = totalSpins,
            netProfit = netProfit,
            roi = roi,
            strikeRate = strikeRate,
            avgMult = avgMult,
            maxDrawdown = maxDD,
            currentBalance = bal.toInt(),
            lastSpins = spins.takeLast(20)
        )
    }

    data class SessionStats(
        val totalSpins: Int = 0,
        val netProfit: Int = 0,
        val roi: Double = 0.0,
        val strikeRate: Double = 0.0,
        val avgMult: Double = 0.0,
        val maxDrawdown: Double = 0.0,
        val currentBalance: Int = 0,
        val lastSpins: List<SpinData> = emptyList()
    )

    private fun saveSpins(spins: List<SpinData>) {
        prefs.edit().putString("spins", gson.toJson(spins)).apply()
    }
}
