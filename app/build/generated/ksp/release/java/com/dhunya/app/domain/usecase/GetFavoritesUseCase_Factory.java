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
public final class GetFavoritesUseCase_Factory implements Factory<GetFavoritesUseCase> {
  private final Provider<LibraryRepository> libraryRepositoryProvider;

  public GetFavoritesUseCase_Factory(Provider<LibraryRepository> libraryRepositoryProvider) {
    this.libraryRepositoryProvider = libraryRepositoryProvider;
  }

  @Override
  public GetFavoritesUseCase get() {
    return newInstance(libraryRepositoryProvider.get());
  }

  public static GetFavoritesUseCase_Factory create(
      Provider<LibraryRepository> libraryRepositoryProvider) {
    return new GetFavoritesUseCase_Factory(libraryRepositoryProvider);
  }

  public static GetFavoritesUseCase newInstance(LibraryRepository libraryRepository) {
    return new GetFavoritesUseCase(libraryRepository);
  }
}
