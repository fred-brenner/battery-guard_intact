package com.example.batteryguardlite

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
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
import com.example.batteryguardlite.history.DailyVoltageReading
import com.example.batteryguardlite.ui.BatteryScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            val backupLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("text/csv")
            ) { uri ->
                if (uri != null) backupTo(uri)
            }
            val restoreLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    restoreFrom(uri) { restoredReadings ->
                        historyReadings = restoredReadings
                    }
                }
            }

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
                onRetry = { requestPermissionsOrStart() },
                onBackup = { backupLauncher.launch(backupFileName()) },
                onRestore = {
                    restoreLauncher.launch(
                        arrayOf(
                            "text/csv",
                            "text/comma-separated-values",
                            "application/csv",
                            "text/plain"
                        )
                    )
                }
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

    private fun backupTo(uri: Uri) {
        runCatching {
            val output = requireNotNull(contentResolver.openOutputStream(uri)) {
                "Could not open the selected backup file"
            }
            output.bufferedWriter().use { writer ->
                writer.write(historyStore.exportCsv())
            }
        }.onSuccess {
            showMessage("Backup saved")
        }.onFailure { error ->
            showMessage("Backup failed: ${error.message ?: "unknown error"}")
        }
    }

    private fun restoreFrom(
        uri: Uri,
        onRestored: (List<DailyVoltageReading>) -> Unit
    ) {
        val fileName = displayName(uri)
        if (fileName == null || !fileName.endsWith(".csv", ignoreCase = true)) {
            showMessage("Please select a CSV file")
            return
        }

        runCatching {
            historyStore.importCsv(readCsv(uri))
        }.onSuccess { result ->
            onRestored(result.readings)
            val overlapText = if (result.skippedOverlappingCount > 0) {
                ", ${result.skippedOverlappingCount} overlapping skipped"
            } else {
                ""
            }
            showMessage("Restore complete: ${result.importedCount} imported$overlapText")
        }.onFailure { error ->
            showMessage("Restore failed: ${error.message ?: "invalid CSV"}")
        }
    }

    private fun readCsv(uri: Uri): String {
        val input = requireNotNull(contentResolver.openInputStream(uri)) {
            "Could not open the selected CSV file"
        }
        return input.bufferedReader().use { reader ->
            buildString {
                var lineCount = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    lineCount += 1
                    require(lineCount <= MAX_CSV_LINES) { "CSV file contains too many rows" }
                    appendLine(line)
                }
            }
        }
    }

    private fun displayName(uri: Uri): String? {
        return contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        }
    }

    private fun backupFileName(): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return "battery-guard-backup-$date.csv"
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val MAX_CSV_LINES = 10_000
    }
}
