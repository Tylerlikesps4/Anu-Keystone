// Copyright (C) 2026 Tylerlikesps4
// SPDX-License-Identifier: GPL-3.0-or-later
package keystone.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks GitHub Releases for a newer version. Runs only when the user taps "Check for updates":
 * one HTTPS GET to api.github.com, no account, no identifiers sent, nothing downloaded automatically.
 */
object UpdateChecker {
    const val REPO = "Tylerlikesps4/Anu-Keystone"
    const val RELEASES_PAGE = "https://github.com/$REPO/releases"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"

    data class Release(val version: String, val title: String, val notes: String, val pageUrl: String, val apkUrl: String?)

    sealed interface Result {
        data class UpToDate(val latest: String) : Result
        data class Available(val release: Release) : Result
        data class Failed(val reason: String) : Result
    }

    suspend fun check(current: String = BuildConfig.VERSION_NAME): Result = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000; readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "AnuKeystone/$current")
            }
            try {
                when (val code = conn.responseCode) {
                    200 -> {}
                    404 -> return@withContext Result.Failed("No releases published yet")
                    403, 429 -> return@withContext Result.Failed("GitHub is busy. Try again in a few minutes")
                    else -> return@withContext Result.Failed("GitHub answered $code")
                }
                val body = conn.inputStream.use { it.readBytes().decodeToString() }
                val rel = parse(body) ?: return@withContext Result.Failed("Couldn't read the release info")
                if (isNewer(rel.version, current)) Result.Available(rel) else Result.UpToDate(rel.version)
            } finally { conn.disconnect() }
        } catch (e: java.io.IOException) {
            Result.Failed("No connection. Check your internet and try again")
        } catch (e: Exception) {
            Result.Failed(e.message ?: "Something went wrong")
        }
    }

    fun parse(json: String): Release? = runCatching {
        val o = Json.parseToJsonElement(json).jsonObject
        val tag = o["tag_name"]!!.jsonPrimitive.content
        val apk = o["assets"]?.jsonArray?.map { it.jsonObject }
            ?.firstOrNull { it["name"]?.jsonPrimitive?.content?.endsWith(".apk", true) == true }
            ?.get("browser_download_url")?.jsonPrimitive?.content
        val page = o["html_url"]?.jsonPrimitive?.content ?: RELEASES_PAGE
        // Only ever hand the user links that point at this project's GitHub releases.
        fun safe(u: String?) = u?.takeIf { it.startsWith("https://github.com/$REPO/") }
        Release(
            version = tag.trimStart('v', 'V'),
            title = o["name"]?.jsonPrimitive?.content?.ifBlank { null } ?: tag,
            notes = o["body"]?.jsonPrimitive?.content.orEmpty().trim(),
            pageUrl = safe(page) ?: RELEASES_PAGE,
            apkUrl = safe(apk),
        )
    }.getOrNull()

    /** Compares dotted versions numerically: 0.02 > 0.01, 1.0 > 0.99, 0.1.1 > 0.1. */
    fun isNewer(latest: String, current: String): Boolean {
        fun parts(v: String) = v.trimStart('v', 'V').split('.', '-', '+').map { p -> p.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(latest); val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
