package com.dhunya.app.domain.usecase;

import com.dhunya.app.domain.repository.DownloadRepository;
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
public final class ManageDownloadsUseCase_Factory implements Factory<ManageDownloadsUseCase> {
  private final Provider<DownloadRepository> downloadRepositoryProvider;

  public ManageDownloadsUseCase_Factory(Provider<DownloadRepository> downloadRepositoryProvider) {
    this.downloadRepositoryProvider = downloadRepositoryProvider;
  }

  @Override
  public ManageDownloadsUseCase get() {
    return newInstance(downloadRepositoryProvider.get());
  }

  public static ManageDownloadsUseCase_Factory create(
      Provider<DownloadRepository> downloadRepositoryProvider) {
    return new ManageDownloadsUseCase_Factory(downloadRepositoryProvider);
  }

  public static ManageDownloadsUseCase newInstance(DownloadRepository downloadRepository) {
    return new ManageDownloadsUseCase(downloadRepository);
  }
}
