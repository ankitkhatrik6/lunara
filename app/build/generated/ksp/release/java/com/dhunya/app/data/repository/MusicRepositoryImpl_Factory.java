package com.dhunya.app.data.repository;

import com.dhunya.app.data.local.LocalAudioDataSource;
import com.dhunya.app.data.local.dao.FavoriteDao;
import com.dhunya.app.data.local.dao.RecentSearchDao;
import com.dhunya.app.data.local.dao.SongDao;
import com.dhunya.app.data.remote.music.MusicRemoteDataSource;
import com.dhunya.app.data.remote.music.YouTubeMusicRemoteDataSource;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation"
})
public final class MusicRepositoryImpl_Factory implements Factory<MusicRepositoryImpl> {
  private final Provider<MusicRemoteDataSource> remoteSourceProvider;

  private final Provider<YouTubeMusicRemoteDataSource> youtubeSourceProvider;

  private final Provider<LocalAudioDataSource> localAudioSourceProvider;

  private final Provider<SongDao> songDaoProvider;

  private final Provider<RecentSearchDao> recentSearchDaoProvider;

  private final Provider<FavoriteDao> favoriteDaoProvider;

  public MusicRepositoryImpl_Factory(Provider<MusicRemoteDataSource> remoteSourceProvider,
      Provider<YouTubeMusicRemoteDataSource> youtubeSourceProvider,
      Provider<LocalAudioDataSource> localAudioSourceProvider, Provider<SongDao> songDaoProvider,
      Provider<RecentSearchDao> recentSearchDaoProvider,
      Provider<FavoriteDao> favoriteDaoProvider) {
    this.remoteSourceProvider = remoteSourceProvider;
    this.youtubeSourceProvider = youtubeSourceProvider;
    this.localAudioSourceProvider = localAudioSourceProvider;
    this.songDaoProvider = songDaoProvider;
    this.recentSearchDaoProvider = recentSearchDaoProvider;
    this.favoriteDaoProvider = favoriteDaoProvider;
  }

  @Override
  public MusicRepositoryImpl get() {
    return newInstance(remoteSourceProvider.get(), youtubeSourceProvider.get(), localAudioSourceProvider.get(), songDaoProvider.get(), recentSearchDaoProvider.get(), favoriteDaoProvider.get());
  }

  public static MusicRepositoryImpl_Factory create(
      Provider<MusicRemoteDataSource> remoteSourceProvider,
      Provider<YouTubeMusicRemoteDataSource> youtubeSourceProvider,
      Provider<LocalAudioDataSource> localAudioSourceProvider, Provider<SongDao> songDaoProvider,
      Provider<RecentSearchDao> recentSearchDaoProvider,
      Provider<FavoriteDao> favoriteDaoProvider) {
    return new MusicRepositoryImpl_Factory(remoteSourceProvider, youtubeSourceProvider, localAudioSourceProvider, songDaoProvider, recentSearchDaoProvider, favoriteDaoProvider);
  }

  public static MusicRepositoryImpl newInstance(MusicRemoteDataSource remoteSource,
      YouTubeMusicRemoteDataSource youtubeSource, LocalAudioDataSource localAudioSource,
      SongDao songDao, RecentSearchDao recentSearchDao, FavoriteDao favoriteDao) {
    return new MusicRepositoryImpl(remoteSource, youtubeSource, localAudioSource, songDao, recentSearchDao, favoriteDao);
  }
}
