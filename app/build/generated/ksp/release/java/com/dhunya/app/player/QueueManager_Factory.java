package com.dhunya.app.player;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class QueueManager_Factory implements Factory<QueueManager> {
  @Override
  public QueueManager get() {
    return newInstance();
  }

  public static QueueManager_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static QueueManager newInstance() {
    return new QueueManager();
  }

  private static final class InstanceHolder {
    private static final QueueManager_Factory INSTANCE = new QueueManager_Factory();
  }
}
