package com.dhunya.app

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.dhunya.app.data.local.RealDownloadManager
import com.dhunya.app.data.local.dao.DownloadDao
import com.dhunya.app.data.local.dao.SongDao
import com.dhunya.app.data.repository.DownloadRepositoryImpl
import com.dhunya.app.domain.model.Song
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class DownloadRepositoryTest {

    private val downloadDao: DownloadDao = mockk(relaxed = true)
    private val songDao: SongDao = mockk(relaxed = true)
    private val workManager: WorkManager = mockk(relaxed = true)
    private val realDownloadManager: RealDownloadManager = mockk(relaxed = true)

    private lateinit var downloadRepository: DownloadRepositoryImpl

    @Before
    fun setUp() {
        downloadRepository = DownloadRepositoryImpl(
            downloadDao = downloadDao,
            songDao = songDao,
            workManager = workManager,
            realDownloadManager = realDownloadManager
        )
    }

    @Test
    fun startDownload_enqueuesUniqueWorkInWorkManager() = runTest {
        val song = Song(
            id = "test_song_1",
            title = "Midnight Horizon",
            artistName = "Aura Bloom",
            streamUrl = "https://example.com/audio.mp3"
        )

        downloadRepository.startDownload(song)

        coVerify(exactly = 1) { songDao.insertSong(any()) }
        coVerify(exactly = 1) { downloadDao.insertOrUpdateDownload(any()) }
        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                "download_${song.id}",
                ExistingWorkPolicy.REPLACE,
                any<OneTimeWorkRequest>()
            )
        }
    }

    @Test
    fun cancelDownload_cancelsWorkManagerTaskAndDeletesLocalData() = runTest {
        val songId = "test_song_1"

        downloadRepository.cancelDownload(songId)

        verify(exactly = 1) { workManager.cancelUniqueWork("download_$songId") }
        verify(exactly = 1) { realDownloadManager.deleteDownloadedFile(songId) }
        coVerify(exactly = 1) { downloadDao.deleteDownload(songId) }
        coVerify(exactly = 1) { songDao.updateDownloadStatus(songId, false, null) }
    }
}
