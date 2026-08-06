# Battery Guard Lite

Replacement for the outdated and decommissioned [Battery Guard](https://intact-battery-guard.de.softonic.com/android) app from Intact.

100% local and telemetry-free.

### Description
Battery Guard Lite is a minimal native Android Kotlin app for displaying live 12 V battery voltage from a BLE device named `BLE-VoltageCheck`.

It is local-only. It does not use Cordova, cloud services, analytics, internet access, accounts, push, or camera features. The app stores only private on-device daily voltage readings for the bottom history chart.

## Display

- Top: live voltage from BLE notifications.
- Middle: 12 V car battery gauge with red, yellow, and green ranges.
- Bottom: one saved voltage point per day, keeping the latest 20 saved dates.

If another voltage arrives on the same day, that day's saved point is overwritten with the latest reading.

## Backup and restore

The Backup button saves the current history through Android's file picker as CSV with these columns:

```text
date,time,voltage_v,millivolts,timestamp_millis
```

The Restore button accepts `.csv` files with a `date` column and either a voltage or millivolts column. Existing dates are never overwritten. After merging, only the newest 20 dates are retained.

## BLE protocol

Note: I configured this for my own BLE device and some IDs might need to be changed. Please create a ticket if you encounter an issue with your "BLE-VoltageCheck" device.

- Device name: `BLE-VoltageCheck`
- Service UUID: `00001814-0000-1000-8000-00805f9b34fb`
- Characteristic UUID: `00002a53-0000-1000-8000-00805f9b34fb`
- CCCD UUID: `00002902-0000-1000-8000-00805f9b34fb`
- Characteristic mode: notify only

Notification bytes:

- `byte[0]`: flags, ignored
- `byte[1]`: voltage millivolts low byte
- `byte[2]`: voltage millivolts high byte
- `byte[3..n]`: ignored

Decode:

```text
millivolts = (byte[1] & 0xFF) | ((byte[2] & 0xFF) << 8)
volts = millivolts / 1000.0
```

Payloads shorter than 3 bytes are ignored. Values below 3000 mV or above 32000 mV are ignored.

## Build

```bash
./gradlew assembleDebug
```

Expected APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install:

Download the APK release and install on your Android device.

OR

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Run parser tests:

```bash
./gradlew testDebugUnitTest
```
