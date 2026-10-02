package com.lunara.app

import com.lunara.app.data.remote.innertube.InnerTubeParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Browse shelves, read out of **real** YouTube Music responses.
 *
 * `fixtures/browse_shelves.json` is an unedited slice of what
 * `music.youtube.com/youtubei/v1/browse` answers for the explore page (`FEmusic_explore`) and the
 * charts page (`FEmusic_charts`), trimmed to two rows per shelf. It is here so the parser is
 * tested against YouTube's own field names: a carousel keeps its heading in
 * `header.musicCarouselShelfBasicHeaderRenderer.title` while a horizontal shelf keeps it in
 * `title`, a playlist tile and an artist tile are both `musicTwoRowItemRenderer` but only the
 * artist one has a `browseEndpoint`, and the moods chooser is not a shelf row at all but a
 * `musicNavigationButtonRenderer` whose `params` is what picks the mood.
 */
class InnerTubeBrowseTest {

    private fun fixture(name: String): JsonObject {
        val text = requireNotNull(javaClass.getResourceAsStream("/fixtures/$name")) {
            "missing test fixture $name"
        }.bufferedReader().use { it.readText() }
        return Json.parseToJsonElement(text) as JsonObject
    }

    @Test
    fun parseBrowseShelves_readsEveryShelfInTheOrderYoutubeDrawsThem() {
        val shelves = InnerTubeParser.parseBrowseShelves(fixture("browse_shelves.json"))

        assertEquals(
            listOf(
                "New albums & singles",
                "Moods & genres",
                "New music videos",
                "Video charts",
                "Top artists"
            ),
            shelves.map { it.title }
        )
    }

    @Test
    fun parseBrowseShelves_dropsAHeadingWithNoRowsUnderIt() {
        // The charts page carries a country-filter shelf that is empty when logged out: a heading
        // over nothing would draw an empty rail in the feed.
        val shelves = InnerTubeParser.parseBrowseShelves(fixture("browse_shelves.json"))

        assertTrue(shelves.none { it.isEmpty })
        assertEquals(5, shelves.size)
    }

    @Test
    fun parseBrowseShelves_readsMusicVideoTilesAsPlayableSongs() {
        val shelf = InnerTubeParser.parseBrowseShelves(fixture("browse_shelves.json"))
            .first { it.title == "New music videos" }

        assertEquals(listOf("yt_4AeQ-0AbLIo", "yt_wjWud5YXNT4"), shelf.songs.map { it.id })
        // A music video's own title, artist included - YouTube lists them exactly like this.
        assertEquals("Bajo un Cielo Nuevo | FireVolk", shelf.songs[0].title)
        assertEquals("FireVolk", shelf.songs[0].artistName)
        // "FireVolk • 18K views" - the view count is not an album.
        assertNull(shelf.songs[0].albumName)
        assertTrue(shelf.songs[0].artworkUrl?.startsWith("https://") == true)
        assertTrue(shelf.cards.isEmpty())
    }

    @Test
    fun parseBrowseShelves_readsAlbumTilesAsCardsThatLeadSomewhere() {
        val shelf = InnerTubeParser.parseBrowseShelves(fixture("browse_shelves.json"))
            .first { it.title == "New albums & singles" }

        assertEquals(listOf("Maya Le", "Bahun Baje Kina Kaseko"), shelf.cards.map { it.title })
        assertEquals("MPREb_44T9rUkT28h", shelf.cards[0].browseId)
        assertEquals("Single • John Rai", shelf.cards[0].subtitle)
        assertTrue(shelf.songs.isEmpty())
    }

    @Test
    fun parseBrowseShelves_keepsThePlaylistIdOfAChartTile() {
        val shelf = InnerTubeParser.parseBrowseShelves(fixture("browse_shelves.json"))
            .first { it.title == "Video charts" }

        // A `VL` browse id is the playlist itself plus YouTube's prefix, and it is what the
        // existing album/playlist listing already knows how to open.
        assertEquals("VLPL4fGSI1pDJn6t3TXLGiiJdD-sZbrG3tG0", shelf.cards[0].browseId)
    }

    @Test
    fun parseBrowseShelves_readsArtistRowsAsCards() {
        val shelf = InnerTubeParser.parseBrowseShelves(fixture("browse_shelves.json"))
            .first { it.title == "Top artists" }

        // An artist shelf is a `musicShelfRenderer` of `musicResponsiveListItemRenderer` rows with
        // no video id at all - a song row without a video id would have been a playable song.
        assertEquals(listOf("Alka Yagnik", "Udit Narayan"), shelf.cards.map { it.title })
        assertEquals("UCptBkLZ6XRxoyn8SkUMc_Iw", shelf.cards[0].browseId)
        assertEquals("2.27M subscribers", shelf.cards[0].subtitle)
        assertTrue(shelf.songs.isEmpty())
    }

    @Test
    fun parseBrowseShelves_readsTheMoodChooserWithTheParamsThatPickEachMood() {
        val shelf = InnerTubeParser.parseBrowseShelves(fixture("browse_shelves.json"))
            .first { it.title == "Moods & genres" }

        assertEquals(listOf("Chill", "Commute"), shelf.categories.map { it.title })
        assertEquals(
            listOf("FEmusic_moods_and_genres_category", "FEmusic_moods_and_genres_category"),
            shelf.categories.map { it.browseId }
        )
        // Every mood lives at the same browse id; only the params tell them apart.
        assertEquals("ggMPOg1uX1JOQWZFeDByc2Jm", shelf.categories[0].params)
        assertEquals("ggMPOg1uX044Z2o5WERLckpU", shelf.categories[1].params)
    }

    @Test
    fun parseBrowseShelves_returnsNothingForAResponseWithoutShelves() {
        val shelves = InnerTubeParser.parseBrowseShelves(
            Json.parseToJsonElement("""{ "contents": { "somethingElse": { "rows": [] } } }""")
        )

        assertTrue(shelves.isEmpty())
    }
}
