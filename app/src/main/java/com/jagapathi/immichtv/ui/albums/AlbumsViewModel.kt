package com.jagapathi.immichtv.ui.albums

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jagapathi.immichtv.data.PreferenceRepository
import com.jagapathi.immichtv.network.ImmichApiService
import com.jagapathi.immichtv.network.ImmichApiService.ThumbnailSize
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

sealed interface AlbumsUiState {
    data object Loading : AlbumsUiState
    data class Error(val message: String) : AlbumsUiState
    data class Ready(val groups: List<AlbumGroup>) : AlbumsUiState
}

/** Loads the albums for the Albums tab: the user's own and those shared with them. */
@HiltViewModel
class AlbumsViewModel @Inject constructor(
    private val apiService: ImmichApiService,
    repository: PreferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow<AlbumsUiState>(AlbumsUiState.Loading)
    val state = _state.asStateFlow()

    private val refreshRequests = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            // collectLatest cancels an in-flight load when the profile changes or a retry starts.
            combine(repository.activeProfile.map { it?.id }.distinctUntilChanged(), refreshRequests) { id, _ -> id }
                .collectLatest { userId ->
                    _state.value = AlbumsUiState.Loading
                    // Without a profile we're mid-logout and about to leave.
                    if (userId != null) _state.value = loadAlbums(userId)
                }
        }
    }

    private suspend fun loadAlbums(userId: String): AlbumsUiState = try {
        val albums = apiService.getAllAlbums().map { album ->
            album.toAlbumUi(userId) { apiService.getAssetThumbnailUrl(it, ThumbnailSize.Thumbnail) }
        }
        AlbumsUiState.Ready(groupByYear(albums))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Failed to load albums", e)
        AlbumsUiState.Error("Couldn't load your albums. ${e.toUserMessage()}")
    }

    fun retry() {
        refreshRequests.value++
    }

    private companion object {
        const val TAG = "AlbumsViewModel"
    }
}
