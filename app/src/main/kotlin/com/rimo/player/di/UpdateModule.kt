package com.rimo.player.di

import com.rimo.player.BuildConfig
import com.rimo.player.data.update.UpdateInfoFetcher
import com.rimo.player.data.update.UpdateInfoParser
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
    fun provideUpdateInfoFetcher(
        client: OkHttpClient,
        @IoDispatcher io: CoroutineDispatcher,
    ): UpdateInfoFetcher =
        UpdateInfoFetcher(client = client, manifestUrl = BuildConfig.UPDATE_URL, ioDispatcher = io)
}
