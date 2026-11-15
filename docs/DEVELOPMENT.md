# Development guide

## Build from source

Use JDK 21, Android SDK 37.0, Build Tools 36.0.0 and accepted SDK licenses. Gradle 9.5.0 / AGP 9.3.3 and dependencies are pinned. Set `sdk.dir` in local `local.properties` or configure `ANDROID_HOME`.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

Use `gradlew.bat` on Windows. Connected tests need an Android device but do not need Air and cannot confirm vibration. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. On a personal phone, install with `adb install -r` and run AndroidJUnitRunner directly if you need to avoid Gradle's test installation cleanup removing the installed app/data.

The project has one Android module. Vendored Kotlin JNI bindings and native libraries are under `app/src/main/vendor` and `app/src/main/jniLibs`. See [NOTICE](../NOTICE) and [transport research](TRANSPORT.md). A Linux native rebuild needs Python 3, Git, CMake, Ninja and NDK 28.2.13676358:

```sh
python3 tools/native/build.py --ndk "$ANDROID_HOME/ndk/28.2.13676358" --check
```

The supplied binary hashes were checked; a full native rebuild was not performed locally.

## Signing and future updates

To create your own first signing key, use `tools/Initialize-Signing.ps1` with JAVA_HOME set. It refuses to overwrite an existing key. Back up **both** `.private/airnoti-release.p12` and `.private/signing.properties` securely; neither belongs in Git or release assets. Losing the original key prevents updating an installed APK with that signature.

Keep `applicationId = ru.itoltec.airnoti`, increase versionCode/versionName, use the same key and run tests followed by `./gradlew :app:assembleRelease`. Without signing.properties, release output is unsigned. CI has no signing key. Local debug uses the local release key when available to allow upgrading without deleting settings. A self-built APK with a different key cannot update the published APK in place.
