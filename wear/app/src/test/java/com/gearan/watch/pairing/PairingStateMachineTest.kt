package com.gearan.watch.pairing

import org.junit.Assert.assertEquals
import org.junit.Test

class PairingStateMachineTest {
    @Test
    fun fullPairingPath() {
        val m = GearanPairingStateMachine()
        assertEquals(PairingState.IDLE, m.state)
        m.send(PairingEvent.StartPairing)
        assertEquals(PairingState.SCANNING, m.state)
        m.send(PairingEvent.PeerFound)
        assertEquals(PairingState.WATCH_FOUND, m.state)
        m.send(PairingEvent.LinkConnected)
        assertEquals(PairingState.CONNECTING, m.state)
        m.send(PairingEvent.LinkConnected)
        assertEquals(PairingState.HANDSHAKING, m.state)
        m.send(PairingEvent.HandshakeDone("482731"))
        assertEquals(PairingState.WAITING_FOR_USER_CONFIRMATION, m.state)
        m.send(PairingEvent.UserConfirmed)
        assertEquals(PairingState.SECURING_CONNECTION, m.state)
        m.send(PairingEvent.SessionSecured)
        assertEquals(PairingState.SAVING_TRUSTED_DEVICE, m.state)
        m.send(PairingEvent.TrustedSaved)
        assertEquals(PairingState.INITIAL_SYNC, m.state)
        m.send(PairingEvent.SyncDone)
        assertEquals(PairingState.PAIRED, m.state)
        m.send(PairingEvent.LinkLost)
        assertEquals(PairingState.RECONNECTING, m.state)
        m.send(PairingEvent.Reconnected)
        assertEquals(PairingState.CONNECTED, m.state)
    }

    @Test
    fun cancelAndTimeoutGoToFailedOrIdle() {
        val m = GearanPairingStateMachine()
        m.send(PairingEvent.StartPairing)
        m.send(PairingEvent.Failed(PairingError("TIMEOUT", "t")))
        assertEquals(PairingState.FAILED, m.state)
        m.send(PairingEvent.Reset)
        assertEquals(PairingState.IDLE, m.state)
    }
}
