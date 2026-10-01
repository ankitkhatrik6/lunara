package com.lunara.app.player

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lunara.app.data.local.dataStore
import com.lunara.app.domain.model.RepeatMode
import com.lunara.app.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The session a previous run of Lunara left behind: what was queued, where the playhead was, and
 * how the queue was being played.
 */
data class PlaybackSnapshot(
    val songs: List<Song>,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: RepeatMode
) {
    val currentSong: Song? get() = songs.getOrNull(currentIndex)
}

/**
 * Stores the playback session across process death, so reopening Lunara continues the music
 * instead of opening an empty player.
 *
 * The session is written as **two** preferences and not one blob, because the two halves change at
 * completely different rates:
 *
 * - [saveQueue] holds the queue as JSON, written only when the queue, the current index, shuffle
 *   or repeat actually change - a few times a session.
 * - [savePosition] holds a single `Long`, written every few seconds while music plays.
 *
 * Keeping the playhead out of the queue blob is what stops a ~50 KB payload from being rewritten
 * every few seconds just to move one number by five.
 *
 * The playhead also stores the id it belongs to. Without that, a position written for the track
 * that was playing would be applied to whatever track is current after a queue edit - and Lunara
 * would resume one song 90 seconds in while showing another.
 */
@Singleton
class PlaybackSnapshotStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** The stored session, or `null` when there is nothing usable to restore. */
    suspend fun load(): PlaybackSnapshot? = withContext(Dispatchers.IO) {
        val prefs = context.dataStore.data.first()
        val raw = prefs[KEY_QUEUE] ?: return@withContext null

        // A snapshot written by an older or newer build must never crash the app at start-up: a
        // session that cannot be read is treated as "no session".
        val stored = runCatching {
            json.decodeFromString(StoredPlaybackQueue.serializer(), raw)
        }.getOrNull() ?: return@withContext null

        val songs = stored.songs.map { it.toSong() }
        if (songs.isEmpty()) return@withContext null
        val currentIndex = stored.currentIndex.coerceIn(0, songs.size - 1)
        val current = songs[currentIndex]

        val positionMs = if (prefs[KEY_POSITION_SONG] == current.id) {
            (prefs[KEY_POSITION] ?: 0L).coerceAtLeast(0L)
        } else {
            0L
        }

        PlaybackSnapshot(
            songs = songs,
            currentIndex = currentIndex,
            positionMs = positionMs,
            shuffle = stored.shuffle,
            repeatMode = stored.repeatMode()
        )
    }

    /** Persists the queue and how it is being played. An empty queue forgets the session. */
    suspend fun saveQueue(
        songs: List<Song>,
        currentIndex: Int,
        shuffle: Boolean,
        repeatMode: RepeatMode
    ) = withContext(Dispatchers.IO) {
        if (songs.isEmpty()) {
            clear()
            return@withContext
        }
        val payload = json.encodeToString(
            StoredPlaybackQueue.serializer(),
            StoredPlaybackQueue(
                songs = songs.map { StoredPlaybackSong.of(it) },
                currentIndex = currentIndex.coerceIn(0, songs.size - 1),
                shuffle = shuffle,
                repeatMode = repeatMode.name
            )
        )
        context.dataStore.edit { it[KEY_QUEUE] = payload }
    }

    /**
     * Moves the stored playhead. [songId] is written alongside it, so a position can never be
     * applied to a different track.
     */
    suspend fun savePosition(songId: String, positionMs: Long) = withContext(Dispatchers.IO) {
        context.dataStore.edit {
            it[KEY_POSITION_SONG] = songId
            it[KEY_POSITION] = positionMs.coerceAtLeast(0L)
        }
    }

    /** Forgets the session, e.g. when the queue has been emptied. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        context.dataStore.edit {
            it.remove(KEY_QUEUE)
            it.remove(KEY_POSITION)
            it.remove(KEY_POSITION_SONG)
        }
    }

    private companion object {
        val KEY_QUEUE = stringPreferencesKey("pref_playback_queue")
        val KEY_POSITION = longPreferencesKey("pref_playback_position")
        val KEY_POSITION_SONG = stringPreferencesKey("pref_playback_position_song")
    }
}

/** The queue half of a stored session. Kept small and versionable. */
@Serializable
internal data class StoredPlaybackQueue(
    val songs: List<StoredPlaybackSong> = emptyList(),
    val currentIndex: Int = 0,
    val shuffle: Boolean = false,
    /**
     * Stored as a name rather than as the enum itself: a queue written by a build that knows a
     * repeat mode this one does not must still load, minus that one detail.
     */
    val repeatMode: String = RepeatMode.OFF.name
) {
    fun repeatMode(): RepeatMode =
        RepeatMode.entries.firstOrNull { it.name == repeatMode } ?: RepeatMode.OFF
}

/**
 * A queue row as it is written to disk.
 *
 * Two fields are dropped deliberately:
 *
 * - **`streamUrl`** is a URL YouTube signs for minutes. Handing one back a day later is exactly the
 *   "source error" the player fights elsewhere; the resolver mints a fresh one on the next tap.
 * - **`content://` local URIs** (MediaStore tracks) are another app's grant. Restoring one after
 *   the permission was revoked would give ExoPlayer a URI Lunara may no longer read. Only `file:`
 *   URIs - the copies Lunara downloaded into its own storage - survive a restart.
 */
@Serializable
internal data class StoredPlaybackSong(
    val id: String,
    val title: String,
    val artistName: String,
    val albumName: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long = 0L,
    val localFile: String? = null,
    val isDownloaded: Boolean = false
) {
    fun toSong(): Song = Song(
        id = id,
        title = title,
        artistName = artistName,
        albumName = albumName,
        artworkUrl = artworkUrl,
        durationMs = durationMs,
        localUri = localFile,
        isDownloaded = isDownloaded
    )

    companion object {
        fun of(song: Song): StoredPlaybackSong = StoredPlaybackSong(
            id = song.id,
            title = song.title,
            artistName = song.artistName,
            albumName = song.albumName,
            artworkUrl = song.artworkUrl,
            durationMs = song.durationMs,
            localFile = song.localUri?.takeIf { it.startsWith("file:") },
            isDownloaded = song.isDownloaded
        )
    }
}
