package com.dhunya.app.data.worker;

import android.content.Context;
import androidx.work.WorkerParameters;
import dagger.internal.DaggerGenerated;
import dagger.internal.InstanceFactory;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class MusicDownloadWorker_AssistedFactory_Impl implements MusicDownloadWorker_AssistedFactory {
  private final MusicDownloadWorker_Factory delegateFactory;

  MusicDownloadWorker_AssistedFactory_Impl(MusicDownloadWorker_Factory delegateFactory) {
    this.delegateFactory = delegateFactory;
  }

  @Override
  public MusicDownloadWorker create(Context p0, WorkerParameters p1) {
    return delegateFactory.get(p0, p1);
  }

  public static Provider<MusicDownloadWorker_AssistedFactory> create(
      MusicDownloadWorker_Factory delegateFactory) {
    return InstanceFactory.create(new MusicDownloadWorker_AssistedFactory_Impl(delegateFactory));
  }

  public static dagger.internal.Provider<MusicDownloadWorker_AssistedFactory> createFactoryProvider(
      MusicDownloadWorker_Factory delegateFactory) {
    return InstanceFactory.create(new MusicDownloadWorker_AssistedFactory_Impl(delegateFactory));
  }
}
