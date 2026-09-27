package com.jagapathi.immichtv

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import javax.inject.Provider

@HiltAndroidApp
class ImmichTVApplication : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var imageLoader: Provider<ImageLoader>

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()
}
