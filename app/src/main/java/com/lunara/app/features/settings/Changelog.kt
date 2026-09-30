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
