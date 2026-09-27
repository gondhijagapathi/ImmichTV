package com.jagapathi.immichtv.ui.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jagapathi.immichtv.data.PreferenceRepository
import com.jagapathi.immichtv.network.ImmichApiService
import com.jagapathi.immichtv.network.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: PreferenceRepository,
    private val apiService: ImmichApiService
) : ViewModel() {
    val activeProfile = repository.activeProfile

    private val _people = MutableStateFlow<PeopleUiState>(PeopleUiState.Loading)
    val people = _people.asStateFlow()

    private val refreshRequests = MutableStateFlow(0)

    private val _logoutSuccessEvent = MutableStateFlow(false)
    val logoutSuccessEvent = _logoutSuccessEvent.asStateFlow()

    init {
        viewModelScope.launch {
            // collectLatest cancels an in-flight fetch when the profile changes or a retry starts.
            combine(activeProfile, refreshRequests) { profile, _ -> profile }
                .collectLatest { profile ->
                    if (profile != null) {
                        fetchPeople()
                    } else {
                        // Settings are still loading, or we're mid-logout and about to leave.
                        _people.value = PeopleUiState.Loading
                    }
                }
        }
    }

    private suspend fun fetchPeople() {
        _people.value = PeopleUiState.Loading
        _people.value = try {
            val people = apiService.getAllPeople()
                .filter { it.name.isNotBlank() && !it.isHidden }
                .sortedByDescending { it.isFavorite }
                .map { PersonUi(it.id, it.name, apiService.getPersonThumbnailUrl(it.id)) }
            PeopleUiState.Success(people)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch people", e)
            PeopleUiState.Error("Couldn't load people. ${e.toUserMessage()}")
        }
    }

    fun retry() {
        refreshRequests.value++
    }

    fun logout() {
        viewModelScope.launch {
            repository.clearCredentials()
            _logoutSuccessEvent.value = true
        }
    }

    fun resetLogoutSuccessEvent() {
        _logoutSuccessEvent.value = false
    }

    private companion object {
        const val TAG = "MainViewModel"
    }
}
