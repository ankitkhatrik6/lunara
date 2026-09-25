package com.dhunya.app.data.remote.lyrics;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import io.ktor.client.HttpClient;
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
public final class LrclibLyricsApi_Factory implements Factory<LrclibLyricsApi> {
  private final Provider<HttpClient> httpClientProvider;

  public LrclibLyricsApi_Factory(Provider<HttpClient> httpClientProvider) {
    this.httpClientProvider = httpClientProvider;
  }

  @Override
  public LrclibLyricsApi get() {
    return newInstance(httpClientProvider.get());
  }

  public static LrclibLyricsApi_Factory create(Provider<HttpClient> httpClientProvider) {
    return new LrclibLyricsApi_Factory(httpClientProvider);
  }

  public static LrclibLyricsApi newInstance(HttpClient httpClient) {
    return new LrclibLyricsApi(httpClient);
  }
}
