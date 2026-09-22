package com.gearan.watch

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.gearan.watch.ble.BlePermissions
import com.gearan.watch.ble.GearanAdvertiser
import com.gearan.watch.ble.GearanPeripheralService
import com.gearan.watch.pairing.GearanPairingManager
import com.gearan.watch.pairing.PairingState
import com.gearan.watch.security.CryptoSelfTest
import com.gearan.watch.storage.TrustedDeviceStore
import com.gearan.watch.sync.BatteryMonitor
import com.gearan.watch.sync.CapsBitmask
import com.gearan.watch.ui.screens.BleSmokeTestScreen
import com.gearan.watch.ui.screens.CryptoSelfTestScreen
import com.gearan.watch.ui.screens.DeveloperMenuScreen
import com.gearan.watch.ui.screens.HomeScreen
import com.gearan.watch.ui.screens.PairingScreen
import com.gearan.watch.ui.screens.PermissionGate
import com.gearan.watch.ui.theme.GearanTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val advertiser = GearanAdvertiser.get(applicationContext)
        setContent {
            GearanTheme {
                val scope = rememberCoroutineScope()
                val pairing = remember { GearanPairingManager(scope) }
                val state by pairing.state.collectAsState()
                val sas by pairing.sas.collectAsState()
                val advertising by advertiser.advertising.collectAsState()
                val batteryMonitor = remember { BatteryMonitor(this) }
                val battery by batteryMonitor.battery.collectAsState()
                var missing by remember { mutableStateOf(BlePermissions.missing(this)) }
                var cryptoResult by remember { mutableStateOf<String?>(null) }
                val nav = rememberSwipeDismissableNavController()
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { missing = BlePermissions.missing(this) }

                LaunchedEffect(Unit) {
                    batteryMonitor.refresh()
                    if (missing.isNotEmpty()) {
                        launcher.launch(BlePermissions.required())
                    }
                }

                if (missing.isNotEmpty()) {
                    PermissionGate(missing = missing, onGrant = { launcher.launch(BlePermissions.required()) })
                    return@GearanTheme
                }

                SwipeDismissableNavHost(navController = nav, startDestination = "home") {
                    composable("home") {
                        when {
                            state == PairingState.WAITING_FOR_USER_CONFIRMATION -> PairingScreen(
                                sas = sas,
                                state = state,
                                onConfirm = { pairing.confirmCode() },
                                onCancel = { pairing.cancelPairing() }
                            )
                            else -> HomeScreen(
                                pairingState = state,
                                advertising = advertising,
                                batteryText = if (battery.percent >= 0) {
                                    "Battery: ${battery.percent}%${if (battery.charging) " (charging)" else ""}"
                                } else null,
                                onPair = {
                                    pairing.startPairing {
                                        scope.launch {
                                            val store = TrustedDeviceStore(this@MainActivity)
                                            val shortId = store.ownDeviceId()
                                                .hashCode().toString().toByteArray().copyOf(8)
                                            advertiser.startPairingMode(shortId, CapsBitmask.encode(emptyList()))
                                        }
                                    }
                                    startForegroundService(Intent(this@MainActivity, GearanPeripheralService::class.java))
                                },
                                onDeveloper = { nav.navigate("developer") }
                            )
                        }
                    }
                    composable("developer") {
                        DeveloperMenuScreen(
                            onSmoke = { nav.navigate("smoke") },
                            onCrypto = { nav.navigate("crypto") },
                            onBack = { nav.popBackStack() }
                        )
                    }
                    composable("smoke") {
                        BleSmokeTestScreen(
                            advertising = advertising,
                            onStartAdvertise = {
                                scope.launch {
                                    val store = TrustedDeviceStore(this@MainActivity)
                                    val shortId = store.ownDeviceId()
                                        .hashCode().toString().toByteArray().copyOf(8)
                                    advertiser.startPairingMode(shortId, CapsBitmask.encode(emptyList()))
                                }
                            },
                            onStartServer = {
                                startForegroundService(Intent(this@MainActivity, GearanPeripheralService::class.java))
                            },
                            onStop = { advertiser.stop() },
                            onBack = { nav.popBackStack() }
                        )
                    }
                    composable("crypto") {
                        CryptoSelfTestScreen(
                            result = cryptoResult,
                            onRun = {
                                scope.launch {
                                    val r = CryptoSelfTest.run()
                                    cryptoResult = if (r.passed) "Crypto self-test:\nPASS\n${r.detail}" else r.detail
                                }
                            },
                            onBack = { nav.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
