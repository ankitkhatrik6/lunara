package com.dhunya.app.features.share

/** The lyric section printed under the cover: the live line plus its neighbours. */
data class LyricQuote(
    /** Lines shown above the highlighted one (the warm-up). */
    val lead: List<String>,
    /** The line matching the share moment, or the first line for plain lyrics. */
    val current: String,
    /** Lines shown after it (the pay-off). */
    val tail: List<String>
) {
    companion object {
        fun forPosition(
            lyrics: com.dhunya.app.domain.model.Lyrics?,
            positionMs: Long
        ): LyricQuote? {
            if (lyrics == null) return null
            if (lyrics.isSynced && lyrics.syncedLyrics.isNotEmpty()) {
                val lines = lyrics.syncedLyrics
                val index = lines.indexOfLast { it.timestampMs <= positionMs }.coerceAtLeast(0)
                val at = { i: Int -> lines.getOrNull(i)?.text?.trim().orEmpty() }
                val current = at(index)
                if (current.isBlank()) return null
                return LyricQuote(
                    lead = listOf(at(index - 2), at(index - 1))
                        .filter { it.isNotBlank() }
                        .takeLast(1),
                    current = current,
                    tail = listOf(at(index + 1), at(index + 2))
                        .filter { it.isNotBlank() }
                        .take(2)
                )
            }
            val plain = lyrics.plainLyrics
                ?.lineSequence()
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() && !it.startsWith("Instrumental or no lyrics") }
                ?.toList()
                .orEmpty()
            if (plain.isEmpty()) return null
            return LyricQuote(
                lead = emptyList(),
                current = plain.first(),
                tail = plain.drop(1).take(3)
            )
        }
    }
}
