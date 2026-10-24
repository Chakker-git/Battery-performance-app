package com.chakker.batterycheck.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import kotlin.math.abs

object BatteryReader {

    fun read(ctx: Context): BatterySnapshot {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager

        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) level * 100 / scale
        else bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val plugged = (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0

        val rawCurrent = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentMa = if (rawCurrent == Long.MIN_VALUE || rawCurrent == 0L) null else {
            val a = abs(rawCurrent)
            // Spec says microamps, but some OEMs report milliamps.
            (if (a < 10_000) a else a / 1000).toInt()
        }

        val chargeUah = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val estFull = if (chargeUah > 0 && chargeUah != Long.MIN_VALUE && pct >= 15)
            (chargeUah / 1000.0 / (pct / 100.0)).toInt() else null

        val cycles = if (Build.VERSION.SDK_INT >= 34)
            i?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)?.takeIf { it > 0 } else null

        return BatterySnapshot(
            timestamp = System.currentTimeMillis(),
            levelPct = pct,
            tempC = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f,
            voltageMv = i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0,
            charging = charging,
            plugged = plugged,
            health = i?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
                ?: BatteryManager.BATTERY_HEALTH_UNKNOWN,
            technology = i?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown",
            screenOn = pm.isInteractive,
            powerSave = pm.isPowerSaveMode,
            currentMa = currentMa,
            estFullCapacityMah = estFull,
            designCapacityMah = designCapacity(ctx),
            cycleCount = cycles,
        )
    }

    /** Hidden API; works on many devices, silently null on the rest. */
    private fun designCapacity(ctx: Context): Int? = runCatching {
        val c = Class.forName("com.android.internal.os.PowerProfile")
        val profile = c.getConstructor(Context::class.java).newInstance(ctx)
        (c.getMethod("getBatteryCapacity").invoke(profile) as Double).toInt().takeIf { it > 500 }
    }.getOrNull()

    fun healthLabel(health: Int): String = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheated"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
        BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
        else -> "Unknown"
    }
}
