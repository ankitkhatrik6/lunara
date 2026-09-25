package com.dhunya.app.data.local;

import android.content.Context;
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
public final class PreferencesDataStore_Factory implements Factory<PreferencesDataStore> {
  private final Provider<Context> contextProvider;

  public PreferencesDataStore_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public PreferencesDataStore get() {
    return newInstance(contextProvider.get());
  }

  public static PreferencesDataStore_Factory create(Provider<Context> contextProvider) {
    return new PreferencesDataStore_Factory(contextProvider);
  }

  public static PreferencesDataStore newInstance(Context context) {
    return new PreferencesDataStore(context);
  }
}
