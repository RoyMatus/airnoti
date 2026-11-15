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

Air firmware, real SMS, lock screen, extended idle, out-of-range/recovery, permissions revocation/Bluetooth off, Google Health sync after commands/errors. Physical vibration and automatic Telegram dispatch were subsequently observed in the alpha03 investigation below. Native load and successful GATT/CoAP do not replace these observations. TalkBack spoken output has not been manually tested; standard semantic roles and click targets were inspected.

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


## Notification forwarding investigation (0.1.0-alpha03)

On the Pixel 7 running alpha02, notification listener access was absent and Android had not bound AirNotificationListener. After the owner enabled access through Android, both the permission and system service binding were confirmed. The owner then reported a Telegram notification without physical vibration; enabling access alone did not establish successful delivery.

A separate filter defect was reproduced on-device: a normal notification containing EXTRA_PROGRESS=0 and EXTRA_PROGRESS_MAX=0 was classified as a service notification solely because the progress key existed. The regression test failed before the fix. The first repair distinguished actual progress from zero-valued metadata. The owner then requested all notifications, including service/progress events: the final alpha03 policy does not reject those categories at all. A real Telegram snapshot was later observed with progressKey=true and progressMax=0, confirming that alpha02 would reject it.

The updated local debug APK passed 20 JVM tests and 12 instrumentation tests on Pixel 7, including real synthetic Android MessagingStyle objects, new messages under the same notification ID, progress filtering, and the UI distinction between manual testing and automatic forwarding. Lint: 0 errors, 38 warnings. Debug-only technical traces contain routing booleans/flags and transport outcomes, not notification text, sender, OTP, address or notification IDs. The release build does not emit these traces.

UI now says forwarding is not working when Android permission is missing, explains the one-time permission, opens AirNoti's own system permission page with a fallback, initially clarified app selection, then removed it entirely as requested, and explains that manual testing alone does not enable forwarding. On returning to the UI, a permitted but disconnected listener gets one event-driven rebind request; no polling or reconnect timer was added.

### Completed live forwarding checks

- Android notification access was enabled by the owner, not granted through a shell bypass. The system-bound listener was verified connected.
- 15:07:46: a real Telegram notification was accepted. At 15:07:47 automatic send began with origin=NOTIFICATION; at 15:07:53 the band command/readback returned VERIFIED. The owner subsequently confirmed feeling vibration without pressing the manual test button.
- 15:10:18: a local Android shell notification was accepted while AirNoti was not foreground. GATT connection, service discovery, subscription and DTLS session succeeded; initial/readback CoAP responses were class 2/detail 5, six bytes. At 15:10:24 the notification-origin operation completed VERIFIED. No notification body or sender was captured in diagnostics.
- An earlier local notification attempt returned FAILED; it was not presented as a successful delivery or automatically retried. These observations do not establish guaranteed delivery, extended idle reliability, measured battery cost or Google Health synchronization after repeated signals.
- Final forwarding semantics: all packages, including those without launcher activities, and all service/progress categories; no in-app app selection. Identical reposts, own notifications, old seeded notifications and redundant group summaries still do not cause duplicate signals. Bounded conflation, rate limit and expiry remain.
- The final simplified UI and policy passed 20 JVM and 12 Android instrumentation tests. The new progress regression initially failed before repair; updated tests now also assert that actual ongoing/progress notifications pass routing.
Final alpha03 release assembly and JVM/Lint checks passed; Lint reports 0 errors and 48 warnings (including resources left unused by the removed app selector).

