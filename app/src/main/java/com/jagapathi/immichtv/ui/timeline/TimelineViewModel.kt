package com.jagapathi.immichtv.ui.timeline

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jagapathi.immichtv.data.PreferenceRepository
import com.jagapathi.immichtv.model.TimeBucketDto
import com.jagapathi.immichtv.model.TimelineQuery
import com.jagapathi.immichtv.network.ImmichApiService
import com.jagapathi.immichtv.network.ImmichApiService.ThumbnailSize
import com.jagapathi.immichtv.network.toUserMessage
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth
import java.time.format.DateTimeParseException

sealed interface TimelineUiState {
    data object Loading : TimelineUiState
    data class Error(val message: String) : TimelineUiState
    data class Ready(val layout: TimelineLayout) : TimelineUiState
}

/**
 * Loads a timeline for [query]: first the list of months, then each month's assets as it comes
 * into view (see [loadMonths]), so even a huge library opens quickly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = TimelineViewModel.Factory::class)
class TimelineViewModel @AssistedInject constructor(
    @Assisted private val query: TimelineQuery,
    private val apiService: ImmichApiService,
    repository: PreferenceRepository
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(query: TimelineQuery): TimelineViewModel
    }

    private sealed interface Months {
        data object Loading : Months
        data class Error(val message: String) : Months
        data class Loaded(val serverUrl: String, val months: List<TimelineMonth>) : Months
    }

    private val months = MutableStateFlow<Months>(Months.Loading)
    private val refreshRequests = MutableStateFlow(0)
    private val monthLoads = mutableMapOf<Int, Job>()

    val state: StateFlow<TimelineUiState> = months
        .mapLatest { months ->
            when (months) {
                Months.Loading -> TimelineUiState.Loading
                is Months.Error -> TimelineUiState.Error(months.message)
                is Months.Loaded -> TimelineUiState.Ready(TimelineLayout(months.months))
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, TimelineUiState.Loading)

    private val _monthError = MutableStateFlow<String?>(null)

    /** Why the last month failed to load, until one loads successfully. */
    val monthError = _monthError.asStateFlow()

    init {
        viewModelScope.launch {
            // collectLatest cancels an in-flight load when the profile changes or a retry starts.
            combine(repository.activeProfile.map { it?.id }.distinctUntilChanged(), refreshRequests) { id, _ -> id }
                .collectLatest { profileId ->
                    monthLoads.values.forEach { it.cancel() }
                    monthLoads.clear()
                    months.value = Months.Loading
                    // Without a profile we're mid-logout and about to leave.
                    if (profileId != null) loadBuckets()
                }
        }
    }

    private suspend fun loadBuckets() {
        months.value = try {
            val serverUrl = apiService.baseUrl ?: throw IllegalStateException("Server URL not configured")
            val buckets = apiService.getTimeBuckets(query)
            Months.Loaded(serverUrl, buckets.mapNotNull { it.toTimelineMonth() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load the timeline", e)
            Months.Error("Couldn't load your photos. ${e.toUserMessage()}")
        }
    }

    private fun TimeBucketDto.toTimelineMonth(): TimelineMonth? = try {
        // Buckets look like "2024-05-01" (or a full timestamp on older servers).
        TimelineMonth(timeBucket, YearMonth.parse(timeBucket.take(7)), count)
    } catch (e: DateTimeParseException) {
        Log.w(TAG, "Skipping unreadable time bucket $timeBucket", e)
        null
    }

    fun retry() {
        refreshRequests.value++
    }

    /** Starts loading the assets of any months in [range] that haven't been loaded yet. */
    fun loadMonths(range: IntRange) {
        val loaded = months.value as? Months.Loaded ?: return
        for (index in range) {
            val month = loaded.months.getOrNull(index) ?: continue
            if (month.assets != null || monthLoads[index]?.isActive == true) continue
            monthLoads[index] = viewModelScope.launch { loadMonth(index, month.bucket) }
        }
    }

    private suspend fun loadMonth(index: Int, bucket: String) {
        try {
            val dto = apiService.getTimeBucket(bucket, query)
            val assets = withContext(Dispatchers.Default) { dto.toTimelineAssets() }
            months.update { current ->
                // Ignore the result if the timeline was reloaded meanwhile.
                if (current !is Months.Loaded || current.months.getOrNull(index)?.bucket != bucket) return@update current
                current.copy(months = current.months.toMutableList().also { it[index] = it[index].copy(assets = assets) })
            }
            _monthError.value = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The month stays unloaded, so it's retried the next time it comes into view.
            Log.w(TAG, "Failed to load time bucket $bucket", e)
            _monthError.value = "Couldn't load some photos. ${e.toUserMessage()}"
        }
    }

    fun thumbnailUrl(assetId: String): String? = assetUrl(assetId, ThumbnailSize.Thumbnail)

    fun previewUrl(assetId: String): String? = assetUrl(assetId, ThumbnailSize.Preview)

    // Built from the server the timeline was loaded from, so a logout can't break it mid-frame.
    private fun assetUrl(assetId: String, size: ThumbnailSize): String? {
        val serverUrl = (months.value as? Months.Loaded)?.serverUrl ?: return null
        return apiService.getAssetThumbnailUrl(assetId, size, serverUrl)
    }

    private companion object {
        const val TAG = "TimelineViewModel"
    }
}
