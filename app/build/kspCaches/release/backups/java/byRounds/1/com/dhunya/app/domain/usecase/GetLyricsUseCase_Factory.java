package com.dhunya.app.domain.usecase;

import com.dhunya.app.domain.repository.LyricsRepository;
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
public final class GetLyricsUseCase_Factory implements Factory<GetLyricsUseCase> {
  private final Provider<LyricsRepository> lyricsRepositoryProvider;

  public GetLyricsUseCase_Factory(Provider<LyricsRepository> lyricsRepositoryProvider) {
    this.lyricsRepositoryProvider = lyricsRepositoryProvider;
  }

  @Override
  public GetLyricsUseCase get() {
    return newInstance(lyricsRepositoryProvider.get());
  }

  public static GetLyricsUseCase_Factory create(
      Provider<LyricsRepository> lyricsRepositoryProvider) {
    return new GetLyricsUseCase_Factory(lyricsRepositoryProvider);
  }

  public static GetLyricsUseCase newInstance(LyricsRepository lyricsRepository) {
    return new GetLyricsUseCase(lyricsRepository);
  }
}
