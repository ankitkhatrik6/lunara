package com.dhunya.app.features.downloads;

import com.dhunya.app.domain.usecase.ManageDownloadsUseCase;
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
public final class DownloadsViewModel_Factory implements Factory<DownloadsViewModel> {
  private final Provider<ManageDownloadsUseCase> manageDownloadsUseCaseProvider;

  private final Provider<PlayerManager> playerManagerProvider;

  public DownloadsViewModel_Factory(Provider<ManageDownloadsUseCase> manageDownloadsUseCaseProvider,
      Provider<PlayerManager> playerManagerProvider) {
    this.manageDownloadsUseCaseProvider = manageDownloadsUseCaseProvider;
    this.playerManagerProvider = playerManagerProvider;
  }

  @Override
  public DownloadsViewModel get() {
    return newInstance(manageDownloadsUseCaseProvider.get(), playerManagerProvider.get());
  }

  public static DownloadsViewModel_Factory create(
      Provider<ManageDownloadsUseCase> manageDownloadsUseCaseProvider,
      Provider<PlayerManager> playerManagerProvider) {
    return new DownloadsViewModel_Factory(manageDownloadsUseCaseProvider, playerManagerProvider);
  }

  public static DownloadsViewModel newInstance(ManageDownloadsUseCase manageDownloadsUseCase,
      PlayerManager playerManager) {
    return new DownloadsViewModel(manageDownloadsUseCase, playerManager);
  }
}
