package com.lunara.app.domain.model

/**
 * One shelf of a YouTube Music browse page: the horizontal rail the service itself draws, with its
 * own heading and its own rows.
 *
 * The heading is [title] exactly as YouTube Music wrote it ("New releases", "Chilled", "Top
 * artists"), so the feed shows the service's own sections instead of ones invented locally.
 *
 * The three row kinds are kept apart because they are used differently: a [songs] shelf plays as
 * it stands, a [cards] shelf opens a playlist or an album, and a [categories] shelf is a chooser
 * whose rows lead to further shelves.
 */
data class BrowseShelf(
    val title: String,
    val songs: List<Song> = emptyList(),
    val cards: List<BrowseCard> = emptyList(),
    val categories: List<BrowseCategory> = emptyList()
) {
    /** `true` while there is nothing to draw - YouTube sends such shelves too, and they are dropped. */
    val isEmpty: Boolean
        get() = songs.isEmpty() && cards.isEmpty() && categories.isEmpty()
}

/**
 * A tile of a browse shelf that leads somewhere: a playlist, an album or an artist.
 *
 * [browseId] is the page behind the tile as YouTube named it - `VL…` for a playlist, `MPRE…` for an
 * album, `UC…` for an artist - which is the whole id needed to open it, so nothing here has to be
 * reassembled from a "kind" plus an id.
 */
data class BrowseCard(
    val title: String,
    val subtitle: String? = null,
    val browseId: String,
    val artworkUrl: String? = null
)

/**
 * One row of YouTube Music's moods and genres chooser.
 *
 * Tapping it browses [browseId] with [params], the protobuf selector that picks *this* mood inside
 * the shared page - "Chill", "Commute" and the rest all live at the same browse id, and only the
 * params tell them apart.
 */
data class BrowseCategory(
    val title: String,
    val browseId: String,
    val params: String? = null
)
