# AirNoti

[Русский](README.md) · **English**

[Download APK / releases](https://github.com/RoyMatus/airnoti/releases)

A small, unofficial Android notification bridge for Google Fitbit Air: new phone notifications trigger a short wrist signal. Native Kotlin and Jetpack Compose Material 3, with a simple home screen and band selection and help. Not affiliated with Google or Fitbit.

**Experimental alpha.** The app uses a reverse-engineered protocol. A confirmed command and settings readback do not prove a physical vibration. Physical vibration from notification forwarding has been confirmed by the wearer on one Pixel 7 / Air setup; Google Health coexistence still needs verification. Upstream results are not AirNoti test results.

## Install and enable

1. Download the APK and SHA-256 file from Releases and compare the checksum. Allow installation from your chosen source in Android settings.
2. Pair Fitbit Air normally in Google Health on this phone. Do not reset or unpair it.
3. Open AirNoti. The contextual home-screen action requests Bluetooth access and opens notification access settings. Guided help is under **Help → How to set up AirNoti**. Only the main switch enables forwarding. If Android restricts notification access, use the documented “Allow restricted settings” option in app information when available; disabling Play Protect is not required.
4. A single paired device named Fitbit Air is selected automatically, without scanning. Choose manually if there are multiple candidates or your Air was renamed. The selector lists paired devices and their addresses; other models are not supported.
5. Enable the main switch. All new phone notifications, including service and progress notifications, are accepted automatically. There is no app selector. The Test vibration button only tests the band, not Android notification permission.
6. Confirm that you actually feel the signal, then check Google Health synchronization. An unknown result may mean the command arrived: check vibration strength in Google Health before testing again. There is no automatic retry of an uncertain command.

Fresh installations start with forwarding off. Once access is granted and forwarding is enabled, notifications from all apps are accepted, including packages without launcher activities. Android controls which apps may show notifications. Legacy saved app choices no longer restrict forwarding. Identical reposts, AirNoti's own events and duplicate group summaries do not trigger a separate signal.

**Help → Vibration strength → As in Google Health** reads the current strength, sends one atomic alternate/restore settings command, then checks the final setting. Low and High change the band's global strength on the next signal, including its use by Google Health. No arbitrary motor patterns or duration are claimed. Alarms are not read or changed.

## Compatibility

- APK: Android 11 / API 30+, BLE and an already paired Air. Native libraries: `arm64-v8a`, `armeabi-v7a`, `x86_64`; no 32-bit x86.
- Google Health requires **Android 14+**, per its [official requirements](https://support.google.com/product-documentation/answer/14226283), checked November 15, 2026. APK installation on Android 11–13 does not imply compatibility with current Google Health.
- No Pixel-specific APIs. Samsung, Xiaomi, OnePlus and other brands are potential configurations, not tested devices.
- Upstream reports Air firmware `67.20001.253.2` with Pixel 7 Pro. This is **not** an AirNoti hardware verification. Firmware changes may break the protocol.
- An Android APK does not run on iPhone. iOS is outside this project.

## Checks for 0.1.0-alpha03

App selection and service-notification filtering were removed. A Telegram filtering defect was identified: the old code treated even zero-valued progress metadata as a service notification. On Pixel 7 / Android 17, a real Telegram notification was received, automatically dispatched and acknowledged by the band. The wearer confirmed physical vibration without pressing the manual test button. A local Android notification also completed the pipeline while AirNoti was in the background.

20 JVM tests and 12 instrumentation tests passed. Some connection attempts failed: delivery is not guaranteed. The tested Air firmware was not recorded. Extended idle, Google Health sync after a series of signals, range recovery and battery measurements remain outstanding. Manual TalkBack speech and OEM launcher themed icons remain untested. See [validation](docs/VALIDATION.md).

## Background behavior and limitations

A system-bound `NotificationListenerService` receives notifications. Ongoing, foreground-service, progress and media/service notifications are accepted too. AirNoti's own events and duplicates are excluded. Existing notifications are seeded on listener connection without replaying them. MessagingStyle messages are fingerprinted in memory using content, timestamp and sender so a new message under the same notification ID can be detected. Other identical updates under the same ID are deduplicated; indistinguishable new events can therefore be missed.

Events merge over 700 ms, with at least 3 seconds between send starts. The queue is bounded to 16 events, with a 10-second expiry before sending. Group summaries are suppressed when a child was processed. Events are not stored on disk or replayed after a long absence; delivery is not guaranteed.

The transport opens only for an event or manual test: GATT → Gattlink → DTLS BOOTSTRAP → CoAP `/settings` → verification → bounded handoff to Google Health → close. Ready does not mean a persistent Bluetooth connection. All six outcomes remain distinct: confirmed command/readback, unavailable, failed, uncertain, busy/skipped, and unconfirmed handoff.

No BLE scan, heartbeat, periodic WorkManager jobs, health-data reads, permanent wake lock, server or account. Operations run in the system-bound listener process; no ordinary service is presented as a foreground-service exemption. Android may terminate the process and interrupt an operation. OEM restrictions and deep idle need physical testing. Force stop requires reopening the app. Normal listener reconnection is controlled by Android and does not replay old notifications.

Busy synchronization causes a bounded wait or skipped event. Turning forwarding/Bluetooth off, revoking Bluetooth access, disconnecting the listener or changing device address cancels the operation. Choose Air again if its saved address is no longer valid. CompanionDeviceManager was evaluated but not added: coexistence with the existing pairing/address is unverified and continuous presence observation is unnecessary for this version.

**No battery measurements are available.** Short connections are a bounded starting design, not a proven energy optimum; repeated handshakes may cost more than a warm connection during bursts. See the [measurement procedure](docs/VALIDATION.md).

## Permissions and privacy

Bluetooth Connect on Android 12+, legacy Bluetooth on Android 11, and user-enabled notification access. The app no longer queries installed applications; the launcher queries declaration was removed. No QUERY_ALL_PACKAGES.

No INTERNET, READ_SMS, contacts, Accessibility, root, medical, location or scanning permissions. Notification texts, senders and verification codes are not stored or logged. Deduplication uses temporary in-memory fingerprints. DataStore stores switches, selected package names, device address and strength. Android backup is disabled. See the [privacy policy](PRIVACY.md).

## Build from source

Use JDK 21, Android SDK 37.0, Build Tools 36.0.0 and accepted SDK licenses. Gradle 9.5.0 / AGP 9.3.3 and dependencies are pinned. Set `sdk.dir` in local `local.properties` or configure `ANDROID_HOME`.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

Use `gradlew.bat` on Windows. Connected tests need an Android device but do not need Air and cannot confirm vibration. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. On a personal phone, install with `adb install -r` and run AndroidJUnitRunner directly if you need to avoid Gradle's test installation cleanup removing the installed app/data.

The project has one Android module. Vendored Kotlin JNI bindings and native libraries are under `app/src/main/vendor` and `app/src/main/jniLibs`. See [NOTICE](NOTICE) and [transport research](docs/TRANSPORT.md). A Linux native rebuild needs Python 3, Git, CMake, Ninja and NDK 28.2.13676358:

```sh
python3 tools/native/build.py --ndk "$ANDROID_HOME/ndk/28.2.13676358" --check
```

The supplied binary hashes were checked; a full native rebuild was not performed locally.

## Signing and future updates

To create your own first signing key, use `tools/Initialize-Signing.ps1` with JAVA_HOME set. It refuses to overwrite an existing key. Back up **both** `.private/airnoti-release.p12` and `.private/signing.properties` securely; neither belongs in Git or release assets. Losing the original key prevents updating an installed APK with that signature.

Keep `applicationId = ru.itoltec.airnoti`, increase versionCode/versionName, use the same key and run tests followed by `./gradlew :app:assembleRelease`. Without signing.properties, release output is unsigned. CI has no signing key. Local debug uses the local release key when available to allow upgrading without deleting settings. A self-built APK with a different key cannot update the published APK in place.

## Contributing and licenses

Issues and pull requests are welcome. Public forks may be modified by their owners; they do not grant write access to this repository. The repository owner decides which changes to merge. Original code is MIT; vendored components retain their MIT, Apache-2.0 and BSD-3-Clause notices.
