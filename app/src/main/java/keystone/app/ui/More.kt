// Copyright (C) 2026 Tylerlikesps4
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
package keystone.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import keystone.app.BuildConfig
import keystone.app.Ks
import keystone.app.Reminders
import keystone.app.UpdateChecker
import keystone.app.UpdateInstaller
import keystone.app.shield.ShieldCore
import keystone.app.shield.ShieldFirewall
import keystone.app.shield.ShieldVpnService
import keystone.app.Shield
import keystone.app.MainActivity
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import android.net.Uri
import keystone.engine.*
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate

@Composable
fun SettingsScreen() {
    tick()
    val nav = LocalNav.current
    val eng = Ks.engine
    val ctx = LocalContext.current
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var foundUpdate by remember { mutableStateOf<UpdateChecker.Release?>(null) }
    var testBuilds by remember { mutableStateOf(eng.setting("updates_test_builds") == "on") }
    var downloading by remember { mutableStateOf<Float?>(null) }
    var lockOn by remember { mutableStateOf(Shield.enabled) }
    var bioOn by remember { mutableStateOf(Shield.biometric) }
    var hideRecents by remember { mutableStateOf(Shield.hideInRecents) }
    var wipeOn by remember { mutableStateOf(Shield.wipeAfterFailures) }
    var timeout by remember { mutableIntStateOf(Shield.timeoutMinutes) }
    var pinDialog by remember { mutableStateOf(false) }
    var confirmWipeToggle by remember { mutableStateOf(false) }
    val act = ctx as? FragmentActivity
    val fwRunning by ShieldFirewall.running
    var fwTrackers by remember { mutableStateOf(ShieldFirewall.blockTrackers) }
    var appsDialog by remember { mutableStateOf(false) }
    var domainDialog by remember { mutableStateOf(false) }
    var fwBlockedApps by remember { mutableStateOf(ShieldFirewall.blockedApps) }
    var fwDomains by remember { mutableStateOf(ShieldFirewall.customDomains) }
    val vpnConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == android.app.Activity.RESULT_OK) ShieldVpnService.start(ctx)
        else Toast.makeText(ctx, "The firewall needs VPN permission to run", Toast.LENGTH_LONG).show()
    }
    fun restartFirewall() { if (ShieldFirewall.running.value) { ShieldVpnService.stop(ctx); scope.launch { kotlinx.coroutines.delay(400); ShieldVpnService.start(ctx) } } }
    fun openUrl(url: String) = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        .onFailure { Toast.makeText(ctx, "No browser found", Toast.LENGTH_SHORT).show() }
    fun downloadAndInstall(rel: UpdateChecker.Release) {
        val url = rel.apkUrl ?: run { openUrl(rel.pageUrl); return }
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(ctx, "Allow Anu Keystone to install updates, then tap Download again", Toast.LENGTH_LONG).show()
            runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))) }
            return
        }
        downloading = 0f; updateStatus = "Downloading v${rel.version}…"
        scope.launch {
            when (val r = UpdateInstaller.download(ctx, url) { p -> downloading = p }) {
                is UpdateInstaller.Result.Ready -> { updateStatus = "Signature verified. Opening installer"; UpdateInstaller.install(ctx, r.file) }
                is UpdateInstaller.Result.Rejected -> updateStatus = "Update refused: ${r.reason}"
            }
            downloading = null
        }
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            ctx.contentResolver.openOutputStream(uri)!!.use { it.write(eng.exportJson().toByteArray()) }
        }.onSuccess { Toast.makeText(ctx, "Backup saved", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(ctx, "Export failed: ${it.message}", Toast.LENGTH_LONG).show() }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }
            .onSuccess { pendingImport = it }
            .onFailure { Toast.makeText(ctx, "Couldn't read file", Toast.LENGTH_LONG).show() }
    }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Screen("Settings") { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SectionCard("Profile", Icons.Outlined.Person) {
                    ListItem(headlineContent = { Text(eng.setting("profile_name")?.ifBlank { null } ?: "Add your name") }, supportingContent = { Text("Used in greetings and your CV") },
                        modifier = Modifier.clickable { editName = true })
                    ListItem(headlineContent = { Text("Life Setup") }, supportingContent = { Text("Wheel of life, values, vision, goals") }, modifier = Modifier.clickable { nav.navigate("setup") })
                }
            }
            item {
                SectionCard("Appearance", Icons.Outlined.Palette) {
                    val mode = eng.setting("theme") ?: "system"
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (k, l) ->
                            FilterChip(selected = mode == k, onClick = { eng.setSetting("theme", k) }, label = { Text(l) })
                        }
                    }
                    if (Build.VERSION.SDK_INT >= 31) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Use wallpaper colors (Material You)", Modifier.weight(1f))
                        Switch(eng.setting("dynamic_color") == "on", { eng.setSetting("dynamic_color", if (it) "on" else "off") })
                    }
                }
            }
            item {
                SectionCard("Reminders", Icons.Outlined.Notifications) {
                    val on = eng.setting("reminders") != "off"
                    val hour = eng.setting("reminder_hour")?.toIntOrNull() ?: 9
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Daily digest (tasks, birthdays, warranties, car service, renewals, habits)", Modifier.weight(1f))
                        Switch(on, {
                            eng.setSetting("reminders", if (it) "on" else "off")
                            if (it) { Reminders.schedule(ctx, hour); if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS) } else Reminders.cancel(ctx)
                        })
                    }
                    if (on) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Time", Modifier.weight(1f))
                        listOf(7, 9, 12, 18, 21).forEach { h ->
                            FilterChip(selected = hour == h, onClick = { eng.setSetting("reminder_hour", "$h"); Reminders.schedule(ctx, h) }, label = { Text("%02d:00".format(h)) }, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                    TextButton(onClick = {
                        if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
                        Reminders.post(ctx)
                        if (Reminders.digest() == null) Toast.makeText(ctx, "Nothing due today", Toast.LENGTH_SHORT).show()
                    }) { Text("Send today's digest now") }
                }
            }
            item {
                SectionCard("Backup", Icons.Outlined.Backup) {
                    Text("All data lives only on this device. Export a JSON backup regularly.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { exporter.launch("keystone-backup-${LocalDate.now()}.json") }) { Text("Export") }
                        OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Import") }
                    }
                }
            }
            item {
                SectionCard("Data", Icons.Outlined.Storage) {
                    Text("${eng.collections().size} collections · ${eng.allEntries(true).size} items", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = {
                        val n = eng.installPresets(seed = false)
                        Toast.makeText(ctx, if (n == 0) "All presets present" else "Restored $n collections", Toast.LENGTH_SHORT).show()
                    }) { Text("Restore missing preset collections") }
                    TextButton(onClick = { nav.navigate("archive") }) { Text("Open archive") }
                    TextButton(onClick = { confirmReset = true }) { Text("Erase everything", color = MaterialTheme.colorScheme.error) }
                }
            }
            item {
                SectionCard("Security", Icons.Outlined.Shield) {
                    ListItem(headlineContent = { Text("App lock") },
                        supportingContent = { Text(if (lockOn) "PIN required to open the app" else "Protect the app with a PIN") },
                        trailingContent = { Switch(lockOn, { if (it) pinDialog = true else { Shield.disable(); lockOn = false; bioOn = false } }) })
                    if (lockOn) {
                        TextButton(onClick = { pinDialog = true }) { Text("Change PIN") }
                        if (act != null && biometricAvailable(act)) ListItem(headlineContent = { Text("Unlock with fingerprint or face") },
                            trailingContent = { Switch(bioOn, { v -> if (v) promptBiometric(act, "Confirm it's you") { Shield.biometric = true; bioOn = true } else { Shield.biometric = false; bioOn = false } }) })
                        Text("Lock after leaving the app", style = MaterialTheme.typography.bodyMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "Immediately", 1 to "1 min", 5 to "5 min", 15 to "15 min").forEach { (m, label) ->
                                FilterChip(selected = timeout == m, onClick = { Shield.timeoutMinutes = m; timeout = m }, label = { Text(label) })
                            }
                        }
                        ListItem(headlineContent = { Text("Erase data after ${Shield.MAX_ATTEMPTS} wrong PINs") },
                            supportingContent = { Text("For lost or stolen phones. Keep a backup") },
                            trailingContent = { Switch(wipeOn, { v -> if (v) confirmWipeToggle = true else { Shield.wipeAfterFailures = false; wipeOn = false } }) })
                    }
                    ListItem(headlineContent = { Text("Hide in recent apps") },
                        supportingContent = { Text("Blanks the app preview and blocks screenshots") },
                        trailingContent = { Switch(hideRecents, { Shield.hideInRecents = it; hideRecents = it; (ctx as? MainActivity)?.applySecureFlag() }) })
                    Text("Backups exclude your PIN and lock settings. Android cloud backup is off, so use Export to keep a copy.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                SectionCard("Firewall (test)", Icons.Outlined.Security) {
                    Text("Blocks trackers and apps you choose by filtering their internet lookups on your phone. Runs as a local VPN; nothing is sent to us.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ListItem(headlineContent = { Text("Firewall") },
                        supportingContent = { Text(when { !ShieldCore.available -> "Not included in this build"; fwRunning -> "On · ${ShieldFirewall.blockedCount.intValue} blocked, ${ShieldFirewall.allowedCount.intValue} allowed"; else -> "Off" }) },
                        trailingContent = { Switch(fwRunning, enabled = ShieldCore.available, onCheckedChange = { on ->
                            if (on) { val i = android.net.VpnService.prepare(ctx); if (i != null) vpnConsent.launch(i) else ShieldVpnService.start(ctx) }
                            else ShieldVpnService.stop(ctx)
                        }) })
                    ShieldFirewall.lastError.value?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    ListItem(headlineContent = { Text("Block ads and trackers") },
                        trailingContent = { Switch(fwTrackers, { fwTrackers = it; ShieldFirewall.blockTrackers = it; restartFirewall() }) })
                    TextButton(onClick = { appsDialog = true }) { Text("Blocked apps (${fwBlockedApps.size})") }
                    TextButton(onClick = { domainDialog = true }) { Text("Blocked websites (${fwDomains.size})") }
                    TextButton(onClick = { runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_VPN_SETTINGS)) } }) { Text("Always-on VPN settings") }
                    if (ShieldFirewall.recentBlocks.isNotEmpty()) {
                        Text("Recently blocked", style = MaterialTheme.typography.labelLarge)
                        ShieldFirewall.recentBlocks.take(8).forEach { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                    }
                    Text("Test build: blocks lookups, not every connection. Apps with their own secure DNS, or Private DNS set to a hostname, can get around it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                SectionCard("Updates", Icons.Outlined.SystemUpdate) {
                    Text("You're on v${BuildConfig.VERSION_NAME}. Checking contacts GitHub once and sends nothing about you.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(enabled = !checking, onClick = {
                            checking = true; updateStatus = null
                            scope.launch {
                                when (val r = UpdateChecker.check(includeTestBuilds = testBuilds)) {
                                    is UpdateChecker.Result.Available -> { foundUpdate = r.release; updateStatus = "v${r.release.version} is available" }
                                    is UpdateChecker.Result.UpToDate -> updateStatus = "You're up to date"
                                    is UpdateChecker.Result.Failed -> updateStatus = r.reason
                                }
                                checking = false
                            }
                        }) { Text(if (checking) "Checking…" else "Check for updates") }
                        if (checking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                    updateStatus?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    downloading?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                    ListItem(headlineContent = { Text("Include test builds") },
                        supportingContent = { Text("Also offer pre-releases. They may be unstable") },
                        trailingContent = { Switch(testBuilds, { testBuilds = it; eng.setSetting("updates_test_builds", if (it) "on" else null) }) })
                    Text("Downloads are only installed if they're signed with the Anu Keystone key.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { openUrl(UpdateChecker.RELEASES_PAGE) }) { Text("All releases") }
                }
            }
            item {
                SectionCard("About", Icons.Outlined.Info) {
                    Text("Anu Keystone v${BuildConfig.VERSION_NAME} · free & open source (GPL-3.0-or-later)", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { nav.navigate("about") }) { Text("Licenses & credits") }
                    TextButton(onClick = { nav.navigate("guide") }) { Text("Setup guide") }
                }
            }
        }
    }
    foundUpdate?.let { rel ->
        AlertDialog(onDismissRequest = { foundUpdate = null },
            title = { Text("Update available: v${rel.version}" + if (rel.prerelease) " (test build)" else "") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text(rel.title, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(rel.notes.ifBlank { "No release notes." }, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Install the new APK over this one. Your data stays. Export a backup first to be safe.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { downloadAndInstall(rel); foundUpdate = null }) { Text(if (rel.apkUrl != null) "Download & install" else "Open release") } },
            dismissButton = { TextButton(onClick = { foundUpdate = null }) { Text("Later") } })
    }
    pendingImport?.let { json ->
        AlertDialog(onDismissRequest = { pendingImport = null }, title = { Text("Import backup") },
            text = { Text("Replace wipes current data and restores the backup. Merge keeps current data and adds or updates items from the backup (newer edits win).") },
            confirmButton = {
                Row {
                    TextButton(onClick = { doImport(ctx, json, false); pendingImport = null }) { Text("Merge") }
                    TextButton(onClick = { doImport(ctx, json, true); pendingImport = null }) { Text("Replace") }
                }
            }, dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } })
    }
    if (confirmReset) ConfirmDialog("Erase everything?", "All collections, items and settings will be deleted from this device. Export a backup first if unsure.", "Erase",
        onDismiss = { confirmReset = false }) {
        Ks.eraseAll()
        nav.navigate("onboarding") { popUpTo(nav.graph.id) { inclusive = true } }
    }
    if (appsDialog) BlockedAppsDialog(fwBlockedApps, onDismiss = { appsDialog = false }) { sel ->
        ShieldFirewall.blockedApps = sel; fwBlockedApps = sel; appsDialog = false; restartFirewall() }
    if (domainDialog) TextInputDialog("Block a website", label = "Domain, e.g. example.com", onDismiss = { domainDialog = false }) { d ->
        val host = d.trim().lowercase().removePrefix("https://").removePrefix("http://").substringBefore('/')
        if (Regex("^[a-z0-9.-]+\\.[a-z]{2,}$").matches(host)) { fwDomains = (fwDomains + host).distinct(); ShieldFirewall.customDomains = fwDomains; restartFirewall() }
        else Toast.makeText(ctx, "That doesn't look like a website address", Toast.LENGTH_SHORT).show()
        domainDialog = false }
    if (pinDialog) PinSetupDialog(onDismiss = { pinDialog = false }) { pin -> Shield.setPin(pin); lockOn = true; pinDialog = false
        Toast.makeText(ctx, "App lock is on", Toast.LENGTH_SHORT).show() }
    if (confirmWipeToggle) ConfirmDialog("Erase after ${Shield.MAX_ATTEMPTS} wrong PINs?", "After ${Shield.MAX_ATTEMPTS} wrong PINs in a row, every collection, item and setting on this device is deleted. This can't be undone, so export a backup regularly.", "Turn on",
        onDismiss = { confirmWipeToggle = false }) { Shield.wipeAfterFailures = true; wipeOn = true }
    if (editName) TextInputDialog("Your name", eng.setting("profile_name") ?: "", onDismiss = { editName = false }) { eng.setSetting("profile_name", it) }
}

private fun doImport(ctx: android.content.Context, json: String, replace: Boolean) {
    runCatching { Ks.engine.importJson(json, replace) }
        .onSuccess { Toast.makeText(ctx, "Imported ${it.entries} items, ${it.collections} collections", Toast.LENGTH_LONG).show() }
        .onFailure { Toast.makeText(ctx, it.message ?: "Import failed", Toast.LENGTH_LONG).show() }
}

@Composable
fun AboutScreen() {
    val ctx = LocalContext.current
    val text = remember { runCatching { ctx.assets.open("CREDITS.md").bufferedReader().readText() }.getOrDefault("See CREDITS.md in the source repository.") }
    Screen("Licenses & credits") { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Anu Keystone is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. Copyright (C) 2026 Tylerlikesps4.",
                style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun CvScreen() {
    tick()
    val nav = LocalNav.current
    val eng = Ks.engine
    val ctx = LocalContext.current
    Screen("CV builder", actions = {
        IconButton(onClick = {
            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, Insights.cvText(eng)).putExtra(Intent.EXTRA_SUBJECT, "CV"), "Share CV"))
        }) { Icon(Icons.Outlined.Share, "Share CV") }
    }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SectionCard("Header", Icons.Outlined.Person) {
                    listOf("profile_name" to "Full name", "cv_headline" to "Headline (e.g. Android developer)", "cv_contact" to "Contact line (email · phone · city · links)").forEach { (k, l) ->
                        var t by remember(k) { mutableStateOf(eng.setting(k) ?: "") }
                        OutlinedTextField(t, { t = it; eng.setSetting(k, it) }, Modifier.fillMaxWidth(), label = { Text(l) }, singleLine = true)
                    }
                    var s by remember { mutableStateOf(eng.setting("cv_summary") ?: "") }
                    OutlinedTextField(s, { s = it; eng.setSetting("cv_summary", it) }, Modifier.fillMaxWidth().heightIn(min = 90.dp), label = { Text("Summary") })
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("cv_experience", "cv_education", "cv_skills").forEach { id ->
                        val c = eng.collection(id) ?: return@forEach
                        AssistChip(onClick = { nav.openCollection(id) }, label = { Text("${c.name.removePrefix("CV · ")} (${eng.entries(id).size})") }, leadingIcon = { Icon(iconFor(c.icon), null, Modifier.size(16.dp)) })
                    }
                }
            }
            item {
                SectionCard("Preview", Icons.Outlined.Description) {
                    Surface(shape = RoundedCornerShape(8.dp), color = androidx.compose.ui.graphics.Color.White) {
                        Text(Insights.cvText(eng), Modifier.padding(16.dp), color = androidx.compose.ui.graphics.Color(0xFF1A1A1A), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun FlashcardsScreen() {
    tick()
    val eng = Ks.engine
    val nav = LocalNav.current
    var lang by remember { mutableStateOf<String?>(null) }
    val deck = remember(Ks.version.longValue, lang) { eng.entries("vocabulary").filter { !eng.bool(it, "learned") && (lang == null || eng.text(it, "language") == lang) }.shuffled() }
    var idx by remember(deck.size, lang) { mutableIntStateOf(0) }
    var reveal by remember(idx, lang) { mutableStateOf(false) }
    Screen("Flashcards") { pad ->
        Column(Modifier.padding(pad).padding(16.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val langs = eng.entries("vocabulary").mapNotNull { eng.text(it, "language") }.distinct()
            if (langs.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(lang == null, { lang = null }, label = { Text("All") })
                langs.forEach { l -> FilterChip(lang == l, { lang = l }, label = { Text(l) }) }
            }
            if (deck.isEmpty()) {
                EmptyHint("No words left to learn. Add words to Vocabulary.")
                Button(onClick = { nav.newEntry("vocabulary", mapOf("added" to JsonPrimitive(eng.today().toString()))) }) { Text("Add a word") }
                return@Column
            }
            val card = deck[idx % deck.size]
            Text("${idx % deck.size + 1} / ${deck.size}", style = MaterialTheme.typography.labelMedium)
            ElevatedCard(onClick = { reveal = !reveal }, modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(card.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    eng.text(card, "pos")?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                    if (reveal) {
                        HorizontalDivider()
                        Text(eng.text(card, "meaning") ?: "(no meaning yet)", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        eng.text(card, "example")?.let { Text("“$it”", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
                    } else Text("Tap to reveal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { idx++ }) { Text("Again") }
                Button(onClick = { eng.setValue(card.id, "learned", JsonPrimitive(true)) }) { Text("Got it") }
            }
        }
    }
}

/** Edit a collection's fields and views ("create your own" databases). */
@Composable
fun SchemaScreen(id: String) {
    tick()
    val nav = LocalNav.current
    val eng = Ks.engine
    val c = eng.collection(id) ?: return
    var addField by remember { mutableStateOf(false) }
    var addView by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var removeField by remember { mutableStateOf<Field?>(null) }
    var iconPick by remember { mutableStateOf(false) }
    Screen("Edit ${c.name}") { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { rename = true }) { Text("Rename") }
                    OutlinedButton(onClick = { iconPick = true }) { Icon(iconFor(c.icon), null); Text(" Icon") }
                    OutlinedButton(onClick = { nav.openCollection(id) }) { Text("Open") }
                }
            }
            item { Text("Fields", style = MaterialTheme.typography.titleMedium) }
            items(eng.fields(c), key = { it.key }) { f ->
                ListItem(headlineContent = { Text(f.name) },
                    supportingContent = { Text(f.type.name.lowercase().replace('_', ' ') + (f.target?.let { " → " + (eng.collection(it)?.name ?: it) } ?: "") + if (f.options.isNotEmpty()) " · " + f.options.joinToString(", ") { it.name } else "", maxLines = 2) },
                    trailingContent = { if (!f.key.startsWith("_")) IconButton(onClick = { removeField = f }) { Icon(Icons.Outlined.Delete, "Remove field") } })
            }
            item { Button(onClick = { addField = true }) { Icon(Icons.Outlined.Add, null); Text(" Add field") } }
            item { Text("Views", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }
            items(c.views, key = { "v_" + it.id }) { v ->
                ListItem(leadingContent = { Icon(viewIcon(v.type), null) }, headlineContent = { Text(v.name) },
                    supportingContent = { Text(listOfNotNull(v.type.name.lowercase(), v.groupBy?.let { "group by $it" }, v.dateField?.let { "date $it" }, v.filters.takeIf { it.isNotEmpty() }?.let { "${it.size} filter(s)" }).joinToString(" · ")) },
                    trailingContent = { if (c.views.size > 1) IconButton(onClick = { eng.removeView(id, v.id) }) { Icon(Icons.Outlined.Delete, "Remove view") } })
            }
            item { Button(onClick = { addView = true }) { Icon(Icons.Outlined.Add, null); Text(" Add view") } }
            if (!c.preset) item { TextButton(onClick = { confirmDelete = true }) { Text("Delete this collection", color = MaterialTheme.colorScheme.error) } }
        }
    }
    if (addField) AddFieldDialog(c, onDismiss = { addField = false })
    if (addView) AddViewDialog(c, onDismiss = { addView = false })
    if (rename) TextInputDialog("Rename collection", c.name, onDismiss = { rename = false }) { eng.upsertCollection(c.copy(name = it)) }
    if (iconPick) AlertDialog(onDismissRequest = { iconPick = false }, title = { Text("Icon") }, text = {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            CUSTOM_ICONS.forEach { n -> IconButton(onClick = { eng.upsertCollection(c.copy(icon = n)); iconPick = false }) { Icon(iconFor(n), n) } }
        }
    }, confirmButton = { TextButton(onClick = { iconPick = false }) { Text("Close") } })
    removeField?.let { f ->
        ConfirmDialog("Remove “${f.name}”?", "Its values will be deleted from every item.", "Remove", onDismiss = { removeField = null }) { eng.removeField(id, f.key) }
    }
    if (confirmDelete) ConfirmDialog("Delete ${c.name}?", "The collection and all its items will be deleted.", onDismiss = { confirmDelete = false }) {
        nav.popBackStack(); nav.popBackStack(); eng.deleteCollection(id)
    }
}

private val ADDABLE = FieldType.entries.filter { it != FieldType.ROLLUP }

@Composable
fun AddFieldDialog(c: CollectionDef, onDismiss: () -> Unit) {
    val eng = Ks.engine
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(FieldType.TEXT) }
    var options by remember { mutableStateOf("") }
    var target by remember { mutableStateOf(eng.collections().first().id) }
    var formula by remember { mutableStateOf("") }
    var remind by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add field") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
            Text("Type", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ADDABLE.forEach { t -> FilterChip(type == t, { type = t }, label = { Text(t.name.lowercase().replace('_', ' ')) }) }
            }
            when (type) {
                FieldType.SELECT, FieldType.MULTI_SELECT -> OutlinedTextField(options, { options = it }, label = { Text("Options, comma separated") })
                FieldType.RELATION -> {
                    Text("Links to", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        eng.collections().forEach { tc -> FilterChip(target == tc.id, { target = tc.id }, label = { Text(tc.name) }) }
                    }
                }
                FieldType.FORMULA -> {
                    OutlinedTextField(formula, { formula = it }, label = { Text("Formula, e.g. pct(saved,target)") }, singleLine = true)
                    Text("Fields: " + eng.fields(c).joinToString(", ") { it.key } + "\nFunctions: " + Formula.FUNCTIONS.joinToString(", "), style = MaterialTheme.typography.labelSmall)
                }
                FieldType.DATE -> Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(remind, { remind = it }); Text("Show in Upcoming & reminders") }
                else -> {}
            }
        }
    }, confirmButton = {
        TextButton(enabled = name.isNotBlank(), onClick = {
            eng.addField(c.id, name, type) { f ->
                f.copy(
                    options = if (type == FieldType.SELECT || type == FieldType.MULTI_SELECT) Engine.opts(*options.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray()) else emptyList(),
                    target = if (type == FieldType.RELATION) target else null,
                    formula = if (type == FieldType.FORMULA) formula.trim() else null,
                    remind = remind,
                )
            }
            onDismiss()
        }) { Text("Add") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun AddViewDialog(c: CollectionDef, onDismiss: () -> Unit) {
    val eng = Ks.engine
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ViewType.LIST) }
    var field by remember { mutableStateOf<String?>(null) }
    val fs = eng.fields(c)
    val candidates = when (type) {
        ViewType.BOARD -> fs.filter { it.type in setOf(FieldType.SELECT, FieldType.MULTI_SELECT, FieldType.CHECKBOX, FieldType.RELATION) }
        ViewType.CALENDAR -> fs.filter { it.type == FieldType.DATE }
        ViewType.GALLERY -> fs.filter { it.type == FieldType.IMAGE }
        ViewType.STATS -> fs.filter { it.type in setOf(FieldType.SELECT, FieldType.MULTI_SELECT, FieldType.NUMBER, FieldType.CURRENCY, FieldType.DATE, FieldType.RATING, FieldType.FORMULA) }
        else -> emptyList()
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add view") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { ViewType.entries.forEach { t -> FilterChip(type == t, { type = t; field = null }, label = { Text(t.name.lowercase()) }) } }
            if (candidates.isNotEmpty()) {
                Text(when (type) { ViewType.BOARD -> "Group by"; ViewType.CALENDAR -> "Date field"; ViewType.GALLERY -> "Cover image"; else -> "Chart field" }, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { candidates.forEach { f -> FilterChip(field == f.key, { field = f.key }, label = { Text(f.name) }) } }
            } else if (type == ViewType.BOARD || type == ViewType.CALENDAR) Text("Add a ${if (type == ViewType.BOARD) "select" else "date"} field first.", color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = {
        val needs = type == ViewType.BOARD || type == ViewType.CALENDAR
        TextButton(enabled = name.isNotBlank() && (!needs || field != null), onClick = {
            val f = fs.firstOrNull { it.key == field }
            val v = ViewDef("v" + System.currentTimeMillis(), name.trim(), type,
                groupBy = if (type == ViewType.BOARD || (type == ViewType.STATS && f?.type in setOf(FieldType.SELECT, FieldType.MULTI_SELECT))) field else null,
                dateField = if (type == ViewType.CALENDAR || (type == ViewType.STATS && f?.type == FieldType.DATE)) field else null,
                imageField = if (type == ViewType.GALLERY) field else null,
                valueField = if (type == ViewType.STATS && f?.type in setOf(FieldType.NUMBER, FieldType.CURRENCY, FieldType.RATING, FieldType.FORMULA)) field else null)
            eng.upsertView(c.id, v); onDismiss()
        }) { Text("Add") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun PinSetupDialog(onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    val valid = a.length in 4..12 && a == b
    val kb = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword)
    val mask = androidx.compose.ui.text.input.PasswordVisualTransformation()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Set a PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(a, { if (it.length <= 12 && it.all(Char::isDigit)) a = it }, label = { Text("PIN (4 to 12 digits)") }, singleLine = true, keyboardOptions = kb, visualTransformation = mask)
                OutlinedTextField(b, { if (it.length <= 12 && it.all(Char::isDigit)) b = it }, label = { Text("Repeat PIN") }, singleLine = true, keyboardOptions = kb, visualTransformation = mask,
                    isError = b.isNotEmpty() && a != b)
                Text("If you forget it, the only way back in is reinstalling, which erases your data. Keep a backup.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onDone(a) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun BlockedAppsDialog(initial: Set<String>, onDismiss: () -> Unit, onDone: (Set<String>) -> Unit) {
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    val apps = remember {
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != ctx.packageName }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
    }
    var sel by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Block apps from the internet") },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(apps.size) { i ->
                    val (pkg, label) = apps[i]
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(pkg in sel, { sel = if (it) sel + pkg else sel - pkg })
                        Text(label, maxLines = 1)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(sel) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
