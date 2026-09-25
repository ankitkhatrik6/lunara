package com.dhunya.app.data.repository;

import com.dhunya.app.data.remote.lyrics.BlazifyLyricsEngine;
import com.dhunya.app.data.remote.lyrics.LrclibLyricsApi;
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
public final class LyricsRepositoryImpl_Factory implements Factory<LyricsRepositoryImpl> {
  private final Provider<LrclibLyricsApi> lrclibApiProvider;

  private final Provider<BlazifyLyricsEngine> blazifyLyricsEngineProvider;

  public LyricsRepositoryImpl_Factory(Provider<LrclibLyricsApi> lrclibApiProvider,
      Provider<BlazifyLyricsEngine> blazifyLyricsEngineProvider) {
    this.lrclibApiProvider = lrclibApiProvider;
    this.blazifyLyricsEngineProvider = blazifyLyricsEngineProvider;
  }

  @Override
  public LyricsRepositoryImpl get() {
    return newInstance(lrclibApiProvider.get(), blazifyLyricsEngineProvider.get());
  }

  public static LyricsRepositoryImpl_Factory create(Provider<LrclibLyricsApi> lrclibApiProvider,
      Provider<BlazifyLyricsEngine> blazifyLyricsEngineProvider) {
    return new LyricsRepositoryImpl_Factory(lrclibApiProvider, blazifyLyricsEngineProvider);
  }

  public static LyricsRepositoryImpl newInstance(LrclibLyricsApi lrclibApi,
      BlazifyLyricsEngine blazifyLyricsEngine) {
    return new LyricsRepositoryImpl(lrclibApi, blazifyLyricsEngine);
  }
}
