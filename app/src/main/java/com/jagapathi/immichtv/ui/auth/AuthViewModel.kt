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

class AuthViewModel(
    private val repository: PreferenceRepository,
    private val apiService: ImmichApiService = ImmichApiService()
) : ViewModel() {
    private val _serverUrl = MutableStateFlow("")
    val serverUrl = _serverUrl.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey = _apiKey.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    val ipAddress = NetworkUtils.getIpAddress() ?: "127.0.0.1"
    val serverUrlForQr = "http://$ipAddress:8080"

    private val authServer = LocalAuthServer { url, key ->
        fetchAndSaveProfile(url, key)
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
                val user = apiService.getUserMe(url, key)
                val profile = UserProfile(
                    id = user.id,
                    name = user.name ?: user.email ?: "Immich User",
                    profilePictureUrl = apiService.getProfileImageUrl(url, user.id),
                    credentials = ImmichCredentials(url, key)
                )
                repository.saveProfile(profile)
            } catch (e: Exception) {
                _errorMessage.value = "Login failed: ${e.message}"
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
