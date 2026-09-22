# Repository Guidelines

## Project Structure & Module Organization

Gearan connects a Samsung Galaxy Watch4 Classic to an iPhone over Bluetooth Low Energy. `wear/` contains the Kotlin Wear OS app: production code is in `wear/app/src/main/`, resources in `wear/app/src/main/res/`, and JUnit tests in `wear/app/src/test/`. `ios/` contains the Swift package and Xcode project, with app code in `Sources/GearanApp/`, protocol code in `Sources/GearanCore/`, and XCTest suites in `Tests/GearanCoreTests/`. `shared/gearan_link.py` is the Python reference implementation. Protocol tests and golden vectors live in `tests/`; specifications and setup notes live in `protocol/` and `docs/`. `android/` is an optional ADB diagnostic tool, not the companion app.

## Build, Test, and Development Commands

Run commands from the repository root unless noted:

```bash
python -m pytest tests/ -v
cd wear && ./gradlew :app:assembleDebug
cd wear && ./gradlew :app:testDebugUnitTest
cd ios && swift test
```

The first command runs protocol tests. The Gradle commands build the Wear debug APK and run its unit tests; they require Java 17 and an Android SDK. `swift test` runs core Swift tests on macOS. Open `ios/Gearan.xcodeproj` in Xcode 15+ to build the iPhone app; BLE scanning requires a physical device.

## Coding Style & Naming Conventions

Match existing four-space indentation. Use `PascalCase` for Kotlin and Swift types, `lowerCamelCase` for methods and properties, and Python `snake_case` with `UPPER_SNAKE_CASE` constants. No repository-wide formatter or linter is configured, so preserve nearby formatting and keep changes focused.

## Testing Guidelines

Python uses pytest-compatible `test_*.py` modules, Wear tests use JUnit 4, and iOS tests use XCTest. Add tests beside the affected platform code. Protocol changes must update `tests/vectors/golden_vectors.json` and synchronized copies consumed by Kotlin and Swift. No coverage threshold is configured; run every relevant suite before submitting.

## Commit & Pull Request Guidelines

Git history is unavailable in this source snapshot. Use short, imperative commit subjects and keep each commit scoped to one change. Pull requests should describe affected platforms, protocol compatibility, and commands run. Link relevant issues and include screenshots for UI changes.

## Security & Configuration

Never commit signing certificates, provisioning profiles, keystores, private keys, or `.env` files. Keep session keys ephemeral, and keep diagnostics free of keys, health data, and notification content as required by `docs/SECURITY.md`.
