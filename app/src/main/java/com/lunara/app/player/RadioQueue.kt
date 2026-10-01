package com.lunara.app.player

import com.lunara.app.domain.model.Song

/**
 * The rule that turns a page of YouTube Music's "up next" into queue entries.
 *
 * Kept apart from [PlayerManager] - and pure - because it is the part the listener actually
 * hears: YouTube's mix repeats the seed track and repeats songs across pages, and appending
 * any of that is what makes the end of a queue feel like a glitch instead of a radio.
 *
 * Nothing here touches the network or the queue manager; it answers one question: out of these
 * suggestions, which ones may be appended right now?
 */
object RadioQueue {

    /**
     * [candidates] in YouTube's own order, minus everything that would be a repeat:
     *
     *  - the seed track that is playing right now (YouTube always lists it first),
     *  - anything already in the queue, so the radio never re-queues a track the listener
     *    asked for or has already been given,
     *  - duplicates within the page itself.
     *
     * At most [limit] tracks come back, so one batch of recommendations cannot flood the queue.
     */
    fun pick(
        candidates: List<Song>,
        seedSongId: String?,
        alreadyQueued: Set<String>,
        limit: Int
    ): List<Song> {
        if (limit <= 0) return emptyList()
        val seen = HashSet<String>(limit)
        val picked = ArrayList<Song>(limit)
        for (song in candidates) {
            if (song.id.isBlank() || song.title.isBlank()) continue
            if (song.id == seedSongId) continue
            if (song.id in alreadyQueued) continue
            if (!seen.add(song.id)) continue
            picked.add(song)
            if (picked.size == limit) break
        }
        return picked
    }
}
