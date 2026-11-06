// Copyright (C) 2026 Tylerlikesps4
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
package keystone.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import keystone.app.Ks
import keystone.engine.*
import kotlinx.serialization.json.JsonPrimitive

/** Life Setup hub: vision, wheel of life, core values, end & means goals. */
@Composable
fun LifeSetupScreen() {
    tick()
    val nav = LocalNav.current
    val eng = Ks.engine
    var editVision by remember { mutableStateOf(false) }
    var addValue by remember { mutableStateOf(false) }
    Screen("Life Setup") { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SectionCard("Vision statement", Icons.Outlined.Visibility, action = { TextButton(onClick = { editVision = true }) { Text("Edit") } }) {
                    Text(eng.setting("vision")?.takeIf { it.isNotBlank() } ?: "Describe the life you're building in a few sentences.", style = MaterialTheme.typography.bodyLarge)
                }
            }
            item {
                SectionCard("Wheel of Life", Icons.Outlined.DonutLarge, action = { TextButton(onClick = { nav.openCollection("life_areas") }) { Text("Areas") } }) {
                    RadarChart(Insights.wheel(eng))
                    Spacer(Modifier.height(8.dp))
                    eng.query("life_areas", null).sortedBy { it.created }.forEach { a ->
                        var v by remember(a.id) { mutableFloatStateOf((eng.num(a, "current") ?: 5.0).toFloat()) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(a.title, Modifier.width(130.dp), style = MaterialTheme.typography.bodySmall)
                            Slider(v, { v = it }, Modifier.weight(1f), valueRange = 0f..10f, steps = 9, onValueChangeFinished = { eng.setValue(a.id, "current", JsonPrimitive(v.toInt())) })
                            Text("${v.toInt()}", Modifier.width(24.dp))
                        }
                    }
                }
            }
            item {
                SectionCard("Core values", Icons.Outlined.Diamond, action = { TextButton(onClick = { addValue = true }) { Text("+ Add") } }) {
                    val vals = eng.entries("values")
                    if (vals.isEmpty()) EmptyHint("What matters most to you?")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        vals.forEach { v -> AssistChip(onClick = { nav.openEntry(v.id) }, label = { Text(v.title) }) }
                    }
                }
            }
            item {
                SectionCard("Goals", Icons.Outlined.Flag, action = { TextButton(onClick = { nav.openCollection("goals") }) { Text("Board") } }) {
                    val ends = eng.entries("goals").filter { eng.text(it, "kind") != "Means goal" }
                    if (ends.isEmpty()) EmptyHint("Set an end goal: the outcome you want. Then add means goals: what you'll do to get there.")
                    ends.forEach { g ->
                        ProgressLine("🎯 " + g.title, (eng.num(g, "progress") ?: 0.0) / 100, eng.text(g, "status") ?: "") { nav.openEntry(g.id) }
                        eng.backlinks(g.id).filter { it.first.collection == "goals" }.forEach { (m, _) ->
                            ProgressLine("     ↳ " + m.title, (eng.num(m, "progress") ?: 0.0) / 100, eng.text(m, "status") ?: "") { nav.openEntry(m.id) }
                        }
                        TextButton(onClick = { nav.newEntry("goals", mapOf("kind" to JsonPrimitive("Means goal"), "parent" to V.list(listOf(g.id)), "status" to JsonPrimitive("Active"))) }) { Text("+ Means goal for “${g.title}”") }
                    }
                    Button(onClick = { nav.newEntry("goals", mapOf("kind" to JsonPrimitive("End goal"), "status" to JsonPrimitive("Active"))) }) { Text("New end goal") }
                }
            }
            item { OutlinedButton(onClick = { nav.navigate("onboarding") }) { Text("Run the setup wizard again") } }
        }
    }
    if (editVision) {
        var t by remember { mutableStateOf(eng.setting("vision") ?: "") }
        AlertDialog(onDismissRequest = { editVision = false }, title = { Text("Vision statement") },
            text = { OutlinedTextField(t, { t = it }, Modifier.fillMaxWidth().heightIn(min = 140.dp), placeholder = { Text("In five years I am…") }) },
            confirmButton = { TextButton(onClick = { eng.setSetting("vision", t.trim()); editVision = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editVision = false }) { Text("Cancel") } })
    }
    if (addValue) TextInputDialog("Add a core value", label = "Value", onDismiss = { addValue = false }) { eng.create("values", it, mapOf("importance" to JsonPrimitive(4))) }
}

/** First-run wizard that mirrors the Life Setup. */
@Composable
fun OnboardingScreen() {
    val nav = LocalNav.current
    val eng = Ks.engine
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf(eng.setting("profile_name") ?: "") }
    val ratings = remember { mutableStateMapOf<String, Float>().apply { Presets.WHEEL_AREAS.forEach { put(it, 5f) } } }
    val chosen = remember { mutableStateListOf<String>().apply { addAll(eng.entries("values").map { it.title }) } }
    var vision by rememberSaveable { mutableStateOf(eng.setting("vision") ?: "") }
    var endGoal by rememberSaveable { mutableStateOf("") }
    val means = remember { mutableStateListOf("", "", "") }
    var board by rememberSaveable { mutableStateOf("full") }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val steps = 6

    fun finish() {
        eng.setSetting("profile_name", name.trim())
        val areas = eng.entries("life_areas")
        ratings.forEach { (k, v) -> areas.firstOrNull { it.title == k }?.let { eng.setValue(it.id, "current", JsonPrimitive(v.toInt())) } }
        val existing = eng.entries("values").map { it.title }.toSet()
        chosen.filter { it !in existing }.forEach { eng.create("values", it, mapOf("importance" to JsonPrimitive(4))) }
        if (vision.isNotBlank()) eng.setSetting("vision", vision.trim())
        if (endGoal.isNotBlank()) {
            val g = eng.create("goals", endGoal.trim(), mapOf("kind" to JsonPrimitive("End goal"), "status" to JsonPrimitive("Active")))
            means.filter { it.isNotBlank() }.forEach { eng.create("goals", it.trim(), mapOf("kind" to JsonPrimitive("Means goal"), "status" to JsonPrimitive("Active"), "parent" to V.list(listOf(g.id)))) }
        }
        eng.setSetting("dashboard", board)
        eng.setSetting("onboarded", "true")
        if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
        nav.navigate("home") { popUpTo(nav.graph.id) { inclusive = true } }
    }

    Scaffold(bottomBar = {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) TextButton(onClick = { step-- }) { Text("Back") }
            Spacer(Modifier.weight(1f))
            if (step > 0) TextButton(onClick = { if (step < steps - 1) step++ else finish() }) { Text("Skip") }
            Button(onClick = { if (step < steps - 1) step++ else finish() }) { Text(if (step < steps - 1) "Next" else "Start using Anu Keystone") }
        }
    }) { pad ->
        Column(Modifier.padding(pad).statusBarsPadding().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            LinearProgressIndicator(progress = { (step + 1f) / steps }, Modifier.fillMaxWidth())
            when (step) {
                0 -> {
                    Text("Welcome to Anu Keystone", style = MaterialTheme.typography.headlineMedium)
                    Text("One place for your tasks, goals, habits, health, money, learning, home, people and adventures. Everything stays on your phone.", style = MaterialTheme.typography.bodyLarge)
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("What should we call you?") }, singleLine = true)
                    Text("Next we'll do a 2-minute Life Setup: Wheel of Life, core values, vision and goals. You can skip any step.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                1 -> {
                    Text("Wheel of Life", style = MaterialTheme.typography.headlineSmall)
                    Text("How satisfied are you with each area right now? (0–10)", style = MaterialTheme.typography.bodyMedium)
                    RadarChart(Presets.WHEEL_AREAS.map { Insights.WheelSlice(it, (ratings[it] ?: 5f).toDouble(), 8.0) }, 200)
                    Presets.WHEEL_AREAS.forEach { a ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(a, Modifier.width(140.dp), style = MaterialTheme.typography.bodySmall)
                            Slider(ratings[a] ?: 5f, { ratings[a] = it }, Modifier.weight(1f), valueRange = 0f..10f, steps = 9)
                            Text("${(ratings[a] ?: 5f).toInt()}", Modifier.width(24.dp))
                        }
                    }
                }
                2 -> {
                    Text("Core values", style = MaterialTheme.typography.headlineSmall)
                    Text("Pick the 3–7 values that guide your decisions.", style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Presets.VALUE_SUGGESTIONS.forEach { v ->
                            FilterChip(selected = v in chosen, onClick = { if (v in chosen) chosen.remove(v) else chosen.add(v) }, label = { Text(v) })
                        }
                    }
                    Text("${chosen.size} selected", style = MaterialTheme.typography.labelMedium)
                }
                3 -> {
                    Text("Vision statement", style = MaterialTheme.typography.headlineSmall)
                    Text("Picture yourself in 3–5 years at your best. Who are you, what do you do, how do you feel?", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(vision, { vision = it }, Modifier.fillMaxWidth().heightIn(min = 160.dp), placeholder = { Text("I am healthy and strong, I build things I'm proud of…") })
                }
                4 -> {
                    Text("Means & end goals", style = MaterialTheme.typography.headlineSmall)
                    Text("An end goal is the outcome you want. Means goals are the steps that get you there.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(endGoal, { endGoal = it }, Modifier.fillMaxWidth(), label = { Text("End goal") }, placeholder = { Text("Run a half marathon") }, singleLine = true)
                    means.indices.forEach { i ->
                        OutlinedTextField(means[i], { means[i] = it }, Modifier.fillMaxWidth(), label = { Text("Means goal ${i + 1}") }, singleLine = true)
                    }
                }
                5 -> {
                    Text("Pick your dashboard", style = MaterialTheme.typography.headlineSmall)
                    listOf("simple" to "Simple — greeting, habits, tasks, upcoming", "full" to "Command Center — everything at a glance", "mobile" to "Mobile — quick add, habits, today's tasks", "custom" to "My Dashboard — start minimal and build your own")
                        .forEach { (id, label) ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                RadioButton(board == id, { board = id }); Text(label)
                            }
                        }
                    Text("You can switch dashboards and add 60+ widgets anytime.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
