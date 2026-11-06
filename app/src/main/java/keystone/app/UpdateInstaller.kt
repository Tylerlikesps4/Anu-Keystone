// Copyright (C) 2026 Tylerlikesps4
// SPDX-License-Identifier: GPL-3.0-or-later
package keystone.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Keystone Shield, phase 1: signature-checked updates.
 * Downloads the release APK into private cache, then refuses it unless it is the same app
 * signed with the same certificate as the copy that is running now.
 */
object UpdateInstaller {
    /** SHA-256 of the Anu Keystone release signing certificate. */
    const val RELEASE_CERT_SHA256 = "9274391cc94531a49ecb805122c4e81acbb5d823af6aa0c506d627d9b297b9b3"
    private const val MAX_APK_BYTES = 150L * 1024 * 1024
    private val ALLOWED_HOSTS = setOf("github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")

    sealed interface Result {
        data class Ready(val file: File) : Result
        data class Rejected(val reason: String) : Result
    }

    suspend fun download(ctx: Context, url: String, onProgress: (Float) -> Unit = {}): Result = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val out = File(dir, "update.apk")
        try {
            var target = URL(url)
            var conn: HttpURLConnection
            var hops = 0
            while (true) {
                if (target.protocol != "https" || target.host !in ALLOWED_HOSTS) return@withContext Result.Rejected("Blocked download from ${target.host}")
                conn = (target.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false; connectTimeout = 15_000; readTimeout = 30_000
                    setRequestProperty("User-Agent", "AnuKeystone/${BuildConfig.VERSION_NAME}")
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: return@withContext Result.Rejected("Bad redirect")
                    conn.disconnect(); target = URL(target, loc)
                    if (++hops > 5) return@withContext Result.Rejected("Too many redirects")
                    continue
                }
                if (code != 200) return@withContext Result.Rejected("Download failed ($code)")
                break
            }
            val total = conn.contentLengthLong
            if (total > MAX_APK_BYTES) return@withContext Result.Rejected("File is too large")
            conn.inputStream.use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024); var done = 0L
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        done += n; if (done > MAX_APK_BYTES) return@withContext Result.Rejected("File is too large")
                        o.write(buf, 0, n); if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
            conn.disconnect()
            verify(ctx, out)?.let { out.delete(); return@withContext Result.Rejected(it) }
            Result.Ready(out)
        } catch (e: java.io.IOException) {
            out.delete(); Result.Rejected("No connection. Check your internet and try again")
        }
    }

    /** Returns null when the APK is a genuine newer Anu Keystone build, otherwise the reason it was refused. */
    fun verify(ctx: Context, apk: File): String? {
        val pm = ctx.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val info = pm.getPackageArchiveInfo(apk.path, flags) ?: return "Not a valid Android app"
        if (info.packageName != ctx.packageName) return "Wrong app (${info.packageName})"
        val theirs = certs(info)
        if (theirs.isEmpty()) return "The update isn't signed"
        val ours = certs(pm.getPackageInfo(ctx.packageName, flags))
        if (theirs != setOf(RELEASE_CERT_SHA256) || (ours.isNotEmpty() && theirs != ours)) return "Signature doesn't match. This file was not made by Anu Keystone"
        val newCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        if (newCode <= BuildConfig.VERSION_CODE) return "That version isn't newer than this one"
        return null
    }

    fun install(ctx: Context, apk: File) {
        val uri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", apk)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun certs(info: PackageInfo): Set<String> {
        val sigs: Array<Signature> = if (Build.VERSION.SDK_INT >= 28) {
            val si = info.signingInfo ?: return emptySet()
            if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
        } else @Suppress("DEPRECATION") (info.signatures ?: return emptySet())
        val md = MessageDigest.getInstance("SHA-256")
        return sigs.map { s -> md.digest(s.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    }
}
