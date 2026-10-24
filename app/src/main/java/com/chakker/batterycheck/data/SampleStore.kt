package com.chakker.batterycheck.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Append-only JSON-lines log of battery samples, capped to keep the file small. */
class SampleStore(ctx: Context) {
    private val file = File(ctx.filesDir, "samples.jsonl")

    @Synchronized
    fun append(s: BatterySnapshot) {
        val last = load().lastOrNull()
        // Avoid duplicates when the app is opened repeatedly.
        if (last != null && s.timestamp - last.ts < MIN_GAP_MS) return
        val line = JSONObject()
            .put("ts", s.timestamp).put("lv", s.levelPct).put("t", s.tempC.toDouble())
            .put("c", s.charging).put("s", s.screenOn)
            .toString()
        file.appendText(line + "\n")
        trim()
    }

    @Synchronized
    fun load(): List<Sample> {
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { line ->
            runCatching {
                val j = JSONObject(line)
                Sample(j.getLong("ts"), j.getInt("lv"), j.getDouble("t").toFloat(),
                    j.getBoolean("c"), j.getBoolean("s"))
            }.getOrNull()
        }
    }

    @Synchronized
    fun clear() { file.delete() }

    private fun trim() {
        val lines = file.readLines()
        if (lines.size > MAX_SAMPLES) file.writeText(lines.takeLast(MAX_SAMPLES).joinToString("\n") + "\n")
    }

    private companion object {
        const val MAX_SAMPLES = 3000        // ~30 days at 15-minute spacing
        const val MIN_GAP_MS = 2 * 60_000L
    }
}
