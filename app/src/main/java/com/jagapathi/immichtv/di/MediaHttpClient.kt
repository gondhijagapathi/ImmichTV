package com.jagapathi.immichtv.di

import javax.inject.Qualifier

/**
 * The HTTP client for images and videos, which adds the API key to requests for the signed-in
 * server since those requests are made by libraries rather than [com.jagapathi.immichtv.network.ImmichApiService].
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MediaHttpClient
