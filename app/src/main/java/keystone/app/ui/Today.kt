// Copyright (C) 2026 Tylerlikesps4
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
package keystone.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import keystone.app.Ks
import keystone.engine.*
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The daily page: periodic note, mood/energy, habits, tasks, highlight, journal & sleep shortcuts. */
@Composable
fun TodayScreen() {
    tick()
    val nav = LocalNav.current
    val eng = Ks.engine
    var dayStr by rememberSaveable { mutableStateOf(eng.today().toString()) }
    val day = LocalDate.parse(dayStr)
    val note = eng.findByDate("days", "date", day)
    Screen("Today", back = false, actions = {
        IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Outlined.Settings, "Settings") }
    }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { dayStr = day.minusDays(1).toString() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Previous day") }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(eng.fmtDate(day), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                        Text(day.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { dayStr = day.plusDays(1).toString() }) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Next day") }
                }
            }
            item { SectionCard("Mood & energy", Icons.Outlined.Mood) { DayRatings(day) } }
            item {
                SectionCard("Habits", Icons.Outlined.Repeat, action = { TextButton(onClick = { nav.openCollection("habits") }) { Text("Manage") } }) {
                    val habits = Insights.activeHabits(eng)
                    if (habits.isEmpty()) EmptyHint("No active habits yet")
                    habits.forEach { HabitRow(it, day) }
                }
            }
            item {
                val c = eng.collection("tasks")!!
                val tasks = if (day == eng.today()) Insights.todayTasks(eng) else eng.entries("tasks").filter { eng.date(it, "due") == day }
                SectionCard(if (day == eng.today()) "Tasks due & overdue" else "Tasks", Icons.Outlined.CheckCircle,
                    action = { IconButton(onClick = { nav.newEntry("tasks", mapOf("due" to JsonPrimitive(day.toString()), "status" to JsonPrimitive("To do"))) }) { Icon(Icons.Outlined.Add, "Add task") } }) {
                    if (tasks.isEmpty()) EmptyHint("No tasks for this day")
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { tasks.forEach { EntryRow(it, c, cardFields(c, null, 2), doneSpec(c)) } }
                }
            }
            item {
                SectionCard("Highlight & notes", Icons.Outlined.EditNote) {
                    var hl by remember(dayStr) { mutableStateOf(note?.let { eng.text(it, "highlight") } ?: "") }
                    OutlinedTextField(hl, { hl = it; val d = eng.ensureDay(day); eng.setValue(d.id, "highlight", if (it.isBlank()) null else JsonPrimitive(it)) },
                        Modifier.fillMaxWidth(), label = { Text("Best thing about today") })
                    Spacer(Modifier.height(8.dp))
                    var water by remember(dayStr) { mutableStateOf(note?.let { eng.num(it, "water")?.toInt() } ?: 0) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Water", Modifier.weight(1f))
                        IconButton(onClick = { if (water > 0) { water--; eng.setValue(eng.ensureDay(day).id, "water", JsonPrimitive(water)) } }) { Icon(Icons.Outlined.Remove, "Less water") }
                        Text("$water glasses")
                        IconButton(onClick = { water++; eng.setValue(eng.ensureDay(day).id, "water", JsonPrimitive(water)) }) { Icon(Icons.Outlined.Add, "More water") }
                    }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AssistChip(onClick = { nav.newEntry("journal", mapOf("date" to JsonPrimitive(day.toString()), "kind" to JsonPrimitive("Evening reflection")), title = "Journal · " + day) },
                        label = { Text("Write journal") }, leadingIcon = { Icon(Icons.Outlined.Book, null) })
                    AssistChip(onClick = { nav.newEntry("sleep", mapOf("date" to JsonPrimitive(day.toString())), title = "Night of $day") },
                        label = { Text("Log sleep") }, leadingIcon = { Icon(Icons.Outlined.Bedtime, null) })
                    AssistChip(onClick = { nav.newEntry("workouts", mapOf("date" to JsonPrimitive(day.toString()))) },
                        label = { Text("Log workout") }, leadingIcon = { Icon(Icons.Outlined.FitnessCenter, null) })
                    AssistChip(onClick = { nav.newEntry("transactions", mapOf("date" to JsonPrimitive(day.toString()), "kind" to JsonPrimitive("Expense"))) },
                        label = { Text("Add expense") }, leadingIcon = { Icon(Icons.Outlined.Receipt, null) })
                    AssistChip(onClick = { nav.openEntry(eng.ensureDay(day).id) }, label = { Text("Day note") }, leadingIcon = { Icon(Icons.Outlined.Today, null) })
                    AssistChip(onClick = {
                        val d = eng.ensureDay(day); eng.list(d, "month").firstOrNull()?.let { nav.openEntry(it) }
                    }, label = { Text("Month note") }, leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null) })
                    AssistChip(onClick = {
                        val d = eng.ensureDay(day)
                        eng.list(d, "month").firstOrNull()?.let { m -> eng.entry(m)?.let { me -> eng.list(me, "year").firstOrNull()?.let { nav.openEntry(it) } } }
                    }, label = { Text("Year note") }, leadingIcon = { Icon(Icons.Outlined.Event, null) })
                }
            }
            item {
                val j = eng.entries("journal").filter { eng.date(it, "date") == day }
                if (j.isNotEmpty()) {
                    val c = eng.collection("journal")!!
                    SectionCard("Journal", Icons.Outlined.Book) { j.forEach { EntryRow(it, c, cardFields(c, null, 2)) } }
                }
            }
        }
    }
}
