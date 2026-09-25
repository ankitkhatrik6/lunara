package com.dhunya.app.data.remote.music;

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
public final class MusicRemoteDataSource_Factory implements Factory<MusicRemoteDataSource> {
  private final Provider<HttpClient> httpClientProvider;

  public MusicRemoteDataSource_Factory(Provider<HttpClient> httpClientProvider) {
    this.httpClientProvider = httpClientProvider;
  }

  @Override
  public MusicRemoteDataSource get() {
    return newInstance(httpClientProvider.get());
  }

  public static MusicRemoteDataSource_Factory create(Provider<HttpClient> httpClientProvider) {
    return new MusicRemoteDataSource_Factory(httpClientProvider);
  }

  public static MusicRemoteDataSource newInstance(HttpClient httpClient) {
    return new MusicRemoteDataSource(httpClient);
  }
}
