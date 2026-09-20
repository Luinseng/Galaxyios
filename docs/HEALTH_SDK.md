# Samsung Health Sensor SDK — optional wiring (does NOT block BLE build)

`SamsungHealthSensorAdapter.HEALTH_SDK_ENABLED` is `false` by default.
The Wear app compiles, advertises, pairs and syncs without any Samsung SDK.

## To enable (on a machine with Android Studio)

1. Obtain the official SDK (`samsung-health-sensor-api.aar`) from the
   Samsung Developer portal (requires Samsung partnership / approval for
   privileged streams — Gearan cannot grant this).
2. Place it in `wear/app/libs/samsung-health-sensor-api.aar` and add to
   `wear/app/build.gradle.kts`:
   ```kotlin
   dependencies {
       implementation(files("libs/samsung-health-sensor-api.aar"))
   }
   ```
3. Set `HEALTH_SDK_ENABLED = true` and implement the privileged branches in
   `SamsungHealthSensorAdapter` behind `if (HEALTH_SDK_ENABLED)` guards,
   keeping the `SDK_RESTRICTION` fallback when consent/region denies access.
4. On the Watch4 Classic verify each stream at runtime; every feature keeps
   reporting AVAILABLE / PERMISSION_REQUIRED / SDK_RESTRICTION honestly.

Rules (unchanged): no regional bypass, no Health Monitor workaround, no medical
certification bypass. If the SDK denies a stream, the capability stays
`SDK_RESTRICTION` and the iPhone UI shows it as unavailable.
