package com.jagapathi.immichtv.ui.albums

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jagapathi.immichtv.data.PreferenceRepository
import com.jagapathi.immichtv.network.ImmichApiService
import com.jagapathi.immichtv.network.ImmichApiService.ThumbnailSize
import com.jagapathi.immichtv.network.toUserMessage
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

sealed interface AlbumUiState {
    data object Loading : AlbumUiState

    /** [isGone] when the album was deleted or unshared, so retrying won't help. */
    data class Error(val message: String, val isGone: Boolean) : AlbumUiState

    data class Ready(val album: AlbumUi) : AlbumUiState
}

/** Loads the details of the album with [albumId] for its page. */
@HiltViewModel(assistedFactory = AlbumViewModel.Factory::class)
class AlbumViewModel @AssistedInject constructor(
    @Assisted private val albumId: String,
    private val apiService: ImmichApiService,
    repository: PreferenceRepository
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(albumId: String): AlbumViewModel
    }

    private val _state = MutableStateFlow<AlbumUiState>(AlbumUiState.Loading)
    val state = _state.asStateFlow()

    private val refreshRequests = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            // collectLatest cancels an in-flight load when the profile changes or a retry starts.
            combine(repository.activeProfile.map { it?.id }.distinctUntilChanged(), refreshRequests) { id, _ -> id }
                .collectLatest { userId ->
                    _state.value = AlbumUiState.Loading
                    // Without a profile we're mid-logout and about to leave.
                    if (userId != null) _state.value = loadAlbum(userId)
                }
        }
    }

    private suspend fun loadAlbum(userId: String): AlbumUiState = try {
        val album = apiService.getAlbum(albumId)
        AlbumUiState.Ready(album.toAlbumUi(userId) { apiService.getAssetThumbnailUrl(it, ThumbnailSize.Thumbnail) })
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Failed to load album $albumId", e)
        // Immich answers 400 for albums that don't exist or that the user can no longer see.
        val status = (e as? ClientRequestException)?.response?.status
        if (status == HttpStatusCode.BadRequest || status == HttpStatusCode.NotFound) {
            AlbumUiState.Error("This album isn't available anymore. It may have been deleted or unshared.", isGone = true)
        } else {
            AlbumUiState.Error("Couldn't load this album. ${e.toUserMessage()}", isGone = false)
        }
    }

    fun retry() {
        refreshRequests.value++
    }

    private companion object {
        const val TAG = "AlbumViewModel"
    }
}
