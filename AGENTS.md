# AGENTS.md

Guidance for AI agents (and humans) working in this workspace.

## Layout
- This directory **is** the git repo (default branch `main`). Run all build commands
  from here (the repo root).

## Project
**Battery Guard Lite** — a minimal, local-only Kotlin/Compose Android app that shows
live battery voltage from a BLE device (`BLE-VoltageCheck`) and keeps a per-day history
(latest 20 days). No network, analytics, accounts, or telemetry.
Package: `com.example.batteryguardlite`. Single `app` module.

## Toolchain
Where each version is pinned (the source of truth — check these, don't assume):
- **JDK + Gradle** → `mise.toml`. `mise install`, then build.
- **AGP + Kotlin** → root `build.gradle.kts` (plugin versions, `apply false`).
- **Android SDK** → `local.properties` (`sdk.dir`, gitignored) or `ANDROID_HOME`.

The Java *bytecode/language* target stays **17** (`compileOptions` in
`app/build.gradle.kts`; AGP 9's built-in Kotlin derives `jvmTarget` from it) —
independent of the JDK used to run the build.

## Commands (run from the repo root)
- Unit tests — the only verification gate: `gradle testDebugUnitTest`
- Build debug APK: `gradle assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- Single test class:
  `gradle testDebugUnitTest --tests "com.example.batteryguardlite.ble.BatteryVoltageParserTest"`
- There are **no** lint or format tasks in Gradle; the repo's lint hooks run via
  pre-commit (see **Pre-commit** below).

## Pre-commit
Lint hooks — actionlint, zizmor, shellcheck, ktlint — run on every commit. All tools
are provided by mise (`mise.toml` is the single source of truth for versions).
ktlint uses its default `ktlint_official` code style; the only override is in
`.editorconfig` (PascalCase `@Composable` functions are allowed).
Before your first commit:

```bash
mise install
pre-commit install
```

Run all hooks manually: `pre-commit run --all-files`

## Architecture
Package `com.example.batteryguardlite`:

- `MainActivity.kt` — entry point (`ComponentActivity`). Owns `BatteryBleManager` +
  `BatteryHistoryStore`, requests BLE permissions, saves one reading per
  `BleState.Receiving`, and handles CSV backup/restore via the system file picker.
  Renders `ui/BatteryScreen`.
- `ble/BatteryBleManager.kt` — BLE scan → connect → subscribe (notify). Exposes
  `StateFlow<BleState>`. Filters by device name `BLE-VoltageCheck`; 15 s scan timeout,
  2 s reconnect. Service/characteristic/CCCD UUIDs in the companion object.
- `ble/BatteryVoltageParser.kt` — **pure** parser. `parse(payload): BatteryVoltage?`;
  needs ≥3 bytes; `mV = (b[1]&0xFF) | ((b[2]&0xFF)<<8)` (little-endian, bytes 1–2);
  returns null outside **3000–32000 mV**. Also classifies `BatteryType` (6/12/24 V).
- `ble/BleState.kt` — sealed UI state (`Idle`, `Scanning`, …, `Receiving`,
  `Disconnected`, `Error`, `PermissionMissing`, `BluetoothOff`).
- `history/DailyVoltageHistory.kt` — **pure** logic (no Android deps → unit-testable).
  One reading per day (same-day overwrites), keeps the latest **20**.
  `serialize`/`deserialize` use a compact `date,mV[,ts]` form; `toCsv`/`importCsv` use
  the header `date,time,voltage_v,millivolts,timestamp_millis` with flexible column
  matching. **Keep it pure.**
- `history/DailyVoltageReading.kt` — the reading model
  (`dateKey`, `dayIndex`, `label`, `millivolts`, `timestampMillis?`, `volts`).
- `history/BatteryHistoryStore.kt` — persistence via SharedPreferences
  (`battery_history`, key `daily_readings`). Thin wrapper over `DailyVoltageHistory`.
- `ui/BatteryScreen.kt` — Compose UI: live voltage, 12 V gauge, history chart,
  backup/restore.

## Tests
- `app/src/test/.../ble/BatteryVoltageParserTest.kt`
- `app/src/test/.../history/DailyVoltageHistoryTest.kt`
- JUnit 4 only. No instrumented tests / emulator.

## CI
- GitHub Actions workflow: `.github/workflows/ci.yml` (push to `main` + pull requests).
  Uses **mise** for the JDK and Gradle and `android-actions/setup-android` for the
  SDK; runs `testDebugUnitTest` then `assembleDebug` and uploads the debug APK.
  All third-party actions are pinned by commit SHA (version in a trailing comment).
