package com.gearan.watch.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Text
import com.gearan.watch.ble.BlePermissions
import com.gearan.watch.ble.GearanBleHub

/** Blocks pairing/advertising until runtime Bluetooth permissions are granted. */
@Composable
fun PermissionGate(missing: List<String>, onGrant: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Bluetooth needed", textAlign = TextAlign.Center)
        Text(BlePermissions.rationale(), textAlign = TextAlign.Center)
        Text("Missing: ${missing.joinToString()}", textAlign = TextAlign.Center)
        Button(onClick = onGrant) { Text("Grant") }
    }
}

/** Developer → BLE Smoke Test: advertiser + GATT server + connections + event log. */
@Composable
fun BleSmokeTestScreen(
    advertising: Boolean,
    onStartAdvertise: () -> Unit,
    onStartServer: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
) {
    val serverRunning by GearanBleHub.serverRunning.collectAsState()
    val connections by GearanBleHub.connections.collectAsState()
    val lastEvent by GearanBleHub.lastEvent.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("BLE Smoke Test", textAlign = TextAlign.Center)
        Text("Advertising: ${if (advertising) "YES" else "NO"}", textAlign = TextAlign.Center)
        Text("GATT Server: ${if (serverRunning) "YES" else "NO"}", textAlign = TextAlign.Center)
        Text("Connections: $connections", textAlign = TextAlign.Center)
        Text("Last: $lastEvent", textAlign = TextAlign.Center)
        Button(onClick = onStartAdvertise) { Text("Start Adv") }
        Button(onClick = onStartServer) { Text("Start GATT") }
        Button(onClick = onStop) { Text("Stop") }
        Button(onClick = onBack) { Text("Back") }
    }
}

/** Developer → Crypto self-test: runs against the real provider on-device. */
@Composable
fun CryptoSelfTestScreen(result: String?, onRun: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Crypto self-test", textAlign = TextAlign.Center)
        Text(result ?: "Not run yet", textAlign = TextAlign.Center)
        Button(onClick = onRun) { Text("Run test") }
        Button(onClick = onBack) { Text("Back") }
    }
}

/** Every error shows stage + code + retry. */
@Composable
fun GearanErrorScreen(error: GearanStageError, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Connection failed", textAlign = TextAlign.Center)
        Text("Stage:\n${error.stage}", textAlign = TextAlign.Center)
        Text("Error:\n[${error.code}] ${error.message}", textAlign = TextAlign.Center)
        Button(onClick = onRetry) { Text("Retry") }
        Button(onClick = onBack) { Text("Back") }
    }
}

@Composable
fun DeveloperMenuScreen(onSmoke: () -> Unit, onCrypto: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Developer", textAlign = TextAlign.Center)
        Button(onClick = onSmoke) { Text("BLE Smoke Test") }
        Button(onClick = onCrypto) { Text("Crypto self-test") }
        Button(onClick = onBack) { Text("Back") }
    }
}
