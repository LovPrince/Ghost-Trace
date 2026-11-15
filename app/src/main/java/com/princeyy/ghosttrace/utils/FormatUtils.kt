package com.princeyy.ghosttrace.utils

import java.text.SimpleDateFormat
import java.util.*

object FormatUtils {

    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun formatMs(ms: Long): String {
        val sec = ms / 1000
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return "${h}h ${m.toString().padStart(2, '0')}m ${s.toString().padStart(2, '0')}s"
    }

    fun formatDate(epochMs: Long): String = sdf.format(Date(epochMs))

    fun totalFgFormatted(records: List<com.princeyy.ghosttrace.model.AppRecord>): String {
        val totalMs = records.sumOf { it.fgMillis }
        return formatMs(totalMs)
    }

    fun exportTxt(records: List<com.princeyy.ghosttrace.model.AppRecord>): String {
        val bg = records.filter { it.svcStarts > 0 && it.sessions == 0 }
        val ghosts = records.filterNot { it.svcStarts > 0 && it.sessions == 0 }
        val sb = StringBuilder()
        sb.appendLine("GHOSTTRACE — Deleted App Forensics Report")
        sb.appendLine("by @princeyy")
        sb.appendLine("Generated : ${formatDate(System.currentTimeMillis())}")
        sb.appendLine("Log window: last 7 days")
        sb.appendLine("=".repeat(50))
        sb.appendLine()
        if (ghosts.isNotEmpty() || bg.isEmpty()) {
        sb.appendLine("DELETED / UNINSTALLED APPS SEEN IN USAGE LOG")
        sb.appendLine()
        }
        ghosts.forEach { a ->
            sb.appendLine(a.packageName)
            sb.appendLine("  label      : ${a.appLabel}")
            sb.appendLine("  first seen : ${formatDate(a.firstSeen)}")
            sb.appendLine("  last active: ${formatDate(a.lastSeen)}")
            sb.appendLine("  fg time    : ${formatMs(a.fgMillis)} in ${a.sessions} sessions")
            if (a.isGame) sb.appendLine("  tags       : GAME")
            if (a.isSystem) sb.appendLine("  tags       : SYSTEM")
            if (a.svcStarts > 0) sb.appendLine("  fg service : ${a.svcStarts} starts, ${formatMs(a.svcMillis)}")
            sb.appendLine()
        }
        if (bg.isNotEmpty()) {
            sb.appendLine("BACKGROUND-ONLY APPS (foreground service, never opened)")
            sb.appendLine()
            bg.forEach { a ->
                sb.appendLine(a.packageName)
                sb.appendLine("  label      : ${a.appLabel}")
                sb.appendLine("  last svc   : ${formatDate(a.lastSvc)}")
                sb.appendLine("  svc time   : ${formatMs(a.svcMillis)} in ${a.svcStarts} starts")
                if (a.runningNow) sb.appendLine("  status     : running now")
                sb.appendLine()
            }
        }
        sb.appendLine("=".repeat(50))
        sb.appendLine("Total ghosts : ${ghosts.size}")
        if (bg.isNotEmpty()) sb.appendLine("Background   : ${bg.size}")
        sb.appendLine("Total fg time: ${totalFgFormatted(ghosts)}")
        return sb.toString()
    }
}
