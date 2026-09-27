package com.jagapathi.immichtv.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.jagapathi.immichtv.data.AesGcmCipher
import com.jagapathi.immichtv.data.AppSettings
import com.jagapathi.immichtv.data.AppSettingsSerializer
import com.jagapathi.immichtv.data.PreferenceRepository
import com.jagapathi.immichtv.network.ApiKeyInterceptor
import com.jagapathi.immichtv.network.ImmichApiService
import com.jagapathi.immichtv.ui.video.VideoPlayerFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context
    ): DataStore<AppSettings> {
        return DataStoreFactory.create(
            serializer = AppSettingsSerializer(
                AesGcmCipher { AesGcmCipher.androidKeystoreKey(SETTINGS_KEY_ALIAS) }
            ),
            // Settings encrypted with a key this device no longer has can't be recovered; start over.
            corruptionHandler = ReplaceFileCorruptionHandler { AppSettings() },
            produceFile = { context.dataStoreFile(SETTINGS_FILE_NAME) }
        )
    }

    @Provides
    @Singleton
    fun providePreferenceRepository(
        dataStore: DataStore<AppSettings>
    ): PreferenceRepository {
        return PreferenceRepository(dataStore, CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }

    /** Shared by the API client and image loading so they reuse one connection pool. */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient()

    @Provides
    @Singleton
    fun provideImmichApiService(
        okHttpClient: OkHttpClient,
        preferenceRepository: PreferenceRepository
    ): ImmichApiService {
        return ImmichApiService(
            client = ImmichApiService.createDefaultClient(okHttpClient),
            config = preferenceRepository
        )
    }

    @Provides
    @Singleton
    @MediaHttpClient
    fun provideMediaHttpClient(
        okHttpClient: OkHttpClient,
        preferenceRepository: PreferenceRepository
    ): OkHttpClient = okHttpClient.newBuilder()
        .addInterceptor(ApiKeyInterceptor(preferenceRepository))
        .build()

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        @MediaHttpClient mediaHttpClient: OkHttpClient
    ): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { mediaHttpClient }))
            }
            .crossfade(true)
            .build()
    }

    @OptIn(UnstableApi::class)
    @Provides
    fun provideVideoPlayerFactory(
        @ApplicationContext context: Context,
        @MediaHttpClient mediaHttpClient: OkHttpClient
    ): VideoPlayerFactory = VideoPlayerFactory {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(mediaHttpClient)))
            // Pauses when another app starts playing sound.
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setSeekBackIncrementMs(VIDEO_SEEK_INCREMENT_MS)
            .setSeekForwardIncrementMs(VIDEO_SEEK_INCREMENT_MS)
            .build()
    }

    private const val SETTINGS_FILE_NAME = "app_settings"
    private const val SETTINGS_KEY_ALIAS = "app_settings_key"
    private const val VIDEO_SEEK_INCREMENT_MS = 10_000L
}
