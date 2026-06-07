package com.slottracker

data class SpinData(
    val id: Int,
    val timestamp: Long,
    val type: String,
    val bet: Int,
    val win: Int,
    val balanceAfter: Int,
    val multiplier: Double
)
