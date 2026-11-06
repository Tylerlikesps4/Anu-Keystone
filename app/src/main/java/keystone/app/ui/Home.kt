// Copyright (C) 2026 Tylerlikesps4
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
package keystone.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import keystone.app.Ks
import keystone.engine.*
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen() {
    tick()
    val nav = LocalNav.current
    val eng = Ks.engine
    val boards = eng.dashboards()
    val currentId = eng.setting("dashboard") ?: if (boards.any { it.id == "full" }) "full" else boards.first().id
    val board = boards.firstOrNull { it.id == currentId } ?: boards.first()
    var editing by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var newBoard by remember { mutableStateOf(false) }

    fun save(b: Dashboard) = eng.saveDashboards(boards.map { if (it.id == b.id) b else it })

    Screen("Keystone", back = false, actions = {
        IconButton(onClick = { editing = !editing }) { Icon(if (editing) Icons.Outlined.Check else Icons.Outlined.Edit, if (editing) "Done editing" else "Edit dashboard") }
        IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Outlined.Settings, "Settings") }
    }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(boards) { b -> FilterChip(selected = b.id == board.id, onClick = { eng.setSetting("dashboard", b.id) }, label = { Text(b.name) }) }
                    item { AssistChip(onClick = { newBoard = true }, label = { Text("+ New") }) }
                }
            }
            itemsIndexed(board.widgets, key = { _, w -> w.id }) { i, w ->
                Column {
                    if (editing) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(widgetName(w), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        IconButton(enabled = i > 0, onClick = { save(board.copy(widgets = board.widgets.toMutableList().also { it.add(i - 1, it.removeAt(i)) })) }) { Icon(Icons.Outlined.ArrowUpward, "Move up") }
                        IconButton(enabled = i < board.widgets.size - 1, onClick = { save(board.copy(widgets = board.widgets.toMutableList().also { it.add(i + 1, it.removeAt(i)) })) }) { Icon(Icons.Outlined.ArrowDownward, "Move down") }
                        IconButton(onClick = { save(board.copy(widgets = board.widgets.filterNot { it.id == w.id })) }) { Icon(Icons.Outlined.Delete, "Remove") }
                    }
                    WidgetView(w, editing)
                }
            }
            if (editing) item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { adding = true }) { Icon(Icons.Outlined.Add, null); Text(" Add widget") }
                    if (boards.size > 1) OutlinedButton(onClick = {
                        val rest = boards.filterNot { it.id == board.id }; eng.saveDashboards(rest); eng.setSetting("dashboard", rest.first().id); editing = false
                    }) { Text("Delete dashboard") }
                }
            }
            if (board.widgets.isEmpty() && !editing) item { EmptyHint("This dashboard is empty. Tap the pencil to add widgets.") }
        }
    }
    if (adding) WidgetPicker(onDismiss = { adding = false }) { w ->
        save(board.copy(widgets = board.widgets + w.copy(id = "w" + System.currentTimeMillis())))
    }
    if (newBoard) TextInputDialog("New dashboard", label = "Dashboard name", onDismiss = { newBoard = false }) { name ->
        val d = Dashboard("d" + System.currentTimeMillis(), name, listOf(Widget("w" + System.nanoTime(), WidgetType.GREETING)))
        eng.saveDashboards(boards + d); eng.setSetting("dashboard", d.id); editing = true
    }
}

fun widgetName(w: Widget): String = w.title ?: when (w.type) {
    WidgetType.GREETING -> "Greeting & day summary"
    WidgetType.VISION -> "Vision statement"
    WidgetType.WHEEL_OF_LIFE -> "Wheel of Life"
    WidgetType.CORE_VALUES -> "Core values"
    WidgetType.GOALS -> "Goals"
    WidgetType.HABITS_TODAY -> "Habits today"
    WidgetType.TODAY_TASKS -> "Tasks due"
    WidgetType.PROJECTS -> "Active projects"
    WidgetType.UPCOMING -> "Upcoming reminders"
    WidgetType.MONEY_MONTH -> "This month's money"
    WidgetType.BUDGETS -> "Budgets"
    WidgetType.SLEEP_CHART -> "Sleep (7 nights)"
    WidgetType.WEIGHT_CHART -> "Weight trend"
    WidgetType.READING_NOW -> "Reading now"
    WidgetType.DAY_NOTE -> "Mood & energy"
    WidgetType.QUICK_ADD -> "Quick add"
    WidgetType.COLLECTION_LIST -> "List"
    WidgetType.COUNTER -> "Counter"
    WidgetType.STATS_CHART -> "Chart"
    WidgetType.LEARNING_PROGRESS -> "Learning progress"
    WidgetType.BIRTHDAYS -> "Birthdays"
    WidgetType.QUICK_LINKS -> "Quick links"
}

@Composable
fun WidgetPicker(onDismiss: () -> Unit, onPick: (Widget) -> Unit) {
    val eng = Ks.engine
    var q by remember { mutableStateOf("") }
    val catalog = remember { Presets.widgetCatalog(eng) }
    val shown = catalog.filter { q.isBlank() || widgetName(it).contains(q, true) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add a widget (${catalog.size})") }, text = {
        Column {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search widgets") }, singleLine = true)
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(shown, key = { it.id }) { w ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(w); onDismiss() }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(widgetIcon(w), null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp))
                        Text(widgetName(w))
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

fun widgetIcon(w: Widget) = when (w.type) {
    WidgetType.COLLECTION_LIST, WidgetType.COUNTER, WidgetType.QUICK_ADD, WidgetType.STATS_CHART -> iconFor(w.collection?.let { Ks.engine.collection(it)?.icon })
    WidgetType.GREETING -> Icons.Outlined.WbSunny
    WidgetType.VISION -> Icons.Outlined.Visibility
    WidgetType.WHEEL_OF_LIFE -> Icons.Outlined.DonutLarge
    WidgetType.CORE_VALUES -> Icons.Outlined.Diamond
    WidgetType.GOALS -> Icons.Outlined.Flag
    WidgetType.HABITS_TODAY -> Icons.Outlined.Repeat
    WidgetType.TODAY_TASKS -> Icons.Outlined.CheckCircle
    WidgetType.PROJECTS -> Icons.Outlined.RocketLaunch
    WidgetType.UPCOMING -> Icons.Outlined.NotificationsActive
    WidgetType.MONEY_MONTH -> Icons.Outlined.AccountBalanceWallet
    WidgetType.BUDGETS -> Icons.Outlined.Savings
    WidgetType.SLEEP_CHART -> Icons.Outlined.Bedtime
    WidgetType.WEIGHT_CHART -> Icons.Outlined.MonitorWeight
    WidgetType.READING_NOW -> Icons.Outlined.Book
    WidgetType.DAY_NOTE -> Icons.Outlined.Mood
    WidgetType.LEARNING_PROGRESS -> Icons.Outlined.School
    WidgetType.BIRTHDAYS -> Icons.Outlined.Cake
    WidgetType.QUICK_LINKS -> Icons.Outlined.Apps
}

@Composable
fun WidgetView(w: Widget, editing: Boolean) {
    tick()
    val nav = LocalNav.current
    val eng = Ks.engine
    val t = eng.today()
    when (w.type) {
        WidgetType.GREETING -> {
            val h = LocalTime.now().hour
            val hello = when (h) { in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; else -> "Good evening" }
            val name = eng.setting("profile_name")?.takeIf { it.isNotBlank() }
            val tasks = Insights.todayTasks(eng).size
            val habits = Insights.activeHabits(eng)
            val left = habits.count { !eng.habitDone(it.id, t) }
            Column(Modifier.padding(vertical = 4.dp)) {
                Text(t.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(hello + (name?.let { ", $it" } ?: ""), style = MaterialTheme.typography.headlineMedium)
                Text("$tasks task${if (tasks == 1) "" else "s"} due · $left of ${habits.size} habits left", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        WidgetType.VISION -> SectionCard("Vision", Icons.Outlined.Visibility, onTitleClick = { nav.navigate("setup") }) {
            val v = eng.setting("vision")
            if (v.isNullOrBlank()) TextButton(onClick = { nav.navigate("setup") }) { Text("Write your vision statement") }
            else Text("“$v”", style = MaterialTheme.typography.bodyLarge)
        }
        WidgetType.WHEEL_OF_LIFE -> SectionCard("Wheel of Life", Icons.Outlined.DonutLarge, onTitleClick = { nav.navigate("setup") }) { RadarChart(Insights.wheel(eng), 220) }
        WidgetType.CORE_VALUES -> SectionCard("Core values", Icons.Outlined.Diamond, onTitleClick = { nav.openCollection("values") }) {
            val vals = eng.query("values", eng.collection("values")?.views?.first())
            if (vals.isEmpty()) EmptyHint("Pick your core values in Life Setup")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { vals.forEach { v -> SelectChip(v.title, paletteColor(v.title.length)) } }
        }
        WidgetType.GOALS -> SectionCard("Goals", Icons.Outlined.Flag, onTitleClick = { nav.openCollection("goals") }) {
            val goals = eng.entries("goals").filter { eng.text(it, "status") != "Achieved" && eng.text(it, "status") != "Dropped" }
                .sortedWith(compareBy<Entry> { if (eng.text(it, "kind") == "End goal") 0 else 1 }.thenBy { it.created })
            if (goals.isEmpty()) EmptyHint("No active goals")
            goals.take(w.limit.coerceAtLeast(5)).forEach { g ->
                ProgressLine((if (eng.text(g, "kind") == "Means goal") "  ↳ " else "") + g.title, (eng.num(g, "progress") ?: 0.0) / 100, eng.date(g, "target")?.let { eng.fmtDate(it) } ?: "") { nav.openEntry(g.id) }
            }
        }
        WidgetType.HABITS_TODAY -> SectionCard("Habits today", Icons.Outlined.Repeat, onTitleClick = { nav.navigate("today") }) {
            val habits = Insights.activeHabits(eng)
            if (habits.isEmpty()) EmptyHint("No active habits")
            habits.forEach { h -> HabitRow(h, t) }
        }
        WidgetType.TODAY_TASKS -> SectionCard("Tasks due", Icons.Outlined.CheckCircle, onTitleClick = { nav.openCollection("tasks", "today") },
            action = { IconButton(onClick = { nav.newEntry("tasks", mapOf("due" to JsonPrimitive(t.toString()), "status" to JsonPrimitive("To do"))) }) { Icon(Icons.Outlined.Add, "Add task") } }) {
            val tasks = Insights.todayTasks(eng)
            val c = eng.collection("tasks")!!
            if (tasks.isEmpty()) EmptyHint("Nothing due. Nice.")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { tasks.take(w.limit.coerceAtLeast(5)).forEach { EntryRow(it, c, cardFields(c, c.views.first(), 2), doneSpec(c)) } }
        }
        WidgetType.PROJECTS -> SectionCard("Active projects", Icons.Outlined.RocketLaunch, onTitleClick = { nav.openCollection("projects") }) {
            val ps = eng.entries("projects").filter { eng.text(it, "status") == "In progress" }
            if (ps.isEmpty()) EmptyHint("No projects in progress")
            ps.take(w.limit).forEach { p -> ProgressLine(p.title, (eng.num(p, "progress") ?: 0.0) / 100, disp(p, "progress")) { nav.openEntry(p.id) } }
        }
        WidgetType.UPCOMING, WidgetType.BIRTHDAYS -> {
            val birthdays = w.type == WidgetType.BIRTHDAYS
            val up = if (birthdays) eng.upcoming(60, 0, setOf("people")) else eng.upcoming(30)
            SectionCard(if (birthdays) "Birthdays" else "Upcoming", if (birthdays) Icons.Outlined.Cake else Icons.Outlined.NotificationsActive) {
                if (up.isEmpty()) EmptyHint(if (birthdays) "No birthdays in the next 60 days" else "Nothing in the next 30 days")
                up.take(if (birthdays) 5 else w.limit.coerceAtLeast(6)).forEach { u ->
                    val col = eng.collection(u.entry.collection)
                    Row(Modifier.fillMaxWidth().clickable { nav.openEntry(u.entry.id) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(iconFor(col?.icon), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(u.entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${u.field.name} · ${col?.name ?: ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val label = when { u.days < 0 -> "${-u.days}d overdue"; u.days == 0L -> "Today"; u.days == 1L -> "Tomorrow"; else -> "in ${u.days}d" }
                        Text(label, style = MaterialTheme.typography.labelMedium, color = if (u.days < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        WidgetType.MONEY_MONTH -> SectionCard("This month", Icons.Outlined.AccountBalanceWallet, onTitleClick = { nav.openCollection("transactions", "this_month") },
            action = { IconButton(onClick = { nav.newEntry("transactions", mapOf("kind" to JsonPrimitive("Expense"), "date" to JsonPrimitive(t.toString()))) }) { Icon(Icons.Outlined.Add, "Add transaction") } }) {
            val m = Insights.money(eng)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Income", V.fmtMoney(m.income, "$"), Modifier.weight(1f))
                StatTile("Spent", V.fmtMoney(m.expense, "$"), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Text("Net " + V.fmtMoney(m.net, "$"), style = MaterialTheme.typography.titleMedium, color = if (m.net >= 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error)
        }
        WidgetType.BUDGETS -> SectionCard("Budgets", Icons.Outlined.Savings, onTitleClick = { nav.openCollection("budgets") }) {
            val b = Insights.budgets(eng)
            if (b.isEmpty()) TextButton(onClick = { nav.newEntry("budgets") }) { Text("Set a monthly budget") }
            b.forEach { l -> ProgressLine(l.category, l.ratio, "${V.fmtMoney(l.spent, "$")} / ${V.fmtMoney(l.limit, "$")}", if (l.ratio > 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
        }
        WidgetType.SLEEP_CHART -> SectionCard("Sleep · last 7 nights", Icons.Outlined.Bedtime, onTitleClick = { nav.openCollection("sleep") },
            action = { IconButton(onClick = { nav.newEntry("sleep", mapOf("date" to JsonPrimitive(t.toString())), title = "Night of ${t}") }) { Icon(Icons.Outlined.Add, "Log sleep") } }) {
            val s = eng.series(eng.entries("sleep"), "date", "hours", Engine.Bucket.DAY, 7, Engine.Agg.AVG)
            val avg = s.mapNotNull { it.second }.takeIf { it.isNotEmpty() }?.average()
            Text(avg?.let { "Average ${V.fmtNumber(Engine.round1(it))} h" } ?: "No sleep logged this week", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            ColumnChart(s.map { (d, v) -> d.dayOfWeek.name.take(2).lowercase().replaceFirstChar { it.uppercase() } to v }, 100, MaterialTheme.colorScheme.secondary)
        }
        WidgetType.WEIGHT_CHART -> SectionCard("Weight trend", Icons.Outlined.MonitorWeight, onTitleClick = { nav.openCollection("body") }) {
            val s = eng.series(eng.entries("body"), "date", "weight", Engine.Bucket.WEEK, 12, Engine.Agg.LAST)
            LineChart(s.map { (d, v) -> d.format(DateTimeFormatter.ofPattern("d/M")) to v }, 100)
        }
        WidgetType.READING_NOW -> SectionCard("Reading now", Icons.Outlined.Book, onTitleClick = { nav.openCollection("books") }) {
            val books = eng.entries("books").filter { eng.text(it, "status") == "Reading" }
            if (books.isEmpty()) EmptyHint("Not reading anything right now")
            books.forEach { b -> ProgressLine(b.title + (eng.text(b, "author")?.let { " · $it" } ?: ""), (eng.num(b, "progress") ?: 0.0) / 100, eng.num(b, "page")?.let { "p. ${V.fmtNumber(it)}" } ?: "") { nav.openEntry(b.id) } }
        }
        WidgetType.LEARNING_PROGRESS -> SectionCard("Learning", Icons.Outlined.School, onTitleClick = { nav.openCollection("learning") }) {
            val l = eng.entries("learning").filter { eng.text(it, "status") == "In progress" }
            val rc = eng.entries("res_collections").filter { (eng.num(it, "count") ?: 0.0) > 0 && (eng.num(it, "progress") ?: 0.0) < 100 }
            if (l.isEmpty() && rc.isEmpty()) EmptyHint("No courses or collections in progress")
            l.take(4).forEach { x -> ProgressLine(x.title, (eng.num(x, "progress") ?: 0.0) / 100, "${V.fmtNumber(eng.num(x, "progress") ?: 0.0)}%") { nav.openEntry(x.id) } }
            rc.take(4).forEach { x -> ProgressLine("📚 " + x.title, (eng.num(x, "progress") ?: 0.0) / 100, "${V.fmtNumber(eng.num(x, "progress") ?: 0.0)}%") { nav.openEntry(x.id) } }
        }
        WidgetType.DAY_NOTE -> SectionCard("How's today?", Icons.Outlined.Mood, onTitleClick = { nav.navigate("today") }) { DayRatings(t) }
        WidgetType.QUICK_ADD -> {
            val c = w.collection?.let { eng.collection(it) } ?: return
            var text by remember { mutableStateOf("") }
            SectionCard(w.title ?: "Add to ${c.name}", iconFor(c.icon)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text(c.titleLabel) }, singleLine = true)
                    IconButton(enabled = text.isNotBlank(), onClick = {
                        val vals = if (c.id == "tasks") mapOf("status" to JsonPrimitive("To do"), "due" to JsonPrimitive(t.toString())) else emptyMap()
                        eng.create(c.id, text, vals); text = ""
                    }) { Icon(Icons.Outlined.Send, "Add") }
                }
            }
        }
        WidgetType.COLLECTION_LIST -> {
            val c = w.collection?.let { eng.collection(it) } ?: return
            val v = c.views.firstOrNull { it.id == w.view } ?: c.views.first()
            val list = eng.query(c.id, v)
            SectionCard(w.title ?: "${c.name} · ${v.name}", iconFor(c.icon), onTitleClick = { nav.openCollection(c.id, v.id) },
                action = { IconButton(onClick = { nav.newEntry(c.id) }) { Icon(Icons.Outlined.Add, "Add") } }) {
                if (list.isEmpty()) EmptyHint("Empty")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { list.take(w.limit).forEach { EntryRow(it, c, cardFields(c, v, 2), doneSpec(c)) } }
                if (list.size > w.limit) TextButton(onClick = { nav.openCollection(c.id, v.id) }) { Text("See all ${list.size}") }
            }
        }
        WidgetType.COUNTER -> {
            val c = w.collection?.let { eng.collection(it) } ?: return
            val v = c.views.firstOrNull { it.id == w.view } ?: c.views.first()
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { nav.openCollection(c.id, v.id) }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(iconFor(c.icon), null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(w.title ?: c.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(eng.query(c.id, v).size.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
        WidgetType.STATS_CHART -> {
            val c = w.collection?.let { eng.collection(it) } ?: return
            val v = c.views.firstOrNull { it.id == w.view && it.type == ViewType.STATS } ?: return
            SectionCard(w.title ?: "${c.name} · ${v.name}", iconFor(c.icon), onTitleClick = { nav.openCollection(c.id, v.id) }) {
                val list = eng.query(c.id, v)
                val vf = v.valueField?.let { eng.field(c.id, it) }
                val gb = v.groupBy; val dfld = v.dateField
                when {
                    gb != null -> { val d = eng.groupTotals(list, gb, vf?.key).sortedByDescending { it.second }.take(6); if (d.isEmpty()) EmptyHint("No data yet") else BarList(d) }
                    dfld != null -> {
                        val s = eng.series(list, dfld, vf?.key, Engine.Bucket.MONTH, 6, if (isAverageField(vf)) Engine.Agg.AVG else if (vf == null) Engine.Agg.COUNT else Engine.Agg.SUM)
                        ColumnChart(s.map { (d, x) -> d.format(DateTimeFormatter.ofPattern("MMM", Locale.US)) to x }, 100)
                    }
                    c.id == "life_areas" -> RadarChart(Insights.wheel(eng), 200)
                    else -> EmptyHint("No chart for this view")
                }
            }
        }
        WidgetType.QUICK_LINKS -> LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Presets.hubs) { h ->
                AssistChip(onClick = { if (h.id == "setup") nav.navigate("setup") else nav.navigate("hub/${h.id}") }, label = { Text(h.name) }, leadingIcon = { Icon(iconFor(h.icon), null, Modifier.size(16.dp)) })
            }
        }
    }
}

@Composable
fun HabitRow(h: Entry, day: java.time.LocalDate) {
    val eng = Ks.engine
    val nav = LocalNav.current
    val done = eng.habitDone(h.id, day)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { eng.toggleHabit(h.id, day) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, if (done) "Done" else "Not done",
            tint = if (done) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).clickable { nav.openEntry(h.id) }) {
            Text(h.title, style = MaterialTheme.typography.bodyLarge)
            val s = eng.streak(h.id)
            Text(if (s > 0) "🔥 $s-day streak" else (eng.text(h, "time") ?: ""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            eng.habitWeek(h.id).forEach { d -> Box(Modifier.size(8.dp).clip(CircleShape).background(if (d) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceContainerHighest)) }
        }
    }
}

@Composable
fun DayRatings(day: java.time.LocalDate) {
    val eng = Ks.engine
    val note = eng.findByDate("days", "date", day)
    listOf("mood" to "Mood", "energy" to "Energy").forEach { (k, label) ->
        val cur = note?.let { eng.num(it, k)?.toInt() } ?: 0
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.width(70.dp), style = MaterialTheme.typography.bodyMedium)
            (1..5).forEach { i ->
                val emoji = if (k == "mood") listOf("😞", "🙁", "😐", "🙂", "😄")[i - 1] else listOf("🪫", "😴", "🙂", "⚡", "🚀")[i - 1]
                Text(emoji, Modifier.clip(CircleShape).background(if (i == cur) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                    .clickable { val d = eng.ensureDay(day); eng.setValue(d.id, k, JsonPrimitive(i)) }.padding(8.dp), style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}
