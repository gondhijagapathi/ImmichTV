package com.jagapathi.immichtv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jagapathi.immichtv.data.AppTheme
import com.jagapathi.immichtv.data.PreferenceRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: PreferenceRepository) : ViewModel() {

    val currentTheme: StateFlow<AppTheme> = repository.theme

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch {
            repository.setTheme(theme)
        }
    }
}
