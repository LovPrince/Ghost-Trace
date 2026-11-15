package com.princeyy.ghosttrace.model

data class AppRecord(
    val packageName: String,
    val appLabel: String,          // resolved from PackageManager, or pkg name fallback
    val firstSeen: Long,           // epoch ms
    val lastSeen: Long,            // epoch ms
    val fgMillis: Long,            // total foreground time
    val sessions: Int,             // foreground session count
    val isGame: Boolean = false,
    val isSystem: Boolean = false,
    val dailySessions: Map<String, Int> = emptyMap()  // "yyyy-MM-dd" -> count
)
