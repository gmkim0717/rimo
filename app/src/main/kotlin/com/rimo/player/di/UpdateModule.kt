package com.rimo.player.di

import com.rimo.player.BuildConfig
import com.rimo.player.data.update.ApkDownloader
import com.rimo.player.data.update.UpdateInfoFetcher
import com.rimo.player.data.update.UpdateInfoParser
import com.rimo.player.domain.update.RetryPolicy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {

    @Provides
    @Singleton
    fun provideUpdateInfoParser(): UpdateInfoParser =
        UpdateInfoParser(allowInsecureUrls = BuildConfig.DEBUG)

    @Provides
    @Singleton
    fun provideApkDownloader(
        client: OkHttpClient,
        @IoDispatcher io: CoroutineDispatcher,
    ): ApkDownloader = ApkDownloader(client = client, retryPolicy = RetryPolicy(), ioDispatcher = io)

    @Provides
    @Singleton
    fun provideUpdateInfoFetcher(
        client: OkHttpClient,
        @IoDispatcher io: CoroutineDispatcher,
    ): UpdateInfoFetcher =
        UpdateInfoFetcher(client = client, manifestUrl = BuildConfig.UPDATE_URL, ioDispatcher = io)
}
