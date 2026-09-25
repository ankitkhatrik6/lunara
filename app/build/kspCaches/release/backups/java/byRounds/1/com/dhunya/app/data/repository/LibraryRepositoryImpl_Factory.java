package com.dhunya.app.data.repository;

import com.dhunya.app.data.local.dao.FavoriteDao;
import com.dhunya.app.data.local.dao.HistoryDao;
import com.dhunya.app.data.local.dao.PlaylistDao;
import com.dhunya.app.data.local.dao.SongDao;
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
public final class LibraryRepositoryImpl_Factory implements Factory<LibraryRepositoryImpl> {
  private final Provider<FavoriteDao> favoriteDaoProvider;

  private final Provider<HistoryDao> historyDaoProvider;

  private final Provider<PlaylistDao> playlistDaoProvider;

  private final Provider<SongDao> songDaoProvider;

  public LibraryRepositoryImpl_Factory(Provider<FavoriteDao> favoriteDaoProvider,
      Provider<HistoryDao> historyDaoProvider, Provider<PlaylistDao> playlistDaoProvider,
      Provider<SongDao> songDaoProvider) {
    this.favoriteDaoProvider = favoriteDaoProvider;
    this.historyDaoProvider = historyDaoProvider;
    this.playlistDaoProvider = playlistDaoProvider;
    this.songDaoProvider = songDaoProvider;
  }

  @Override
  public LibraryRepositoryImpl get() {
    return newInstance(favoriteDaoProvider.get(), historyDaoProvider.get(), playlistDaoProvider.get(), songDaoProvider.get());
  }

  public static LibraryRepositoryImpl_Factory create(Provider<FavoriteDao> favoriteDaoProvider,
      Provider<HistoryDao> historyDaoProvider, Provider<PlaylistDao> playlistDaoProvider,
      Provider<SongDao> songDaoProvider) {
    return new LibraryRepositoryImpl_Factory(favoriteDaoProvider, historyDaoProvider, playlistDaoProvider, songDaoProvider);
  }

  public static LibraryRepositoryImpl newInstance(FavoriteDao favoriteDao, HistoryDao historyDao,
      PlaylistDao playlistDao, SongDao songDao) {
    return new LibraryRepositoryImpl(favoriteDao, historyDao, playlistDao, songDao);
  }
}
