package com.dhunya.app.features.lyrics;

import com.dhunya.app.domain.usecase.GetLyricsUseCase;
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
public final class LyricsViewModel_Factory implements Factory<LyricsViewModel> {
  private final Provider<GetLyricsUseCase> getLyricsUseCaseProvider;

  private final Provider<PlayerManager> playerManagerProvider;

  public LyricsViewModel_Factory(Provider<GetLyricsUseCase> getLyricsUseCaseProvider,
      Provider<PlayerManager> playerManagerProvider) {
    this.getLyricsUseCaseProvider = getLyricsUseCaseProvider;
    this.playerManagerProvider = playerManagerProvider;
  }

  @Override
  public LyricsViewModel get() {
    return newInstance(getLyricsUseCaseProvider.get(), playerManagerProvider.get());
  }

  public static LyricsViewModel_Factory create(Provider<GetLyricsUseCase> getLyricsUseCaseProvider,
      Provider<PlayerManager> playerManagerProvider) {
    return new LyricsViewModel_Factory(getLyricsUseCaseProvider, playerManagerProvider);
  }

  public static LyricsViewModel newInstance(GetLyricsUseCase getLyricsUseCase,
      PlayerManager playerManager) {
    return new LyricsViewModel(getLyricsUseCase, playerManager);
  }
}
