package com.dhunya.app

import com.dhunya.app.data.local.RealDownloadManager
import com.dhunya.app.data.local.dao.DownloadDao
import com.dhunya.app.data.local.dao.SongDao
import com.dhunya.app.data.remote.music.MusicRemoteDataSource
import com.dhunya.app.data.repository.DownloadRepositoryImpl
import com.dhunya.app.domain.model.PlayableMedia
import com.dhunya.app.domain.model.Song
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

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
        val file = java.io.File("/tmp/test_song_1.m4a")
        coEvery { remoteSource.resolvePlayableMedia(song) } returns PlayableMedia(
            song = song,
            mediaUri = "https://example.com/audio.m4a",
            isLocal = false
        )
        coEvery {
            realDownloadManager.downloadStream(any(), any(), any())
        } returns file

        downloadRepository.startDownload(song)

        coVerify(exactly = 1) { songDao.insertSong(any()) }
        coVerify(exactly = 1) { downloadDao.insertOrUpdateDownload(any()) }
        coVerify(exactly = 1) {
            realDownloadManager.downloadStream(song.id, "https://example.com/audio.m4a", any())
        }
        coVerify(exactly = 1) {
            songDao.insertSong(match { it.id == song.id && it.isDownloaded })
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
}
