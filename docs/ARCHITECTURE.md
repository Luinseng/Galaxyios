# Gearan architecture (Watch4 Classic ↔ iPhone)

```
Watch4 Classic (Kotlin/Compose, GATT server + advertiser)
  LinkFrameCodec ⇄ GearanAdvertiser ⇄ GearanPeripheralService
  GearanPairingManager ⇄ GearanSecureSession ⇄ TrustedDeviceStore
  InitialSyncManager / BatteryMonitor / SamsungHealthSensorAdapter
  NetworkRelay (request) / NotificationBridge / Diagnostics / Heartbeat / AutoReconnect
        ↕ BLE GATT (Gearan Link 0.1 frames, HMAC + AES-GCM)
iPhone (Swift/SwiftUI, central + GATT client)
  BLEScanner ⇄ GearanPairingManager ⇄ GearanSecureSession ⇄ TrustedDeviceStore(Keychain)
  Sync UI / Battery UI / Capabilities / NetworkRelay(URLSession) / NotificationBridge
  AncsCapabilityProbe(experimental) / Diagnostics / Heartbeat / AutoReconnect
```

- Watch is peripheral (advertises `6E400001-…`), iPhone is central (filters by UUID).
- iOS has no `createBond()`; bonding happens implicitly on encrypted characteristics.
- Capabilities negotiated every sync; iPhone never assumes availability.
- Heartbeat adaptive (30 s active / 120 s idle / paused offline) for Watch battery.
