package com.gearan.watch.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.Text
import com.gearan.watch.ble.BlePermissions
import com.gearan.watch.ble.GearanBleHub

private val DevRoundScreenPadding = PaddingValues(horizontal = 18.dp, vertical = 26.dp)

/** Blocks pairing and BLE tests until required runtime permissions are granted. */
@Composable
fun PermissionGate(missing: List<String>, onGrant: () -> Unit) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = DevRoundScreenPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { Text("Bluetooth permission", textAlign = TextAlign.Center) }
        item { Text(BlePermissions.rationale(), textAlign = TextAlign.Center) }
        item {
            Text(
                "Not granted: ${missing.joinToString { it.substringAfterLast('.') }}",
                textAlign = TextAlign.Center,
            )
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Grant permission") },
                onClick = onGrant,
            )
        }
    }
}

/** Developer BLE checks expose only live advertiser, GATT, connection, and log state. */
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
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = DevRoundScreenPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { Text("BLE smoke test", textAlign = TextAlign.Center) }
        item { Text("Advertising: ${if (advertising) "ON" else "OFF"}", textAlign = TextAlign.Center) }
        item { Text("GATT server: ${if (serverRunning) "ON" else "OFF"}", textAlign = TextAlign.Center) }
        item { Text("iPhone connections: $connections", textAlign = TextAlign.Center) }
        item { Text("Last event: $lastEvent", textAlign = TextAlign.Center) }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Start GATT server") },
                onClick = onStartServer,
            )
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Start advertising") },
                onClick = onStartAdvertise,
            )
        }
        if (advertising) {
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Stop advertising") },
                    onClick = onStop,
                )
            }
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Back") },
                onClick = onBack,
            )
        }
    }
}

/** Runs the real crypto provider self-test on-device. */
@Composable
fun CryptoSelfTestScreen(result: String?, onRun: () -> Unit, onBack: () -> Unit) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = DevRoundScreenPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { Text("Crypto self-test", textAlign = TextAlign.Center) }
        item { Text(result ?: "Not run on this watch", textAlign = TextAlign.Center) }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Run self-test") },
                onClick = onRun,
            )
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Back") },
                onClick = onBack,
            )
        }
    }
}

/** Every error shows stage, code, and actionable retry. */
@Composable
fun GearanErrorScreen(error: GearanStageError, onRetry: () -> Unit, onBack: () -> Unit) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = DevRoundScreenPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { Text("Connection failed", textAlign = TextAlign.Center) }
        item { Text("Stage: ${error.stage}", textAlign = TextAlign.Center) }
        item { Text("[${error.code}] ${error.message}", textAlign = TextAlign.Center) }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Retry") },
                onClick = onRetry,
            )
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Back") },
                onClick = onBack,
            )
        }
    }
}

@Composable
fun DeveloperMenuScreen(onSmoke: () -> Unit, onCrypto: () -> Unit, onBack: () -> Unit) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = DevRoundScreenPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { Text("Developer tests", textAlign = TextAlign.Center) }
        item { Text("Runs checks on this watch", textAlign = TextAlign.Center) }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("BLE smoke test") },
                onClick = onSmoke,
            )
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Crypto self-test") },
                onClick = onCrypto,
            )
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Back") },
                onClick = onBack,
            )
        }
    }
}
