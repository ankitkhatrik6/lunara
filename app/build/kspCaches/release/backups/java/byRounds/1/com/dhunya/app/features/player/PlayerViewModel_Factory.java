package com.dhunya.app.features.player;

import com.dhunya.app.domain.repository.DownloadRepository;
import com.dhunya.app.domain.repository.LibraryRepository;
import com.dhunya.app.player.PlayerManager;
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
public final class PlayerViewModel_Factory implements Factory<PlayerViewModel> {
  private final Provider<PlayerManager> playerManagerProvider;

  private final Provider<LibraryRepository> libraryRepositoryProvider;

  private final Provider<DownloadRepository> downloadRepositoryProvider;

  public PlayerViewModel_Factory(Provider<PlayerManager> playerManagerProvider,
      Provider<LibraryRepository> libraryRepositoryProvider,
      Provider<DownloadRepository> downloadRepositoryProvider) {
    this.playerManagerProvider = playerManagerProvider;
    this.libraryRepositoryProvider = libraryRepositoryProvider;
    this.downloadRepositoryProvider = downloadRepositoryProvider;
  }

  @Override
  public PlayerViewModel get() {
    return newInstance(playerManagerProvider.get(), libraryRepositoryProvider.get(), downloadRepositoryProvider.get());
  }

  public static PlayerViewModel_Factory create(Provider<PlayerManager> playerManagerProvider,
      Provider<LibraryRepository> libraryRepositoryProvider,
      Provider<DownloadRepository> downloadRepositoryProvider) {
    return new PlayerViewModel_Factory(playerManagerProvider, libraryRepositoryProvider, downloadRepositoryProvider);
  }

  public static PlayerViewModel newInstance(PlayerManager playerManager,
      LibraryRepository libraryRepository, DownloadRepository downloadRepository) {
    return new PlayerViewModel(playerManager, libraryRepository, downloadRepository);
  }
}
