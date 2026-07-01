package com.example.batteryguardlite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.batteryguardlite.ble.BatteryBleManager
import com.example.batteryguardlite.ble.BleState
import com.example.batteryguardlite.history.BatteryHistoryStore
import com.example.batteryguardlite.ui.BatteryScreen

class MainActivity : ComponentActivity() {
    private lateinit var bleManager: BatteryBleManager
    private lateinit var historyStore: BatteryHistoryStore

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val allGranted = results.values.all { it }
            if (allGranted && bleManager.hasRequiredPermissions()) {
                startBleIfReady()
            } else {
                bleManager.markPermissionMissing()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bleManager = BatteryBleManager(applicationContext)
        historyStore = BatteryHistoryStore(applicationContext)

        setContent {
            val state by bleManager.state.collectAsState()
            var historyReadings by remember { mutableStateOf(historyStore.readings()) }

            LaunchedEffect(state) {
                val receiving = state as? BleState.Receiving ?: return@LaunchedEffect
                historyReadings = historyStore.saveReading(
                    millivolts = receiving.millivolts,
                    timestampMillis = receiving.lastUpdateMillis
                )
            }

            BatteryScreen(
                state = state,
                historyReadings = historyReadings,
                onGrantPermissions = { requestPermissionsOrStart() },
                onStart = { requestPermissionsOrStart() },
                onStop = { bleManager.stop() },
                onRetry = { requestPermissionsOrStart() }
            )
        }

        requestPermissionsOrStart()
    }

    override fun onDestroy() {
        bleManager.destroy()
        super.onDestroy()
    }

    private fun requestPermissionsOrStart() {
        if (!bleManager.hasRequiredPermissions()) {
            bleManager.markPermissionMissing()
            permissionLauncher.launch(bleManager.requiredPermissions())
            return
        }

        startBleIfReady()
    }

    private fun startBleIfReady() {
        if (!bleManager.hasBluetoothAdapter()) {
            bleManager.start()
            return
        }

        if (!bleManager.isBluetoothEnabled()) {
            bleManager.markBluetoothOff()
            return
        }

        bleManager.start()
    }
}
