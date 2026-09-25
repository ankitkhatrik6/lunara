package com.dhunya.app.player;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
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
public final class PlayerManager_Factory implements Factory<PlayerManager> {
  private final Provider<Context> contextProvider;

  private final Provider<QueueManager> queueManagerProvider;

  public PlayerManager_Factory(Provider<Context> contextProvider,
      Provider<QueueManager> queueManagerProvider) {
    this.contextProvider = contextProvider;
    this.queueManagerProvider = queueManagerProvider;
  }

  @Override
  public PlayerManager get() {
    return newInstance(contextProvider.get(), queueManagerProvider.get());
  }

  public static PlayerManager_Factory create(Provider<Context> contextProvider,
      Provider<QueueManager> queueManagerProvider) {
    return new PlayerManager_Factory(contextProvider, queueManagerProvider);
  }

  public static PlayerManager newInstance(Context context, QueueManager queueManager) {
    return new PlayerManager(context, queueManager);
  }
}
