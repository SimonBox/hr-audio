# Developer guide

## Current milestone

The completed version 0.1 field test confirmed that live BPM can reach earbuds with the screen locked while Strava continues receiving the same WHOOP stream. The test procedure and expected observations are in the [README](../README.md#first-five-minute-test-with-strava). Emulator results cover Android session wiring. The version 0.2 hardware checklist covers spoken zone changes and persisted audio settings.

## Code map

All production Kotlin lives in `app/src/main/java/dev/hraudio/`.

| File | Responsibility |
| --- | --- |
| MainActivity.kt | Compose run screen, runtime permissions, 15-second BLE discovery, voice preview, and user-initiated session start/stop. |
| RunService.kt | Connected-device foreground service, ongoing notification, main-thread session state, reconnection, saved device, and readout orchestration. |
| BleLink.kt | Discover Heart Rate Service 0x180D, subscribe to Heart Rate Measurement 0x2A37 through its client configuration descriptor. Ignore callbacks from a closed connection. |
| Settings.kt | Validate and save the complete set of five zone boundaries, speech switch and readout interval. Empty boundaries keep zone tracking disabled. |
| Readings.kt | Decode 8-bit and little-endian 16-bit BPM. Ignore missing values, zero BPM and explicit loss of sensor contact. Use elapsed time and fresh samples to schedule speech. |
| Voice.kt | Android English TTS, short-lived audio focus with ducking, initialization and cleanup. Android routes output to the active media device. |

Starting a run from the visible activity lets the service own Bluetooth and speech while the activity is backgrounded. The service gives an initial connection attempt 20 seconds, treats 10 seconds without a valid reading as loss of signal, then retries after three seconds. An explicit disconnect also starts a retry. Stop closes the GATT link, cancels retries, shuts down TTS and ends the notification. Session activity is transient. Device name and address are saved to private preferences on the first valid reading and survive relaunch and reboot. If the device address changes, use **Change sensor**.

## Tests and contributions

From the repository root, run:

```sh
./gradlew testDebugUnitTest assembleDebug
./gradlew connectedDebugAndroidTest
```

The second command needs a running Android 12+ emulator or connected test phone. Prefer a disposable emulator for instrumentation. CI builds the APK, runs JVM tests, and runs instrumentation on API 35. The debug APK is available as the **hr-audio-debug** workflow artifact when its upload step succeeds.

- JVM tests in `app/src/test` cover malformed packets, contact flags, measurement widths and readout timing boundaries.
- The instrumented flow in `app/src/androidTest` uses the real activity, preferences and foreground service. A simulated strap and speech recorder drive Start, a trace of heart-rate readings, signal loss, reconnection and Stop. The assertions check the visible BPM and phrases requested for speech.
- Use the README field test to evaluate real BLE discovery, simultaneous Strava reception, TTS output, audio focus and locked-screen delivery. Record the phone and strap models, software versions, order of connection and observed gaps.
- Keep commits focused. For changes to timing, packet decoding or the session lifecycle, include a regression test with the behavior change.

## Zone and timing behavior

RunService loads one snapshot of RunOptions per session. A new Readout instance establishes a baseline on the first sample and after signal loss. It tracks the last confirmed zone, the candidate zone, candidate start time and the periodic BPM deadline. Only fresh samples advance these decisions. A candidate needs 10 continuous seconds in its zone. Returning to the confirmed zone or entering a different candidate restarts that decision. The run screen displays the instantaneous zone, so the runner can see a crossing before hearing it.

Periodic and zone cues are independent. A transition never shifts the periodic schedule. When both are due, Readout emits one phrase. The instrumentation flow sets five bounds and a 15-second cadence through the UI, recreates the activity and exercises a coincident zone and BPM cue, loss of signal, reconnect and stop. JVM tests cover exact boundaries, jitter, skipped zones, upward and downward transitions, and independent audio controls.

## Platform references

- [Android background BLE connections](https://developer.android.com/develop/connectivity/bluetooth/ble/background)
- [Connected-device foreground services](https://developer.android.com/develop/background-work/services/fgs/service-types#connected-device)
- [Android audio focus](https://developer.android.com/media/optimize/audio-focus)
- [Bluetooth Heart Rate Service specification](https://www.bluetooth.com/wp-content/uploads/Files/Specification/HTML/HRS_v1.0/out/en/index-en.html)
