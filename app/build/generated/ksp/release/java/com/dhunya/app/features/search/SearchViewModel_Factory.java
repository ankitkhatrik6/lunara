package com.dhunya.app.features.search;

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
public final class SearchViewModel_Factory implements Factory<SearchViewModel> {
  private final Provider<MusicRepository> musicRepositoryProvider;

  private final Provider<LibraryRepository> libraryRepositoryProvider;

  private final Provider<PlayerManager> playerManagerProvider;

  public SearchViewModel_Factory(Provider<MusicRepository> musicRepositoryProvider,
      Provider<LibraryRepository> libraryRepositoryProvider,
      Provider<PlayerManager> playerManagerProvider) {
    this.musicRepositoryProvider = musicRepositoryProvider;
    this.libraryRepositoryProvider = libraryRepositoryProvider;
    this.playerManagerProvider = playerManagerProvider;
  }

  @Override
  public SearchViewModel get() {
    return newInstance(musicRepositoryProvider.get(), libraryRepositoryProvider.get(), playerManagerProvider.get());
  }

  public static SearchViewModel_Factory create(Provider<MusicRepository> musicRepositoryProvider,
      Provider<LibraryRepository> libraryRepositoryProvider,
      Provider<PlayerManager> playerManagerProvider) {
    return new SearchViewModel_Factory(musicRepositoryProvider, libraryRepositoryProvider, playerManagerProvider);
  }

  public static SearchViewModel newInstance(MusicRepository musicRepository,
      LibraryRepository libraryRepository, PlayerManager playerManager) {
    return new SearchViewModel(musicRepository, libraryRepository, playerManager);
  }
}
