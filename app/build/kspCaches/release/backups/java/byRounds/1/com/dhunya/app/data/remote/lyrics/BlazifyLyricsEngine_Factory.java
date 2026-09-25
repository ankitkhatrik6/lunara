package com.dhunya.app.data.remote.lyrics;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import io.ktor.client.HttpClient;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
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
public final class BlazifyLyricsEngine_Factory implements Factory<BlazifyLyricsEngine> {
  private final Provider<HttpClient> httpClientProvider;

  public BlazifyLyricsEngine_Factory(Provider<HttpClient> httpClientProvider) {
    this.httpClientProvider = httpClientProvider;
  }

  @Override
  public BlazifyLyricsEngine get() {
    return newInstance(httpClientProvider.get());
  }

  public static BlazifyLyricsEngine_Factory create(Provider<HttpClient> httpClientProvider) {
    return new BlazifyLyricsEngine_Factory(httpClientProvider);
  }

  public static BlazifyLyricsEngine newInstance(HttpClient httpClient) {
    return new BlazifyLyricsEngine(httpClient);
  }
}
