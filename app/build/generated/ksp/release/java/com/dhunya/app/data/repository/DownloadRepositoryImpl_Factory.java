package com.dhunya.app.data.repository;

import androidx.work.WorkManager;
import com.dhunya.app.data.local.RealDownloadManager;
import com.dhunya.app.data.local.dao.DownloadDao;
import com.dhunya.app.data.local.dao.SongDao;
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
public final class DownloadRepositoryImpl_Factory implements Factory<DownloadRepositoryImpl> {
  private final Provider<DownloadDao> downloadDaoProvider;

  private final Provider<SongDao> songDaoProvider;

  private final Provider<WorkManager> workManagerProvider;

  private final Provider<RealDownloadManager> realDownloadManagerProvider;

  public DownloadRepositoryImpl_Factory(Provider<DownloadDao> downloadDaoProvider,
      Provider<SongDao> songDaoProvider, Provider<WorkManager> workManagerProvider,
      Provider<RealDownloadManager> realDownloadManagerProvider) {
    this.downloadDaoProvider = downloadDaoProvider;
    this.songDaoProvider = songDaoProvider;
    this.workManagerProvider = workManagerProvider;
    this.realDownloadManagerProvider = realDownloadManagerProvider;
  }

  @Override
  public DownloadRepositoryImpl get() {
    return newInstance(downloadDaoProvider.get(), songDaoProvider.get(), workManagerProvider.get(), realDownloadManagerProvider.get());
  }

  public static DownloadRepositoryImpl_Factory create(Provider<DownloadDao> downloadDaoProvider,
      Provider<SongDao> songDaoProvider, Provider<WorkManager> workManagerProvider,
      Provider<RealDownloadManager> realDownloadManagerProvider) {
    return new DownloadRepositoryImpl_Factory(downloadDaoProvider, songDaoProvider, workManagerProvider, realDownloadManagerProvider);
  }

  public static DownloadRepositoryImpl newInstance(DownloadDao downloadDao, SongDao songDao,
      WorkManager workManager, RealDownloadManager realDownloadManager) {
    return new DownloadRepositoryImpl(downloadDao, songDao, workManager, realDownloadManager);
  }
}
