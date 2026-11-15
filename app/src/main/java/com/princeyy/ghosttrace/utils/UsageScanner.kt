package com.princeyy.ghosttrace.utils

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.princeyy.ghosttrace.model.AppRecord
import java.text.SimpleDateFormat
import java.util.*

object UsageScanner {

    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun scan(context: Context, windowDays: Int = 7): List<AppRecord> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = context.packageManager

        val endTime = System.currentTimeMillis()
        val startTime = endTime - windowDays.toLong() * 24 * 60 * 60 * 1000

        // --- Collect raw events ---
        val firstSeen = mutableMapOf<String, Long>()
        val lastSeen = mutableMapOf<String, Long>()
        val fgStart = mutableMapOf<String, Long>()
        val fgTotal = mutableMapOf<String, Long>()
        val sessionCount = mutableMapOf<String, Int>()
        val dailySessions = mutableMapOf<String, MutableMap<String, Int>>()

        val events = usm.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            val ts = event.timeStamp

            if (!firstSeen.containsKey(pkg)) firstSeen[pkg] = ts
            lastSeen[pkg] = ts

            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    fgStart[pkg] = ts
                    sessionCount[pkg] = (sessionCount[pkg] ?: 0) + 1
                    val day = dayFmt.format(Date(ts))
                    dailySessions.getOrPut(pkg) { mutableMapOf() }
                        .let { it[day] = (it[day] ?: 0) + 1 }
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    fgStart.remove(pkg)?.let { start ->
                        fgTotal[pkg] = (fgTotal[pkg] ?: 0L) + (ts - start)
                    }
                }
            }
        }

        // --- Filter: not currently installed ---
        val deletedPkgs = firstSeen.keys.filter { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                false // still installed
            } catch (e: PackageManager.NameNotFoundException) {
                true // deleted!
            }
        }

        // --- Resolve labels from archived/system sources (best-effort) ---
        return deletedPkgs.map { pkg ->
            val label = resolveLabel(context, pkg)
            val isGame = isLikelyGame(pkg)
            val isSystem = isLikelySystem(pkg)
            AppRecord(
                packageName = pkg,
                appLabel = label,
                firstSeen = firstSeen[pkg] ?: 0L,
                lastSeen = lastSeen[pkg] ?: 0L,
                fgMillis = fgTotal[pkg] ?: 0L,
                sessions = sessionCount[pkg] ?: 0,
                isGame = isGame,
                isSystem = isSystem,
                dailySessions = dailySessions[pkg] ?: emptyMap()
            )
        }.sortedByDescending { it.lastSeen }
    }

    private fun resolveLabel(context: Context, pkg: String): String {
        // Try archived/disabled apps first
        return try {
            val ai = context.packageManager.getApplicationInfo(pkg, 0)
            context.packageManager.getApplicationLabel(ai).toString()
        } catch (e: Exception) {
            // Fallback: derive a readable name from package suffix
            pkg.split(".").lastOrNull()
                ?.replaceFirstChar { it.uppercase() }
                ?: pkg
        }
    }

    private fun isLikelyGame(pkg: String): Boolean {
        val gameKeywords = listOf("game", "ffi", "garena", "pubg", "cod", "clash",
            "arena", "legends", "mobile", "play", "gaming")
        return gameKeywords.any { pkg.lowercase().contains(it) }
    }

    private fun isLikelySystem(pkg: String): Boolean {
        val systemPrefixes = listOf("com.android.", "com.google.", "moe.shizuku",
            "com.miui.", "com.oppo.", "com.realme.", "com.oneplus.")
        return systemPrefixes.any { pkg.startsWith(it) }
    }
}
