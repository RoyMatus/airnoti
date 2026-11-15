# Validation record

Date: 2026-11-15. Only completed checks belong under results.

## 0.1.0-alpha02 UI update

The redesigned Compose UI passed 20 JVM tests and 8 instrumentation tests on Pixel 7 / Android 17. Settings/all-mode behavior, six result messages, cancellation UI, system Back, actual Activity recreation, native loading and adaptive/monochrome icon rendering were checked. Screenshots cover RU/EN, light/dark/dynamic colors and a 320 dp Compose area with 200% font. Lint: 0 errors, 34 warnings. See [UI-IMPLEMENTATION.md](UI-IMPLEMENTATION.md) for scope and remaining limitations. These are software/UI checks, not physical Air vibration or battery measurements.

## Earlier completed checks

- Pixel 7 connected over Wi-Fi ADB: Android 17, arm64-v8a.
- Initial debug APK installed and MainActivity launched.
- Twelve JVM policy/protocol tests passed: selection/all-mode semantics, disabled gate, self/service/unselected filtering, changed content under reused ID, initial snapshot suppression, bounded queue/expiry, burst conflation/rate limit, group child/summary ordering, pending selection changes, same-key replacement, protobuf encoding and invalid strength.
- Two instrumented Pixel tests passed: native library loading + JNI Tx/Rx construction/disposal without BLE; DataStore persistence and preservation of individual selection across all-mode toggles.
- All six vendored native binaries matched pinned SHA-256 manifest.
- Final minimal UI: signed release/debug assembly, 16 JVM tests and Android Lint passed (0 errors; 20 warnings, mainly dependency versions and inherited unused resources/style suggestions).
- Pixel 7 UI instrumentation passed: light, dark, and 200% font; scrolling and control semantics asserted. Three screenshots visually inspected: no overlapping or clipped controls, larger text wraps and screen scrolls. Test run kept system theme/font unchanged.
- APK v2 signature verified, RSA3072, release certificate SHA-256 212fe0f4379bbc65344d341e1587df3c3db25b6f606db617b86f7043c6353947.
- APK permissions inspected: Bluetooth/Connect plus AndroidX signature receiver permission only. No Internet, SMS, location, scan, QUERY_ALL_PACKAGES or FGS permissions.

- Four automatic-device-choice tests passed: unique Air, preserved explicit choice, ambiguous devices and unrelated/missing names.

## Still to confirm

Physical haptic, Air firmware, real SMS/messenger, lock screen, extended idle, out-of-range/recovery, permissions revocation/Bluetooth off, Google Health sync after commands/errors. Native load and successful GATT/CoAP do not replace these observations. TalkBack spoken output has not been manually tested; standard semantic roles and click targets were inspected.

## Reproducible hardware scenarios

Keep notification bodies, senders, OTPs, pairing keys and full system dumps out of artifacts.

1. Note phone/OS, Air firmware and Google Health version manually; pair normally.
2. Default strength test: feel/count pulse, record app outcome, check strength and Google Health sync. Repeat with low/high, noting explicit global change, then restore desired value through Google Health.
3. Select messaging app; send genuine SMS and messenger message. Same notification ID with a new message must alert; repost/progress/group summary must not duplicate. Repeat screen locked.
4. Toggle all-apps on/off; verify individual selection survives restart and an installed-later launchable app appears without polling. All mode accepts suitable new packages.
5. Disable: attempt test button and send messages; no GATT calls should occur. Revoke Nearby Devices while active; verify cancellation/no crash. Switch Bluetooth off/on; no retry burst. Return and manually test.
6. Walk out of range, send events, wait >30 seconds and return; no backfill. Generate 50 notifications in a short burst; queue stays bounded and events merge.
7. Leave phone idle for at least 1 hour, then send a notification. Test ordinary process death and reboot separately from Force stop. Force stop requires manual reopening.
8. Sync Google Health before/after 10 signals, and after timeout/cancellation; compare completion and data freshness. Record missed signals and latency, not just BLE status.

## Battery comparison (no results yet)

Use the same phone/Air charge levels, location, radio settings, Google Health sync schedule and screen-off duration. Turn off ADB/Wi-Fi debugging during energy measurements. Repeat and alternate condition order:

- A: AirNoti off, no test notifications, e.g. 4 hours.
- B: AirNoti on, same interval without notifications.
- C: AirNoti on, same interval with a reproducible 60-notification schedule.
- D: AirNoti off, same 60-notification schedule.

Record Android battery statistics for AirNoti UID (CPU, wakelocks, Bluetooth attribution if available), start/end phone and band battery, successful signals and latency. Separate test traffic from other activity. Compare B-A for idle overhead and C-D for notification overhead; do not infer precision beyond battery gauge resolution. For short versus warm link use a separate experimental branch and the identical schedule, including sync success and connection latency. No percentage or autonomy claim can be made from the architecture alone.

