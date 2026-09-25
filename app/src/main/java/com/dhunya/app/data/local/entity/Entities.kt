package com.dhunya.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artistName: String,
    val albumName: String?,
    val artworkUrl: String?,
    val durationMs: Long,
    val streamUrl: String?,
    val localUri: String?,
    val isDownloaded: Boolean = false
)

@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val imageUrl: String?,
    val monthlyListeners: Long?
)

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artistName: String,
    val artworkUrl: String?,
    val releaseYear: Int?,
    val songCount: Int
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val coverArtworkUrl: String?
)

@Entity(
    tableName = "playlist_song_cross_ref",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["playlistId"]), Index(value = ["songId"])]
)
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songId: String,
    val addedAt: Long = System.currentTimeMillis(),
    val orderIndex: Int = 0
)

@Entity(
    tableName = "favorites",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["songId"])]
)
data class FavoriteEntity(
    @PrimaryKey val songId: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "history",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["songId"])]
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val historyId: Long = 0,
    val songId: String,
    val playedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "downloads",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["songId"])]
)
data class DownloadEntity(
    @PrimaryKey val songId: String,
    val status: String,
    val progress: Float,
    val localFilePath: String?,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "recent_searches")
data class RecentSearchEntity(
    @PrimaryKey val query: String,
    val timestamp: Long = System.currentTimeMillis()
)
