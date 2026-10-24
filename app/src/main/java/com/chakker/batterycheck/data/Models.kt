package com.chakker.batterycheck.data

/** Point-in-time battery reading. */
data class BatterySnapshot(
    val timestamp: Long,
    val levelPct: Int,
    val tempC: Float,
    val voltageMv: Int,
    val charging: Boolean,
    val plugged: Boolean,
    val health: Int,
    val technology: String,
    val screenOn: Boolean,
    val powerSave: Boolean,
    /** Instantaneous current in mA, absolute value; null if the device doesn't report it. */
    val currentMa: Int?,
    /** Estimated full-charge capacity in mAh, derived from the charge counter. */
    val estFullCapacityMah: Int?,
    /** Rated capacity in mAh when the platform exposes it (not guaranteed). */
    val designCapacityMah: Int?,
    val cycleCount: Int?,
)

/** Compact record persisted for trend analysis. */
data class Sample(
    val ts: Long,
    val level: Int,
    val tempC: Float,
    val charging: Boolean,
    val screenOn: Boolean,
)

data class DeviceSettings(
    val adaptiveBrightness: Boolean,
    val brightnessPct: Int,
    val screenTimeoutSec: Int,
    val refreshRateHz: Int,
    val darkMode: Boolean,
    val locationOn: Boolean,
    val bluetoothOn: Boolean,
    val wifiOn: Boolean,
    val dataSaverOn: Boolean,
)

data class AppUsage(
    val packageName: String,
    val label: String,
    val foregroundMin: Int,
)

data class DrainAnalysis(
    /** Overall %/hour across discharging intervals in the window. */
    val overallPctPerHour: Double?,
    /** %/hour when the screen was off for the whole interval (idle/standby drain). */
    val idlePctPerHour: Double?,
    /** %/hour when the screen was on for the whole interval. */
    val activePctPerHour: Double?,
    /** Hours until empty at the overall rate. */
    val hoursRemaining: Double?,
    val sampleCount: Int,
)

enum class Impact { HIGH, MEDIUM, LOW, INFO }

enum class FixAction { NONE, DISPLAY, BATTERY_SAVER, USAGE_ACCESS, LOCATION, BATTERY_OPTIMIZATION }

data class Recommendation(
    val title: String,
    val detail: String,
    val impact: Impact,
    val action: FixAction = FixAction.NONE,
)
