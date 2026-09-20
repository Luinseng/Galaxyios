package com.gearan.watch.pairing

import com.gearan.watch.security.CryptoUtils
import com.gearan.watch.security.GearanSecureSession
import com.gearan.watch.util.GearanLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Watch-side pairing orchestrator: advertising -> handshake -> SAS display ->
 * user confirm -> secure -> save trusted -> initial sync signal.
 * 120 s timeout, full error mapping.
 */
class GearanPairingManager(private val scope: CoroutineScope) {
    private val machine = GearanPairingStateMachine()
    private val _state = MutableStateFlow(PairingState.IDLE)
    val state: StateFlow<PairingState> = _state
    private val _sas = MutableStateFlow<String?>(null)
    val sas: StateFlow<String?> = _sas
    private val _error = MutableStateFlow<PairingError?>(null)
    val error: StateFlow<PairingError?> = _error

    val secureSession = GearanSecureSession()
    private var timeoutJob: Job? = null

    private fun emit(s: PairingState) {
        if (s != _state.value) GearanLog.pair("state ${_state.value} -> $s")
        _state.value = s
    }

    fun startPairing(onAdvertise: () -> Unit) {
        reset()
        emit(machine.send(PairingEvent.StartPairing))
        secureSession.beginHandshake()
        onAdvertise()
        armTimeout()
    }

    fun onCentralConnected() {
        emit(machine.send(PairingEvent.LinkConnected))
        emit(machine.send(PairingEvent.LinkConnected)) // CONNECTING -> HANDSHAKING
    }

    fun onHandshakeMaterialReady(): String {
        val sas = secureSession.deriveSession()
        _sas.value = sas
        emit(machine.send(PairingEvent.HandshakeDone(sas)))
        return sas
    }

    fun confirmCode() {
        emit(machine.send(PairingEvent.UserConfirmed))
    }

    fun cancelPairing(reason: String = "user cancelled") {
        emit(machine.send(PairingEvent.UserCancelled))
        fail(PairingError("CANCELLED", reason))
    }

    fun onSessionSecured() = emit(machine.send(PairingEvent.SessionSecured))
    fun onTrustedSaved() = emit(machine.send(PairingEvent.TrustedSaved))
    fun onSyncDone() {
        emit(machine.send(PairingEvent.SyncDone))
        cancelTimeout()
    }

    fun onLinkLost() { emit(machine.send(PairingEvent.LinkLost)) }
    fun onReconnected() { emit(machine.send(PairingEvent.Reconnected)) }

    fun fail(e: PairingError) {
        _error.value = e
        GearanLog.pairWarn("pairing failed [${e.code}]: ${e.message}")
        emit(machine.send(PairingEvent.Failed(e)))
        cancelTimeout()
    }

    fun reset() {
        cancelTimeout()
        _sas.value = null
        _error.value = null
        secureSession.reset()
        machine.send(PairingEvent.Reset)
        emit(machine.state)
    }

    private fun armTimeout() {
        cancelTimeout()
        timeoutJob = scope.launch {
            delay(CryptoUtils.PAIRING_TIMEOUT_MS)
            if (_state.value != PairingState.PAIRED && _state.value != PairingState.CONNECTED) {
                fail(PairingError("TIMEOUT", "Pairing timed out after 120 s"))
            }
        }
    }

    private fun cancelTimeout() { timeoutJob?.cancel(); timeoutJob = null }
}
