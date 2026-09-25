package com.dhunya.app.domain.usecase;

import com.dhunya.app.domain.repository.MusicRepository;
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
public final class SearchMusicUseCase_Factory implements Factory<SearchMusicUseCase> {
  private final Provider<MusicRepository> musicRepositoryProvider;

  public SearchMusicUseCase_Factory(Provider<MusicRepository> musicRepositoryProvider) {
    this.musicRepositoryProvider = musicRepositoryProvider;
  }

  @Override
  public SearchMusicUseCase get() {
    return newInstance(musicRepositoryProvider.get());
  }

  public static SearchMusicUseCase_Factory create(
      Provider<MusicRepository> musicRepositoryProvider) {
    return new SearchMusicUseCase_Factory(musicRepositoryProvider);
  }

  public static SearchMusicUseCase newInstance(MusicRepository musicRepository) {
    return new SearchMusicUseCase(musicRepository);
  }
}
