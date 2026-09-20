import SwiftUI
import GearanCore

@main
struct GearanApp: App {
    @StateObject private var store = AppStore()
    var body: some Scene {
        WindowGroup {
            ContentView().environmentObject(store)
        }
    }
}

final class AppStore: ObservableObject {
    @Published var trustedWatch: TrustedWatch?
    @Published var pairingState: PairingState = .idle
    @Published var sas: String?
    @Published var batteryPercent: Int?
    @Published var lastSync: String?
    @Published var connected = false
}

struct ContentView: View {
    @EnvironmentObject var store: AppStore
    var body: some View {
        NavigationStack {
            if store.trustedWatch == nil {
                HomeUnpairedView()
            } else {
                DeviceHomeView()
            }
        }
    }
}

/// Home without paired Watch.
struct HomeUnpairedView: View {
    var body: some View {
        VStack(spacing: 16) {
            Text("Gearan").font(.largeTitle).bold()
            Text("Connect your Galaxy Watch4 Classic").multilineTextAlignment(.center)
            NavigationLink("Add Watch") { AddWatchView() }
                .buttonStyle(.borderedProminent)
            NavigationLink("BLE Scan Debug") { ScanDebugView() }
        }.padding()
        .navigationTitle("Gearan")
    }
}

struct AddWatchView: View {
    @StateObject private var scanner = BLEScanner()
    @StateObject private var conn = GearanConnectionManager()
    var body: some View {
        VStack {
            if !scanner.isScanning {
                Button("Start scan") { scanner.startScan() }
            } else {
                ProgressView("Scanning for Galaxy Watch4 Classic…")
                Button("Stop") { scanner.stopScan() }
            }
            if let info = conn.deviceInfo {
                Text(info).font(.caption).foregroundStyle(.secondary)
            }
            if let err = conn.lastError {
                ConnectionErrorView(error: err) { conn.lastError = nil }
            }
            List(scanner.discoveries) { w in
                VStack(alignment: .leading) {
                    Text("Galaxy Watch4 Classic").bold()
                    Text("Nearby · RSSI \(w.rssi)").font(.caption)
                }
                NavigationLink("Connect") { PairingView(watchName: w.name) }
            }
        }.padding()
        .navigationTitle("Add Watch")
        .onAppear { scanner.startScan() }
        .onDisappear { scanner.stopScan() }
    }
}

/// BLE Scan Debug (FASE 9): Bluetooth state, scanning flag, Gearan devices,
/// RSSI, peripheral UUID, advertised service UUIDs. No MAC address.
struct ScanDebugView: View {
    @StateObject private var scanner = BLEScanner()
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Bluetooth state: \(scanner.centralState)")
            Text("Scanning: \(scanner.isScanning ? "YES" : "NO")")
            Text("Gearan devices found: \(scanner.discoveries.count)")
            if scanner.discoveries.isEmpty {
                // Stock Watch Probe (separate, non-priority): no Gearan service seen.
                Text("Gearan Watch App not detected. Advanced features require Gearan on your Galaxy Watch4 Classic.")
                    .font(.caption).foregroundStyle(.secondary)
            }
            List(scanner.discoveries) { w in
                VStack(alignment: .leading, spacing: 4) {
                    Text("Galaxy Watch4 Classic").bold()
                    Text("RSSI: \(w.rssi)").font(.caption)
                    Text("Peripheral UUID: \(w.id.uuidString)").font(.caption).textSelection(.enabled)
                    Text("Services: \(w.serviceUUIDs.joined(separator: ", "))").font(.caption)
                }
            }
            HStack {
                Button(scanner.isScanning ? "Stop" : "Scan") {
                    scanner.isScanning ? scanner.stopScan() : scanner.startScan()
                }
            }
        }.padding()
        .navigationTitle("BLE Scan Debug")
        .onAppear { scanner.startScan() }
        .onDisappear { scanner.stopScan() }
    }
}

/// Staged error with retry (FASE 18).
struct ConnectionErrorView: View {
    var error: GearanStageError
    var onRetry: () -> Void
    var body: some View {
        VStack(spacing: 8) {
            Text("Connection failed").bold()
            Text("Stage:\n\(error.stage)").multilineTextAlignment(.center)
            Text("Error:\n[\(error.code)] \(error.message)").multilineTextAlignment(.center).font(.caption)
            Button("Retry", action: onRetry)
        }.padding()
    }
}

struct PairingView: View {
    @EnvironmentObject var store: AppStore
    var watchName = "Galaxy Watch4 Classic"
    var body: some View {
        VStack(spacing: 16) {
            Text("Pair Galaxy Watch4 Classic").font(.headline)
            Text("Check that this code appears on your Watch:").multilineTextAlignment(.center)
            Text(store.sas ?? "······").font(.system(size: 48, design: .monospaced))
            HStack {
                Button("Cancel", role: .destructive) { store.pairingState = .disconnected }
                NavigationLink("Confirm") { SyncProgressView() }
                    .buttonStyle(.borderedProminent)
            }
            Text("Code is derived from the pairing session (anti-MITM). It is never used as an encryption key.")
                .font(.caption).foregroundStyle(.secondary)
        }.padding()
        .navigationTitle("Pairing")
    }
}

struct SyncProgressView: View {
    let phases = ["Connecting", "Securing connection", "Checking Watch", "Checking sensors", "Synchronizing", "Complete"]
    var body: some View {
        VStack(spacing: 8) {
            Text("Synchronizing Galaxy Watch4 Classic…").font(.headline)
            ForEach(phases, id: \.self) { Text($0) }
        }.padding()
        .navigationTitle("Sync")
    }
}

struct DeviceHomeView: View {
    @EnvironmentObject var store: AppStore
    @StateObject private var reconnect = GearanAutoReconnectManager()
    var body: some View {
        List {
            Section {
                Text("Galaxy Watch4 Classic").bold()
                switch reconnect.state {
                case .connected:
                    Text("Connected")
                case .reconnecting:
                    Text("Reconnecting…")
                case .watchUnavailable:
                    Text("Watch out of range")
                case .bluetoothDisabled:
                    Text("Bluetooth is off")
                case .serviceUnavailable:
                    Text("Gearan service unavailable")
                case .authRequired:
                    Text("Authentication required")
                case .idle:
                    Text(store.connected ? "Connected" : "Reconnecting…")
                }
                if let b = store.batteryPercent { Text("Battery: \(b)%") }
                if let s = store.lastSync { Text("Last sync: \(s)").font(.caption) }
            }
            Section("Watch") {
                NavigationLink("Health") { SimpleDetail(title: "Health", lines: ["Heart rate: capability-gated", "SpO2/BIA/ECG: SDK-gated"]) }
                NavigationLink("Notifications") { SimpleDetail(title: "Notifications", lines: ["Gearan notifications: supported", "ANCS: experimental, probe first"]) }
                NavigationLink("Internet Relay") { SimpleDetail(title: "Internet via iPhone", lines: ["HTTPS-only app proxy", "Test connection"]) }
                NavigationLink("Diagnostics") { SimpleDetail(title: "Diagnostics", lines: ["Export Diagnostics (no secrets)"]) }
                NavigationLink("Settings") { SettingsView(reconnect: reconnect) }
            }
        }.navigationTitle("Gearan")
        .onAppear { reconnect.restoreAndReconnect() }
    }
}

/// Settings with Developer Mode (BLE debug, reconnect, protocol, export).
struct SettingsView: View {
    @ObservedObject var reconnect: GearanAutoReconnectManager
    var body: some View {
        List {
            Section("General") {
                Text("Forget Watch").foregroundStyle(.red)
            }
            Section("Developer Mode") {
                NavigationLink("BLE Scan Debug") { ScanDebugView() }
                Button("Force reconnect") { reconnect.forceReconnect() }
                NavigationLink("Protocol info") {
                    SimpleDetail(title: "Gearan Link 0.1", lines: [
                        "Service: 6E400001-…-B001",
                        "Frame: 20B + HMAC-SHA256",
                        "ECDH: X25519, P-256 fallback",
                        "SAS: 6 digits, display only"
                    ])
                }
                ShareLink(item: DiagnosticsSnapshot().debugDescription, subject: Text("Gearan diagnostics")) {
                    Text("Export diagnostics (no secrets)")
                }
            }
        }.navigationTitle("Settings")
    }
}

struct SimpleDetail: View {
    var title: String; var lines: [String]
    var body: some View {
        VStack(spacing: 8) { ForEach(lines, id: \.self) { Text($0) } }
        .padding().navigationTitle(title)
    }
}
