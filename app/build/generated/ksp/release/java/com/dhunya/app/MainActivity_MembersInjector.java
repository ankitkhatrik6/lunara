package com.dhunya.app;

import com.dhunya.app.player.PlayerManager;
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
public final class MainActivity_MembersInjector implements MembersInjector<MainActivity> {
  private final Provider<PlayerManager> playerManagerProvider;

  public MainActivity_MembersInjector(Provider<PlayerManager> playerManagerProvider) {
    this.playerManagerProvider = playerManagerProvider;
  }

  public static MembersInjector<MainActivity> create(
      Provider<PlayerManager> playerManagerProvider) {
    return new MainActivity_MembersInjector(playerManagerProvider);
  }

  @Override
  public void injectMembers(MainActivity instance) {
    injectPlayerManager(instance, playerManagerProvider.get());
  }

  @InjectedFieldSignature("com.dhunya.app.MainActivity.playerManager")
  public static void injectPlayerManager(MainActivity instance, PlayerManager playerManager) {
    instance.playerManager = playerManager;
  }
}
