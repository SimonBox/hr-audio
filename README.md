# HR Audio

Hear your heart rate through your earbuds while Strava records your run. This first Android build is a hardware trial for a WHOOP strap (or another sensor advertising the standard Bluetooth Heart Rate Service), your phone, and your usual audio device.

The first fresh reading triggers “Sensor connected. 118 beats per minute.” During the session, the next fresh reading at or after each 60-second interval triggers a BPM update. After signal loss the app clears the reading and tries the saved device again. Tap **Stop** in the app or its ongoing notification to finish.

## Build from source

You need a computer with Git, a JDK 17, the Android SDK, and an Android 12 or newer phone with Bluetooth LE. The project includes the Gradle wrapper. Android Studio is the simplest way to install the SDK and connect a phone.

1. Clone the repository and enter the project directory.

   ```sh
   git clone https://github.com/SimonBox/hr-audio.git
   cd hr-audio
   ```

2. In Android Studio, open the cloned directory. In **SDK Manager**, install **Android SDK Platform 35**, **Android SDK Build-Tools 34.0.0** (under **Show Package Details**), and **Android SDK Platform-Tools**. Allow Gradle sync to finish. For terminal builds, set `JAVA_HOME` to your JDK 17 and `ANDROID_HOME` to the SDK location shown in SDK Manager, or let Studio write the SDK location to `local.properties`.
3. Run the unit tests and build the debug APK from the repository root.

   ```sh
   ./gradlew testDebugUnitTest assembleDebug
   ```

   On Windows use `gradlew.bat` in place of `./gradlew`. The APK is **app/build/outputs/apk/debug/app-debug.apk**. The first build downloads dependencies.

## Install on your phone

1. In Android settings, enable **Developer options** (usually by tapping **Build number** seven times), then enable **USB debugging**. Connect the phone using a data-capable USB cable, unlock it, and accept its debugging prompt.
2. In Android Studio, select your phone in the device selector and click **Run** with the **app** configuration. Or, from the repository root, run:

   ```sh
   ./gradlew installDebug
   ```

3. Launch **HR Audio** on the phone. Allow **Nearby devices** when requested. On Android 13 or newer, allow notifications to have the session's **Stop** action in the notification drawer.

If you already have the debug APK, enable USB debugging as above and run `adb install -r path/to/app-debug.apk` using **adb** from SDK Platform-Tools. If Gradle cannot see the phone, `adb devices` should list it as `device`; an `unauthorized` entry means the phone is waiting for approval. [Android device setup](https://developer.android.com/studio/run/device)

## First five-minute test with Strava

| Step | What to check |
| --- | --- |
| Enable **Heart Rate Broadcast** in WHOOP. Connect your earbuds to the phone. | In HR Audio, **Test voice** should be audible in the earbuds at a comfortable media volume. |
| Start an activity in Strava as usual. | Confirm live BPM in Strava before opening HR Audio. |
| In HR Audio, tap **Choose sensor** and select your strap. | Selecting starts the session. Watch for a live BPM and listen for the first spoken reading. The app saves the sensor after receiving a valid measurement. |
| Lock the phone and walk for at least three minutes. | Listen for roughly one fresh update per minute. If you usually play music, try it during this step. |
| Stop HR Audio, finish and save the activity in Strava. | Speech should stop. Inspect the Strava heart-rate trace for gaps during the overlapping minutes. |
| Reopen HR Audio and tap **Start**. | The saved strap should reconnect without another scan. **Change sensor** lets you replace it. |

**If the strap doesn't appear**, check broadcast, Bluetooth and Nearby devices permission, then scan again. If it works only after you disconnect it from Strava, record that result along with phone model, Android version, WHOOP model, and which app connected first. Simultaneous reception on your setup is the main question this build tests. For silent speech, try **Test voice**, check the selected media output and media volume, and check for an English voice in Android text-to-speech settings.

## Project status

This build uses a fixed one-minute cadence. Configurable cadence, five saved zone boundaries, and spoken upward and downward zone transitions are planned after the hardware trial. HR Audio stores the selected sensor locally. It does not request location or internet access or record a workout history.

See the [developer guide](docs/DEVELOPMENT.md) for architecture, tests and the agreed next milestone.
