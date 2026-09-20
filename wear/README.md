# Gearan Wear OS build (Galaxy Watch4 Classic)

Requires Android Studio Hedgehog+ with Wear OS SDK (API 30, Wear OS 3 image).

```bash
cd wear
./gradlew :app:assembleDebug        # APK sideloadabile via ADB
./gradlew :app:testDebugUnitTest    # LinkFrameCodecTest + PairingStateMachineTest
./gradlew :app:assembleRelease
```

Install on Watch4 Classic (developer mode + ADB over Wi-Fi):

```bash
adb connect <watch-ip>:5555
adb install app/build/outputs/apk/debug/app-debug.apk
```

Then open Gearan on the Watch → Pair iPhone. Full first-setup constraints:
see `docs/WATCH4_CLASSIC_SETUP.md` (initial provisioning still needs Android).
