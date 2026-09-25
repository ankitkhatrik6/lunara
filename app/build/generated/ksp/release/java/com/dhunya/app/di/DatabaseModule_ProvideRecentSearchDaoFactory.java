package com.dhunya.app.di;

import com.dhunya.app.data.local.dao.RecentSearchDao;
import com.dhunya.app.data.local.database.DhunyaDatabase;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class DatabaseModule_ProvideRecentSearchDaoFactory implements Factory<RecentSearchDao> {
  private final Provider<DhunyaDatabase> databaseProvider;

  public DatabaseModule_ProvideRecentSearchDaoFactory(Provider<DhunyaDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public RecentSearchDao get() {
    return provideRecentSearchDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideRecentSearchDaoFactory create(
      Provider<DhunyaDatabase> databaseProvider) {
    return new DatabaseModule_ProvideRecentSearchDaoFactory(databaseProvider);
  }

  public static RecentSearchDao provideRecentSearchDao(DhunyaDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideRecentSearchDao(database));
  }
}
