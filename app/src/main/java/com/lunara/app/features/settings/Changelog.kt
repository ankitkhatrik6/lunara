package com.lunara.app.features.settings

/**
 * One shipped release, written for people rather than for a changelog file.
 */
data class LunaraRelease(
    val version: String,
    val headline: String,
    val highlights: List<String>
)

/**
 * Bundled release notes.
 *
 * These live in the app instead of being fetched so "What's new" works offline and instantly; the
 * updater still shows the notes of a *newer* release straight from GitHub.
 */
val LunaraChangelog: List<LunaraRelease> = listOf(
    LunaraRelease(
        version = "2.6.0",
        headline = "Faster start for every track",
        highlights = listOf(
            "One fewer network round trip per play: the mid-file check now decides on its own when the stream's size is known.",
            "Lunara remembers the client identity that last streamed a track to the end and starts there.",
            "Fallbacks are untouched: a stream that cuts out still swaps to the next candidate mid track."
        )
    ),
    LunaraRelease(
        version = "2.5.0",
        headline = "Offline playback that works",
        highlights = listOf(
            "Downloaded tracks play from storage even when they are opened from Home, Search or a queue.",
            "Without a connection (or with Offline mode on) Lunara names the track that is not downloaded instead of spinning.",
            "Offline mode in Settings is now enforced: streaming is skipped and only saved files play.",
            "Prefetching stays quiet while offline instead of spending battery on requests that cannot succeed."
        )
    ),
    LunaraRelease(
        version = "2.4.0",
        headline = "A home screen that is actually alive",
        highlights = listOf(
            "Trending, Quick picks, New releases and Popular rails are fetched from YouTube Music on every launch.",
            "Moods and genres: pick a mood and the rail beside it becomes that mix, ready to play.",
            "Lunara wordmark centred at the top with the settings icon on the right, plus a search bar in the feed.",
            "Skeleton placeholders while rails load, and a retry card when the catalogue cannot be reached."
        )
    ),
    LunaraRelease(
        version = "2.3.0",
        headline = "Settings you can actually use",
        highlights = listOf(
            "Theme selector: System, Light or Dark, saved with your other preferences.",
            "In-app updates: Lunara checks GitHub releases and offers the APK directly.",
            "What's new: release notes for the versions you have installed.",
            "Grouped sections for playback, storage and about, with the version you are running."
        )
    ),
    LunaraRelease(
        version = "2.2.0",
        headline = "Light and dark themes",
        highlights = listOf(
            "New Appearance setting: follow the system, or pin light or dark.",
            "A daylight palette tuned so the teal accent stays readable on white.",
            "Every screen, including the player and lyrics, follows the chosen theme."
        )
    ),
    LunaraRelease(
        version = "2.1.0",
        headline = "A proper Lunara start-up",
        highlights = listOf(
            "Branded splash screen that hides real start-up work instead of delaying it.",
            "New launcher icon and adaptive icon built from the Lunara mark.",
            "Lunara naming and identity throughout the app."
        )
    ),
    LunaraRelease(
        version = "2.0.3",
        headline = "Playback that survives long tracks",
        highlights = listOf(
            "Large, range-based stream requests keep their speed from the first second to the last.",
            "Requests carry the user agent of the client that issued them, which stops mid-track 403s.",
            "Expired stream URLs are detected and replaced from a fresh candidate automatically."
        )
    ),
    LunaraRelease(
        version = "2.0.2",
        headline = "Stability pass",
        highlights = listOf(
            "Smoother seeking and faster recovery when a stream stalls.",
            "Deeper probing of stream candidates before playback starts."
        )
    )
)
