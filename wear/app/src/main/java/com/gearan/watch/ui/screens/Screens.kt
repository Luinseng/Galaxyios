package com.gearan.watch.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Text
import com.gearan.watch.pairing.PairingState

/** Round-screen home: big title, connection line, single Pair action. */
@Composable
fun HomeScreen(
    pairingState: PairingState,
    connected: Boolean,
    batteryText: String? = null,
    syncText: String? = null,
    onPair: () -> Unit,
    onOpen: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Gearan", textAlign = TextAlign.Center)
        Text(
            if (connected) "iPhone\nConnected" else "Connect to iPhone",
            textAlign = TextAlign.Center
        )
        if (connected) {
            if (batteryText != null) Text(batteryText, textAlign = TextAlign.Center)
            if (syncText != null) Text(syncText, textAlign = TextAlign.Center)
        }
        if (!connected && pairingState == PairingState.IDLE) {
            Button(onClick = onPair) { Text("Pair iPhone") }
        }
        if (connected) {
            Button(onClick = { onOpen("sync") }) { Text("Sync") }
            Button(onClick = { onOpen("internet") }) { Text("Internet") }
            Button(onClick = { onOpen("health") }) { Text("Health") }
            Button(onClick = { onOpen("notifications") }) { Text("Notifications") }
            Button(onClick = { onOpen("diagnostics") }) { Text("Diagnostics") }
            Button(onClick = { onOpen("settings") }) { Text("Settings") }
        }
        Button(onClick = { onOpen("developer") }) { Text("Developer") }
    }
}

@Composable
fun PairingScreen(sas: String?, state: PairingState, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Pair with this iPhone?", textAlign = TextAlign.Center)
        Text(sas ?: "······", textAlign = TextAlign.Center)
        Button(onClick = onConfirm) { Text("✓") }
        Button(onClick = onCancel) { Text("X") }
    }
}

@Composable
fun SimpleScreen(title: String, lines: List<String>, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, textAlign = TextAlign.Center)
        lines.forEach { Text(it, textAlign = TextAlign.Center) }
        Button(onClick = onBack) { Text("Back") }
    }
}
