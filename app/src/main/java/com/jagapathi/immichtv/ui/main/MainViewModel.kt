package com.jagapathi.immichtv.ui.main

import androidx.lifecycle.ViewModel
import com.jagapathi.immichtv.data.PreferenceRepository

class MainViewModel(private val repository: PreferenceRepository) : ViewModel() {
    val activeProfile = repository.activeProfile

    fun logout() {
        repository.clearCredentials()
    }
}
