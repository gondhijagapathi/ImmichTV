package com.jagapathi.immichtv.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jagapathi.immichtv.data.PreferenceRepository
import com.jagapathi.immichtv.model.ImmichCredentials
import com.jagapathi.immichtv.model.UserProfile
import com.jagapathi.immichtv.network.ImmichApiService
import com.jagapathi.immichtv.network.LocalAuthServer
import com.jagapathi.immichtv.util.NetworkUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: PreferenceRepository,
    private val apiService: ImmichApiService
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

    val ipAddress = NetworkUtils.getIpAddress() ?: "127.0.0.1"
    val serverUrlForQr = "http://$ipAddress:8080"

    private val authServer = LocalAuthServer { url, key ->
        // The Ktor server invokes this callback on a background Netty thread.
        // We MUST ensure the login process and state updates happen on the Main thread.
        viewModelScope.launch {
            fetchAndSaveProfile(url, key)
        }
    }

    init {
        authServer.start()
    }

    fun onServerUrlChange(url: String) {
        _serverUrl.value = url
    }

    fun onApiKeyChange(key: String) {
        _apiKey.value = key
    }

    private fun fetchAndSaveProfile(url: String, key: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                // Ensure the URL is trimmed of whitespace before making the request
                val trimmedUrl = url.trim()
                val trimmedKey = key.trim()
                
                println("AuthViewModel: Attempting to authenticate with Immich server: $trimmedUrl")
                val user = apiService.getUserMe(trimmedUrl, trimmedKey)
                println("AuthViewModel: Authentication successful! User ID: ${user.id}")
                
                val profile = UserProfile(
                    id = user.id,
                    name = user.name ?: user.email ?: "Immich User",
                    profilePictureUrl = apiService.getProfileImageUrl(trimmedUrl, user.id),
                    credentials = ImmichCredentials(trimmedUrl, trimmedKey)
                )
                repository.saveProfile(profile)
                
                // Fire the success callback
                _loginSuccessEvent.value = true
                
            } catch (e: Exception) {
                e.printStackTrace()
                println("AuthViewModel: Authentication failed: ${e.message}")
                _errorMessage.value = "Login failed: ${e.localizedMessage ?: e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun login() {
        if (_serverUrl.value.isNotEmpty() && _apiKey.value.isNotEmpty()) {
            fetchAndSaveProfile(_serverUrl.value, _apiKey.value)
        }
    }

    override fun onCleared() {
        authServer.stop()
    }
}
