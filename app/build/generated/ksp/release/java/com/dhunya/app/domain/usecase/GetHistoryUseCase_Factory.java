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
public final class GetHistoryUseCase_Factory implements Factory<GetHistoryUseCase> {
  private final Provider<LibraryRepository> libraryRepositoryProvider;

  public GetHistoryUseCase_Factory(Provider<LibraryRepository> libraryRepositoryProvider) {
    this.libraryRepositoryProvider = libraryRepositoryProvider;
  }

  @Override
  public GetHistoryUseCase get() {
    return newInstance(libraryRepositoryProvider.get());
  }

  public static GetHistoryUseCase_Factory create(
      Provider<LibraryRepository> libraryRepositoryProvider) {
    return new GetHistoryUseCase_Factory(libraryRepositoryProvider);
  }

  public static GetHistoryUseCase newInstance(LibraryRepository libraryRepository) {
    return new GetHistoryUseCase(libraryRepository);
  }
}
