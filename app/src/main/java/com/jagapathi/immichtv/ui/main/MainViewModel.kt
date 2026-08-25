package com.jagapathi.immichtv.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jagapathi.immichtv.data.PreferenceRepository
import com.jagapathi.immichtv.model.ImmichPersonResponseDto
import com.jagapathi.immichtv.network.ImmichApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: PreferenceRepository,
    private val apiService: ImmichApiService
) : ViewModel() {
    val activeProfile = repository.activeProfile

    private val _people = MutableStateFlow<List<ImmichPersonResponseDto>>(emptyList())
    val people = _people.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _logoutSuccessEvent = MutableStateFlow(false)
    val logoutSuccessEvent = _logoutSuccessEvent.asStateFlow()

    init {
        viewModelScope.launch {
            activeProfile.collectLatest { profile ->
                if (profile != null) {
                    fetchPeople()
                } else {
                    _people.value = emptyList()
                }
            }
        }
    }

    private fun fetchPeople() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val peopleList = withContext(Dispatchers.IO) {
                    val response = apiService.getPeople()
                    // Filter people who have a name and are marked as favorite
                    response.people.filter {
                        it.name.isNotBlank() && it.isFavorite
                    }
                }
                _people.value = peopleList
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = "Failed to fetch people: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun getPersonThumbnailUrl(id: String): String {
        return apiService.getPersonThumbnailUrl(id)
    }

    fun logout() {
        repository.clearCredentials()
        _logoutSuccessEvent.value = true
    }

    fun resetLogoutSuccessEvent() {
        _logoutSuccessEvent.value = false
    }
}
