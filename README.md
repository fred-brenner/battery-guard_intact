# Battery Guard Lite

Battery Guard Lite is a minimal native Android Kotlin app for displaying live 12 V battery voltage from a BLE device named `BLE-VoltageCheck`.

It is local-only. It does not use Cordova, cloud services, analytics, internet access, accounts, storage, export, push, history, or camera features.

## BLE protocol

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

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Run parser tests:

```bash
./gradlew testDebugUnitTest
```
