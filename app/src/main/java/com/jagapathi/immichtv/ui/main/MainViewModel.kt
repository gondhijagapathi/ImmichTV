package com.jagapathi.immichtv.ui.main

import androidx.lifecycle.ViewModel
import com.jagapathi.immichtv.data.PreferenceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: PreferenceRepository
) : ViewModel() {
    val activeProfile = repository.activeProfile

    private val _logoutSuccessEvent = MutableStateFlow(false)
    val logoutSuccessEvent = _logoutSuccessEvent.asStateFlow()



    fun logout() {
        repository.clearCredentials()
        _logoutSuccessEvent.value = true
    }

    fun resetLogoutSuccessEvent() {
        _logoutSuccessEvent.value = false
    }
}
