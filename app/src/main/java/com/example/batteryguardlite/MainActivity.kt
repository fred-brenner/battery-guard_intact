package com.example.batteryguardlite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.batteryguardlite.ble.BatteryBleManager
import com.example.batteryguardlite.ui.BatteryScreen

class MainActivity : ComponentActivity() {
    private lateinit var bleManager: BatteryBleManager

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

        setContent {
            val state by bleManager.state.collectAsState()
            BatteryScreen(
                state = state,
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
