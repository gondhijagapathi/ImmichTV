package com.jagapathi.immichtv.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.jagapathi.immichtv.model.UserProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

@Serializable
data class AppSettings(
    val profiles: List<UserProfile> = emptyList(),
    val activeProfileId: String? = null,
    val theme: AppTheme = AppTheme.System
) {
    val activeProfile: UserProfile? get() = profiles.find { it.id == activeProfileId }
}

enum class AppTheme {
    System, Light, Dark
}

/**
 * Stores [AppSettings] as JSON encrypted with [cipher], since they contain API keys. Anything that
 * can't be decrypted or parsed (e.g. the Keystore key was lost) is reported as corrupt, so the
 * DataStore's corruption handler resets it instead of the app crashing.
 */
class AppSettingsSerializer(private val cipher: AesGcmCipher) : Serializer<AppSettings> {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    override val defaultValue = AppSettings()

    override suspend fun readFrom(input: InputStream): AppSettings {
        val encrypted = input.readBytes()
        return try {
            json.decodeFromString(cipher.decrypt(encrypted).decodeToString())
        } catch (e: Exception) {
            throw CorruptionException("Stored settings are unreadable", e)
        }
    }

    override suspend fun writeTo(t: AppSettings, output: OutputStream) {
        output.write(cipher.encrypt(json.encodeToString(t).encodeToByteArray()))
    }
}
