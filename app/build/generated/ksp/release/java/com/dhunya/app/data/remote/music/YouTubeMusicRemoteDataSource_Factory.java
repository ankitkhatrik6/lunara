package com.dhunya.app.data.remote.music;

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
public final class YouTubeMusicRemoteDataSource_Factory implements Factory<YouTubeMusicRemoteDataSource> {
  private final Provider<HttpClient> httpClientProvider;

  public YouTubeMusicRemoteDataSource_Factory(Provider<HttpClient> httpClientProvider) {
    this.httpClientProvider = httpClientProvider;
  }

  @Override
  public YouTubeMusicRemoteDataSource get() {
    return newInstance(httpClientProvider.get());
  }

  public static YouTubeMusicRemoteDataSource_Factory create(
      Provider<HttpClient> httpClientProvider) {
    return new YouTubeMusicRemoteDataSource_Factory(httpClientProvider);
  }

  public static YouTubeMusicRemoteDataSource newInstance(HttpClient httpClient) {
    return new YouTubeMusicRemoteDataSource(httpClient);
  }
}
