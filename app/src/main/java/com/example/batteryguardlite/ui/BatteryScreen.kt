package com.example.batteryguardlite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.batteryguardlite.ble.BatteryType
import com.example.batteryguardlite.ble.BleState
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BatteryScreen(
    state: BleState,
    onGrantPermissions: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val receiving = state as? BleState.Receiving
    val voltageText = receiving?.let { String.format(Locale.US, "%.2f V", it.voltage) } ?: "--.-- V"

    MaterialTheme {
        Surface(modifier = modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Battery Guard Lite",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(40.dp))
                    Text(
                        text = voltageText,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = receiving?.let { "${it.millivolts} mV" } ?: "No voltage received",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = statusText(state),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    DebugSection(state)
                }

                ActionButtons(
                    state = state,
                    onGrantPermissions = onGrantPermissions,
                    onStart = onStart,
                    onStop = onStop,
                    onRetry = onRetry
                )
            }
        }
    }
}

@Composable
private fun DebugSection(state: BleState) {
    val lines = when (state) {
        is BleState.DeviceFound -> listOf(
            "Device: ${state.name ?: "Unknown"}",
            "Address: ${state.address}"
        )
        is BleState.Connecting -> listOf("Address: ${state.address}")
        is BleState.Discovering -> listOf("Address: ${state.address}")
        is BleState.Subscribing -> listOf("Address: ${state.address}")
        is BleState.Receiving -> listOf(
            "Address: ${state.address}",
            "Raw: ${state.rawHex}",
            "Last update: ${DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(state.lastUpdateMillis))}",
            "Battery type: ${batteryTypeText(state.batteryType)}"
        )
        is BleState.Disconnected -> listOf(state.reason)
        is BleState.Error -> listOf(state.message)
        else -> emptyList()
    }

    if (lines.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActionButtons(
    state: BleState,
    onGrantPermissions: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (state) {
            BleState.PermissionMissing -> {
                Button(
                    onClick = onGrantPermissions,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Grant permissions")
                }
            }
            BleState.Idle -> {
                Button(
                    onClick = onStart,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Start scan")
                }
            }
            BleState.Scanning,
            is BleState.DeviceFound,
            is BleState.Connecting,
            is BleState.Discovering,
            is BleState.Subscribing,
            is BleState.Receiving -> {
                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors()
                ) {
                    Text("Stop")
                }
            }
            BleState.BluetoothOff,
            is BleState.Disconnected,
            is BleState.Error -> {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Retry")
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Stop")
                }
            }
        }
    }
}

private fun statusText(state: BleState): String {
    return when (state) {
        BleState.Idle -> "Ready to scan"
        BleState.PermissionMissing -> "Waiting for permission"
        BleState.BluetoothOff -> "Bluetooth is off"
        BleState.Scanning -> "Scanning for BLE-VoltageCheck..."
        is BleState.DeviceFound -> "Device found"
        is BleState.Connecting -> "Connecting..."
        is BleState.Discovering -> "Discovering voltage service..."
        is BleState.Subscribing -> "Subscribing to voltage notifications..."
        is BleState.Receiving -> "Receiving live voltage"
        is BleState.Disconnected -> state.reason
        is BleState.Error -> state.message
    }
}

private fun batteryTypeText(type: BatteryType): String {
    return when (type) {
        BatteryType.SIX_V -> "6 V"
        BatteryType.TWELVE_V -> "12 V"
        BatteryType.TWENTY_FOUR_V -> "24 V"
        BatteryType.UNKNOWN -> "Unknown"
    }
}

@Preview(showBackground = true)
@Composable
private fun ReceivingPreview() {
    BatteryScreen(
        state = BleState.Receiving(
            address = "01:23:45:67:89:AB",
            voltage = 12.34,
            millivolts = 12340,
            lastUpdateMillis = System.currentTimeMillis(),
            rawHex = "00 34 30",
            batteryType = BatteryType.TWELVE_V
        ),
        onGrantPermissions = {},
        onStart = {},
        onStop = {},
        onRetry = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun DisconnectedPreview() {
    BatteryScreen(
        state = BleState.Disconnected("Disconnected; retrying..."),
        onGrantPermissions = {},
        onStart = {},
        onStop = {},
        onRetry = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun ScanningPreview() {
    BatteryScreen(
        state = BleState.Scanning,
        onGrantPermissions = {},
        onStart = {},
        onStop = {},
        onRetry = {}
    )
}
