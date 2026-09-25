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
public final class ManagePlaylistUseCase_Factory implements Factory<ManagePlaylistUseCase> {
  private final Provider<LibraryRepository> libraryRepositoryProvider;

  public ManagePlaylistUseCase_Factory(Provider<LibraryRepository> libraryRepositoryProvider) {
    this.libraryRepositoryProvider = libraryRepositoryProvider;
  }

  @Override
  public ManagePlaylistUseCase get() {
    return newInstance(libraryRepositoryProvider.get());
  }

  public static ManagePlaylistUseCase_Factory create(
      Provider<LibraryRepository> libraryRepositoryProvider) {
    return new ManagePlaylistUseCase_Factory(libraryRepositoryProvider);
  }

  public static ManagePlaylistUseCase newInstance(LibraryRepository libraryRepository) {
    return new ManagePlaylistUseCase(libraryRepository);
  }
}
