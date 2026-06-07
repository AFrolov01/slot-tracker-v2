package com.slottracker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class BlackjackistTrackerService : AccessibilityService() {

    companion object {
        var isRunning = false
        var lastBalance = 0
        var currentBalance = 0
    }

    private lateinit var statsManager: StatsManager
    private val handler = Handler(Looper.getMainLooper())
    private var lastDetectedBalance = 0
    private var stableCount = 0
    private var lastSpinTime = 0L
    private var spinCounter = 0
    private var bonusFlag = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        statsManager = StatsManager(this)
        isRunning = true
        lastBalance = statsManager.startBalance
        currentBalance = lastBalance
        lastDetectedBalance = lastBalance
        spinCounter = statsManager.getSpins().size

        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                         AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            packageNames = arrayOf("com.kamagames.blackjack")
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }

        Log.d("SlotTracker", "Сервис подключен. Начальный баланс: $lastBalance")
        broadcastUpdate()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val rootNode = rootInActiveWindow ?: return
        try {
            val balance = findBalanceInNode(rootNode)
            if (balance != null && balance > 0) {
                processBalance(balance)
            }
        } finally {
            rootNode.recycle()
        }
    }

    private fun findBalanceInNode(node: AccessibilityNodeInfo): Int? {
        val text = node.text?.toString() ?: ""
        val content = node.contentDescription?.toString() ?: ""
        val combined = "$text $content"
        val number = parseBalance(combined)
        if (number != null && number >= 100) {
            return number
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                val result = findBalanceInNode(child)
                if (result != null) return result
            } finally {
                child.recycle()
            }
        }
        return null
    }

    private fun parseBalance(text: String): Int? {
        val clean = text.replace(",", "").replace(" ", "").replace("$", "").replace("₽", "").replace(" chips", "").replace(" balance", "").trim()
        var multiplier = 1
        var numStr = clean

        if (clean.endsWith("K", true) || clean.endsWith("k", true)) {
            multiplier = 1000
            numStr = clean.dropLast(1)
        } else if (clean.endsWith("M", true) || clean.endsWith("m", true)) {
            multiplier = 1000000
            numStr = clean.dropLast(1)
        }

        return try {
            (numStr.toDouble() * multiplier).toInt()
        } catch (e: Exception) {
            null
        }
    }

    private fun processBalance(newBalance: Int) {
        if (newBalance == lastDetectedBalance) {
            stableCount++
        } else {
            stableCount = 0
            lastDetectedBalance = newBalance
            return
        }

        if (stableCount < 2) return
        if (newBalance == currentBalance) return

        val now = System.currentTimeMillis()
        if (now - lastSpinTime < 1500) return
        lastSpinTime = now

        val diff = newBalance - currentBalance
        val betSize = statsManager.betSize

        val spinType: String
        val bet: Int
        val win: Int

        when {
            diff < -betSize * 0.8 -> {
                spinType = "loss"
                bet = betSize
                win = 0
            }
            diff > betSize * 10 -> {
                spinType = "bonus"
                bet = 0
                win = diff
            }
            diff > 0 || diff >= -betSize * 0.5 -> {
                spinType = "win"
                bet = betSize
                win = diff + betSize
            }
            else -> {
                spinType = "loss"
                bet = betSize
                win = 0
            }
        }

        if (bonusFlag && win > 0) {
            spinType = "bonus"
            bet = 0
            bonusFlag = false
        }

        val mult = if (bet > 0) win.toDouble() / bet else win.toDouble() / betSize

        spinCounter++
        val spin = SpinData(
            id = spinCounter,
            timestamp = now,
            type = spinType,
            bet = bet,
            win = win,
            balanceAfter = newBalance,
            multiplier = mult
        )

        statsManager.addSpin(spin)
        currentBalance = newBalance
        isRunning = true

        Log.d("SlotTracker", "Спин #$spinCounter: $spinType | Bet=$bet | Win=$win | Balance=$newBalance | x${String.format("%.2f", mult)}")
        broadcastUpdate()
    }

    private fun broadcastUpdate() {
        val intent = Intent("com.slottracker.UPDATE")
        intent.putExtra("balance", currentBalance)
        sendBroadcast(intent)
    }

    fun setBonusFlag() {
        bonusFlag = true
    }

    override fun onInterrupt() {
        isRunning = false
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
    }
}
