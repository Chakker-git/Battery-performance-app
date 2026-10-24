package com.chakker.batterycheck

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.lifecycleScope
import com.chakker.batterycheck.data.SampleWorker
import com.chakker.batterycheck.ui.BatteryApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashScreen.pending(this)?.let { CrashScreen.show(this, it); return }
        SampleWorker.schedule(applicationContext)
        // Re-read every time we return from Settings (e.g. after granting usage access).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) { vm.refresh() }
        }
        setContent { BatteryApp(vm) }
    }
}
