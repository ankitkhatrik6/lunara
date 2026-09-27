package com.dhunya.app.player

import com.dhunya.app.domain.model.RepeatMode
import com.dhunya.app.domain.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QueueManager @Inject constructor() {
    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private var originalQueue: List<Song> = emptyList()

    fun setQueue(songs: List<Song>, startIndex: Int = 0) {
        originalQueue = songs
        if (_isShuffle.value) {
            val current = songs.getOrNull(startIndex)
            val shuffled = songs.filter { it.id != current?.id }.shuffled().toMutableList()
            if (current != null) shuffled.add(0, current)
            _queue.value = shuffled
            _currentIndex.value = if (current != null) 0 else -1
        } else {
            _queue.value = songs
            _currentIndex.value = startIndex.coerceIn(-1, songs.size - 1)
        }
    }

    val currentSong: Song?
        get() = _queue.value.getOrNull(_currentIndex.value)

    /**
     * `true` while [next] would hand out another track (repeat mode aware).
     *
     * The media notification asks this before enabling its skip buttons, so the state has to
     * be computed from the queue and not from ExoPlayer's single media item.
     */
    val hasUpcoming: Boolean
        get() {
            val q = _queue.value
            if (q.isEmpty()) return false
            return when (_repeatMode.value) {
                RepeatMode.ALL -> true
                RepeatMode.ONE -> _currentIndex.value != -1
                RepeatMode.OFF -> _currentIndex.value + 1 < q.size
            }
        }

    /** `true` while a track is loaded, i.e. the notification can offer "previous". */
    val hasPrevious: Boolean
        get() = _queue.value.isNotEmpty() && _currentIndex.value >= 0

    /**
     * Tracks queued after the current one, in play order. Used to pre-resolve the next
     * stream while the current track is still playing.
     */
    fun upcomingSongs(): List<Song> {
        val q = _queue.value
        val start = _currentIndex.value + 1
        if (start !in q.indices) return emptyList()
        return q.subList(start, q.size).toList()
    }

    fun next(): Song? {
        val q = _queue.value
        if (q.isEmpty()) return null

        if (_repeatMode.value == RepeatMode.ONE && _currentIndex.value != -1) {
            return q[_currentIndex.value]
        }

        val nextIdx = _currentIndex.value + 1
        return if (nextIdx < q.size) {
            _currentIndex.value = nextIdx
            q[nextIdx]
        } else if (_repeatMode.value == RepeatMode.ALL) {
            _currentIndex.value = 0
            q.firstOrNull()
        } else {
            null
        }
    }

    fun previous(): Song? {
        val q = _queue.value
        if (q.isEmpty()) return null
        val prevIdx = _currentIndex.value - 1
        return if (prevIdx >= 0) {
            _currentIndex.value = prevIdx
            q[prevIdx]
        } else {
            _currentIndex.value = 0
            q.firstOrNull()
        }
    }

    fun playTrackAt(index: Int): Song? {
        val q = _queue.value
        return if (index in q.indices) {
            _currentIndex.value = index
            q[index]
        } else null
    }

    /**
     * Replaces a queue entry (matched by id) with an updated version of the same
     * track, e.g. once a stream URL has been resolved for a YouTube Music song.
     */
    fun updateSong(song: Song) {
        _queue.value = _queue.value.map { if (it.id == song.id) song else it }
        originalQueue = originalQueue.map { if (it.id == song.id) song else it }
    }

    fun addToQueueNext(song: Song) {
        val list = _queue.value.toMutableList()
        val insertIndex = (_currentIndex.value + 1).coerceAtMost(list.size)
        list.add(insertIndex, song)
        _queue.value = list
    }

    fun addToQueueEnd(song: Song) {
        val list = _queue.value.toMutableList()
        list.add(song)
        _queue.value = list
    }

    fun removeAt(index: Int) {
        val list = _queue.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            if (index < _currentIndex.value) {
                _currentIndex.value--
            } else if (index == _currentIndex.value && index >= list.size) {
                _currentIndex.value = list.size - 1
            }
            _queue.value = list
        }
    }

    fun reorder(fromIndex: Int, toIndex: Int) {
        val list = _queue.value.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            // Adjust current index if affected
            val curr = _currentIndex.value
            if (curr == fromIndex) {
                _currentIndex.value = toIndex
            } else if (fromIndex < curr && toIndex >= curr) {
                _currentIndex.value = curr - 1
            } else if (fromIndex > curr && toIndex <= curr) {
                _currentIndex.value = curr + 1
            }
            _queue.value = list
        }
    }

    fun toggleShuffle() {
        val current = currentSong
        val nextShuffle = !_isShuffle.value
        _isShuffle.value = nextShuffle
        if (nextShuffle) {
            val shuffled = originalQueue.filter { it.id != current?.id }.shuffled().toMutableList()
            if (current != null) shuffled.add(0, current)
            _queue.value = shuffled
            _currentIndex.value = if (current != null) 0 else -1
        } else {
            _queue.value = originalQueue
            _currentIndex.value = if (current != null) originalQueue.indexOfFirst { it.id == current.id } else -1
        }
    }

    fun cycleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    fun clearQueue() {
        _queue.value = emptyList()
        _currentIndex.value = -1
        originalQueue = emptyList()
    }
}
