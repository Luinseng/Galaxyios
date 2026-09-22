package com.gearan.watch.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.Text
import com.gearan.watch.pairing.PairingState

private val RoundScreenPadding = PaddingValues(horizontal = 18.dp, vertical = 26.dp)

/** Home state is derived from pairing state, never inferred from a stale UI flag. */
@Composable
fun HomeScreen(
    pairingState: PairingState,
    advertising: Boolean,
    batteryText: String? = null,
    onPair: () -> Unit,
    onDeveloper: () -> Unit,
) {
    val status = when (pairingState) {
        PairingState.CONNECTED -> "Connected to iPhone"
        PairingState.PAIRED -> "Paired; iPhone not connected"
        PairingState.SCANNING -> if (advertising) {
            "Pairing: advertising to iPhone"
        } else {
            "Pairing: starting BLE advertising"
        }
        PairingState.WATCH_FOUND -> "Pairing: iPhone found"
        PairingState.CONNECTING -> "Pairing: connecting"
        PairingState.HANDSHAKING -> "Pairing: verifying secure link"
        PairingState.WAITING_FOR_USER_CONFIRMATION -> "Pairing: confirm code"
        PairingState.SECURING_CONNECTION -> "Pairing: securing link"
        PairingState.SAVING_TRUSTED_DEVICE -> "Pairing: saving device"
        PairingState.INITIAL_SYNC -> "Pairing: initial sync"
        PairingState.RECONNECTING -> "Paired; reconnecting"
        PairingState.DISCONNECTED -> "Disconnected"
        PairingState.FAILED -> "Pairing failed; try again"
        PairingState.IDLE -> if (advertising) "Watch visible to iPhone" else "Ready to pair"
    }
    val canPair = pairingState == PairingState.IDLE ||
        pairingState == PairingState.DISCONNECTED || pairingState == PairingState.FAILED

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = RoundScreenPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { Text("Gearan", textAlign = TextAlign.Center) }
        item { Text(status, textAlign = TextAlign.Center) }
        if (pairingState == PairingState.CONNECTED) {
            batteryText?.let { battery -> item { Text(battery, textAlign = TextAlign.Center) } }
        }
        if (canPair) {
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (advertising) "Restart visibility" else "Make watch visible") },
                    onClick = onPair,
                )
            }
            item { Text("Open Gearan on iPhone to find this watch", textAlign = TextAlign.Center) }
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Developer tests") },
                onClick = onDeveloper,
            )
        }
    }
}

@Composable
fun PairingScreen(sas: String?, state: PairingState, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val ready = state == PairingState.WAITING_FOR_USER_CONFIRMATION && sas != null
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = RoundScreenPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { Text("Confirm pairing", textAlign = TextAlign.Center) }
        item {
            Text(
                if (ready) "Check this code matches iPhone" else "Waiting for verification code",
                textAlign = TextAlign.Center,
            )
        }
        item { Text(sas ?: "------", textAlign = TextAlign.Center) }
        if (ready) {
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Code matches") },
                    onClick = onConfirm,
                )
            }
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Cancel pairing") },
                onClick = onCancel,
            )
        }
    }
}
