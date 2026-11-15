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
        val sb = StringBuilder()
        sb.appendLine("GHOSTTRACE — Deleted App Forensics Report")
        sb.appendLine("by @princeyy")
        sb.appendLine("Generated : ${formatDate(System.currentTimeMillis())}")
        sb.appendLine("Log window: last 7 days")
        sb.appendLine("=".repeat(50))
        sb.appendLine()
        sb.appendLine("DELETED / UNINSTALLED APPS SEEN IN USAGE LOG")
        sb.appendLine()
        records.forEach { a ->
            sb.appendLine(a.packageName)
            sb.appendLine("  label      : ${a.appLabel}")
            sb.appendLine("  first seen : ${formatDate(a.firstSeen)}")
            sb.appendLine("  last active: ${formatDate(a.lastSeen)}")
            sb.appendLine("  fg time    : ${formatMs(a.fgMillis)} in ${a.sessions} sessions")
            if (a.isGame) sb.appendLine("  tags       : GAME")
            if (a.isSystem) sb.appendLine("  tags       : SYSTEM")
            sb.appendLine()
        }
        sb.appendLine("=".repeat(50))
        sb.appendLine("Total ghosts : ${records.size}")
        sb.appendLine("Total fg time: ${totalFgFormatted(records)}")
        return sb.toString()
    }
}
