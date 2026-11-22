# HR Audio

Hear your heart rate through your earbuds while Strava records your run. Use a WHOOP strap or another sensor advertising the standard Bluetooth Heart Rate Service. HR Audio speaks periodic BPM readings and sustained zone changes while the phone is locked.

The first fresh reading triggers “Sensor connected. 118 beats per minute.” During the session, the next fresh reading at or after each configured interval (60 seconds by default) triggers a BPM update. After signal loss the app clears the reading and tries the saved device again. Tap **Stop** in the app or its ongoing notification to finish.

## Update an existing install

In the same clone you used for version 0.1, run `git pull --ff-only`. Connect your phone and click **Run** in Android Studio, or run `./gradlew installDebug`. Using the same application ID and signing key updates the installed app in place and preserves your saved strap. If Android reports a signature mismatch, rebuild from the same computer and debug keystore you used for the first install.

## Set your zones and audio

Before starting a session, open **Settings**. Copy the five starting BPM values from your WHOOP zones. If zone 3 starts at 140 and zone 4 at 155, the app displays zone 3 as 140–154. Below your first boundary the run screen shows **Below zone 1**. Leave all five fields blank for BPM-only use.

Set **BPM readout interval** in seconds, or enter **0** to turn off periodic BPM. **Speak zone changes** controls transition cues independently. A valid set saves on the phone and applies to the next run. Your strap and the initial connection announcement work as before.

During a run, the screen shows the zone of the latest reading. Speech waits for 10 continuous seconds of readings in the new zone. A brief crossing or a return to the previous zone cancels the pending cue. When a periodic reading and a zone cue coincide, you hear one phrase, such as “Up to zone four, 156 beats per minute.”

## Test the zone update

1. Enter your five boundaries, set a short readout interval (e.g., 20 seconds), and tap **Done**. Reopen Settings to check the values.
2. Start Strava and HR Audio as usual. During a walk or your normal run, listen as BPM crosses a boundary. For an easy test, temporarily place a boundary near your walking BPM. Restore your personal boundaries afterwards.
3. Check that a brief crossing stays quiet, a sustained rise produces one upward cue, and a sustained fall produces one downward cue. Repeat with the phone locked.
4. Stop, change the interval to **0**, and restart. The initial BPM and sustained zone cues should still speak. Restore your preferred cadence when done.

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

The version 0.1 field trial passed on the tester’s phone with a WHOOP strap, Strava, earbuds, music and a locked screen. Strava retained the heart-rate trace and the saved strap reconnected after restart. Version 0.2 adds zone announcements and configurable cadence for the next field trial.

See the [developer guide](docs/DEVELOPMENT.md) for architecture and tests.
