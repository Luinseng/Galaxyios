# Gearan Android debug tool (optional)

This module is NOT required for normal use (iPhone ↔ Watch4 Classic).
It exists for development: ADB setup checks, Wear OS Data Layer comparison,
and reading public device info over ADB.

Build (Android Studio, phone/tablet module):

```bash
# Equivalent: create a minimal `android/` app module if needed and run:
adb devices
adb shell dumpsys bluetooth_manager
adb shell dumpsys battery
adb shell getprop ro.build.version.release
```

Planned commands (reference, no privileged APIs):
- `gearan doctor` — ADB connectivity, Wear OS version, Gearan APK presence.
- `gearan diag-pull` — pulls sanitized diagnostics (never keys/health/notif content).
- `gearan compare-datalayer` — notes differences vs Wear Data Layer (which needs Android companion).
