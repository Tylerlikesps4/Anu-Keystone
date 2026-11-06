// Copyright (C) 2026 Tylerlikesps4
// SPDX-License-Identifier: GPL-3.0-or-later
package keystone.app.ui

import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import keystone.app.Shield
import kotlinx.coroutines.delay

fun biometricAvailable(act: FragmentActivity) =
    BiometricManager.from(act).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

fun promptBiometric(act: FragmentActivity, title: String = "Unlock Anu Keystone", onOk: () -> Unit) {
    val prompt = BiometricPrompt(act, ContextCompat.getMainExecutor(act), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()
    })
    prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(title).setNegativeButtonText("Use PIN")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).build())
}

/** Full-screen PIN pad shown while [Shield.locked] is true. */
@Composable
fun LockScreen(onWipe: () -> Unit) {
    val act = LocalContext.current as FragmentActivity
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val until by Shield.lockoutUntil
    val fails by Shield.failedAttempts
    val bio = remember { Shield.biometric && biometricAvailable(act) }
    LaunchedEffect(Unit) { if (bio) promptBiometric(act) { Shield.unlocked() } }
    LaunchedEffect(until) { while (SystemClock.elapsedRealtime() < until) { now = SystemClock.elapsedRealtime(); delay(500) }; now = SystemClock.elapsedRealtime() }
    val waitSec = ((until - now) / 1000).coerceAtLeast(0)

    fun submit() {
        when (Shield.tryUnlock(pin)) {
            Shield.Attempt.OK -> message = null
            Shield.Attempt.WIPE -> onWipe()
            Shield.Attempt.LOCKED_OUT -> message = "Too many tries. Wait a moment"
            Shield.Attempt.WRONG -> {
                val left = Shield.MAX_ATTEMPTS - Shield.failedAttempts.intValue
                message = if (Shield.wipeAfterFailures && left in 1..5) "Wrong PIN. $left tries before everything is erased" else "Wrong PIN"
            }
        }
        pin = ""
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Outlined.Lock, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text("Anu Keystone is locked", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(maxOf(4, pin.length)) { i ->
                    Box(Modifier.size(14.dp).clip(CircleShape).background(if (i < pin.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(when { waitSec > 0 -> "Try again in ${waitSec}s"; else -> message ?: " " }, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "bio", "0", "del")
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    row.forEach { k ->
                        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                            when (k) {
                                "bio" -> if (bio) IconButton(onClick = { promptBiometric(act) { Shield.unlocked() } }) { Icon(Icons.Outlined.Fingerprint, "Use fingerprint") }
                                "del" -> IconButton(onClick = { pin = pin.dropLast(1) }) { Icon(Icons.AutoMirrored.Outlined.Backspace, "Delete") }
                                else -> FilledTonalButton(onClick = {
                                    if (waitSec == 0L && pin.length < 12) { pin += k; if (pin.length == Shield.pinLength) submit() }
                                }, modifier = Modifier.size(72.dp), shape = CircleShape, contentPadding = PaddingValues(0.dp)) { Text(k, fontSize = 24.sp) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            TextButton(enabled = pin.length >= 4 && waitSec == 0L, onClick = { submit() }) { Text("Unlock") }
            if (fails > 0) Text("$fails wrong ${if (fails == 1) "try" else "tries"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
