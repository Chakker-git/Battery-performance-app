package com.chakker.batterycheck

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chakker.batterycheck.data.Analyzer
import com.chakker.batterycheck.data.AppUsage
import com.chakker.batterycheck.data.BatteryReader
import com.chakker.batterycheck.data.BatterySnapshot
import com.chakker.batterycheck.data.DeviceSettingsReader
import com.chakker.batterycheck.data.DrainAnalysis
import com.chakker.batterycheck.data.Recommendation
import com.chakker.batterycheck.data.Sample
import com.chakker.batterycheck.data.SampleStore
import com.chakker.batterycheck.data.UsageReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val battery: BatterySnapshot? = null,
    val samples: List<Sample> = emptyList(),
    val drain: DrainAnalysis? = null,
    val usage: List<AppUsage> = emptyList(),
    val usageGranted: Boolean = false,
    val recommendations: List<Recommendation> = emptyList(),
    val score: Int = 0,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SampleStore(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun refresh() {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            _state.value = withContext(Dispatchers.IO) {
                val battery = BatteryReader.read(ctx)
                store.append(battery)
                val samples = store.load()
                val settings = DeviceSettingsReader.read(ctx)
                val granted = UsageReader.hasAccess(ctx)
                val usage = UsageReader.topApps(ctx)
                val drain = Analyzer.drain(samples)
                val recs = Analyzer.recommend(battery, settings, drain, usage, granted)
                UiState(battery, samples, drain, usage, granted, recs, Analyzer.score(recs))
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) { store.clear() }
        refresh()
    }
}
