# Gearan Watch — build APK & install (Galaxy Watch4 Classic)

## 1. Build APK
Requisiti: Android Studio Hedgehog+ con SDK + Wear OS image, JDK 17.

```bash
cd wear
./gradlew :app:assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest   # LinkFrameCodec, PairingMachine, GoldenVectors, SyncEngine
```

Release: `./gradlew :app:assembleRelease` (firmata con la tua chiave locale,
mai nel repo).

## 2. Prerequisito: provisioning ufficiale (una volta sola)
Se il Watch è nuovo/resettato: pair con Android via Galaxy Wearable
(vedi `docs/WATCH4_CLASSIC_SETUP.md`). Gearan non sostituisce questo passo.

## 3. Wireless debugging + install
1. Watch: Impostazioni → Info orologio → Software → tocca 7×
   `Versione software` → Opzioni sviluppatore → `Debug ADB` + `Debug via Wi-Fi` ON.
2. Watch: nota IP e porta (es. `192.168.1.50:5555`).
3. Da PC con Platform-Tools:
   ```bash
   adb connect 192.168.1.50:5555
   adb devices                       # deve elencare il Watch
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```
4. Apri Gearan sul Watch → Pair iPhone (advertising 120 s).

## 4. Log diagnostici
```bash
adb logcat | grep GEARAN
# tag: GEARAN_BLE GEARAN_PAIR GEARAN_LINK GEARAN_SYNC GEARAN_CRYPTO
# mai chiavi/dati sanitari/contenuti notifiche nei log
```

## 5. Reinstallazione / Forget
- Reinstallare l'APK non cancella il trust (DataStore resta).
- Forget iPhone dal Watch (Settings) cancella l'associazione Gearan.
