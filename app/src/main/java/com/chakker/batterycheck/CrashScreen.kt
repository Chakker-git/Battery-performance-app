package com.chakker.batterycheck

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

/** Plain-View screen (no Compose) so it still works if the crash came from the Compose UI. */
object CrashScreen {
    fun pending(activity: Activity): String? {
        val f = File(activity.filesDir, App.CRASH_FILE)
        return if (f.exists()) f.readText() else null
    }

    fun show(activity: Activity, report: String) {
        val pad = (16 * activity.resources.displayMetrics.density).toInt()
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad * 2, pad, pad) }

        root.addView(TextView(activity).apply {
            text = "Battery Check crashed last time. Tap Copy and send me this text."
            textSize = 16f
        })
        root.addView(Button(activity).apply {
            text = "Copy"
            setOnClickListener {
                val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("crash", report))
                Toast.makeText(activity, "Copied", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(Button(activity).apply {
            text = "Dismiss and retry"
            setOnClickListener {
                File(activity.filesDir, App.CRASH_FILE).delete()
                activity.recreate()
            }
        })
        root.addView(ScrollView(activity).apply {
            addView(TextView(activity).apply {
                text = report
                textSize = 11f
                setTextIsSelectable(true)
                typeface = android.graphics.Typeface.MONOSPACE
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        activity.setContentView(root)
    }
}
