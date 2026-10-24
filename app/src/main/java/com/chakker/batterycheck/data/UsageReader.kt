package com.chakker.batterycheck.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process

object UsageReader {

    fun hasAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION") // unsafeCheckOpNoThrow needs API 29; minSdk is 28
        val mode = ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Foreground time per app over the last [hours] hours, highest first. */
    fun topApps(ctx: Context, hours: Int = 24, limit: Int = 15): List<AppUsage> {
        if (!hasAccess(ctx)) return emptyList()
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - hours * 3_600_000L
        val pm = ctx.packageManager

        return usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, end)
            .filter { it.totalTimeInForeground > 60_000 && it.packageName != ctx.packageName }
            .groupBy { it.packageName }
            .map { (pkg, stats) -> pkg to stats.sumOf { it.totalTimeInForeground } }
            .sortedByDescending { it.second }
            .mapNotNull { (pkg, ms) ->
                val label = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (_: PackageManager.NameNotFoundException) {
                    return@mapNotNull null // not a launchable/visible app (system service etc.)
                }
                AppUsage(pkg, label, (ms / 60_000).toInt())
            }
            .take(limit)
    }
}
