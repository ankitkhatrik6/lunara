/**
 * Lunara Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 */

package com.lunara.app.utils.cipher

import com.lunara.innertube.models.response.PlayerResponse
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Lunara's own stream extractor — the three things the player needs from the
 * site's player script, and nothing else.
 *
 * Every step is done by code in this repository. [PlayerJsFetcher] downloads the
 * script the site is currently serving, [FunctionNameExtractor] reads the
 * signature function, the `n` function and the signature timestamp out of it,
 * and [CipherWebView] runs those functions. None of it is borrowed: reading the
 * script, following its rewrites and running it are the whole job, and they were
 * already here.
 *
 * A bundled extractor used to sit behind these as a second implementation. It is
 * gone: a second opinion that ships from somewhere else is also a second thing
 * that can be abandoned, and the project does not take on that dependency for a
 * handful of fallbacks.
 */
object LunaraExtractor {

    private const val TAG = "LunaraExtractor"

    /**
     * The signature timestamp of the player generation this app deciphers with.
     *
     * It is deliberately the timestamp of the script the cipher itself will run,
     * not of some other copy: a signature minted against one player generation
     * and unscrambled by another is refused by the content server, which shows up
     * as a 403 on a step that reported no error at all.
     */
    suspend fun signatureTimestamp(): Result<Int> = try {
        CipherDeobfuscator
            .signatureTimestamp()
            ?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("Player signature timestamp unavailable"))
    } catch (e: CancellationException) {
        throw e // request superseded/cancelled — let the playback coroutine unwind
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Undo the throttle an address already carries.
     *
     * The content server serves a whole song to whoever solves the `n` challenge
     * and a 403 to everybody else. Comes back null when the address was handed
     * back unchanged, which is the signal that the script has been rewritten into
     * a shape this build cannot read yet — not that there was nothing to do.
     */
    suspend fun deobfuscateThrottling(url: String): String? {
        if ("n=" !in url && "/n/" !in url) return null
        val transformed =
            if ("/n/" in url) {
                CipherDeobfuscator.transformNParamInPath(url)
            } else {
                CipherDeobfuscator.transformNParamInUrl(url)
            }
        return transformed.takeIf { it != url }
    }

    /**
     * A playable address for one format of a video, or null when it cannot be
     * worked out.
     *
     * A format either carries its address directly or carries a signature cipher
     * that has to be unscrambled before it means anything. Both ways end at an
     * address that still has its `n` challenge to solve, which the caller does,
     * so it is left alone here.
     */
    suspend fun streamUrl(format: PlayerResponse.StreamingData.Format, videoId: String): String? {
        val direct = format.url
        if (!direct.isNullOrEmpty()) return direct

        val cipher = format.signatureCipher ?: format.cipher
        if (cipher.isNullOrEmpty()) return null

        return try {
            CipherDeobfuscator.deobfuscateStreamUrl(cipher, videoId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Could not unscramble the stream address: ${e.message}")
            null
        }
    }
}
