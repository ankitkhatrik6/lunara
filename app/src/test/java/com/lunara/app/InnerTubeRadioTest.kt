package com.lunara.app

import com.lunara.app.data.remote.innertube.InnerTubeParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The song radio, read out of a **real** `next` response.
 *
 * `fixtures/next_radio.json` is an unedited slice of what `music.youtube.com/youtubei/v1/next`
 * answers for `RDAMVMdQw4w9WgXcQ` (an actual queue of 50 tracks, trimmed to its first two rows so
 * the fixture stays small). It is here so the parser is tested against YouTube's own field names
 * and quirks - the `longBylineText` of a queue row is `Artist • 1.8B views • 19M likes`, which is
 * exactly the shape that used to leak a view count into the album field.
 */
class InnerTubeRadioTest {

    private fun fixture(name: String): JsonObject {
        val text = requireNotNull(javaClass.getResourceAsStream("/fixtures/$name")) {
            "missing test fixture $name"
        }.bufferedReader().use { it.readText() }
        return Json.parseToJsonElement(text) as JsonObject
    }

    @Test
    fun parseRadio_readsTheQueueYoutubeServesForATrack() {
        val page = InnerTubeParser.parseRadio(fixture("next_radio.json"))

        assertEquals(listOf("yt_dQw4w9WgXcQ", "yt_yPYZpwSpKmA"), page.songs.map { it.id })
        assertEquals("Never Gonna Give You Up", page.songs[0].title)
        assertEquals("Together Forever (2022 Remaster)", page.songs[1].title)
        assertEquals(listOf("Rick Astley", "Rick Astley"), page.songs.map { it.artistName })
    }

    @Test
    fun parseRadio_keepsTheViewerCountsOutOfTheMetadata() {
        val page = InnerTubeParser.parseRadio(fixture("next_radio.json"))

        // "1.8B views" and "19M likes" sit in the byline where an album would be.
        assertNull(page.songs[0].albumName)
        assertNull(page.songs[1].albumName)
    }

    @Test
    fun parseRadio_readsTheDurationAndArtworkOfEveryRow() {
        val page = InnerTubeParser.parseRadio(fixture("next_radio.json"))

        assertEquals(214000L, page.songs[0].durationMs) // 3:34
        assertEquals(204000L, page.songs[1].durationMs) // 3:24
        assertTrue(page.songs.all { it.artworkUrl?.contains("i.ytimg.com/vi/") == true })
    }

    @Test
    fun parseRadio_returnsTheTokenThatAsksForMoreOfTheMix() {
        val page = InnerTubeParser.parseRadio(fixture("next_radio.json"))

        // Without this token the radio would be a 50 track list instead of an endless one.
        assertTrue(page.continuation.orEmpty().startsWith("CDISNRILeVJZ"))
    }

    @Test
    fun parseRadio_readsTheRowsOfAContinuationPage() {
        // A later page of an endless mix comes back as `playlistPanelContinuation` instead of
        // `musicQueueRenderer`, so both shapes have to lead to the same rows.
        val page = InnerTubeParser.parseRadio(
            Json.parseToJsonElement(
                """
                {
                  "continuationContents": {
                    "playlistPanelContinuation": {
                      "contents": [
                        { "playlistPanelVideoRenderer": {
                            "videoId": "abc123",
                            "title": { "runs": [ { "text": "Another One Bites The Dust" } ] },
                            "shortBylineText": { "runs": [ { "text": "Queen" } ] },
                            "lengthText": { "runs": [ { "text": "3:35" } ] }
                        } }
                      ],
                      "continuations": [
                        { "nextRadioContinuationData": { "continuation": "PAGE_TWO" } }
                      ]
                    }
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals(1, page.songs.size)
        assertEquals("yt_abc123", page.songs[0].id)
        assertEquals("Queen", page.songs[0].artistName)
        assertEquals(215000L, page.songs[0].durationMs)
        assertEquals("PAGE_TWO", page.continuation)
    }

    @Test
    fun parseRadio_skipsRowsThatCannotBePlayed() {
        val page = InnerTubeParser.parseRadio(
            Json.parseToJsonElement(
                """
                {
                  "musicQueueRenderer": { "content": { "playlistPanelRenderer": {
                    "contents": [
                      { "playlistPanelVideoRenderer": {
                          "title": { "runs": [ { "text": "No id" } ] }
                      } },
                      { "playlistPanelVideoRenderer": {
                          "videoId": "noTitle"
                      } },
                      { "playlistPanelVideoRenderer": {
                          "videoId": "goodOne",
                          "title": { "runs": [ { "text": "Africa" } ] }
                      } }
                    ]
                  } } }
                }
                """.trimIndent()
            )
        )

        assertEquals(listOf("yt_goodOne"), page.songs.map { it.id })
    }
}
