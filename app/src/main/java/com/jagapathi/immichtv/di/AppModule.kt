package com.jagapathi.immichtv.di

import android.content.Context
import com.jagapathi.immichtv.data.PreferenceRepository
import com.jagapathi.immichtv.network.ImmichApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun providePreferenceRepository(
        @ApplicationContext context: Context
    ): PreferenceRepository {
        return PreferenceRepository(context)
    }

    @Provides
    @Singleton
    fun provideImmichApiService(
        preferenceRepository: PreferenceRepository
    ): ImmichApiService {
        return ImmichApiService(config = preferenceRepository)
    }
}
