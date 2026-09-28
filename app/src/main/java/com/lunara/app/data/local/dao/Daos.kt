package com.lunara.app.data.local.dao

import androidx.room.*
import com.lunara.app.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    /**
     * Upsert, deliberately **not** `@Insert(onConflict = REPLACE)`.
     *
     * SQLite implements `INSERT OR REPLACE` as DELETE + INSERT, which fires the
     * `ON DELETE CASCADE` of every child table, so re-inserting a song (playing a track, loving
     * it, finishing a download, adding it to a playlist) silently wiped that song's
     * `favorites`, `downloads`, `history` and `playlist_song_cross_ref` rows. That was the
     * "heart flips itself back" and "download never shows up" bug. `@Upsert` updates in place
     * instead, so child rows survive.
     */
    @Upsert
    suspend fun insertSong(song: SongEntity)

    @Upsert
    suspend fun insertSongs(songs: List<SongEntity>)

    @Query("SELECT * FROM songs WHERE id = :songId")
    suspend fun getSongById(songId: String): SongEntity?

    @Query("UPDATE songs SET isDownloaded = :isDownloaded, localUri = :localUri WHERE id = :songId")
    suspend fun updateDownloadStatus(songId: String, isDownloaded: Boolean, localUri: String?)
}

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun removeFavorite(songId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :songId)")
    suspend fun isFavorite(songId: String): Boolean

    @Query("""
        SELECT songs.* FROM songs
        INNER JOIN favorites ON songs.id = favorites.songId
        ORDER BY favorites.addedAt DESC
    """)
    fun getFavoriteSongs(): Flow<List<SongEntity>>
}

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: HistoryEntity)

    /**
     * Drops every earlier row for a song before the new one is inserted, so the table holds
     * exactly **one** row per track.
     *
     * `history` uses an auto-generated primary key, so without this every single play appended
     * another row for the same song. The `songs INNER JOIN history` below then returned that
     * song N times — which made "Recently played" show the same cover over and over and, worse,
     * handed Compose two `items()` with an identical key, crashing the list with
     * `IllegalArgumentException: Key "…" was already used`.
     */
    @Query("DELETE FROM history WHERE songId = :songId")
    suspend fun deleteForSong(songId: String)

    /** Collapses any pre-existing duplicate rows, newest row per song kept. */
    @Query(
        """
        DELETE FROM history WHERE historyId NOT IN (
            SELECT historyId FROM history AS h
            WHERE h.songId = history.songId
            ORDER BY h.playedAt DESC, h.historyId DESC
            LIMIT 1
        )
        """
    )
    suspend fun deduplicate()

    @Query("""
        SELECT songs.* FROM songs
        INNER JOIN history ON songs.id = history.songId
        ORDER BY history.playedAt DESC
        LIMIT 50
    """)
    fun getHistorySongs(): Flow<List<SongEntity>>

    @Query("DELETE FROM history")
    suspend fun clearHistory()
}

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("UPDATE playlists SET name = :newName WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, newName: String)

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSongToPlaylist(ref: PlaylistSongCrossRef)

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String)

    @Query("""
        SELECT songs.* FROM songs
        INNER JOIN playlist_song_cross_ref ON songs.id = playlist_song_cross_ref.songId
        WHERE playlist_song_cross_ref.playlistId = :playlistId
        ORDER BY playlist_song_cross_ref.orderIndex ASC
    """)
    fun getSongsForPlaylist(playlistId: Long): Flow<List<SongEntity>>

    @Query("SELECT COUNT(*) FROM playlist_song_cross_ref WHERE playlistId = :playlistId")
    suspend fun getSongCount(playlistId: Long): Int
}

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDownload(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE songId = :songId")
    suspend fun deleteDownload(songId: String)

    @Query("SELECT * FROM downloads WHERE songId = :songId")
    suspend fun getDownloadById(songId: String): DownloadEntity?

    @Query("""
        SELECT * FROM downloads
        ORDER BY updatedAt DESC
    """)
    fun getAllDownloads(): Flow<List<DownloadEntity>>
}

@Dao
interface RecentSearchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(search: RecentSearchEntity)

    @Query("SELECT query FROM recent_searches ORDER BY timestamp DESC LIMIT 15")
    suspend fun getRecentSearches(): List<String>

    @Query("DELETE FROM recent_searches")
    suspend fun clearAll()
}
