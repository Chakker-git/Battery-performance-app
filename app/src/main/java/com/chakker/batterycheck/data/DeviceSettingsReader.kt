package com.chakker.batterycheck.data

import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.provider.Settings
import kotlin.math.roundToInt

object DeviceSettingsReader {

    fun read(ctx: Context): DeviceSettings {
        val cr = ctx.contentResolver
        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        @Suppress("DEPRECATION")
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        val brightness = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128)
        val mode = Settings.System.getInt(
            cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        val timeoutMs = Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 30_000)
        val night = ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

        return DeviceSettings(
            adaptiveBrightness = mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
            brightnessPct = (brightness / 255f * 100).roundToInt(),
            screenTimeoutSec = timeoutMs / 1000,
            refreshRateHz = (dm.getDisplay(0)?.refreshRate ?: 60f).roundToInt(),
            darkMode = night == Configuration.UI_MODE_NIGHT_YES,
            locationOn = runCatching { lm.isLocationEnabled }.getOrDefault(false),
            bluetoothOn = runCatching { Settings.Global.getInt(cr, "bluetooth_on", 0) == 1 }.getOrDefault(false),
            wifiOn = runCatching { wm.isWifiEnabled }.getOrDefault(false),
            dataSaverOn = runCatching {
                cm.restrictBackgroundStatus != ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED
            }.getOrDefault(false),
        )
    }
}
