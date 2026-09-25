package com.dhunya.app.di;

import android.content.Context;
import com.dhunya.app.data.local.PreferencesDataStore;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class AppModule_ProvidePreferencesDataStoreFactory implements Factory<PreferencesDataStore> {
  private final Provider<Context> contextProvider;

  public AppModule_ProvidePreferencesDataStoreFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public PreferencesDataStore get() {
    return providePreferencesDataStore(contextProvider.get());
  }

  public static AppModule_ProvidePreferencesDataStoreFactory create(
      Provider<Context> contextProvider) {
    return new AppModule_ProvidePreferencesDataStoreFactory(contextProvider);
  }

  public static PreferencesDataStore providePreferencesDataStore(Context context) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.providePreferencesDataStore(context));
  }
}
