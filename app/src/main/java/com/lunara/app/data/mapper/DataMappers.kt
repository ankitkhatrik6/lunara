package com.lunara.app.data.mapper

import com.lunara.app.data.local.entity.DownloadEntity
import com.lunara.app.data.local.entity.PlaylistEntity
import com.lunara.app.data.local.entity.SongEntity
import com.lunara.app.domain.model.DownloadItem
import com.lunara.app.domain.model.DownloadStatus
import com.lunara.app.domain.model.Playlist
import com.lunara.app.domain.model.Song

fun SongEntity.toDomain(isFavorite: Boolean = false): Song {
    return Song(
        id = id,
        title = title,
        artistName = artistName,
        albumName = albumName,
        artworkUrl = artworkUrl,
        durationMs = durationMs,
        streamUrl = streamUrl,
        localUri = localUri,
        isDownloaded = isDownloaded,
        isFavorite = isFavorite
    )
}

fun Song.toEntity(): SongEntity {
    return SongEntity(
        id = id,
        title = title,
        artistName = artistName,
        albumName = albumName,
        artworkUrl = artworkUrl,
        durationMs = durationMs,
        streamUrl = streamUrl,
        localUri = localUri,
        isDownloaded = isDownloaded
    )
}

fun PlaylistEntity.toDomain(songCount: Int = 0): Playlist {
    return Playlist(
        id = id,
        name = name,
        description = description,
        createdAt = createdAt,
        songCount = songCount,
        coverArtworkUrl = coverArtworkUrl
    )
}

fun DownloadEntity.toDomain(song: Song): DownloadItem {
    return DownloadItem(
        song = song,
        progress = progress,
        status = try { DownloadStatus.valueOf(status) } catch (e: Exception) { DownloadStatus.QUEUED },
        localFilePath = localFilePath,
        totalBytes = totalBytes,
        downloadedBytes = downloadedBytes
    )
}
