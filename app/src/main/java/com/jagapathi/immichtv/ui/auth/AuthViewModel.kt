package com.jagapathi.immichtv.ui.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jagapathi.immichtv.data.PreferenceRepository
import com.jagapathi.immichtv.model.ImmichCredentials
import com.jagapathi.immichtv.model.UserProfile
import com.jagapathi.immichtv.network.ImmichApiService
import com.jagapathi.immichtv.network.LocalAuthServer
import com.jagapathi.immichtv.network.normalizeServerUrl
import com.jagapathi.immichtv.network.toUserMessage
import com.jagapathi.immichtv.util.LocalNetworkMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: PreferenceRepository,
    private val apiService: ImmichApiService,
    localNetworkMonitor: LocalNetworkMonitor
) : ViewModel() {
    private val _serverUrl = MutableStateFlow("")
    val serverUrl = _serverUrl.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey = _apiKey.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _loginSuccessEvent = MutableStateFlow(false)
    val loginSuccessEvent = _loginSuccessEvent.asStateFlow()

    fun resetLoginSuccessEvent() {
        _loginSuccessEvent.value = false
    }

    private val authServer = LocalAuthServer { url, key -> login(url, key) }
    private val authServerPort = MutableStateFlow<Int?>(null)

    private val _isPairingUnavailable = MutableStateFlow(false)
    val isPairingUnavailable = _isPairingUnavailable.asStateFlow()

    /** Address the phone opens to log in, or null until the TV has a local network address. */
    val pairingUrl: StateFlow<String?> = combine(
        localNetworkMonitor.ipv4Address,
        authServerPort,
        authServer.pairingCode
    ) { ip, port, code ->
        if (ip != null && port != null) "http://$ip:$port/$code" else null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            try {
                authServerPort.value = authServer.start()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Couldn't start the login server", e)
                _isPairingUnavailable.value = true
            }
        }
    }

    fun onServerUrlChange(url: String) {
        _serverUrl.value = url
    }

    fun onApiKeyChange(key: String) {
        _apiKey.value = key
    }

    fun login() {
        viewModelScope.launch {
            login(_serverUrl.value, _apiKey.value)
        }
    }

    /**
     * Checks the credentials against the server and saves them as the active profile. Used by both
     * the manual form and the phone login page. Returns null on success, otherwise the error message.
     */
    private suspend fun login(url: String, key: String): String? {
        val serverUrl = normalizeServerUrl(url)
            ?: return showError("Enter the full server address, starting with http:// or https://")
        val apiKey = key.trim()
        if (apiKey.isEmpty()) return showError("Enter your API key.")

        // The phone and the on-screen form can both submit; only run one login at a time.
        if (!_isLoading.compareAndSet(expect = false, update = true)) {
            return "Another login is already in progress. Try again in a moment."
        }
        _errorMessage.value = null
        return try {
            val user = apiService.getUserMe(serverUrl, apiKey)
            val profile = UserProfile(
                id = user.id,
                name = user.name ?: user.email ?: "Immich User",
                profilePictureUrl = apiService.getProfileImageUrl(user.id, serverUrl),
                credentials = ImmichCredentials(serverUrl, apiKey)
            )
            repository.saveProfile(profile)
            repository.setActiveProfile(profile.id)
            _loginSuccessEvent.value = true
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Login failed", e)
            showError(e.toUserMessage())
        } finally {
            _isLoading.value = false
        }
    }

    private fun showError(message: String): String {
        _errorMessage.value = message
        return message
    }

    override fun onCleared() {
        authServer.stop()
    }

    private companion object {
        const val TAG = "AuthViewModel"
    }
}
