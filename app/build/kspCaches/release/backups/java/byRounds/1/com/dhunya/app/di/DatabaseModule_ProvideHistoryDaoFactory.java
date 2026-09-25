package com.dhunya.app.di;

import com.dhunya.app.data.local.dao.HistoryDao;
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
public final class DatabaseModule_ProvideHistoryDaoFactory implements Factory<HistoryDao> {
  private final Provider<DhunyaDatabase> databaseProvider;

  public DatabaseModule_ProvideHistoryDaoFactory(Provider<DhunyaDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public HistoryDao get() {
    return provideHistoryDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideHistoryDaoFactory create(
      Provider<DhunyaDatabase> databaseProvider) {
    return new DatabaseModule_ProvideHistoryDaoFactory(databaseProvider);
  }

  public static HistoryDao provideHistoryDao(DhunyaDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideHistoryDao(database));
  }
}
