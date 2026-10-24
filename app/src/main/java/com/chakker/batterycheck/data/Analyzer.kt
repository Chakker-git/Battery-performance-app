package com.chakker.batterycheck.data

import android.os.BatteryManager

object Analyzer {
    private const val MAX_GAP_MS = 3 * 3_600_000L

    fun drain(samples: List<Sample>, windowHours: Int = 24): DrainAnalysis {
        val cutoff = System.currentTimeMillis() - windowHours * 3_600_000L
        val recent = samples.filter { it.ts >= cutoff }

        var allDrop = 0.0; var allMs = 0L
        var idleDrop = 0.0; var idleMs = 0L
        var actDrop = 0.0; var actMs = 0L

        for ((a, b) in recent.zipWithNext()) {
            val dt = b.ts - a.ts
            if (a.charging || b.charging || dt <= 0 || dt > MAX_GAP_MS) continue
            val drop = (a.level - b.level).coerceAtLeast(0).toDouble()
            allDrop += drop; allMs += dt
            if (!a.screenOn && !b.screenOn) { idleDrop += drop; idleMs += dt }
            if (a.screenOn && b.screenOn) { actDrop += drop; actMs += dt }
        }

        fun rate(drop: Double, ms: Long, minMs: Long): Double? =
            if (ms >= minMs) drop / (ms / 3_600_000.0) else null

        val overall = rate(allDrop, allMs, 30 * 60_000L)
        val level = recent.lastOrNull()?.level
        return DrainAnalysis(
            overallPctPerHour = overall,
            idlePctPerHour = rate(idleDrop, idleMs, 60 * 60_000L),
            activePctPerHour = rate(actDrop, actMs, 15 * 60_000L),
            hoursRemaining = if (overall != null && overall > 0.05 && level != null) level / overall else null,
            sampleCount = recent.size,
        )
    }

    fun recommend(
        b: BatterySnapshot,
        s: DeviceSettings,
        d: DrainAnalysis,
        usage: List<AppUsage>,
        usageGranted: Boolean,
    ): List<Recommendation> {
        val r = mutableListOf<Recommendation>()

        // --- Battery condition ---
        if (b.health != BatteryManager.BATTERY_HEALTH_GOOD && b.health != BatteryManager.BATTERY_HEALTH_UNKNOWN) {
            r += Recommendation("Battery reports a problem: ${BatteryReader.healthLabel(b.health)}",
                "The system flags this cell as unhealthy. Stop using fast chargers and consider a battery replacement or service check.",
                Impact.HIGH)
        }
        if (b.tempC >= 40f) {
            r += Recommendation("Battery is hot (${"%.1f".format(b.tempC)}°C)",
                "Sustained heat above ~35–40°C permanently shortens battery life. Remove the case while charging, avoid direct sun and heavy gaming while plugged in.",
                Impact.HIGH)
        }
        val full = b.estFullCapacityMah; val design = b.designCapacityMah
        if (full != null && design != null) {
            val pct = full * 100 / design
            if (pct < 80) r += Recommendation("Battery capacity is about $pct% of new",
                "Estimated $full mAh vs $design mAh rated. Below ~80% most people notice the shorter runtime; a replacement will help more than any setting.",
                Impact.HIGH)
        }
        val c = b.cycleCount
        if (c != null) {
            if (c >= 800) r += Recommendation("High charge-cycle count ($c)",
                "Lithium cells typically fall to ~80% capacity around 500–1000 cycles. Expect reduced runtime.", Impact.MEDIUM)
        }
        if (b.plugged && b.levelPct >= 100) {
            r += Recommendation("Avoid sitting at 100% on the charger",
                "Holding a full charge stresses the cell. Unplug near 80–90% when you can, or enable your phone's charge limit / adaptive charging.",
                Impact.LOW)
        }
        if (b.levelPct <= 20 && !b.plugged && !b.powerSave) {
            r += Recommendation("Low battery and Battery Saver is off",
                "Turn Battery Saver on to throttle background work and extend what's left.", Impact.HIGH, FixAction.BATTERY_SAVER)
        }

        // --- Measured drain ---
        val idle = d.idlePctPerHour
        if (idle != null) {
            if (idle > 1.5) r += Recommendation("Heavy standby drain (${"%.1f".format(idle)}%/h with screen off)",
                "A healthy phone loses under ~1%/h idle. Something is holding the CPU or radios awake. Check apps with background activity, restrict the worst ones, and review location and sync settings.",
                Impact.HIGH, FixAction.BATTERY_OPTIMIZATION)
            else if (idle > 0.8) r += Recommendation("Elevated standby drain (${"%.1f".format(idle)}%/h)",
                "Slightly above normal. Restrict background activity for apps you rarely use.",
                Impact.MEDIUM, FixAction.BATTERY_OPTIMIZATION)
        }
        val act = d.activePctPerHour
        if (act != null) {
            if (act > 20) r += Recommendation("Very high on-screen drain (${"%.0f".format(act)}%/h)",
                "Typical use is 8–15%/h. Check brightness, refresh rate and which app is on screen (games and video calls are the usual cause).",
                Impact.HIGH, FixAction.DISPLAY)
        }

        // --- Display ---
        if (!s.adaptiveBrightness && s.brightnessPct > 60) {
            r += Recommendation("Brightness is fixed at ${s.brightnessPct}%",
                "The display is the largest power draw. Turn on adaptive brightness or lower the level.", Impact.MEDIUM, FixAction.DISPLAY)
        }
        if (s.refreshRateHz >= 90) {
            r += Recommendation("Display running at ${s.refreshRateHz} Hz",
                "High refresh rates cost roughly 5–10% extra battery. Switch to Standard (60 Hz) or an adaptive mode if available.", Impact.MEDIUM, FixAction.DISPLAY)
        }
        if (s.screenTimeoutSec > 60) {
            r += Recommendation("Screen timeout is ${s.screenTimeoutSec}s",
                "Every idle second with the screen lit is wasted energy. 30 seconds is plenty.", Impact.LOW, FixAction.DISPLAY)
        }
        if (!s.darkMode) {
            r += Recommendation("Dark mode is off",
                "On OLED screens dark themes save a meaningful amount of power. No benefit on LCD.", Impact.LOW, FixAction.DISPLAY)
        }

        // --- Radios / background ---
        if (s.locationOn) r += Recommendation("Location services are on",
            "Constant GPS/Wi-Fi scanning drains battery. Set most apps to \"While using\" and turn off precise location where it isn't needed.",
            Impact.LOW, FixAction.LOCATION)
        if (s.bluetoothOn) r += Recommendation("Bluetooth is on",
            "Worth turning off when you don't have a device connected.", Impact.LOW)
        if (!s.dataSaverOn && (d.idlePctPerHour ?: 0.0) > 0.8) r += Recommendation("Data Saver is off",
            "With high standby drain, Data Saver blocks background data for apps you aren't using.", Impact.LOW)

        // --- App usage ---
        if (!usageGranted) {
            r += Recommendation("Grant usage access to see which apps use your phone most",
                "Android doesn't let third-party apps read per-app battery stats, but screen time is a strong proxy. Grant access under Settings > Usage access.",
                Impact.INFO, FixAction.USAGE_ACCESS)
        } else {
            val top = usage.firstOrNull()
            if (top != null) {
                if (top.foregroundMin >= 180) r += Recommendation("${top.label}: ${top.foregroundMin / 60}h ${top.foregroundMin % 60}m on screen in 24h",
                    "Your heaviest app. If its drain looks excessive, lower its in-app quality settings (video quality, auto-play, background refresh).",
                    Impact.MEDIUM)
            }
        }

        if (d.sampleCount < 8) r += Recommendation("Still collecting data",
            "Drain figures need several hours of samples. Leave the app installed; it logs a reading about every 15 minutes in the background.",
            Impact.INFO)

        return r.sortedBy { it.impact.ordinal }
    }

    /** 0-100 headline score: starts at 100, loses points per finding weighted by impact. */
    fun score(recs: List<Recommendation>): Int {
        val penalty = recs.fold(0) { acc, r ->
            acc + when (r.impact) { Impact.HIGH -> 15; Impact.MEDIUM -> 7; Impact.LOW -> 3; Impact.INFO -> 0 }
        }
        return (100 - penalty).coerceIn(0, 100)
    }
}
