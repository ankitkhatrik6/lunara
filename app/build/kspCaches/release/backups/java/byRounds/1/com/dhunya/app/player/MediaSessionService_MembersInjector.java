package com.dhunya.app.player;

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
public final class MediaSessionService_MembersInjector implements MembersInjector<MediaSessionService> {
  private final Provider<PlayerManager> playerManagerProvider;

  public MediaSessionService_MembersInjector(Provider<PlayerManager> playerManagerProvider) {
    this.playerManagerProvider = playerManagerProvider;
  }

  public static MembersInjector<MediaSessionService> create(
      Provider<PlayerManager> playerManagerProvider) {
    return new MediaSessionService_MembersInjector(playerManagerProvider);
  }

  @Override
  public void injectMembers(MediaSessionService instance) {
    injectPlayerManager(instance, playerManagerProvider.get());
  }

  @InjectedFieldSignature("com.dhunya.app.player.MediaSessionService.playerManager")
  public static void injectPlayerManager(MediaSessionService instance,
      PlayerManager playerManager) {
    instance.playerManager = playerManager;
  }
}
