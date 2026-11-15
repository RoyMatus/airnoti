# Privacy policy — AirNoti

Updated 2026-11-15.

AirNoti runs on the Android phone. No account, advertisements, analytics, remote server, or INTERNET permission. Bluetooth traffic goes to the selected paired band.

With notification access, AirNoti receives notification metadata and may temporarily process title, text, message timestamp, sender and attachment URI to compute a salted SHA-256 fingerprint. It does not open attachment URIs. Content is not persisted or logged. Fingerprints exist only in process memory in a bounded cache (512 entries); pending alerts contain routing metadata only and expire before sending. No message content is sent to the band, only a haptic/settings command.

DataStore persists the enabled state, all-apps state, selected package names, Bluetooth address and strength preference in app-private storage. Android backup is disabled. The installed-app list is used locally for selection. Uninstalling removes these settings; revoking notification access stops receiving notifications. Force stopping stops the app until reopened.

No health data, SMS database, contacts or location are requested. Google Health remains a separate application with its own privacy practices. Diagnostic validation reports must not contain private notifications, pairing data or credentials.

The bundled stack includes general network-capable library code, but the manifest does not grant Internet access and AirNoti uses its BLE transport only. Local haptic commands do not require an Internet connection.
