package com.jagapathi.immichtv.data

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.jagapathi.immichtv.model.ImmichCredentials
import com.jagapathi.immichtv.model.UserProfile
import com.jagapathi.immichtv.network.ImmichApiConfig
import kotlinx.coroutines.flow.*

class PreferenceRepository(private val context: Context) : ImmichApiConfig {
    override val baseUrl: String?
        get() = activeProfile.value?.credentials?.serverUrl

    override val apiKey: String?
        get() = activeProfile.value?.credentials?.apiKey
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "immich_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _activeProfile = MutableStateFlow(getActiveProfileInternal())
    val activeProfile: StateFlow<UserProfile?> = _activeProfile.asStateFlow()

    fun getAllProfiles(): List<UserProfile> {
        val profileIds = prefs.getStringSet(KEY_PROFILE_IDS, emptySet()) ?: emptySet()
        return profileIds.mapNotNull { getProfileInternal(it) }
    }

    fun saveProfile(profile: UserProfile) {
        val profileIds = (prefs.getStringSet(KEY_PROFILE_IDS, emptySet()) ?: emptySet()).toMutableSet()
        profileIds.add(profile.id)

        prefs.edit(commit = true) {
            putStringSet(KEY_PROFILE_IDS, profileIds)
            putString(getProfileKey(profile.id, FIELD_NAME), profile.name)
            putString(getProfileKey(profile.id, FIELD_PICTURE_URL), profile.profilePictureUrl)
            putString(getProfileKey(profile.id, FIELD_SERVER_URL), profile.credentials.serverUrl)
            putString(getProfileKey(profile.id, FIELD_API_KEY), profile.credentials.apiKey)
        }

        if (_activeProfile.value?.id == profile.id || _activeProfile.value == null) {
            setActiveProfile(profile.id)
        }
    }

    fun setActiveProfile(profileId: String) {
        val profile = getProfileInternal(profileId)
        if (profile != null) {
            prefs.edit(commit = true) {
                putString(KEY_ACTIVE_PROFILE_ID, profileId)
            }
            _activeProfile.value = profile
        }
    }

    fun deleteProfile(profileId: String) {
        val profileIds = (prefs.getStringSet(KEY_PROFILE_IDS, emptySet()) ?: emptySet()).toMutableSet()
        if (profileIds.remove(profileId)) {
            prefs.edit(commit = true) {
                putStringSet(KEY_PROFILE_IDS, profileIds)
                remove(getProfileKey(profileId, FIELD_NAME))
                remove(getProfileKey(profileId, FIELD_PICTURE_URL))
                remove(getProfileKey(profileId, FIELD_SERVER_URL))
                remove(getProfileKey(profileId, FIELD_API_KEY))
                
                if (prefs.getString(KEY_ACTIVE_PROFILE_ID, null) == profileId) {
                    remove(KEY_ACTIVE_PROFILE_ID)
                }
            }
            
            if (_activeProfile.value?.id == profileId) {
                _activeProfile.value = getActiveProfileInternal()
            }
        }
    }

    fun clearCredentials() {
        val activeId = prefs.getString(KEY_ACTIVE_PROFILE_ID, null)
        if (activeId != null) {
            deleteProfile(activeId)
        } else {
            _activeProfile.value = null
        }
    }

    private val _theme = MutableStateFlow(getThemeInternal())
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    fun setTheme(theme: AppTheme) {
        prefs.edit {
            putString(KEY_THEME, theme.name)
        }
        _theme.value = theme
    }

    private fun getThemeInternal(): AppTheme {
        val themeName = prefs.getString(KEY_THEME, AppTheme.System.name)
        return AppTheme.valueOf(themeName ?: AppTheme.System.name)
    }

    private fun getActiveProfileInternal(): UserProfile? {
        val activeId = prefs.getString(KEY_ACTIVE_PROFILE_ID, null) ?: return null
        return getProfileInternal(activeId)
    }

    private fun getProfileInternal(id: String): UserProfile? {
        val name = prefs.getString(getProfileKey(id, FIELD_NAME), null) ?: return null
        val picUrl = prefs.getString(getProfileKey(id, FIELD_PICTURE_URL), null)
        val url = prefs.getString(getProfileKey(id, FIELD_SERVER_URL), null) ?: return null
        val key = prefs.getString(getProfileKey(id, FIELD_API_KEY), null) ?: return null
        
        return UserProfile(id, name, picUrl, ImmichCredentials(url, key))
    }

    private fun getProfileKey(id: String, field: String) = "profile_${id}_$field"

    companion object {
        private const val KEY_PROFILE_IDS = "profile_ids"
        private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
        private const val KEY_THEME = "app_theme"

        const val FIELD_NAME = "name"
        const val FIELD_PICTURE_URL = "picture_url"
        const val FIELD_SERVER_URL = "server_url"
        const val FIELD_API_KEY = "api_key"
    }
}

enum class AppTheme {
    System, Light, Dark
}
