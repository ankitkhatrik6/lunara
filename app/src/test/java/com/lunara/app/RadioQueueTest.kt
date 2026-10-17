package com.lunara.app

import com.lunara.app.domain.model.Song
import com.lunara.app.player.RadioQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioQueueTest {

    private val seed = Song("yt_seed", "Never Gonna Give You Up", "Rick Astley")

    private val suggestions = listOf(
        Song("yt_a", "Together Forever", "Rick Astley"),
        Song("yt_b", "Cheri Cheri Lady", "Modern Talking"),
        Song("yt_c", "Take On Me", "a-ha")
    )

    @Test
    fun pick_neverHandsBackTheSeedTrackThatIsPlaying() {
        // YouTube always lists the track you started the radio from as the first suggestion.
        val picked = RadioQueue.pick(
            candidates = listOf(seed) + suggestions,
            seedSongId = seed.id,
            alreadyQueued = emptySet(),
            limit = 25
        )

        assertEquals(listOf("yt_a", "yt_b", "yt_c"), picked.map { it.id })
    }

    @Test
    fun pick_skipsSongsThatAreAlreadyInTheQueue() {
        val picked = RadioQueue.pick(
            candidates = suggestions,
            seedSongId = seed.id,
            alreadyQueued = setOf("yt_b"),
            limit = 25
        )

        assertEquals(listOf("yt_a", "yt_c"), picked.map { it.id })
    }

    @Test
    fun pick_dropsSongsRepeatedWithinTheSamePage() {
        val picked = RadioQueue.pick(
            candidates = listOf(suggestions[0], suggestions[1], suggestions[0]),
            seedSongId = seed.id,
            alreadyQueued = emptySet(),
            limit = 25
        )

        assertEquals(listOf("yt_a", "yt_b"), picked.map { it.id })
    }

    @Test
    fun pick_capsOneBatchAtTheLimitAndKeepsYoutubesOrder() {
        val picked = RadioQueue.pick(
            candidates = suggestions,
            seedSongId = seed.id,
            alreadyQueued = emptySet(),
            limit = 2
        )

        assertEquals(listOf("yt_a", "yt_b"), picked.map { it.id })
    }

    @Test
    fun pick_ignoresRowsWithoutAnIdOrTitle() {
        val picked = RadioQueue.pick(
            candidates = listOf(Song("", "Untitled", "X"), Song("yt_z", " ", "X")) + suggestions,
            seedSongId = seed.id,
            alreadyQueued = emptySet(),
            limit = 25
        )

        assertEquals(listOf("yt_a", "yt_b", "yt_c"), picked.map { it.id })
    }

    @Test
    fun pick_returnsNothingForAnEmptyPageOrANonPositiveLimit() {
        assertTrue(RadioQueue.pick(emptyList(), seed.id, emptySet(), 25).isEmpty())
        assertTrue(RadioQueue.pick(suggestions, seed.id, emptySet(), 0).isEmpty())
    }

    @Test
    fun pick_givesUpOnceEverythingOnThePageIsAlreadyQueued() {
        // The end of a mix: the same songs come back and nothing new may be appended.
        val picked = RadioQueue.pick(
            candidates = suggestions,
            seedSongId = seed.id,
            alreadyQueued = suggestions.map { it.id }.toSet(),
            limit = 25
        )

        assertTrue(picked.isEmpty())
    }
}
