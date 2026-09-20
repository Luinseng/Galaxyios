package com.gearan.watch.util

import android.util.Log

/**
 * Consistent diagnostic logging. `adb logcat | grep GEARAN` shows everything.
 * NEVER log: private keys, session keys, nonces, health data, notification content.
 * SAS codes are user-visible by design and may be logged as MATCHED/MISMATCH only.
 */
object GearanLog {
    const val BLE = "GEARAN_BLE"
    const val PAIR = "GEARAN_PAIR"
    const val CRYPTO = "GEARAN_CRYPTO"
    const val LINK = "GEARAN_LINK"
    const val SYNC = "GEARAN_SYNC"

    fun ble(msg: String) = Log.i(BLE, msg)
    fun bleWarn(msg: String, t: Throwable? = null) = Log.w(BLE, msg, t)
    fun pair(msg: String) = Log.i(PAIR, msg)
    fun pairWarn(msg: String, t: Throwable? = null) = Log.w(PAIR, msg, t)
    fun crypto(msg: String) = Log.i(CRYPTO, msg)
    fun cryptoWarn(msg: String, t: Throwable? = null) = Log.w(CRYPTO, msg, t)
    fun link(msg: String) = Log.i(LINK, msg)
    fun linkWarn(msg: String, t: Throwable? = null) = Log.w(LINK, msg, t)
    fun sync(msg: String) = Log.i(SYNC, msg)
    fun syncWarn(msg: String, t: Throwable? = null) = Log.w(SYNC, msg, t)
}
