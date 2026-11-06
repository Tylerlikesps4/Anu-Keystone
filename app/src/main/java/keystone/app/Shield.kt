// Copyright (C) 2026 Tylerlikesps4
// SPDX-License-Identifier: GPL-3.0-or-later
package keystone.app

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Keystone Shield, phase 1: app lock.
 * State lives in private SharedPreferences (never in the database, never in backups).
 * The PIN is stored only as a salted PBKDF2-HMAC-SHA256 hash.
 */
object Shield {
    private const val ITERATIONS = 120_000
    const val MAX_ATTEMPTS = 10
    private lateinit var prefs: SharedPreferences

    val locked = mutableStateOf(false)
    val failedAttempts = mutableIntStateOf(0)
    /** Elapsed-realtime ms until which PIN entry is blocked after repeated failures. */
    val lockoutUntil = mutableStateOf(0L)
    private var backgroundedAt = 0L

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("shield", Context.MODE_PRIVATE)
        failedAttempts.intValue = prefs.getInt("failed", 0)
        locked.value = enabled
    }

    val enabled get() = prefs.getString("pin_hash", null) != null
    val pinLength get() = prefs.getInt("pin_len", 0)
    var biometric: Boolean
        get() = prefs.getBoolean("biometric", false)
        set(v) { prefs.edit().putBoolean("biometric", v).apply() }
    /** Minutes in the background before the app locks again. 0 = immediately. */
    var timeoutMinutes: Int
        get() = prefs.getInt("timeout", 1)
        set(v) { prefs.edit().putInt("timeout", v).apply() }
    var hideInRecents: Boolean
        get() = prefs.getBoolean("hide_recents", false)
        set(v) { prefs.edit().putBoolean("hide_recents", v).apply() }
    var wipeAfterFailures: Boolean
        get() = prefs.getBoolean("wipe", false)
        set(v) { prefs.edit().putBoolean("wipe", v).apply() }

    fun setPin(pin: String) {
        require(pin.length in 4..12 && pin.all(Char::isDigit)) { "PIN must be 4 to 12 digits" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString("pin_salt", salt.hex()).putString("pin_hash", hash(pin, salt).hex()).putInt("pin_len", pin.length).putInt("failed", 0).apply()
        failedAttempts.intValue = 0
    }

    fun disable() {
        prefs.edit().remove("pin_hash").remove("pin_salt").putBoolean("biometric", false).putInt("failed", 0).apply()
        failedAttempts.intValue = 0; locked.value = false
    }

    fun check(pin: String): Boolean {
        val salt = prefs.getString("pin_salt", null)?.unhex() ?: return false
        val stored = prefs.getString("pin_hash", null)?.unhex() ?: return false
        return MessageDigest.isEqual(stored, hash(pin, salt))
    }

    enum class Attempt { OK, WRONG, LOCKED_OUT, WIPE }

    fun tryUnlock(pin: String): Attempt {
        if (SystemClock.elapsedRealtime() < lockoutUntil.value) return Attempt.LOCKED_OUT
        if (check(pin)) { unlocked(); return Attempt.OK }
        val n = failedAttempts.intValue + 1
        failedAttempts.intValue = n
        prefs.edit().putInt("failed", n).apply()
        if (wipeAfterFailures && n >= MAX_ATTEMPTS) return Attempt.WIPE
        // Back-off: 30 s after 5 wrong PINs, doubling each time after that (max 15 min).
        if (n >= 5) lockoutUntil.value = SystemClock.elapsedRealtime() + minOf(30_000L shl (n - 5), 900_000L)
        return Attempt.WRONG
    }

    fun unlocked() {
        failedAttempts.intValue = 0; lockoutUntil.value = 0
        prefs.edit().putInt("failed", 0).apply()
        locked.value = false
    }

    fun onBackground() { backgroundedAt = SystemClock.elapsedRealtime() }
    fun onForeground() {
        if (!enabled || locked.value || backgroundedAt == 0L) return
        if (SystemClock.elapsedRealtime() - backgroundedAt >= timeoutMinutes * 60_000L) locked.value = true
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)).encoded
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
    private fun String.unhex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
