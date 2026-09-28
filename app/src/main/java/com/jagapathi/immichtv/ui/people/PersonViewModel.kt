package com.jagapathi.immichtv.ui.people

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jagapathi.immichtv.data.PreferenceRepository
import com.jagapathi.immichtv.network.ImmichApiService
import com.jagapathi.immichtv.network.toUserMessage
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

sealed interface PersonUiState {
    data object Loading : PersonUiState

    /** [isGone] when the person was deleted or merged into someone else, so retrying won't help. */
    data class Error(val message: String, val isGone: Boolean) : PersonUiState

    /** [assetCount] is how many photos and videos the person is in. */
    data class Ready(val person: PersonUi, val assetCount: Int) : PersonUiState
}

/** Loads the details of the person with [personId] for their page. */
@HiltViewModel(assistedFactory = PersonViewModel.Factory::class)
class PersonViewModel @AssistedInject constructor(
    @Assisted private val personId: String,
    private val apiService: ImmichApiService,
    repository: PreferenceRepository
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(personId: String): PersonViewModel
    }

    private val _state = MutableStateFlow<PersonUiState>(PersonUiState.Loading)
    val state = _state.asStateFlow()

    private val refreshRequests = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            // collectLatest cancels an in-flight load when the profile changes or a retry starts.
            combine(repository.activeProfile.map { it?.id }.distinctUntilChanged(), refreshRequests) { id, _ -> id }
                .collectLatest { userId ->
                    _state.value = PersonUiState.Loading
                    // Without a profile we're mid-logout and about to leave.
                    if (userId != null) _state.value = loadPerson()
                }
        }
    }

    private suspend fun loadPerson(): PersonUiState = try {
        coroutineScope {
            val person = async { apiService.getPerson(personId) }
            val statistics = async { apiService.getPersonStatistics(personId) }
            PersonUiState.Ready(
                person.await().let { it.toPersonUi(apiService.getPersonThumbnailUrl(it.id, it.updatedAt)) },
                statistics.await().assets
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Failed to load person $personId", e)
        // Immich answers 400 for people that don't exist or that the user can't see.
        val status = (e as? ClientRequestException)?.response?.status
        if (status == HttpStatusCode.BadRequest || status == HttpStatusCode.NotFound) {
            PersonUiState.Error("This person isn't available anymore. They may have been removed or merged with someone else.", isGone = true)
        } else {
            PersonUiState.Error("Couldn't load this person. ${e.toUserMessage()}", isGone = false)
        }
    }

    fun retry() {
        refreshRequests.value++
    }

    private companion object {
        const val TAG = "PersonViewModel"
    }
}
