package com.example.batteryguardlite.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.batteryguardlite.ble.BatteryType
import com.example.batteryguardlite.ble.BleState
import com.example.batteryguardlite.history.DailyVoltageReading
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@Composable
fun BatteryScreen(
    state: BleState,
    historyReadings: List<DailyVoltageReading>,
    onGrantPermissions: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val receiving = state as? BleState.Receiving
    val liveVoltage = receiving?.voltage
    val voltageText = liveVoltage?.let { String.format(Locale.US, "%.2f V", it) } ?: "--.-- V"

    MaterialTheme {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = Color(0xFFF8FAFC)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HeaderSection(
                    voltageText = voltageText,
                    state = state
                )

                VoltageGauge(
                    voltage = liveVoltage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                )

                VoltageHistoryChart(
                    readings = historyReadings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .heightIn(min = 150.dp)
                )

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
private fun HeaderSection(
    voltageText: String,
    state: BleState
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Battery Guard Lite",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF111827)
        )
        Text(
            text = voltageText,
            fontSize = 54.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
            color = Color(0xFF0F766E)
        )
        Text(
            text = statusText(state),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF475569)
        )
        LastUpdateLine(state)
    }
}

@Composable
private fun LastUpdateLine(state: BleState) {
    val receiving = state as? BleState.Receiving ?: return
    Text(
        text = "${receiving.millivolts} mV | ${DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(receiving.lastUpdateMillis))} | ${batteryTypeText(receiving.batteryType)}",
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFF64748B)
    )
}

@Composable
private fun VoltageGauge(
    voltage: Double?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 22.dp.toPx()
            val center = Offset(size.width / 2f, size.height * 0.78f)
            val radius = min(size.width * 0.40f, size.height * 0.64f)
            val arcSize = Size(radius * 2f, radius * 2f)
            val topLeft = Offset(center.x - radius, center.y - radius)

            drawArc(
                color = Color(0xFFE5E7EB),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth + 8.dp.toPx(), cap = StrokeCap.Round)
            )
            drawScaleArc(VoltageScale.MIN, VoltageScale.RED_MAX, Color(0xFFB91C1C), topLeft, arcSize, strokeWidth)
            drawScaleArc(VoltageScale.RED_MAX, VoltageScale.YELLOW_MAX, Color(0xFFF59E0B), topLeft, arcSize, strokeWidth)
            drawScaleArc(VoltageScale.YELLOW_MAX, VoltageScale.MAX, Color(0xFF16A34A), topLeft, arcSize, strokeWidth)

            val needleVoltage = voltage?.toFloat() ?: VoltageScale.MIN
            val needleAngle = voltageAngle(needleVoltage)
            val needleRadians = needleAngle * PI.toFloat() / 180f
            val needleEnd = Offset(
                x = center.x + cos(needleRadians) * radius * 0.82f,
                y = center.y + sin(needleRadians) * radius * 0.82f
            )

            drawLine(
                color = Color(0xFF111827),
                start = center,
                end = needleEnd,
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(color = Color.White, radius = 18.dp.toPx(), center = center)
            drawCircle(color = Color(0xFF111827), radius = 7.dp.toPx(), center = center)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(71, 85, 105)
                textAlign = Paint.Align.CENTER
                textSize = 12.sp.toPx()
            }
            drawContext.canvas.nativeCanvas.drawText(
                String.format(Locale.US, "%.1f", VoltageScale.MIN),
                center.x - radius,
                center.y + 28.dp.toPx(),
                paint
            )
            drawContext.canvas.nativeCanvas.drawText(
                String.format(Locale.US, "%.1f", VoltageScale.MAX),
                center.x + radius,
                center.y + 28.dp.toPx(),
                paint
            )
        }
    }
}

@Composable
private fun VoltageHistoryChart(
    readings: List<DailyVoltageReading>,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val left = 18.dp.toPx()
            val right = size.width - 18.dp.toPx()
            val top = 22.dp.toPx()
            val bottom = size.height - 20.dp.toPx()
            val chartWidth = max(1f, right - left)
            val chartHeight = max(1f, bottom - top)

            fun yFor(voltage: Float): Float {
                val fraction = voltageFraction(voltage)
                return bottom - fraction * chartHeight
            }

            fun band(topVoltage: Float, bottomVoltage: Float, color: Color) {
                val yTop = yFor(topVoltage)
                val yBottom = yFor(bottomVoltage)
                drawRect(
                    color = color,
                    topLeft = Offset(left, yTop),
                    size = Size(chartWidth, yBottom - yTop)
                )
            }

            band(VoltageScale.MAX, VoltageScale.YELLOW_MAX, Color(0x5522C55E))
            band(VoltageScale.YELLOW_MAX, VoltageScale.RED_MAX, Color(0x66EAB308))
            band(VoltageScale.RED_MAX, VoltageScale.MIN, Color(0x55DC2626))

            listOf(VoltageScale.MIN, VoltageScale.RED_MAX, VoltageScale.YELLOW_MAX, VoltageScale.MAX).forEach { voltage ->
                val y = yFor(voltage)
                drawLine(
                    color = Color(0x55FFFFFF),
                    start = Offset(left, y),
                    end = Offset(right, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            val sorted = readings.sortedBy { it.dayIndex }
            if (sorted.isEmpty()) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.rgb(71, 85, 105)
                    textAlign = Paint.Align.CENTER
                    textSize = 14.sp.toPx()
                }
                drawContext.canvas.nativeCanvas.drawText(
                    "No saved readings yet",
                    size.width / 2f,
                    top + chartHeight / 2f,
                    paint
                )
                return@Canvas
            }

            val firstDay = sorted.first().dayIndex
            val lastDay = sorted.last().dayIndex
            val daySpan = max(1, lastDay - firstDay)

            fun xFor(reading: DailyVoltageReading): Float {
                return if (sorted.size == 1) {
                    left + chartWidth / 2f
                } else {
                    left + ((reading.dayIndex - firstDay).toFloat() / daySpan.toFloat()) * chartWidth
                }
            }

            val points = sorted.map { reading ->
                Offset(xFor(reading), yFor(reading.volts.toFloat()))
            }

            points.zipWithNext().forEach { (start, end) ->
                drawLine(
                    color = Color(0xFF334155),
                    start = start,
                    end = end,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(30, 41, 59)
                textAlign = Paint.Align.CENTER
                textSize = 11.sp.toPx()
            }
            sorted.forEachIndexed { index, reading ->
                val point = points[index]
                val labelX = point.x.coerceIn(left + 14.dp.toPx(), right - 14.dp.toPx())
                val labelY = (point.y - 10.dp.toPx()).coerceAtLeast(top + 12.dp.toPx())
                drawCircle(
                    color = Color.White,
                    radius = 6.dp.toPx(),
                    center = point
                )
                drawCircle(
                    color = Color(0xFF0F766E),
                    radius = 4.dp.toPx(),
                    center = point
                )
                drawContext.canvas.nativeCanvas.save()
                drawContext.canvas.nativeCanvas.rotate(-35f, labelX, labelY)
                drawContext.canvas.nativeCanvas.drawText(reading.label, labelX, labelY, labelPaint)
                drawContext.canvas.nativeCanvas.restore()
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawScaleArc(
    startVoltage: Float,
    endVoltage: Float,
    color: Color,
    topLeft: Offset,
    arcSize: Size,
    strokeWidth: Float
) {
    val start = voltageAngle(startVoltage)
    val sweep = voltageAngle(endVoltage) - start
    drawArc(
        color = color,
        startAngle = start,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
    )
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
                    modifier = Modifier.weight(1f)
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

private fun voltageAngle(voltage: Float): Float {
    return 180f + voltageFraction(voltage) * 180f
}

private fun voltageFraction(voltage: Float): Float {
    return ((voltage - VoltageScale.MIN) / (VoltageScale.MAX - VoltageScale.MIN)).coerceIn(0f, 1f)
}

private object VoltageScale {
    const val MIN = 11.5f
    const val RED_MAX = 12.0f
    const val YELLOW_MAX = 12.5f
    const val MAX = 12.8f
}

private fun previewHistory(): List<DailyVoltageReading> {
    return listOf(
        DailyVoltageReading("2026-06-10", 739_777, "10.06", 11980),
        DailyVoltageReading("2026-06-12", 739_779, "12.06", 12120),
        DailyVoltageReading("2026-06-18", 739_785, "18.06", 12480),
        DailyVoltageReading("2026-06-24", 739_791, "24.06", 12060),
        DailyVoltageReading("2026-07-01", 739_798, "01.07", 12620)
    )
}

@Preview(showBackground = true)
@Composable
private fun ReceivingPreview() {
    BatteryScreen(
        state = BleState.Receiving(
            address = "01:23:45:67:89:AB",
            voltage = 12.48,
            millivolts = 12480,
            lastUpdateMillis = System.currentTimeMillis(),
            rawHex = "00 C0 30",
            batteryType = BatteryType.TWELVE_V
        ),
        historyReadings = previewHistory(),
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
        historyReadings = previewHistory().dropLast(1),
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
        historyReadings = emptyList(),
        onGrantPermissions = {},
        onStart = {},
        onStop = {},
        onRetry = {}
    )
}
