package com.dhunya.app.domain.usecase;

import com.dhunya.app.domain.repository.LibraryRepository;
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
public final class ToggleFavoriteUseCase_Factory implements Factory<ToggleFavoriteUseCase> {
  private final Provider<LibraryRepository> libraryRepositoryProvider;

  public ToggleFavoriteUseCase_Factory(Provider<LibraryRepository> libraryRepositoryProvider) {
    this.libraryRepositoryProvider = libraryRepositoryProvider;
  }

  @Override
  public ToggleFavoriteUseCase get() {
    return newInstance(libraryRepositoryProvider.get());
  }

  public static ToggleFavoriteUseCase_Factory create(
      Provider<LibraryRepository> libraryRepositoryProvider) {
    return new ToggleFavoriteUseCase_Factory(libraryRepositoryProvider);
  }

  public static ToggleFavoriteUseCase newInstance(LibraryRepository libraryRepository) {
    return new ToggleFavoriteUseCase(libraryRepository);
  }
}
