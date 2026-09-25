package com.dhunya.app

import com.dhunya.app.domain.model.RepeatMode
import com.dhunya.app.domain.model.Song
import com.dhunya.app.player.QueueManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class QueueManagerTest {

    private lateinit var queueManager: QueueManager

    private val sampleSongs = listOf(
        Song("1", "Song One", "Artist A", durationMs = 180000),
        Song("2", "Song Two", "Artist B", durationMs = 200000),
        Song("3", "Song Three", "Artist C", durationMs = 220000)
    )

    @Before
    fun setUp() {
        queueManager = QueueManager()
    }

    @Test
    fun setQueue_setsSongsAndCurrentIndexCorrectly() {
        queueManager.setQueue(sampleSongs, startIndex = 1)
        assertEquals(3, queueManager.queue.value.size)
        assertEquals(1, queueManager.currentIndex.value)
        assertEquals("Song Two", queueManager.currentSong?.title)
    }

    @Test
    fun next_advancesToNextTrackWhenAvailable() {
        queueManager.setQueue(sampleSongs, startIndex = 0)
        val nextSong = queueManager.next()
        assertEquals("Song Two", nextSong?.title)
        assertEquals(1, queueManager.currentIndex.value)
    }

    @Test
    fun next_withRepeatAll_loopsToFirstTrackAtEnd() {
        queueManager.setQueue(sampleSongs, startIndex = 2)
        queueManager.cycleRepeatMode() // Repeat ALL
        assertEquals(RepeatMode.ALL, queueManager.repeatMode.value)

        val nextSong = queueManager.next()
        assertEquals("Song One", nextSong?.title)
        assertEquals(0, queueManager.currentIndex.value)
    }

    @Test
    fun addToQueueNext_insertsSongImmediatelyAfterCurrent() {
        queueManager.setQueue(sampleSongs, startIndex = 0)
        val newSong = Song("4", "Song Four", "Artist D")
        queueManager.addToQueueNext(newSong)

        assertEquals(4, queueManager.queue.value.size)
        assertEquals(newSong.id, queueManager.queue.value[1].id)
    }

    @Test
    fun removeAt_removesCorrectItem() {
        queueManager.setQueue(sampleSongs, startIndex = 0)
        queueManager.removeAt(1)

        assertEquals(2, queueManager.queue.value.size)
        assertEquals("Song Three", queueManager.queue.value[1].title)
    }
}
