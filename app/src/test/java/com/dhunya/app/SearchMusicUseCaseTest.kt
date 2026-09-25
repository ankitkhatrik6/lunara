package com.dhunya.app

import com.dhunya.app.core.result.Resource
import com.dhunya.app.domain.model.Song
import com.dhunya.app.domain.repository.MusicRepository
import com.dhunya.app.domain.usecase.SearchMusicUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SearchMusicUseCaseTest {

    private val musicRepository: MusicRepository = mockk(relaxed = true)
    private lateinit var searchMusicUseCase: SearchMusicUseCase

    @Before
    fun setUp() {
        searchMusicUseCase = SearchMusicUseCase(musicRepository)
    }

    @Test
    fun invoke_withEmptyQuery_returnsEmptySuccessWithoutCallingRepository() = runTest {
        val result = searchMusicUseCase("   ")
        assertTrue(result is Resource.Success)
        val data = (result as Resource.Success).data
        assertTrue(data.songs.isEmpty())
        coVerify(exactly = 0) { musicRepository.searchSongs(any()) }
    }

    @Test
    fun invoke_withValidQuery_savesRecentSearchAndReturnsResults() = runTest {
        val query = "Horizon"
        val expectedSong = Song("1", "Midnight Horizon", "Aura Bloom")
        coEvery { musicRepository.searchSongs(query) } returns Resource.Success(listOf(expectedSong))
        coEvery { musicRepository.searchArtists(query) } returns Resource.Success(emptyList())
        coEvery { musicRepository.searchAlbums(query) } returns Resource.Success(emptyList())

        val result = searchMusicUseCase(query)
        assertTrue(result is Resource.Success)
        val data = (result as Resource.Success).data
        assertEquals(1, data.songs.size)
        assertEquals("Midnight Horizon", data.songs.first().title)
        coVerify(exactly = 1) { musicRepository.saveRecentSearch(query) }
    }
}
