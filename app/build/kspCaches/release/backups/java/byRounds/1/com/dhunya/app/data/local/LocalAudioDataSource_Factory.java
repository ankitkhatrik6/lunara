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
public final class LocalAudioDataSource_Factory implements Factory<LocalAudioDataSource> {
  private final Provider<Context> contextProvider;

  public LocalAudioDataSource_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public LocalAudioDataSource get() {
    return newInstance(contextProvider.get());
  }

  public static LocalAudioDataSource_Factory create(Provider<Context> contextProvider) {
    return new LocalAudioDataSource_Factory(contextProvider);
  }

  public static LocalAudioDataSource newInstance(Context context) {
    return new LocalAudioDataSource(context);
  }
}
