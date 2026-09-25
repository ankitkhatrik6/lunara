package com.dhunya.app.features.home;

import com.dhunya.app.domain.repository.LibraryRepository;
import com.dhunya.app.domain.repository.MusicRepository;
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
public final class HomeViewModel_Factory implements Factory<HomeViewModel> {
  private final Provider<MusicRepository> musicRepositoryProvider;

  private final Provider<LibraryRepository> libraryRepositoryProvider;

  private final Provider<PlayerManager> playerManagerProvider;

  public HomeViewModel_Factory(Provider<MusicRepository> musicRepositoryProvider,
      Provider<LibraryRepository> libraryRepositoryProvider,
      Provider<PlayerManager> playerManagerProvider) {
    this.musicRepositoryProvider = musicRepositoryProvider;
    this.libraryRepositoryProvider = libraryRepositoryProvider;
    this.playerManagerProvider = playerManagerProvider;
  }

  @Override
  public HomeViewModel get() {
    return newInstance(musicRepositoryProvider.get(), libraryRepositoryProvider.get(), playerManagerProvider.get());
  }

  public static HomeViewModel_Factory create(Provider<MusicRepository> musicRepositoryProvider,
      Provider<LibraryRepository> libraryRepositoryProvider,
      Provider<PlayerManager> playerManagerProvider) {
    return new HomeViewModel_Factory(musicRepositoryProvider, libraryRepositoryProvider, playerManagerProvider);
  }

  public static HomeViewModel newInstance(MusicRepository musicRepository,
      LibraryRepository libraryRepository, PlayerManager playerManager) {
    return new HomeViewModel(musicRepository, libraryRepository, playerManager);
  }
}
