package com.jagapathi.immichtv.data

import android.util.Log
import androidx.datastore.core.DataStore
import com.jagapathi.immichtv.model.UserProfile
import com.jagapathi.immichtv.network.ImmichApiConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import java.io.IOException

class PreferenceRepository(
    private val dataStore: DataStore<AppSettings>,
    private val scope: CoroutineScope
) : ImmichApiConfig {

    /** The stored settings, or null until they have been read from disk. */
    val settings: StateFlow<AppSettings?> = dataStore.data
        .catch { e ->
            if (e !is IOException) throw e
            Log.e(TAG, "Couldn't read settings", e)
            emit(AppSettings())
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val activeProfile: StateFlow<UserProfile?> = settings.mapState { it?.activeProfile }

    val theme: StateFlow<AppTheme> = settings.mapState { it?.theme ?: AppTheme.System }

    override val baseUrl: String?
        get() = settings.value?.activeProfile?.credentials?.serverUrl

    override val apiKey: String?
        get() = settings.value?.activeProfile?.credentials?.apiKey

    suspend fun getAllProfiles(): List<UserProfile> = dataStore.data.first().profiles

    /** Adds or updates [profile]. It becomes active if no other profile is. */
    suspend fun saveProfile(profile: UserProfile) {
        dataStore.updateData { settings ->
            val exists = settings.profiles.any { it.id == profile.id }
            settings.copy(
                profiles = if (exists) {
                    settings.profiles.map { if (it.id == profile.id) profile else it }
                } else {
                    settings.profiles + profile
                },
                activeProfileId = settings.activeProfile?.id ?: profile.id
            )
        }
    }

    suspend fun setActiveProfile(profileId: String) {
        dataStore.updateData { settings ->
            if (settings.profiles.any { it.id == profileId }) {
                settings.copy(activeProfileId = profileId)
            } else {
                settings
            }
        }
    }

    suspend fun deleteProfile(profileId: String) {
        dataStore.updateData { settings ->
            settings.copy(
                profiles = settings.profiles.filterNot { it.id == profileId },
                activeProfileId = settings.activeProfileId.takeIf { it != profileId }
            )
        }
    }

    suspend fun clearCredentials() {
        val activeId = dataStore.data.first().activeProfileId ?: return
        deleteProfile(activeId)
    }

    suspend fun setTheme(theme: AppTheme) {
        dataStore.updateData { it.copy(theme = theme) }
    }

    private fun <T> StateFlow<AppSettings?>.mapState(transform: (AppSettings?) -> T): StateFlow<T> =
        map(transform).stateIn(scope, SharingStarted.Eagerly, transform(value))

    private companion object {
        const val TAG = "PreferenceRepository"
    }
}
