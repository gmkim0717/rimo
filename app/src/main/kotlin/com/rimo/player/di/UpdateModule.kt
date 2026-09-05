package com.rimo.player.di

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.rimo.player.BuildConfig
import com.rimo.player.data.update.ApkDownloader
import com.rimo.player.data.update.LocalUpdateStore
import com.rimo.player.data.update.UpdateInfoFetcher
import com.rimo.player.data.update.UpdateInfoParser
import com.rimo.player.domain.update.ApkSource
import com.rimo.player.domain.update.ManifestSource
import com.rimo.player.domain.update.RetryPolicy
import com.rimo.player.domain.update.UpdateCoordinator
import com.rimo.player.domain.update.UpdateStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {

    private const val TAG = "UpdateCoordinator"

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

    @Provides
    @Singleton
    fun provideUpdateDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("update_prefs") }

    @Provides
    @Singleton
    fun provideUpdateStore(
        @ApplicationContext context: Context,
        dataStore: DataStore<Preferences>,
        @IoDispatcher io: CoroutineDispatcher,
    ): UpdateStore = LocalUpdateStore(dataStore, context.filesDir, io)

    @Provides
    @Singleton
    fun provideUpdateCoordinator(
        fetcher: UpdateInfoFetcher,
        parser: UpdateInfoParser,
        downloader: ApkDownloader,
        store: UpdateStore,
        @ApplicationScope scope: CoroutineScope,
    ): UpdateCoordinator {
        val manifest = ManifestSource { fetcher.fetch()?.let(parser::parse) }
        val apk = ApkSource { info, target ->
            when (val r = downloader.download(info, target)) {
                is ApkDownloader.Result.Success -> true
                is ApkDownloader.Result.Failure -> {
                    Log.i(TAG, "download failed: ${r.reason} after ${r.attempts} attempt(s)")
                    false
                }
            }
        }
        return UpdateCoordinator(
            currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
            manifest = manifest,
            apk = apk,
            store = store,
            scope = scope,
            log = { Log.i(TAG, it) },
        )
    }
}
