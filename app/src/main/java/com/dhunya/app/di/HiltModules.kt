package com.dhunya.app.di

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.dhunya.app.core.constants.AppConstants
import com.dhunya.app.core.network.HttpClientFactory
import com.dhunya.app.data.local.PreferencesDataStore
import com.dhunya.app.data.local.dao.*
import com.dhunya.app.data.local.database.DhunyaDatabase
import com.dhunya.app.data.repository.*
import com.dhunya.app.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClientFactory.create()

    @Provides
    @Singleton
    fun provideOkHttpClient(): okhttp3.OkHttpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DhunyaDatabase {
        return Room.databaseBuilder(
            context,
            DhunyaDatabase::class.java,
            AppConstants.DATABASE_NAME
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideSongDao(database: DhunyaDatabase): SongDao = database.songDao()

    @Provides
    fun provideFavoriteDao(database: DhunyaDatabase): FavoriteDao = database.favoriteDao()

    @Provides
    fun provideHistoryDao(database: DhunyaDatabase): HistoryDao = database.historyDao()

    @Provides
    fun providePlaylistDao(database: DhunyaDatabase): PlaylistDao = database.playlistDao()

    @Provides
    fun provideDownloadDao(database: DhunyaDatabase): DownloadDao = database.downloadDao()

    @Provides
    fun provideRecentSearchDao(database: DhunyaDatabase): RecentSearchDao = database.recentSearchDao()
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideContext(application: Application): Context = application.applicationContext

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): PreferencesDataStore {
        return PreferencesDataStore(context)
    }

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): androidx.work.WorkManager {
        return androidx.work.WorkManager.getInstance(context)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindMusicRepository(impl: MusicRepositoryImpl): MusicRepository

    @Binds
    @Singleton
    abstract fun bindLyricsRepository(impl: LyricsRepositoryImpl): LyricsRepository

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(impl: LibraryRepositoryImpl): LibraryRepository

    @Binds
    @Singleton
    abstract fun bindDownloadRepository(impl: DownloadRepositoryImpl): DownloadRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
