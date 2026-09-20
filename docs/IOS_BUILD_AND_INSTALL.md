# Gearan iOS — build & install (app reale)

Procedura completa dal sorgente all'iPhone fisico. Non committare mai
certificati, provisioning profile o password: la firma resta locale.

## 1. Requisiti
- Mac con Xcode 15+ · Apple ID (gratuito ok, vedi limiti sotto) · iPhone + cavo.
- Repo clonato; la firma non è nel repository per scelta.

## 2. Xcode setup (dettaglio in `docs/XCODE_SETUP.md`)
1. Nuovo progetto iOS → App, SwiftUI, iOS 16+, Bundle ID tuo
   (es. `com.tuonome.gearan` — deve essere univoco).
2. Signing & Capabilities → Team = tuo Apple ID.
3. Aggiungi package locale `ios/` (GearanCore) + i 3 file di `Sources/GearanApp/`.
4. Info.plist: `NSBluetoothAlwaysUsageDescription` + `UIBackgroundModes` =
   `bluetooth-central` (vedi `docs/XCODE_SETUP.md` per i testi esatti).

## 3. Installazione su iPhone fisico (consigliata, niente IPA)
1. Collega l'iPhone → selezionalo come destinazione → Run.
2. iPhone: Impostazioni → Generali → VPN e gestione dispositivo → autorizza.
3. Apri Gearan → Add Watch.

## 4. Archive → Export IPA (se serve il file)
1. Destinazione "Any iOS Device" → Product → Archive → Distribute App →
   Development (gratuito) o Ad Hoc/TestFlight (account a pagamento).
2. Installazione IPA: Xcode → Devices and Simulators, Apple Configurator,
   AltStore/Sideloadly. Dettagli e limiti (7 giorni firma gratuita):
   `docs/IPA_INSTALL_IPHONE.md`.

## 5. Test rapido post-installazione
- BLE Scan Debug: stato poweredOn, Watch trovato con RSSI/UUID/service UUID.
- Primo pairing e reconnect: `docs/HARDWARE_TEST_WATCH4_CLASSIC.md`.
