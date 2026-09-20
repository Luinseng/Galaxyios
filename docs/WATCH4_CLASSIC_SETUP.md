# WATCH4 CLASSIC SETUP — first setup & independence from Android companion

Target: **Samsung Galaxy Watch4 Classic (Wear OS 3, One UI Watch)**.
Goal: maximum independence from the Android companion WITHOUT exploits or
protection bypasses. Honest constraints first.

## 1. SYSTEM_PAIRING vs GEARAN_PAIRING

- **SYSTEM_PAIRING** = official Samsung/Google provisioning (Galaxy Wearable app
  on Android + Google account + Play Services on the companion). Creates the
  encrypted Wear OS profile, installs system policy, enables Play Store on watch.
- **GEARAN_PAIRING** = app-level secure association between Gearan-iOS and
  Gearan-Watch over BLE GATT (this repo). It does NOT provision the OS.

Gearan does NOT claim to replace SYSTEM_PAIRING: no public API exists to
provision Wear OS from iOS or from a third-party APK. Any tool claiming so
without Samsung/Google private keys is misrepresenting the platform.

## 2. How to install Gearan on the Watch4 Classic

**Prerequisite (one time): the Watch must be set up via Android first.**
1. Reset the Watch if it was paired before (Settings → General → Reset).
2. On an Android phone (borrowed is fine): install Galaxy Wearable + Watch4 plugin,
   pair the Watch4 Classic, complete Google sign-in and Wi-Fi setup.
3. Enable developer mode on the Watch: Settings → About watch → Software → tap
   `Software version` 7× → Developer options → enable `ADB debugging` + `Debug over Wi-Fi`.
4. Connect from a PC: `adb connect <watch-ip>:5555`, then:
   `adb install app-debug.apk` (from `wear/` build).
5. (Optional, for independence testing) After Gearan is installed and verified,
   you may unpair/remove the Android companion — see §4 for what keeps working.

Why Android first: Play Services provisioning, system time, and Play Store
installs require the official companion flow. Sideloading Gearan via ADB works
any time after that; Gearan itself never touches the system partition.

## 3. Can the Watch later be used with Gearan + iPhone only?

Yes — for Gearan features. After §2:
- Gearan Watch app advertises + runs its GATT server standalone.
- Gearan iPhone app scans, pairs (GEARAN_PAIRING), syncs, relays HTTPS.
- No Android phone needs to stay nearby or stay paired.

What still needs Android/Google (cannot be fixed by Gearan):
- OS updates, Play Store installs/updates, Google account services.
- Samsung Health Monitor medical features (ECG/BP) with region locks.
- Restoring the watch after a factory reset (needs SYSTEM_PAIRING again).

## 4. Limitations after removing the original Android phone

| Area | Works with Gearan+iPhone | Needs Android again |
|---|---|---|
| Gearan pairing/sync/battery/caps | yes | — |
| Network Relay (HTTPS via iPhone) | yes | — |
| Gearan notifications | yes | — |
| ANCS from iOS | experimental, hardware test | — |
| Play Store / OS update | — | yes |
| Factory reset re-provision | — | yes |
| ECG/BP region features | — | Samsung policy |

## 5. Exact GEARAN pairing procedure (after §2)

1. iPhone: open Gearan → **Add Watch** (scan starts, filtered by Gearan UUID).
2. Watch: open Gearan → **Pair iPhone** (advertising starts, 120 s window).
3. iPhone list shows **Galaxy Watch4 Classic — Nearby** → tap **Connect**.
4. Both show the same 6-digit code (e.g. `482731`), derived from the session transcript.
5. Confirm on BOTH devices. A mismatch = abort (possible MITM).
6. `Securing connection → Saving trusted device → Synchronizing → Complete`.
7. Later: automatic reconnect (low-power advertising + central scan). A plain
   disconnect never unpairs — use Forget iPhone / Forget Watch explicitly.
