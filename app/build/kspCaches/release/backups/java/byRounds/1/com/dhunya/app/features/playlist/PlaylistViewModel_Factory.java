package com.dhunya.app.features.playlist;

import androidx.lifecycle.SavedStateHandle;
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
public final class PlaylistViewModel_Factory implements Factory<PlaylistViewModel> {
  private final Provider<SavedStateHandle> savedStateHandleProvider;

  private final Provider<ManagePlaylistUseCase> managePlaylistUseCaseProvider;

  private final Provider<PlayerManager> playerManagerProvider;

  public PlaylistViewModel_Factory(Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<ManagePlaylistUseCase> managePlaylistUseCaseProvider,
      Provider<PlayerManager> playerManagerProvider) {
    this.savedStateHandleProvider = savedStateHandleProvider;
    this.managePlaylistUseCaseProvider = managePlaylistUseCaseProvider;
    this.playerManagerProvider = playerManagerProvider;
  }

  @Override
  public PlaylistViewModel get() {
    return newInstance(savedStateHandleProvider.get(), managePlaylistUseCaseProvider.get(), playerManagerProvider.get());
  }

  public static PlaylistViewModel_Factory create(
      Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<ManagePlaylistUseCase> managePlaylistUseCaseProvider,
      Provider<PlayerManager> playerManagerProvider) {
    return new PlaylistViewModel_Factory(savedStateHandleProvider, managePlaylistUseCaseProvider, playerManagerProvider);
  }

  public static PlaylistViewModel newInstance(SavedStateHandle savedStateHandle,
      ManagePlaylistUseCase managePlaylistUseCase, PlayerManager playerManager) {
    return new PlaylistViewModel(savedStateHandle, managePlaylistUseCase, playerManager);
  }
}
