package com.gearan.watch.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private val Context.dataStore by preferencesDataStore(name = "gearan_watch")

/**
 * Watch-side trusted store: the paired iPhone record + own GearanDeviceID.
 * Identity material lives in Android Keystore (IdentityStore); session keys are
 * never persisted. Survives reboot; Forget iPhone wipes it.
 */
class TrustedDeviceStore(private val context: Context) {
    private val keyDevice = stringPreferencesKey("trusted_iphone_json")
    private val keyOwnId = stringPreferencesKey("own_gear_device_id")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun ownDeviceId(): String {
        val existing = context.dataStore.data.map { it[keyOwnId] }.first()
        if (existing != null) return existing
        val fresh = java.util.UUID.randomUUID().toString()
        context.dataStore.edit { it[keyOwnId] = fresh }
        return fresh
    }

    suspend fun saveTrustedPhone(device: TrustedDevice) {
        context.dataStore.edit { it[keyDevice] = json.encodeToString(device) }
    }

    suspend fun loadTrustedPhone(): TrustedDevice? {
        val raw = context.dataStore.data.map { it[keyDevice] }.first() ?: return null
        return json.decodeFromString<TrustedDevice>(raw)
    }

    suspend fun updateLastSeen(isoNow: String) {
        val cur = loadTrustedPhone() ?: return
        saveTrustedPhone(cur.copy(lastSeen = isoNow))
    }

    suspend fun forgetPhone() {
        context.dataStore.edit { it.remove(keyDevice) }
        // Best-effort wipe of encrypted backup file if present.
        try { File(context.filesDir, "gearan_backup.enc").delete() } catch (_: Exception) { }
    }

    /** Encrypted backup helper (MasterKey + EncryptedFile, standard API). */
    fun encryptedBackupFile(): EncryptedFile {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedFile.Builder(
            context,
            File(context.filesDir, "gearan_backup.enc"),
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
    }
}
