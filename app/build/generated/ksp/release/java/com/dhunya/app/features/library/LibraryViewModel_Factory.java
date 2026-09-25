package com.dhunya.app.features.library;

import com.dhunya.app.domain.repository.LibraryRepository;
import com.dhunya.app.domain.usecase.ManagePlaylistUseCase;
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
public final class LibraryViewModel_Factory implements Factory<LibraryViewModel> {
  private final Provider<LibraryRepository> libraryRepositoryProvider;

  private final Provider<ManagePlaylistUseCase> managePlaylistUseCaseProvider;

  private final Provider<PlayerManager> playerManagerProvider;

  public LibraryViewModel_Factory(Provider<LibraryRepository> libraryRepositoryProvider,
      Provider<ManagePlaylistUseCase> managePlaylistUseCaseProvider,
      Provider<PlayerManager> playerManagerProvider) {
    this.libraryRepositoryProvider = libraryRepositoryProvider;
    this.managePlaylistUseCaseProvider = managePlaylistUseCaseProvider;
    this.playerManagerProvider = playerManagerProvider;
  }

  @Override
  public LibraryViewModel get() {
    return newInstance(libraryRepositoryProvider.get(), managePlaylistUseCaseProvider.get(), playerManagerProvider.get());
  }

  public static LibraryViewModel_Factory create(
      Provider<LibraryRepository> libraryRepositoryProvider,
      Provider<ManagePlaylistUseCase> managePlaylistUseCaseProvider,
      Provider<PlayerManager> playerManagerProvider) {
    return new LibraryViewModel_Factory(libraryRepositoryProvider, managePlaylistUseCaseProvider, playerManagerProvider);
  }

  public static LibraryViewModel newInstance(LibraryRepository libraryRepository,
      ManagePlaylistUseCase managePlaylistUseCase, PlayerManager playerManager) {
    return new LibraryViewModel(libraryRepository, managePlaylistUseCase, playerManager);
  }
}
