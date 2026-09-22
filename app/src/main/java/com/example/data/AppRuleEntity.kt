package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing per-app ad blocking rules and metrics.
 * Designed to be ultra-compact and lightweight to minimize RAM overhead.
 */
@Entity(tableName = "app_rules")
data class AppRuleEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isGame: Boolean = false,
    val isAdBlocked: Boolean = true,
    val adsBlockedCount: Int = 0,
    val dataSavedKb: Long = 0L,
    val category: String = "App",
    val lastBlockedTime: Long = System.currentTimeMillis()
)
