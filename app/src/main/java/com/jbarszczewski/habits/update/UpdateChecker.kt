package com.jbarszczewski.habits.update

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** The latest release found on GitHub, as reported by the GitHub REST API. */
data class UpdateInfo(val versionName: String, val releaseUrl: String)

/**
 * Looks up the newest GitHub release for this app. This is the app's one deliberate exception to
 * being offline-only: a small, explicit call made at app start to compare the installed version
 * against what's published on GitHub. Any failure (no connectivity, rate limiting, unexpected
 * response) is left for the caller to catch and treated the same as "no update found" -- this
 * must never crash or block the app when the device has no network.
 */
class UpdateChecker(
    private val repoOwner: String = "jbarszczewski",
    private val repoName: String = "habits",
) {
    /** Runs blocking I/O; call from [kotlinx.coroutines.Dispatchers.IO]. */
    fun latestRelease(): UpdateInfo {
        val url = URL("https://api.github.com/repos/$repoOwner/$repoName/releases/latest")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "GitHub releases request failed with HTTP ${connection.responseCode}"
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            return UpdateInfo(
                versionName = json.getString("tag_name").removePrefix("v"),
                releaseUrl = json.getString("html_url"),
            )
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000
    }
}

/**
 * Compares dotted numeric versions (e.g. "1.2.0"). Missing or non-numeric segments count as 0,
 * so "1.2" and "1.2.0" are equal.
 */
fun isNewerVersion(current: String, candidate: String): Boolean {
    val currentParts = current.split(".")
    val candidateParts = candidate.split(".")
    for (i in 0 until maxOf(currentParts.size, candidateParts.size)) {
        val currentPart = currentParts.getOrNull(i)?.toIntOrNull() ?: 0
        val candidatePart = candidateParts.getOrNull(i)?.toIntOrNull() ?: 0
        if (candidatePart != currentPart) return candidatePart > currentPart
    }
    return false
}
