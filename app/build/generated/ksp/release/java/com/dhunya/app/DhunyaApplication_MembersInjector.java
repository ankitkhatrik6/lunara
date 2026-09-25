package com.dhunya.app;

import androidx.hilt.work.HiltWorkerFactory;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class DhunyaApplication_MembersInjector implements MembersInjector<DhunyaApplication> {
  private final Provider<HiltWorkerFactory> workerFactoryProvider;

  public DhunyaApplication_MembersInjector(Provider<HiltWorkerFactory> workerFactoryProvider) {
    this.workerFactoryProvider = workerFactoryProvider;
  }

  public static MembersInjector<DhunyaApplication> create(
      Provider<HiltWorkerFactory> workerFactoryProvider) {
    return new DhunyaApplication_MembersInjector(workerFactoryProvider);
  }

  @Override
  public void injectMembers(DhunyaApplication instance) {
    injectWorkerFactory(instance, workerFactoryProvider.get());
  }

  @InjectedFieldSignature("com.dhunya.app.DhunyaApplication.workerFactory")
  public static void injectWorkerFactory(DhunyaApplication instance,
      HiltWorkerFactory workerFactory) {
    instance.workerFactory = workerFactory;
  }
}
