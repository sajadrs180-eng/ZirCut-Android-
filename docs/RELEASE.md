# Store-readiness notes (fix212) — honest status

Done / checked in the source:
- Manifest permissions are only WAKE_LOCK (export keeps running), RECORD_AUDIO (voice-over). Media is picked with the system picker (no broad storage permission). allowBackup is off.
- Privacy: no ads, no analytics, no tracking SDK. Since fix235 the app has NO internet permission and no network code: nothing leaves the phone.
- Data Safety: no data collected, no data shared, no network access.

NOT done (needs the owner / a real device):
- Release signing: `release` still uses the DEBUG key (`signingConfig = debug`). A store build needs your own keystore (never put it in the repo).
- Minify/shrink is OFF (`isMinifyEnabled = false`) on purpose: media3 needs keep-rules that cannot be verified without a build+device test.
- No crash/memory-leak profiling on devices has been done; no Play/Bazaar/Myket listing text or policy pages written.
- Android 14+ foreground-service rules for long exports are not reviewed beyond the existing WAKE_LOCK use.
