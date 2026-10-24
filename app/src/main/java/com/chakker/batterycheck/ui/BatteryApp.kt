package com.chakker.batterycheck.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chakker.batterycheck.MainViewModel
import com.chakker.batterycheck.UiState
import com.chakker.batterycheck.data.AppUsage
import com.chakker.batterycheck.data.BatteryReader
import com.chakker.batterycheck.data.FixAction
import com.chakker.batterycheck.data.Impact
import com.chakker.batterycheck.data.Recommendation
import com.chakker.batterycheck.data.Sample

@Composable
fun BatteryApp(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        var tab by remember { mutableIntStateOf(0) }
        Scaffold(
            bottomBar = {
                NavigationBar {
                    listOf("Overview" to Icons.Default.Home, "Apps" to Icons.Default.List, "Tips" to Icons.Default.Build)
                        .forEachIndexed { i, (label, icon) ->
                            NavigationBarItem(
                                selected = tab == i, onClick = { tab = i },
                                icon = { Icon(icon, contentDescription = label) }, label = { Text(label) },
                            )
                        }
                }
            },
        ) { pad ->
            Column(Modifier.padding(pad).statusBarsPadding().fillMaxSize()) {
                when (tab) {
                    0 -> OverviewTab(state, onClear = vm::clearHistory)
                    1 -> AppsTab(state)
                    else -> TipsTab(state)
                }
            }
        }
    }
}

private val pagePadding = PaddingValues(16.dp)

@Composable
private fun OverviewTab(s: UiState, onClear: () -> Unit) {
    val b = s.battery ?: return
    LazyColumn(contentPadding = pagePadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Battery score", style = MaterialTheme.typography.labelLarge)
                    Text("${s.score}/100", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = scoreColor(s.score))
                    Text(
                        "${b.levelPct}%" + if (b.charging) " · charging" else "",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    s.drain?.hoursRemaining?.let {
                        Text("≈ ${"%.1f".format(it)} h left at your recent rate", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        item { SectionTitle("Last 24 hours") }
        item { LevelChart(s.samples) }
        item { SectionTitle("Drain") }
        item {
            val d = s.drain
            StatCard(
                listOf(
                    "Overall" to fmtRate(d?.overallPctPerHour),
                    "Screen off (standby)" to fmtRate(d?.idlePctPerHour),
                    "Screen on" to fmtRate(d?.activePctPerHour),
                    "Samples logged" to "${d?.sampleCount ?: 0}",
                )
            )
        }
        item { SectionTitle("Battery condition") }
        item {
            StatCard(
                listOfNotNull(
                    "Health" to BatteryReader.healthLabel(b.health),
                    "Temperature" to "%.1f °C".format(b.tempC),
                    "Voltage" to "%.2f V".format(b.voltageMv / 1000f),
                    b.currentMa?.let { "Current draw" to "$it mA" },
                    b.estFullCapacityMah?.let { "Est. full capacity" to "$it mAh" },
                    b.designCapacityMah?.let { "Rated capacity" to "$it mAh" },
                    b.cycleCount?.let { "Charge cycles" to "$it" },
                    "Chemistry" to b.technology,
                    "Battery Saver" to if (b.powerSave) "On" else "Off",
                )
            )
        }
        item { OutlinedButton(onClick = onClear) { Text("Clear history") } }
    }
}

@Composable
private fun AppsTab(s: UiState) {
    val ctx = LocalContext.current
    LazyColumn(contentPadding = pagePadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Screen time, last 24 h", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Android hides per-app battery numbers from third-party apps, so this uses screen time as a proxy. Tap an app to open its settings and restrict background use.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (!s.usageGranted) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Usage access needed")
                        TextButton(onClick = { launch(ctx, FixAction.USAGE_ACCESS) }) { Text("Open settings") }
                    }
                }
            }
        } else if (s.usage.isEmpty()) {
            item { Text("No app usage recorded yet.") }
        } else {
            items(s.usage, key = { it.packageName }) { AppRow(it, s.usage.first().foregroundMin) }
        }
    }
}

@Composable
private fun AppRow(app: AppUsage, max: Int) {
    val ctx = LocalContext.current
    Card(Modifier.fillMaxWidth().clickable {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(app.label, fontWeight = FontWeight.Medium)
                Text("${app.foregroundMin / 60}h ${app.foregroundMin % 60}m")
            }
            Spacer(Modifier.height(6.dp))
            val frac = app.foregroundMin.toFloat() / max.coerceAtLeast(1)
            val track = MaterialTheme.colorScheme.surfaceVariant
            val fill = MaterialTheme.colorScheme.primary
            Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                drawLine(track, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height, StrokeCap.Round)
                drawLine(fill, Offset(0f, size.height / 2), Offset(size.width * frac, size.height / 2), size.height, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun TipsTab(s: UiState) {
    val ctx = LocalContext.current
    LazyColumn(contentPadding = pagePadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Ways to improve battery life", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (s.recommendations.isEmpty()) item { Text("Nothing to fix. Nice.") }
        items(s.recommendations) { RecCard(it) { launch(ctx, it.action) } }
    }
}

@Composable
private fun RecCard(r: Recommendation, onFix: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = impactColor(r.impact).copy(alpha = 0.14f))) {
        Column(Modifier.padding(14.dp)) {
            Text(r.impact.name, color = impactColor(r.impact), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(r.title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(r.detail, style = MaterialTheme.typography.bodyMedium)
            if (r.action != FixAction.NONE) TextButton(onClick = onFix) { Text("Open settings") }
        }
    }
}

@Composable
private fun StatCard(rows: List<Pair<String, String>>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(k, style = MaterialTheme.typography.bodyMedium)
                    Text(v, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun LevelChart(samples: List<Sample>) {
    val cutoff = System.currentTimeMillis() - 24 * 3_600_000L
    val pts = samples.filter { it.ts >= cutoff }
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val charge = Color(0xFF2E9E5B)
    Card(Modifier.fillMaxWidth()) {
        if (pts.size < 2) {
            Text("Collecting data… the graph appears after a couple of samples.", Modifier.padding(16.dp))
            return@Card
        }
        Canvas(Modifier.fillMaxWidth().height(140.dp).padding(12.dp)) {
            listOf(0f, .5f, 1f).forEach { y ->
                drawLine(grid, Offset(0f, size.height * y), Offset(size.width, size.height * y), 1f)
            }
            val t0 = pts.first().ts.toFloat()
            val span = (pts.last().ts - pts.first().ts).coerceAtLeast(1).toFloat()
            fun x(s: Sample) = (s.ts - t0) / span * size.width
            fun y(s: Sample) = size.height * (1f - s.level / 100f)
            pts.zipWithNext().forEach { (a, b) ->
                drawLine(if (b.charging) charge else line, Offset(x(a), y(a)), Offset(x(b), y(b)), 4f, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun SectionTitle(t: String) =
    Text(t, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

private fun fmtRate(v: Double?) = v?.let { "%.1f %%/h".format(it) } ?: "n/a yet"

private fun scoreColor(score: Int) = when {
    score >= 80 -> Color(0xFF2E9E5B)
    score >= 55 -> Color(0xFFD99A00)
    else -> Color(0xFFD64545)
}

private fun impactColor(i: Impact) = when (i) {
    Impact.HIGH -> Color(0xFFD64545)
    Impact.MEDIUM -> Color(0xFFD99A00)
    Impact.LOW -> Color(0xFF3B82C4)
    Impact.INFO -> Color(0xFF7A7A7A)
}

private fun launch(ctx: Context, action: FixAction) {
    val intent = when (action) {
        FixAction.DISPLAY -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
        FixAction.BATTERY_SAVER -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
        FixAction.USAGE_ACCESS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        FixAction.LOCATION -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        FixAction.BATTERY_OPTIMIZATION -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        FixAction.NONE -> return
    }
    runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
