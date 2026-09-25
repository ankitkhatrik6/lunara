package com.dhunya.app.player

import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MediaSessionService : MediaSessionService() {

    @Inject
    lateinit var playerManager: PlayerManager

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = playerManager.getExoPlayer()
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(MediaSessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private inner class MediaSessionCallback : MediaSession.Callback {
        // Custom session actions can be hooked here (like favorite, queue reordering)
    }
}
