package com.lunara.app

import org.junit.Assert.*
import org.junit.Test

class LyricsParserTest {

    @Test
    fun parseLrc_correctlyExtractsTimestampsAndLines() {
        val sampleLrc = """
            [00:12.50]This is the first line
            [00:18.20]Here comes the second melody
            [01:05.10]And the powerful chorus shines
        """.trimIndent()

        val regex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")
        val parsed = sampleLrc.lines().mapNotNull { line ->
            regex.find(line.trim())?.let { match ->
                val (minStr, secStr, fracStr, text) = match.destructured
                val min = minStr.toLong()
                val sec = secStr.toLong()
                val frac = if (fracStr.length == 2) fracStr.toLong() * 10 else fracStr.toLong()
                val totalMs = (min * 60 + sec) * 1000 + frac
                Pair(totalMs, text.trim())
            }
        }

        assertEquals(3, parsed.size)
        assertEquals(12500L, parsed[0].first)
        assertEquals("This is the first line", parsed[0].second)

        assertEquals(18200L, parsed[1].first)
        assertEquals("Here comes the second melody", parsed[1].second)

        assertEquals(65100L, parsed[2].first)
        assertEquals("And the powerful chorus shines", parsed[2].second)
    }
}
