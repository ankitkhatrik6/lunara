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
        version = "2.11.0",
        headline = "Your own music, in Lunara",
        highlights = listOf(
            "The Library has an \"On device\" tab: the audio already on your phone - Music folder, Downloads and SD card - listed beside everything else you listen to.",
            "Lunara asks for the audio permission only when you ask for your own music, and says what the permission is for before Android's dialog appears.",
            "Granting it starts the scan straight away, so there is no second tap between \"Allow\" and seeing your tracks.",
            "What it finds is mirrored into Lunara's local database: the tab is populated the next time you open it without scanning again, and \"Rescan\" is one tap away when you add files.",
            "Playing a track from the device now queues the rest of it - the next song is the next file, not a queue of one.",
            "Nothing is uploaded and nothing is copied: the scan only reads the files that are already on the phone."
        )
    ),
    LunaraRelease(
        version = "2.10.0",
        headline = "A home screen that says hello",
        highlights = listOf(
            "Home opens with a greeting card: the time of day, a line to suit it and the Lunara note, drawn in your accent colour.",
            "The Favorites and Downloads tiles and the search box left the feed - three rows that only repeated what the rest of the app already offers.",
            "Nothing is out of reach: Favorites is the Library's first tab, Downloads sits in its top bar, and Search is one tap away on the bottom bar.",
            "The card takes its colours from the theme, so it follows your wallpaper when Material You is on."
        )
    ),
    LunaraRelease(
        version = "2.9.0",
        headline = "Lunara in your status bar",
        highlights = listOf(
            "The playback notification now shows the Lunara note instead of the generic player glyph Media3 ships, so a glance at the status bar tells you it is your music.",
            "The icon is drawn as a single-colour mark, which is what Android asks of a small icon: the system tints it to suit your theme and lays it over the album art colour.",
            "It follows the notification everywhere it appears - status bar, lock screen, quick settings and Bluetooth or headset controls - on every screen density."
        )
    ),
    LunaraRelease(
        version = "2.8.0",
        headline = "Lunara wears your wallpaper",
        highlights = listOf(
            "New Appearance setting: Material You. Switch it on and Lunara's accents and surfaces are taken from your wallpaper on Android 12 and newer.",
            "The palette reaches everywhere - feed, library, player and lyrics - and the System / Light / Dark selector still decides the brightness.",
            "On devices older than Android 12 the switch is visible but greyed out, so it is clear the phone cannot do this yet, instead of a tap that does nothing.",
            "Also in this release: the launch screen no longer crops the logo in half on a cold start."
        )
    ),
    LunaraRelease(
        version = "2.7.0",
        headline = "Downloads that really play",
        highlights = listOf(
            "Fixed saved tracks that refused to play with \"unsupported audio format\": a download is progressive audio now, never the stream playlist that had been stored under a song's name.",
            "Every saved file is checked before Lunara trusts it, so a playlist, a stub or a truncated transfer can no longer break playback - the next attempt downloads again.",
            "The player reads a file's real container from its own bytes, so an Opus/WebM download carrying another extension opens correctly.",
            "Offline is handled warmly: Home greets you, hides what needs the network and puts your downloads and on-device music first."
        )
    ),
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
