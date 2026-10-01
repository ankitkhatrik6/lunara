package com.lunara.app.data.local

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.lunara.app.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalAudioDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * The runtime permissions that guard a MediaStore audio read on *this* OS version.
     *
     * Android 13 split storage access by media type, so `READ_EXTERNAL_STORAGE` is dead weight
     * there (which is why the manifest declares it with `maxSdkVersion="32"`), and
     * `READ_MEDIA_AUDIO` does not exist before Android 13. Asking for the wrong one is not an
     * error the system reports - it is a dialog that never appears and a scan that comes back
     * empty, which is exactly the bug this list prevents.
     */
    val requiredPermissions: List<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    /**
     * True when every entry of [requiredPermissions] has already been granted.
     *
     * A read permission can be revoked while Lunara is running (the user can take it back in
     * system settings), so this is asked again before every scan rather than remembered.
     */
    fun hasAudioPermission(): Boolean = requiredPermissions.all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Scans the Android MediaStore content provider for all local audio files
     * stored on the device (internal storage, SD card, Music folder, Downloads).
     */
    suspend fun queryDeviceAudioFiles(): List<Song> = withContext(Dispatchers.IO) {
        // Without the runtime permission the query below throws SecurityException, and the
        // `catch` at the bottom of this function would swallow it - so the caller would be told
        // "this phone has no music" instead of "Lunara was not allowed to look". Bail out
        // explicitly so the two cases stay distinguishable.
        if (!hasAudioPermission()) return@withContext emptyList()

        val songList = mutableListOf<Song>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA
        )

        // Only query real music files greater than 10 seconds to filter out ringtones/notification sounds
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 10000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            val cursor = context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
            )

            cursor?.use { c ->
                val idColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val dataColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (c.moveToNext()) {
                    val id = c.getLong(idColumn)
                    val title = c.getString(titleColumn) ?: "Unknown Track"
                    val artist = c.getString(artistColumn) ?: "Unknown Artist"
                    val album = c.getString(albumColumn)
                    val duration = c.getLong(durationColumn)
                    val albumId = c.getLong(albumIdColumn)
                    val filePath = c.getString(dataColumn)

                    val contentUri: Uri = ContentUris.withAppendedId(collection, id)
                    val artworkUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    ).toString()

                    songList.add(
                        Song(
                            id = "local_$id",
                            title = title,
                            artistName = if (artist == "<unknown>") "Unknown Artist" else artist,
                            albumName = album,
                            artworkUrl = artworkUri,
                            durationMs = duration,
                            streamUrl = null,
                            localUri = contentUri.toString(),
                            isDownloaded = true,
                            isFavorite = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        songList
    }
}
