package com.lunara.app

import com.lunara.app.data.local.RealDownloadManager
import com.lunara.app.data.local.dao.DownloadDao
import com.lunara.app.data.local.dao.SongDao
import com.lunara.app.data.remote.music.MusicRemoteDataSource
import com.lunara.app.data.repository.DownloadRepositoryImpl
import com.lunara.app.domain.model.DownloadStatus
import com.lunara.app.domain.model.PlayableMedia
import com.lunara.app.domain.model.Song
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadRepositoryTest {

    private val downloadDao: DownloadDao = mockk(relaxed = true)
    private val songDao: SongDao = mockk(relaxed = true)
    private val realDownloadManager: RealDownloadManager = mockk(relaxed = true)
    private val remoteSource: MusicRemoteDataSource = mockk()

    @Before
    fun setUp() {
        // Said out loud rather than left to `relaxed = true`. `getSongFile` is the switch between
        // "record the copy already on disk" and "download it", and the relaxed answer to a `File?`
        // is a *mock file*, not null - which sent every test below down the local branch, where a
        // stubbed `Uri.fromFile` then blew up before a single row was written.
        every { realDownloadManager.getSongFile(any()) } returns null
    }

    /**
     * The repository downloads on the scope it is handed, so the tests hand it runTest's own
     * [CoroutineScope] - `backgroundScope`. Work and test then share one scheduler: a private
     * TestScope would leave the transfer's continuation queued on a scheduler nothing advances,
     * and the assertions would inspect a download that never ran. `testScheduler.advanceUntilIdle()`
     * after each call drains whatever the transfer queued before the verifications.
     */
    private fun repositoryFor(scope: CoroutineScope) = DownloadRepositoryImpl(
        downloadDao = downloadDao,
        songDao = songDao,
        realDownloadManager = realDownloadManager,
        remoteSource = remoteSource,
        downloadScope = scope
    )

    @Test
    fun startDownload_resolvesMissingUrl_thenDownloadsAndPersists() = runTest {
        val song = Song(
            id = "test_song_1",
            title = "Midnight Horizon",
            artistName = "Aura Bloom"
        )
        val file = audioFile("test_song_1")
        val repository = repositoryFor(backgroundScope)
        // Downloads ask for progressive audio only (`resolveDownloadableMedia`), never for a
        // streamable-but-unplayable HLS playlist.
        coEvery { remoteSource.resolveDownloadableMedia(any()) } returns PlayableMedia(
            song = song,
            mediaUri = "https://example.com/audio.m4a",
            isLocal = false
        )
        coEvery {
            realDownloadManager.downloadStream(any(), any(), any())
        } returns file

        repository.startDownload(song)
        testScheduler.advanceUntilIdle()

        // The row is refreshed with the resolved URL first, and that same row is only flagged as
        // downloaded once real audio is on disk.
        coVerify(exactly = 1) {
            songDao.insertSong(match { it.id == song.id && !it.isDownloaded })
        }
        coVerify(exactly = 1) {
            songDao.insertSong(match { it.id == song.id && it.isDownloaded && it.localUri != null })
        }
        // One row saying DOWNLOADING, one saying COMPLETED.
        coVerify(exactly = 1) {
            downloadDao.insertOrUpdateDownload(
                match { it.status == DownloadStatus.DOWNLOADING.name }
            )
        }
        coVerify(exactly = 1) {
            downloadDao.insertOrUpdateDownload(
                match { it.status == DownloadStatus.COMPLETED.name && it.localFilePath != null }
            )
        }
        coVerify(exactly = 1) {
            realDownloadManager.downloadStream(song.id, "https://example.com/audio.m4a", any())
        }
        // A save that worked throws nothing away.
        verify(exactly = 0) { realDownloadManager.deleteDownloadedFile(any()) }
    }

    @Test
    fun startDownload_rejectsPlaylistBytes_insteadOfSavingUnplayableFile() = runTest {
        val song = Song(
            id = "test_song_2",
            title = "Ghost Signal",
            artistName = "Aura Bloom"
        )
        // What the resolver used to save: the HLS manifest itself, named `lunara_..._2.m4a`. The
        // MP4 extractor rejects it as "unsupported audio format" (3003), which is why downloaded
        // tracks would not play.
        val stub = playlistFile("test_song_2")
        val repository = repositoryFor(backgroundScope)
        coEvery { remoteSource.resolveDownloadableMedia(any()) } returns PlayableMedia(
            song = song,
            mediaUri = "https://example.com/audio.m4a",
            isLocal = false
        )
        coEvery {
            realDownloadManager.downloadStream(any(), any(), any())
        } returns stub

        repository.startDownload(song)
        testScheduler.advanceUntilIdle()

        // The bad bytes are dropped and nothing records a finished download.
        verify(exactly = 1) { realDownloadManager.deleteDownloadedFile(song.id) }
        coVerify(exactly = 1) {
            downloadDao.insertOrUpdateDownload(match { it.status == DownloadStatus.FAILED.name })
        }
        coVerify(exactly = 1) {
            downloadDao.insertOrUpdateDownload(
                match { it.status == DownloadStatus.DOWNLOADING.name }
            )
        }
        coVerify(exactly = 0) { songDao.insertSong(match { it.isDownloaded }) }
        // The row kept its remote URL, so the track is still streamable.
        coVerify(exactly = 1) {
            songDao.insertSong(match { it.id == song.id && it.localUri == null })
        }
    }

    @Test
    fun cancelDownload_cancelsJobDeletesFileAndClearsRows() = runTest {
        val songId = "test_song_1"
        val repository = repositoryFor(backgroundScope)

        repository.cancelDownload(songId)

        verify(exactly = 1) { realDownloadManager.deleteDownloadedFile(songId) }
        coVerify(exactly = 1) { downloadDao.deleteDownload(songId) }
        coVerify(exactly = 1) { songDao.updateDownloadStatus(songId, false, null) }
    }

    /**
     * The branch a relaxed `getSongFile` used to divert every other test into: a file that is
     * already on disk is recorded, with no network round trip and nothing to clean up.
     */
    @Test
    fun startDownload_recordsExistingFile_withoutDownloadingAgain() = runTest {
        val song = Song(
            id = "test_song_3",
            title = "Paper Lanterns",
            artistName = "Aura Bloom"
        )
        val existing = audioFile("test_song_3")
        val repository = repositoryFor(backgroundScope)
        // Same matcher shape as `setUp`, so the answer cannot depend on which of two matching
        // stubs MockK prefers.
        every { realDownloadManager.getSongFile(any()) } returns existing

        repository.startDownload(song)
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            downloadDao.insertOrUpdateDownload(
                match { it.status == DownloadStatus.COMPLETED.name && it.localFilePath != null }
            )
        }
        coVerify(exactly = 1) {
            songDao.insertSong(match { it.id == song.id && it.isDownloaded && it.localUri != null })
        }
        // Nothing was downloaded or resolved, and nothing was deleted.
        coVerify(exactly = 0) { realDownloadManager.downloadStream(any(), any(), any()) }
        coVerify(exactly = 0) { remoteSource.resolveDownloadableMedia(any()) }
        verify(exactly = 0) { realDownloadManager.deleteDownloadedFile(any()) }
    }

    /** Real, if silent, audio: an `ftyp` header and enough bytes to clear the size floor. */
    private fun audioFile(name: String): File {
        val file = File.createTempFile(name, ".m4a")
        file.deleteOnExit()
        file.outputStream().use { out ->
            out.write(byteArrayOf(0, 0, 0, 0x20, 0x66, 0x74, 0x79, 0x70))
            out.write(ByteArray(40 * 1024))
        }
        return file
    }

    /** A playlist saved under an audio extension: text, not music. */
    private fun playlistFile(name: String): File {
        val file = File.createTempFile(name, ".m4a")
        file.deleteOnExit()
        file.outputStream().use { out ->
            out.write("#EXTM3U\n#EXT-X-VERSION:3\n".toByteArray())
            out.write(ByteArray(40 * 1024))
        }
        return file
    }
}
