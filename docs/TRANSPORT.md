# Transport investigation — 2026-11-15

Pinned source snapshots, not a claim of independent hardware verification:

| Project | Commit | License / findings | Decision |
|---|---|---|---|
| ThoreKoritzius/fitbit-air | 5f76f5f8d48d1dab420feeebc6ac87f52be78ba4 | MIT transport adapter, Apache Golden Gate, Apache Mbed TLS, BSD lwIP; own native binaries for arm64, armv7, x86_64 with published hashes and build helper | Use its public JNI bindings/binaries and adapt only haptic session |
| ashu-sa/AirBridge | 9f856bcef92f081f5834086af3ae4a17e2f75ec6 | README declares Apache-2.0; bundled AARs include bitgatt, GG bindings/connection manager, linkcontroller, remoteapi, RxFitbitGatt. README says arm64 only, live delivery/battery/sync verification incomplete | No code/binaries copied; avoid larger bundled dependency/provenance surface |
| paceaitian/fitbit-air-notification-bridge | 6fec57fafc68e03904f49d823248e38b07a54478 | Apache-2.0 GG fork; direct protobuf settings, toggle/restore, arm64-only build, Gradle 6.9.1/AGP 4.1.0/NDK23; v10014 coexistence explicitly pending hardware validation | Protocol cross-check; do not copy its larger old Android build or automatic retries |

Source URLs:
- https://github.com/ThoreKoritzius/fitbit-air/tree/5f76f5f8d48d1dab420feeebc6ac87f52be78ba4
- https://github.com/ashu-sa/AirBridge/tree/9f856bcef92f081f5834086af3ae4a17e2f75ec6
- https://github.com/paceaitian/fitbit-air-notification-bridge/tree/6fec57fafc68e03904f49d823248e38b07a54478

The open-Issues API queries returned no open non-PR issues in the first page of 30 for these snapshots. That is not evidence of no defects. README limitations and source behavior take precedence over marketing descriptions.

## Actual path

`AirTransport` uses the already bonded device chosen by the user. No discovery or new bond is attempted. UUIDs are sourced from `FitbitAirPlugin.kt`, not guessed or taken from the standard Alert Service. The source uses tracker service `abbaff00…`, TX `abbaff01…`, RX `abbaff02…` and standard CCCD.

Golden Gate's public Apache-licensed BOOTSTRAP resolver uses a shared public bootstrap constant. No private Google Health key or normal identity is imported. The per-stack refusing resolver is used only for bounded channel handoff; it changes no stored pairing key. Native provenance and source revisions are in `tools/native/manifest.json`.

The settings read is CoAP PUT `/settings`, body `0a0101`; only the exact observed low/high response encodings are accepted. A POST contains a protobuf haptics setting; the default tap sends alternate/current settings in a single message. This is the upstream observed one-pulse mechanism on firmware 67.20001.253.2, not documented arbitrary motor control. A readback confirms final strength only. AirNoti requires a positive POST response and matching readback, and still asks the human to confirm the pulse. Lost response is UNKNOWN and never automatically retried. Unlike upstream runTap, AirNoti does not turn a failed POST into success merely from a matching readback and does not retry restoring writes.

Low/high selections explicitly persist that strength on the band. Default attempts to keep the current strength in the same atomic settings message. If interrupted or firmware handles it differently, the setting may need checking in Google Health. AirNoti never reads/writes alarms or resets the band.

## Coexistence and bounds

Before session start, observe inbound traffic sizes only; if >=400 bytes in 1.2 seconds, wait up to 5 seconds then skip. This upstream heuristic is not proof that a concurrent sync cannot begin afterward. Session timeout 25 seconds plus at most 4 seconds for handoff. Native outgoing packet queue max 64, event queue max 16. No application-layer reconnect/retry. Gattlink/DTLS necessarily handle their own protocol traffic within this bounded operation.

On a completed session, use upstream's fresh Gattlink stack with refusing resolver and stop on Gattlink reset, letting Google Health negotiate alone. Timeout or failed handoff is explicitly unconfirmed. On user cancellation, immediately dispose endpoint, stack, Rx/Tx and GATT; do not send another handoff command after disable. Google Health may then need its own recovery timeout. No claim of uninterrupted sync until measured.

Short-lived connection was chosen to bound ownership and eliminate idle radio traffic, not because an energy comparison proved it wins. Upstream reports a warm link reduces command startup latency; persistent connection events and reconnection policy would need actual energy/coexistence measurement. AirNoti has no warm link, heartbeat, sensor reads or native stack until a command.

## Android lifecycle

[Android BLE background guide](https://developer.android.com/develop/connectivity/bluetooth/ble/background) permits background connections while the process is alive and warns connections end on process death. AirNoti uses the process bound by the system's NotificationListenerService. It does not start an ordinary service and call it a reliability solution. No foreground-service exemption is assumed. If future measurements require FGS, add a user-started connectedDevice FGS with visible notification and correct permissions; do not launch one blindly from each notification.

[FGS types](https://developer.android.com/develop/background-work/services/fgs/service-types) and Android 17 target changes were reviewed. CompanionDeviceManager is not needed for this initial bonded-device path; association/address behavior and presence permissions would require separate device tests. No claim that CDM supports all rotating addresses. NotificationListener reconnection and process startup read settings asynchronously and never replay existing notifications. Work-profile and OS-redacted notifications can be unavailable to the listener.

## Build choices

Single module; AGP 9.3.3/Gradle9.5 retained from the workspace, compatible with API37. Built-in Kotlin 2.2.10 and matching Compose compiler retained for compatibility; not claimed newest Kotlin. Stable Compose1.12.1, Material3 1.4.0, Activity1.13.0, DataStore1.3.0 checked against official release pages. The borrowed binding dependencies RxJava2/OkHttp4 stay pinned for compatibility. Timber upgraded to5.0.1 because4.7.1's lint detector fails under AGP9.3.3. No private native ABI is loaded from another app.
