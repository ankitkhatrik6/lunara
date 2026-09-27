package com.dhunya.app.player

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Exposes Dhunya's queue to the media session so the system notification shows working
 * "next" / "previous" buttons.
 *
 * ExoPlayer only ever holds the *currently resolved* track — the real queue lives in
 * [QueueManager] because every YouTube Music track's audio URL has to be resolved on demand.
 * Without this wrapper `hasNextMediaItem()` is permanently `false`, Android greys the skip
 * actions out and the notification looks broken next to Spotify.
 */
internal class QueueAwarePlayer(
    player: ExoPlayer,
    private val playerManager: PlayerManager
) : ForwardingPlayer(player) {

    private val queueManager get() = playerManager.queueManager

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .addAll(
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
            )
            .build()

    override fun isCommandAvailable(command: Int): Boolean = when (command) {
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> queueManager.hasUpcoming

        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> queueManager.hasPrevious

        else -> super.isCommandAvailable(command)
    }

    override fun hasNext(): Boolean = queueManager.hasUpcoming

    override fun hasNextMediaItem(): Boolean = queueManager.hasUpcoming

    override fun hasPreviousMediaItem(): Boolean = queueManager.hasPrevious

    override fun seekToNext() = playerManager.playNext()

    override fun seekToNextMediaItem() = playerManager.playNext()

    override fun seekToPrevious() = playerManager.playPrevious()

    override fun seekToPreviousMediaItem() = playerManager.playPrevious()
}
