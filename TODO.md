# Gearan TODO — 2026-09-23

## Verified

- [x] Build and install a test Release APK on Galaxy Watch4 Classic.
- [x] Run Wear unit tests, Python protocol checks, on-device BLE smoke and crypto tests.
- [x] Verify 120 s advertising timeout and retry on the installed Release.
- [x] Run Swift tests and build the unsigned iOS Release IPA on GitHub Actions.

## Next

- [ ] Connect Watch GATT callbacks to the frame and handshake dispatcher.
- [ ] Align iOS and Watch ECDH group negotiation, then test SAS and mutual confirmation on physical devices.
- [ ] Complete trusted-device persistence, initial sync and reconnect hardware checks.
- [ ] Configure Apple distribution signing outside Git; export and test an installable IPA.
- [ ] Run the remaining Watch4 Classic hardware checklist in `docs/HARDWARE_TEST_WATCH4_CLASSIC.md`.
