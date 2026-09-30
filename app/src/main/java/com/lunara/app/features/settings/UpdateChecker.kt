package com.lunara.app.features.settings

import android.content.Context
import com.lunara.app.core.constants.AppConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/** What the updater found when it asked GitHub for the newest release. */
sealed interface UpdateCheckResult {
    /** Installed version is the newest one (or newer). */
    data object UpToDate : UpdateCheckResult

    /** A newer release exists. [downloadUrl] is the APK asset, or the release page if there is none. */
    data class Available(
        val version: String,
        val notes: String,
        val downloadUrl: String
    ) : UpdateCheckResult

    data class Failed(val reason: String) : UpdateCheckResult
}

/**
 * In-app updater.
 *
 * Lunara is distributed through GitHub releases, so the check is a single unauthenticated call to
 * the public releases API. Nothing is downloaded silently: when a newer build exists the screen
 * offers the APK link and the user keeps control.
 */
@Singleton
class UpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient
) {

    /** Installed version, e.g. `2.2.0`. Empty when the package manager cannot answer. */
    val currentVersion: String
        get() = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()

    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${AppConstants.GITHUB_REPOSITORY}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            // GitHub rejects requests without a user agent.
            .header("User-Agent", "Lunara/$currentVersion")
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    UpdateCheckResult.Failed("HTTP ${response.code}")
                } else {
                    parseRelease(response.body?.string().orEmpty())
                }
            }
        }.getOrElse { error ->
            UpdateCheckResult.Failed(error.message ?: error::class.java.simpleName)
        }
    }

    private fun parseRelease(body: String): UpdateCheckResult {
        val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return UpdateCheckResult.Failed("Unreadable release data")

        val tag = root["tag_name"]?.jsonPrimitive?.contentOrNull
            ?.removePrefix("v")
            ?.trim()
            .orEmpty()
        if (tag.isEmpty()) return UpdateCheckResult.Failed("Release has no version tag")

        if (!isNewer(tag, currentVersion)) return UpdateCheckResult.UpToDate

        val notes = root["body"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val pageUrl = root["html_url"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val apkUrl = root["assets"]?.jsonArray
            ?.mapNotNull { asset ->
                asset.jsonObject["browser_download_url"]?.jsonPrimitive?.contentOrNull
            }
            ?.firstOrNull { it.endsWith(".apk", ignoreCase = true) }

        return UpdateCheckResult.Available(
            version = tag,
            notes = notes,
            downloadUrl = apkUrl ?: pageUrl
        )
    }

    companion object {
        /** True when [candidate] is a higher dotted version than [installed]. */
        fun isNewer(candidate: String, installed: String): Boolean {
            val candidateParts = versionNumbers(candidate)
            if (candidateParts.isEmpty()) return false
            val installedParts = versionNumbers(installed)
            for (index in 0 until maxOf(candidateParts.size, installedParts.size)) {
                val left = candidateParts.getOrElse(index) { 0 }
                val right = installedParts.getOrElse(index) { 0 }
                if (left != right) return left > right
            }
            return false
        }

        private fun versionNumbers(version: String): List<Int> = version
            .split('.', '-', '+', ' ')
            .mapNotNull { part -> part.takeWhile { it.isDigit() }.toIntOrNull() }
    }
}
