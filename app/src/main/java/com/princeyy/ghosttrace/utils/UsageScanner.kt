package com.princeyy.ghosttrace.utils

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.princeyy.ghosttrace.model.AppRecord
import java.text.SimpleDateFormat
import java.util.*

data class ScanResult(
    val ghosts: List<AppRecord>,          // uninstalled, seen in usage log
    val backgroundOnly: List<AppRecord>   // installed, ran a foreground service, never opened in window
)

object UsageScanner {

    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun scan(context: Context, windowDays: Int = 7): List<AppRecord> =
        scanAll(context, windowDays).ghosts

    fun scanAll(context: Context, windowDays: Int = 7): ScanResult {
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
        val svcStart = mutableMapOf<String, Long>()
        val svcTotal = mutableMapOf<String, Long>()
        val svcCount = mutableMapOf<String, Int>()
        val lastSvc = mutableMapOf<String, Long>()

        val events = usm.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            val ts = event.timeStamp

            if (!firstSeen.containsKey(pkg)) firstSeen[pkg] = ts
            lastSeen[pkg] = ts

            // Foreground-service events only exist on Android 10+ (API 29)
            if (Build.VERSION.SDK_INT >= 29) {
                when (event.eventType) {
                    UsageEvents.Event.FOREGROUND_SERVICE_START -> {
                        if (!svcStart.containsKey(pkg)) svcStart[pkg] = ts
                        svcCount[pkg] = (svcCount[pkg] ?: 0) + 1
                        lastSvc[pkg] = ts
                    }
                    UsageEvents.Event.FOREGROUND_SERVICE_STOP -> {
                        svcStart.remove(pkg)?.let { start ->
                            svcTotal[pkg] = (svcTotal[pkg] ?: 0L) + (ts - start)
                        }
                        lastSvc[pkg] = ts
                    }
                }
            }

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

        // Services with a START but no STOP are still running right now
        val runningNow = svcStart.keys.toSet()
        svcStart.forEach { (pkg, start) ->
            svcTotal[pkg] = (svcTotal[pkg] ?: 0L) + (endTime - start)
        }

        fun build(pkg: String) = AppRecord(
            packageName = pkg,
            appLabel = resolveLabel(context, pkg),
            firstSeen = firstSeen[pkg] ?: 0L,
            lastSeen = lastSeen[pkg] ?: 0L,
            fgMillis = fgTotal[pkg] ?: 0L,
            sessions = sessionCount[pkg] ?: 0,
            isGame = isLikelyGame(pkg),
            isSystem = isLikelySystem(pkg),
            dailySessions = dailySessions[pkg] ?: emptyMap(),
            svcStarts = svcCount[pkg] ?: 0,
            svcMillis = svcTotal[pkg] ?: 0L,
            lastSvc = lastSvc[pkg] ?: 0L,
            runningNow = pkg in runningNow
        )

        val ghosts = deletedPkgs.map { build(it) }.sortedByDescending { it.lastSeen }

        // --- Background-only: installed, had a foreground service, never in foreground ---
        val deletedSet = deletedPkgs.toSet()
        val backgroundOnly = svcCount.keys
            .filter { it !in deletedSet && it != context.packageName && (sessionCount[it] ?: 0) == 0 }
            .map { build(it) }
            .sortedByDescending { it.lastSvc }

        return ScanResult(ghosts, backgroundOnly)
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
