package com.jagapathi.immichtv.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.jagapathi.immichtv.data.AesGcmCipher
import com.jagapathi.immichtv.data.AppSettings
import com.jagapathi.immichtv.data.AppSettingsSerializer
import com.jagapathi.immichtv.data.PreferenceRepository
import com.jagapathi.immichtv.network.ApiKeyInterceptor
import com.jagapathi.immichtv.network.ImmichApiService
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
    fun provideImageLoader(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        preferenceRepository: PreferenceRepository
    ): ImageLoader {
        val imageClient = okHttpClient.newBuilder()
            .addInterceptor(ApiKeyInterceptor(preferenceRepository))
            .build()
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { imageClient }))
            }
            .crossfade(true)
            .build()
    }

    private const val SETTINGS_FILE_NAME = "app_settings"
    private const val SETTINGS_KEY_ALIAS = "app_settings_key"
}
