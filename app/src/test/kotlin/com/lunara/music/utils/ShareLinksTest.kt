package com.lunara.app.utils

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
class ShareLinksTest {
    @Test
    fun `a song link round-trips to its video id`() {
        val target = ShareLinks.parse(Uri.parse(ShareLinks.song("JGwWNGJdvx8")))
        assertEquals(ShareLinks.Target.Song("JGwWNGJdvx8"), target)
    }

    @Test
    fun `an album link round-trips to its browse id`() {
        val target = ShareLinks.parse(Uri.parse(ShareLinks.album("MPREb_abc")))
        assertEquals(ShareLinks.Target.Album("MPREb_abc"), target)
    }

    @Test
    fun `a playlist link round-trips to its playlist id`() {
        val target = ShareLinks.parse(Uri.parse(ShareLinks.playlist("PL123")))
        assertEquals(ShareLinks.Target.Playlist("PL123"), target)
    }

    @Test
    fun `an artist link round-trips to its channel id`() {
        val target = ShareLinks.parse(Uri.parse(ShareLinks.artist("UCabcd")))
        assertEquals(ShareLinks.Target.Artist("UCabcd"), target)
    }

    @Test
    fun `the app scheme the site redirects to opens too`() {
        val target = ShareLinks.parse(Uri.parse("Lunara://song/JGwWNGJdvx8"))
        assertEquals(ShareLinks.Target.Song("JGwWNGJdvx8"), target)
    }

    @Test
    fun `every link is on the custom domain`() {
        assertTrue(ShareLinks.song("a").startsWith("https://lunara.ankitak.com.np/"))
        assertTrue(ShareLinks.album("b").startsWith("https://lunara.ankitak.com.np/"))
        assertTrue(ShareLinks.playlist("c").startsWith("https://lunara.ankitak.com.np/"))
        assertTrue(ShareLinks.artist("d").startsWith("https://lunara.ankitak.com.np/"))
    }

    @Test
    fun `links that are not ours are left alone`() {
        listOf(
            "https://music.youtube.com/watch?v=JGwWNGJdvx8",
            "https://youtu.be/JGwWNGJdvx8",
            "https://example.com/song/JGwWNGJdvx8",
            "https://lunara.ankitak.com.np/",
            "https://lunara.ankitak.com.np/about",
        ).forEach { assertNull(it, ShareLinks.parse(Uri.parse(it))) }
    }
}
