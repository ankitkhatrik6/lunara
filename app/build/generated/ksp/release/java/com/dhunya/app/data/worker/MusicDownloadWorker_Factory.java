package com.dhunya.app.data.worker;

import android.content.Context;
import androidx.work.WorkerParameters;
import com.dhunya.app.data.local.dao.DownloadDao;
import com.dhunya.app.data.local.dao.SongDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import okhttp3.OkHttpClient;

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
public final class MusicDownloadWorker_Factory {
  private final Provider<SongDao> songDaoProvider;

  private final Provider<DownloadDao> downloadDaoProvider;

  private final Provider<OkHttpClient> okHttpClientProvider;

  public MusicDownloadWorker_Factory(Provider<SongDao> songDaoProvider,
      Provider<DownloadDao> downloadDaoProvider, Provider<OkHttpClient> okHttpClientProvider) {
    this.songDaoProvider = songDaoProvider;
    this.downloadDaoProvider = downloadDaoProvider;
    this.okHttpClientProvider = okHttpClientProvider;
  }

  public MusicDownloadWorker get(Context context, WorkerParameters params) {
    return newInstance(context, params, songDaoProvider.get(), downloadDaoProvider.get(), okHttpClientProvider.get());
  }

  public static MusicDownloadWorker_Factory create(Provider<SongDao> songDaoProvider,
      Provider<DownloadDao> downloadDaoProvider, Provider<OkHttpClient> okHttpClientProvider) {
    return new MusicDownloadWorker_Factory(songDaoProvider, downloadDaoProvider, okHttpClientProvider);
  }

  public static MusicDownloadWorker newInstance(Context context, WorkerParameters params,
      SongDao songDao, DownloadDao downloadDao, OkHttpClient okHttpClient) {
    return new MusicDownloadWorker(context, params, songDao, downloadDao, okHttpClient);
  }
}
