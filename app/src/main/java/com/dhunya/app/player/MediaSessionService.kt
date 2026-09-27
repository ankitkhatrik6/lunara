package com.dhunya.app.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.dhunya.app.MainActivity
import com.dhunya.app.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Hosts the [MediaSession] that backs the system media notification, the lock screen, the
 * quick settings player and Bluetooth/headset controls.
 *
 * The service reuses the singleton [PlayerManager]'s ExoPlayer instead of owning a second
 * player, so the notification controls exactly the playback the app UI already shows.
 */
@AndroidEntryPoint
class MediaSessionService : MediaSessionService() {

    @Inject
    lateinit var playerManager: PlayerManager

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        // The provider has to be installed before super.onCreate(), which builds the
        // notification manager that reads it.
        //
        // `DefaultMediaNotificationProvider` posts a MediaStyle notification (play/pause,
        // skip, seek bar, artwork). The channel name shows up in Android's notification
        // settings; title/artist/artwork come from the MediaMetadata PlayerManager sets on
        // every media item.
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelName(R.string.notification_channel_playback)
                .build()
        )

        super.onCreate()

        mediaSession = MediaSession.Builder(
            this,
            QueueAwarePlayer(playerManager.getExoPlayer(), playerManager)
        )
            .setSessionActivity(openAppIntent())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        // Only the session is released here: the ExoPlayer instance belongs to the injected
        // PlayerManager and stays valid when the service is recreated.
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    /** Tapping the notification (or the lock screen artwork) reopens the app. */
    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
