package com.jagapathi.immichtv.ui.people

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jagapathi.immichtv.data.PreferenceRepository
import com.jagapathi.immichtv.network.ImmichApiService
import com.jagapathi.immichtv.network.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PeopleUiState {
    data object Loading : PeopleUiState
    data class Error(val message: String) : PeopleUiState
    data class Ready(val people: List<PersonUi>) : PeopleUiState
}

/** Loads the people for the People tab. */
@HiltViewModel
class PeopleViewModel @Inject constructor(
    private val apiService: ImmichApiService,
    repository: PreferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow<PeopleUiState>(PeopleUiState.Loading)
    val state = _state.asStateFlow()

    private val refreshRequests = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            // collectLatest cancels an in-flight load when the profile changes or a retry starts.
            combine(repository.activeProfile.map { it?.id }.distinctUntilChanged(), refreshRequests) { id, _ -> id }
                .collectLatest { userId ->
                    _state.value = PeopleUiState.Loading
                    // Without a profile we're mid-logout and about to leave.
                    if (userId != null) _state.value = loadPeople()
                }
        }
    }

    private suspend fun loadPeople(): PeopleUiState = try {
        val people = apiService.getAllPeople().listedPeople().map { person ->
            person.toPersonUi(apiService.getPersonThumbnailUrl(person.id, person.updatedAt))
        }
        PeopleUiState.Ready(people)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Failed to load people", e)
        PeopleUiState.Error("Couldn't load people. ${e.toUserMessage()}")
    }

    fun retry() {
        refreshRequests.value++
    }

    private companion object {
        const val TAG = "PeopleViewModel"
    }
}
