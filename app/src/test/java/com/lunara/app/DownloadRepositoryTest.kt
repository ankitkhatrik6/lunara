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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var downloadRepository: DownloadRepositoryImpl

    @Before
    fun setUp() {
        downloadRepository = DownloadRepositoryImpl(
            downloadDao = downloadDao,
            songDao = songDao,
            realDownloadManager = realDownloadManager,
            remoteSource = remoteSource,
            downloadScope = testScope
        )
    }

    @Test
    fun startDownload_resolvesMissingUrl_thenDownloadsAndPersists() = runTest {
        val song = Song(
            id = "test_song_1",
            title = "Midnight Horizon",
            artistName = "Aura Bloom"
        )
        val file = audioFile("test_song_1")
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

        downloadRepository.startDownload(song)

        coVerify(exactly = 1) { songDao.insertSong(any()) }
        // One row saying DOWNLOADING, one saying COMPLETED.
        coVerify(exactly = 2) { downloadDao.insertOrUpdateDownload(any()) }
        coVerify(exactly = 1) {
            realDownloadManager.downloadStream(song.id, "https://example.com/audio.m4a", any())
        }
        coVerify(exactly = 1) {
            songDao.insertSong(match { it.id == song.id && it.isDownloaded })
        }
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
        coEvery { remoteSource.resolveDownloadableMedia(any()) } returns PlayableMedia(
            song = song,
            mediaUri = "https://example.com/audio.m4a",
            isLocal = false
        )
        coEvery {
            realDownloadManager.downloadStream(any(), any(), any())
        } returns stub

        downloadRepository.startDownload(song)

        // The bad bytes are dropped and nothing records a finished download.
        verify(exactly = 1) { realDownloadManager.deleteDownloadedFile(song.id) }
        coVerify(exactly = 0) { songDao.insertSong(match { it.isDownloaded }) }
        coVerify(exactly = 1) {
            downloadDao.insertOrUpdateDownload(match { it.status == DownloadStatus.FAILED.name })
        }
    }

    @Test
    fun cancelDownload_cancelsJobDeletesFileAndClearsRows() = runTest {
        val songId = "test_song_1"

        downloadRepository.cancelDownload(songId)

        verify(exactly = 1) { realDownloadManager.deleteDownloadedFile(songId) }
        coVerify(exactly = 1) { downloadDao.deleteDownload(songId) }
        coVerify(exactly = 1) { songDao.updateDownloadStatus(songId, false, null) }
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
