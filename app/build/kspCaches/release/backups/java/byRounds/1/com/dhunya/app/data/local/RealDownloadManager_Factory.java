package com.dhunya.app.data.local;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class RealDownloadManager_Factory implements Factory<RealDownloadManager> {
  private final Provider<Context> contextProvider;

  public RealDownloadManager_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public RealDownloadManager get() {
    return newInstance(contextProvider.get());
  }

  public static RealDownloadManager_Factory create(Provider<Context> contextProvider) {
    return new RealDownloadManager_Factory(contextProvider);
  }

  public static RealDownloadManager newInstance(Context context) {
    return new RealDownloadManager(context);
  }
}
