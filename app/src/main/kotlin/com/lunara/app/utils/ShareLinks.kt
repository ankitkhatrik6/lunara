/**
 * Ankit Khatri KC (C) 2026
 * Licensed under GPL-3.0
 */

package com.lunara.app.utils

import android.net.Uri

/**
 * The custom domain every shareable link is built on.
 *
 * These are the links that go out with a shared song, album, playlist or artist. They are short,
 * they carry the app's own name instead of YouTube's, and they open Lunara when it is installed:
 * Android App Links hand the address straight to the app, and when it is not installed the same
 * address lands on the Lunara site, which offers the app and explains what it is. That is why the
 * share buttons use these and not a music.youtube.com URL any more.
 */
object ShareLinks {
    const val SITE = "https://lunara.ankitak.com.np"

    /** A single song, named by its video id. */
    fun song(videoId: String): String = "$SITE/song/$videoId"

    /** An album, named by its browse id (the `MPREb_...` id the album screen opens). */
    fun album(browseId: String): String = "$SITE/album/$browseId"

    /** A playlist that lives on YouTube Music, named by its playlist id. */
    fun playlist(playlistId: String): String = "$SITE/playlist/$playlistId"

    /** An artist channel, named by its channel id. */
    fun artist(channelId: String): String = "$SITE/artist/$channelId"

    /** What a shared address points at, so a link can be turned back into a screen to open. */
    sealed interface Target {
        data class Song(val videoId: String) : Target

        data class Album(val browseId: String) : Target

        data class Playlist(val playlistId: String) : Target

        data class Artist(val channelId: String) : Target
    }

    /**
     * The target [uri] names, or null when it is not one of our share links. Accepts both the web
     * address (`https://lunara.ankitak.com.np/song/ID`) and the in-app scheme the site hands off to
     * (`lunara://song/ID`), because a browser redirects to the scheme form before the app sees it.
     */
    fun parse(uri: Uri): Target? {
        val host = uri.host?.lowercase()
        val scheme = uri.scheme?.lowercase()
        val isOurHost =
            host == "lunara.ankitak.com.np" ||
                (scheme == "lunara" && host in setOf("song", "album", "playlist", "artist"))
        if (!isOurHost) return null

        val segments = uri.pathSegments.filter { it.isNotBlank() }
        // Web form: ["song", "ID"]. Scheme form: host already is the type, so the id is the first path.
        val type =
            if (host == "lunara.ankitak.com.np") segments.firstOrNull()?.lowercase() else host
        val id =
            if (host == "lunara.ankitak.com.np") {
                segments.getOrNull(1) ?: uri.getQueryParameter("v") ?: uri.getQueryParameter("list")
            } else {
                segments.firstOrNull()
            } ?: return null

        return when (type) {
            "song" -> Target.Song(id)
            "album" -> Target.Album(id)
            "playlist" -> Target.Playlist(id)
            "artist" -> Target.Artist(id)
            else -> null
        }
    }
}
