// Copyright (C) 2026 Tylerlikesps4
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
package keystone.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import keystone.app.Ks
import keystone.engine.*

/** Standard screen frame: top bar with optional back arrow, actions and FAB. */
@Composable
fun Screen(
    title: String,
    back: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    fab: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val nav = LocalNav.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { if (back) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = actions,
            )
        },
        floatingActionButton = { fab?.invoke() },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { content(it) }
}

@Composable
fun AddFab(label: String = "Add", onClick: () -> Unit) {
    ExtendedFloatingActionButton(onClick = onClick, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(label) })
}

@Composable
fun CollectionTile(c: CollectionDef, modifier: Modifier = Modifier) {
    tick()
    val nav = LocalNav.current
    val count = Ks.engine.entries(c.id).size
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable { nav.openCollection(c.id) }) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(iconFor(c.icon), null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$count item" + if (count == 1) "" else "s", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun HubsScreen() {
    tick()
    val nav = LocalNav.current
    val eng = Ks.engine
    var newCol by remember { mutableStateOf(false) }
    val custom = eng.collections().filter { c -> Presets.hubs.none { c.id in it.collections } }
    Screen("Life Hubs", back = false, actions = {
        IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Outlined.Settings, "Settings") }
    }) { pad ->
        LazyVerticalGrid(GridCells.Adaptive(160.dp), Modifier.padding(pad), contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(Presets.hubs) { h ->
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.height(150.dp).clip(RoundedCornerShape(20.dp)).clickable { if (h.id == "setup") nav.navigate("setup") else nav.navigate("hub/${h.id}") }) {
                    Column(Modifier.padding(16.dp)) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                            Icon(iconFor(h.icon), null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(h.name, style = MaterialTheme.typography.titleMedium)
                        Text(h.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Your collections", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }
            items(custom) { c -> CollectionTile(c) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedButton(onClick = { newCol = true }) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("Create your own collection") }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { nav.navigate("guide") }, label = { Text("Setup guide") }, leadingIcon = { Icon(Icons.Outlined.MenuBook, null) })
                    AssistChip(onClick = { nav.navigate("archive") }, label = { Text("Archive") }, leadingIcon = { Icon(Icons.Outlined.Archive, null) })
                }
            }
        }
    }
    if (newCol) TextInputDialog("New collection", label = "Collection name", onDismiss = { newCol = false }) { name ->
        val c = eng.createCollection(name)
        nav.navigate("schema/${c.id}")
    }
}

@Composable
fun HubScreen(id: String) {
    tick()
    val nav = LocalNav.current
    val hub = Presets.hub(id) ?: return
    val eng = Ks.engine
    Screen(hub.name) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(hub.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { HubHighlights(id) }
            items(hub.collections.mapNotNull { eng.collection(it) }) { c -> CollectionTile(c, Modifier.fillMaxWidth()) }
            if (id == "career") item { OutlinedButton(onClick = { nav.navigate("cv") }) { Icon(Icons.Outlined.Description, null); Spacer(Modifier.width(6.dp)); Text("Open CV builder") } }
            if (id == "growth") item { OutlinedButton(onClick = { nav.navigate("flashcards") }) { Icon(Icons.Outlined.Style, null); Spacer(Modifier.width(6.dp)); Text("Practice vocabulary") } }
        }
    }
}

/** A few live widgets at the top of each hub. */
@Composable
fun HubHighlights(id: String) {
    val widgets: List<WidgetType> = when (id) {
        "basics" -> listOf(WidgetType.TODAY_TASKS, WidgetType.HABITS_TODAY)
        "health" -> listOf(WidgetType.WEIGHT_CHART, WidgetType.SLEEP_CHART)
        "money" -> listOf(WidgetType.MONEY_MONTH, WidgetType.BUDGETS)
        "growth" -> listOf(WidgetType.READING_NOW, WidgetType.LEARNING_PROGRESS)
        "people" -> listOf(WidgetType.BIRTHDAYS)
        "home" -> listOf(WidgetType.UPCOMING)
        else -> emptyList()
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        widgets.forEach { WidgetView(Widget("hub_$it", it, limit = 4), editing = false) }
    }
}

@Composable
fun ParaScreen() {
    tick()
    val nav = LocalNav.current
    val eng = Ks.engine
    Screen("PARA", back = false, actions = {
        IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Outlined.Settings, "Settings") }
    }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Projects, Areas, Resources, Archive. Link anything to a project or area to see it here.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                val active = eng.entries("projects").filter { eng.text(it, "status") != "Done" }
                SectionCard("Projects", iconFor("rocket"), action = { TextButton(onClick = { nav.openCollection("projects") }) { Text("All") } }) {
                    if (active.isEmpty()) EmptyHint("No active projects")
                    active.take(6).forEach { p ->
                        ProgressLine(p.title, (eng.num(p, "progress") ?: 0.0) / 100, (eng.text(p, "status") ?: "") + " · " + disp(p, "progress")) { nav.openEntry(p.id) }
                    }
                    TextButton(onClick = { nav.newEntry("projects", mapOf("status" to V.str("Planned"))) }) { Text("+ New project") }
                }
            }
            item {
                SectionCard("Areas", iconFor("layers"), action = { TextButton(onClick = { nav.openCollection("areas") }) { Text("All") } }) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        eng.entries("areas").sortedBy { it.created }.forEach { a ->
                            val n = eng.backlinks(a.id).size
                            AssistChip(onClick = { nav.openEntry(a.id) }, label = { Text("${a.title} · $n") })
                        }
                    }
                }
            }
            item {
                SectionCard("Resources", iconFor("library"), action = { TextButton(onClick = { nav.openCollection("res_collections") }) { Text("Collections") } }) {
                    eng.entries("res_collections").take(5).forEach { c ->
                        ProgressLine(c.title, (eng.num(c, "progress") ?: 0.0) / 100, "${disp(c, "count")} · ${disp(c, "progress")}") { nav.openEntry(c.id) }
                    }
                    Row { TextButton(onClick = { nav.openCollection("resources") }) { Text("All resources") }; TextButton(onClick = { nav.newEntry("resources") }) { Text("+ Resource") } }
                }
            }
            item {
                SectionCard("Archive", Icons.Outlined.Archive, action = { TextButton(onClick = { nav.navigate("archive") }) { Text("Open") } }) {
                    Text("${eng.archived().size} archived items", style = MaterialTheme.typography.bodyMedium)
                }
            }
            item { Text("Basics", style = MaterialTheme.typography.titleMedium) }
            items(listOf("tasks", "tags", "habits", "journal", "sleep", "days", "months", "years").mapNotNull { eng.collection(it) }) { CollectionTile(it, Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
fun SearchScreen() {
    tick()
    var q by rememberSaveable { mutableStateOf("") }
    val eng = Ks.engine
    Screen("Search", back = false) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search everything") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true)
            Spacer(Modifier.height(12.dp))
            val results = remember(q, Ks.version.longValue) { eng.search(q) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (q.isBlank()) item { EmptyHint("Find any task, note, book, person, recipe… across all ${eng.collections().size} collections.") }
                else if (results.isEmpty()) item { EmptyHint("Nothing matches “$q”") }
                items(results, key = { it.id }) { e ->
                    val c = eng.collection(e.collection) ?: return@items
                    EntryRow(e, c, emptyList(), showCollection = true)
                }
            }
        }
    }
}

@Composable
fun ArchiveScreen() {
    tick()
    val eng = Ks.engine
    Screen("Archive") { pad ->
        val list = eng.archived()
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (list.isEmpty()) item { EmptyHint("Archived projects, tasks and notes appear here. Nothing is deleted when you archive.") }
            items(list, key = { it.id }) { e ->
                val c = eng.collection(e.collection) ?: return@items
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { EntryRow(e, c, emptyList(), showCollection = true) }
                    IconButton(onClick = { eng.archive(e.id, false) }) { Icon(Icons.Outlined.Unarchive, "Restore") }
                }
            }
        }
    }
}

@Composable
fun GuideScreen() {
    val nav = LocalNav.current
    val steps = listOf(
        "Life Setup" to "Rate your Wheel of Life, pick core values, write a vision statement and set end goals with the means goals that serve them.",
        "PARA" to "Projects have a goal and a deadline. Areas are ongoing responsibilities. Resources are things you learn from. Archive anything you finish — it stays searchable.",
        "Tasks" to "Add tasks with a due date and link them to a project; project progress updates itself. Repeating tasks come back when you mark them done.",
        "Today" to "Your daily page: mood, energy, habits, tasks due, a highlight, and quick links to journal and sleep. Day, month and year notes are created for you.",
        "Hubs" to "Every hub is a set of collections. Each collection has list, board, calendar, gallery, table and stats views. Tap the ⋮ menu to edit fields or add views.",
        "Dashboards" to "Pick Simple, Command Center or Mobile on Home, or build your own from 60+ widgets with the edit button.",
        "Tags" to "Tags work everywhere. Open a tag to see every item that uses it.",
        "Reminders" to "Birthdays, warranties, renewals, car service and due dates feed the Upcoming widget and a daily notification.",
        "Backup" to "Everything stays on your phone. Export a JSON backup from Settings and import it on any device.",
    )
    Screen("Setup guide") { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(steps.size) { i ->
                SectionCard("${i + 1}. ${steps[i].first}") { Text(steps[i].second, style = MaterialTheme.typography.bodyMedium) }
            }
            item {
                Button(onClick = { nav.navigate("onboarding") }) { Text("Run the Life Setup wizard"); Spacer(Modifier.width(6.dp)); Icon(Icons.AutoMirrored.Outlined.ArrowForward, null) }
            }
        }
    }
}
