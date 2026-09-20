package com.gearan.watch.pairing

/** Full pairing lifecycle. Mirrors ios/GearanPairingManager states 1:1. */
enum class PairingState {
    IDLE,
    SCANNING, // watch: advertising (peripheral equivalent)
    WATCH_FOUND,
    CONNECTING,
    HANDSHAKING,
    WAITING_FOR_USER_CONFIRMATION,
    SECURING_CONNECTION,
    SAVING_TRUSTED_DEVICE,
    INITIAL_SYNC,
    PAIRED,
    RECONNECTING,
    CONNECTED,
    DISCONNECTED,
    FAILED,
}

data class PairingError(val code: String, val message: String)

sealed class PairingEvent {
    data object StartPairing : PairingEvent()
    data object PeerFound : PairingEvent()
    data object LinkConnected : PairingEvent()
    data class HandshakeDone(val sas: String) : PairingEvent()
    data object UserConfirmed : PairingEvent()
    data object UserCancelled : PairingEvent()
    data object SessionSecured : PairingEvent()
    data object TrustedSaved : PairingEvent()
    data object SyncDone : PairingEvent()
    data object LinkLost : PairingEvent()
    data object Reconnected : PairingEvent()
    data class Failed(val error: PairingError) : PairingEvent()
    data object Reset : PairingEvent()
}

/** Pure state machine — unit-tested without Bluetooth. Timeout enforced by caller. */
class GearanPairingStateMachine(initial: PairingState = PairingState.IDLE) {
    var state: PairingState = initial
        private set

    fun send(event: PairingEvent): PairingState {
        state = when (state) {
            PairingState.IDLE -> when (event) {
                is PairingEvent.StartPairing -> PairingState.SCANNING
                else -> state
            }
            PairingState.SCANNING -> when (event) {
                is PairingEvent.PeerFound -> PairingState.WATCH_FOUND
                is PairingEvent.LinkConnected -> PairingState.CONNECTING
                is PairingEvent.Failed -> PairingState.FAILED
                is PairingEvent.Reset -> PairingState.IDLE
                else -> state
            }
            PairingState.WATCH_FOUND -> when (event) {
                is PairingEvent.LinkConnected -> PairingState.CONNECTING
                is PairingEvent.Failed -> PairingState.FAILED
                is PairingEvent.Reset -> PairingState.IDLE
                else -> state
            }
            PairingState.CONNECTING -> when (event) {
                is PairingEvent.LinkConnected -> PairingState.HANDSHAKING
                is PairingEvent.HandshakeDone -> PairingState.WAITING_FOR_USER_CONFIRMATION
                is PairingEvent.Failed -> PairingState.FAILED
                is PairingEvent.Reset -> PairingState.IDLE
                else -> state
            }
            PairingState.HANDSHAKING -> when (event) {
                is PairingEvent.HandshakeDone -> PairingState.WAITING_FOR_USER_CONFIRMATION
                is PairingEvent.Failed -> PairingState.FAILED
                is PairingEvent.Reset -> PairingState.IDLE
                else -> state
            }
            PairingState.WAITING_FOR_USER_CONFIRMATION -> when (event) {
                is PairingEvent.UserConfirmed -> PairingState.SECURING_CONNECTION
                is PairingEvent.UserCancelled -> PairingState.DISCONNECTED
                is PairingEvent.Failed -> PairingState.FAILED
                is PairingEvent.Reset -> PairingState.IDLE
                else -> state
            }
            PairingState.SECURING_CONNECTION -> when (event) {
                is PairingEvent.SessionSecured -> PairingState.SAVING_TRUSTED_DEVICE
                is PairingEvent.Failed -> PairingState.FAILED
                else -> state
            }
            PairingState.SAVING_TRUSTED_DEVICE -> when (event) {
                is PairingEvent.TrustedSaved -> PairingState.INITIAL_SYNC
                is PairingEvent.Failed -> PairingState.FAILED
                else -> state
            }
            PairingState.INITIAL_SYNC -> when (event) {
                is PairingEvent.SyncDone -> PairingState.PAIRED
                is PairingEvent.Failed -> PairingState.FAILED
                else -> state
            }
            PairingState.PAIRED -> when (event) {
                is PairingEvent.Reconnected -> PairingState.CONNECTED
                is PairingEvent.LinkLost -> PairingState.RECONNECTING
                is PairingEvent.Reset -> PairingState.IDLE
                else -> state
            }
            PairingState.RECONNECTING -> when (event) {
                is PairingEvent.Reconnected -> PairingState.CONNECTED
                is PairingEvent.Failed -> PairingState.FAILED
                is PairingEvent.Reset -> PairingState.IDLE
                else -> state
            }
            PairingState.CONNECTED -> when (event) {
                is PairingEvent.LinkLost -> PairingState.RECONNECTING
                is PairingEvent.Reset -> PairingState.IDLE
                else -> state
            }
            PairingState.DISCONNECTED -> when (event) {
                is PairingEvent.StartPairing -> PairingState.SCANNING
                is PairingEvent.Reset -> PairingState.IDLE
                else -> state
            }
            PairingState.FAILED -> when (event) {
                is PairingEvent.Reset -> PairingState.IDLE
                is PairingEvent.StartPairing -> PairingState.SCANNING
                else -> state
            }
        }
        return state
    }
}
