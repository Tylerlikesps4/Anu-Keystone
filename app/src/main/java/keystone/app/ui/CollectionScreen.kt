// Copyright (C) 2026 Tylerlikesps4
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
package keystone.app.ui

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import keystone.app.Ks
import keystone.engine.*
import kotlinx.serialization.json.JsonPrimitive
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CollectionScreen(id: String, initialView: String?) {
    tick()
    val nav = LocalNav.current
    val eng = Ks.engine
    val c = eng.collection(id) ?: run { Screen("Missing") { EmptyHint("This collection no longer exists.") }; return }
    var viewId by rememberSaveable { mutableStateOf(initialView ?: c.views.first().id) }
    val view = c.views.firstOrNull { it.id == viewId } ?: c.views.first()
    var search by rememberSaveable { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val entries = remember(Ks.version.longValue, viewId, search) { eng.query(id, view, search) }

    Screen(c.name, actions = {
        IconButton(onClick = { searching = !searching; if (!searching) search = "" }) { Icon(Icons.Outlined.Search, "Search") }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text("Edit fields & views") }, leadingIcon = { Icon(Icons.Outlined.Tune, null) }, onClick = { menu = false; nav.navigate("schema/$id") })
                DropdownMenuItem(text = { Text("Share as CSV") }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = {
                    menu = false
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_TEXT, eng.exportCsv(id)).putExtra(Intent.EXTRA_SUBJECT, c.name + ".csv"), "Share ${c.name}"))
                })
            }
        }
    }, fab = {
        AddFab {
            // New entries inherit the active view's equality filters, e.g. "Date ideas" pre-sets kind.
            val preset = view.filters.filter { it.op == FilterOp.EQ && it.value != null }.associate { it.field to (JsonPrimitive(it.value) as kotlinx.serialization.json.JsonElement) } +
                view.filters.filter { it.op == FilterOp.TODAY || (it.op == FilterOp.DUE_WITHIN && it.value == "0") }.associate { it.field to JsonPrimitive(eng.today().toString()) }
            nav.newEntry(id, preset)
        }
    }) { pad ->
        Column(Modifier.padding(pad)) {
            if (c.description.isNotBlank() && !searching) Text(c.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            if (searching) OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), placeholder = { Text("Filter ${c.name}") }, singleLine = true)
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(c.views) { v ->
                    FilterChip(selected = v.id == view.id, onClick = { viewId = v.id }, label = { Text(v.name) }, leadingIcon = { Icon(viewIcon(v.type), null, Modifier.size(16.dp)) })
                }
            }
            Box(Modifier.weight(1f)) {
                when (view.type) {
                    ViewType.LIST -> ListView(c, view, entries)
                    ViewType.BOARD -> BoardView(c, view, entries)
                    ViewType.CALENDAR -> CalendarView(c, view, entries)
                    ViewType.GALLERY -> GalleryView(c, view, entries)
                    ViewType.TABLE -> TableView(c, view, entries)
                    ViewType.STATS -> StatsView(c, view, entries)
                }
            }
        }
    }
}

fun viewIcon(t: ViewType) = when (t) {
    ViewType.LIST -> Icons.Outlined.ViewList
    ViewType.BOARD -> Icons.Outlined.ViewKanban
    ViewType.CALENDAR -> Icons.Outlined.CalendarMonth
    ViewType.GALLERY -> Icons.Outlined.GridView
    ViewType.TABLE -> Icons.Outlined.TableChart
    ViewType.STATS -> Icons.Outlined.BarChart
}

@Composable
fun ListView(c: CollectionDef, view: ViewDef, entries: List<Entry>) {
    val fields = cardFields(c, view)
    val done = doneSpec(c)
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (entries.isEmpty()) item { EmptyHint("Nothing here yet. Tap Add to create the first one.") }
        items(entries, key = { it.id }) { e -> EntryRow(e, c, fields, done) }
    }
}

@Composable
fun BoardView(c: CollectionDef, view: ViewDef, entries: List<Entry>) {
    val eng = Ks.engine
    val nav = LocalNav.current
    val groups = eng.group(entries, view.groupBy, c.id)
    val gf = view.groupBy?.let { eng.field(c.id, it) }
    val fields = cardFields(c, view, 2)
    var moving by remember { mutableStateOf<Entry?>(null) }
    LazyRow(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(groups.keys.toList()) { g ->
            val list = groups[g].orEmpty()
            Column(Modifier.width(260.dp).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (gf != null && g != Engine.NO_VALUE) SelectChip(g, optionColor(gf, g)) else Text(g, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(6.dp))
                    Text("${list.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    if (gf != null && g != Engine.NO_VALUE && gf.type in setOf(FieldType.SELECT, FieldType.MULTI_SELECT, FieldType.CHECKBOX))
                        IconButton(onClick = {
                            val v: kotlinx.serialization.json.JsonElement = when (gf.type) {
                                FieldType.CHECKBOX -> JsonPrimitive(g == "Done")
                                FieldType.MULTI_SELECT -> V.list(listOf(g))
                                else -> JsonPrimitive(g)
                            }
                            nav.newEntry(c.id, mapOf(gf.key to v))
                        }, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.Add, "Add to $g", Modifier.size(18.dp)) }
                }
                Spacer(Modifier.height(8.dp))
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.forEach { e -> EntryRow(e, c, fields, onLongClick = if (gf?.type == FieldType.SELECT || gf?.type == FieldType.CHECKBOX) ({ moving = e }) else null) }
                    if (list.isEmpty()) Text("Empty", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
    val m = moving
    if (m != null && gf != null) {
        AlertDialog(onDismissRequest = { moving = null }, title = { Text("Move “${m.title}” to") }, text = {
            Column {
                groups.keys.filter { it != Engine.NO_VALUE }.forEach { g ->
                    TextButton(onClick = {
                        eng.setValue(m.id, gf.key, if (gf.type == FieldType.CHECKBOX) JsonPrimitive(g == "Done") else JsonPrimitive(g)); moving = null
                    }) { Text(g) }
                }
            }
        }, confirmButton = { TextButton(onClick = { moving = null }) { Text("Cancel") } })
    }
}

@Composable
fun CalendarView(c: CollectionDef, view: ViewDef, entries: List<Entry>) {
    val eng = Ks.engine
    val nav = LocalNav.current
    val df = view.dateField ?: return
    var month by rememberSaveable { mutableStateOf(YearMonth.from(eng.today()).toString()) }
    val ym = YearMonth.parse(month)
    var selected by rememberSaveable { mutableStateOf(eng.today().toString()) }
    val sel = LocalDate.parse(selected)
    val byDay = eng.calendar(entries, df)
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = ym.minusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Previous month") }
                Text(ym.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                IconButton(onClick = { month = ym.plusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Next month") }
            }
            Row { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            val first = ym.atDay(1)
            val offset = first.dayOfWeek.value - DayOfWeek.MONDAY.value
            val cells = offset + ym.lengthOfMonth()
            val rows = (cells + 6) / 7
            for (r in 0 until rows) {
                Row {
                    for (col in 0 until 7) {
                        val idx = r * 7 + col - offset + 1
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                            if (idx in 1..ym.lengthOfMonth()) {
                                val d = ym.atDay(idx)
                                val n = byDay[d]?.size ?: 0
                                val isSel = d == sel; val isToday = d == eng.today()
                                Column(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else if (n > 0) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface)
                                    .let { if (isToday) it.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else it }
                                    .clickable { selected = d.toString() },
                                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text("$idx", style = MaterialTheme.typography.bodySmall, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                                    if (n > 0) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { repeat(minOf(n, 3)) { Box(Modifier.size(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)) } }
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text(eng.fmtDate(sel), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { nav.newEntry(c.id, mapOf(df to JsonPrimitive(sel.toString()))) }) { Text("+ Add on this day") }
            }
        }
        val dayList = byDay[sel].orEmpty()
        if (dayList.isEmpty()) item { EmptyHint("Nothing on this day") }
        items(dayList, key = { it.id }) { e -> EntryRow(e, c, cardFields(c, view, 2).filter { it.key != df }, doneSpec(c)) }
    }
}

@Composable
fun GalleryView(c: CollectionDef, view: ViewDef, entries: List<Entry>) {
    val eng = Ks.engine
    val nav = LocalNav.current
    val fields = cardFields(c, view, 2)
    LazyVerticalGrid(GridCells.Adaptive(158.dp), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (entries.isEmpty()) item { EmptyHint("Nothing here yet.") }
        items(entries, key = { it.id }) { e ->
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { nav.openEntry(e.id) }) {
                Column {
                    val img = view.imageField?.let { eng.text(e, it) }
                    Box(Modifier.fillMaxWidth().height(110.dp).background(paletteColor(e.title.hashCode()).copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                        if (!img.isNullOrBlank()) AsyncImage(model = img, contentDescription = e.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Icon(iconFor(c.icon), null, Modifier.size(36.dp), tint = paletteColor(e.title.hashCode()))
                    }
                    Column(Modifier.padding(10.dp)) {
                        Text(e.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        FieldChips(e, fields)
                    }
                }
            }
        }
    }
}

@Composable
fun TableView(c: CollectionDef, view: ViewDef, entries: List<Entry>) {
    val eng = Ks.engine
    val nav = LocalNav.current
    val cols = if (view.columns.isNotEmpty()) cardFields(c, view, 99) else eng.fields(c).filter { it.type != FieldType.LONG_TEXT }.take(6)
    val h = rememberScrollState()
    Column(Modifier.fillMaxSize().horizontalScroll(h).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
        Row(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)).padding(vertical = 8.dp)) {
            Text(c.titleLabel, Modifier.width(180.dp).padding(horizontal = 8.dp), style = MaterialTheme.typography.labelLarge)
            cols.forEach { Text(it.name, Modifier.width(130.dp).padding(horizontal = 8.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1) }
        }
        entries.take(500).forEachIndexed { i, e ->
            Row(Modifier.background(if (i % 2 == 0) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface).clickable { nav.openEntry(e.id) }.padding(vertical = 10.dp)) {
                Text(e.title.ifBlank { "Untitled" }, Modifier.width(180.dp).padding(horizontal = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                cols.forEach { f -> Text(eng.display(e, f), Modifier.width(130.dp).padding(horizontal = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        if (entries.isEmpty()) EmptyHint("No rows")
        Text("${entries.size} rows", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
    }
}

fun isAverageField(f: Field?): Boolean = f != null && (f.type in setOf(FieldType.RATING, FieldType.FORMULA, FieldType.PROGRESS) || f.key in setOf("weight", "body_fat", "waist", "resting_hr", "hours", "mood"))

@Composable
fun StatsView(c: CollectionDef, view: ViewDef, entries: List<Entry>) {
    val eng = Ks.engine
    val vf = view.valueField?.let { eng.field(c.id, it) }
    val fmt: (Double) -> String = { d -> if (vf?.type == FieldType.CURRENCY || vf?.unit == "$") V.fmtMoney(d, vf.currency) else V.fmtNumber(Engine.round1(d)) }
    var bucket by rememberSaveable { mutableStateOf(Engine.Bucket.MONTH) }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Count", entries.size.toString(), Modifier.weight(1f))
                if (vf != null) {
                    StatTile(if (isAverageField(vf)) "Average" else "Total", (if (isAverageField(vf)) eng.avg(entries, vf.key) else eng.sum(entries, vf.key))?.let(fmt) ?: "–", Modifier.weight(1f))
                }
            }
        }
        if (c.id == "life_areas") item { SectionCard("Wheel of Life") { RadarChart(Insights.wheel(eng)) } }
        val gb = view.groupBy
        val dfld = view.dateField
        if (gb != null) item {
            SectionCard("By ${eng.field(c.id, gb)?.name ?: gb}") {
                val data = eng.groupTotals(entries, gb, vf?.key).sortedByDescending { it.second }
                if (data.isEmpty()) EmptyHint("No data yet") else BarList(data, fmt)
            }
        }
        if (dfld != null) item {
            SectionCard("Over time") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Engine.Bucket.entries.forEach { b -> FilterChip(selected = bucket == b, onClick = { bucket = b }, label = { Text(b.name.lowercase().replaceFirstChar { it.uppercase() } + "s") }) }
                }
                Spacer(Modifier.height(8.dp))
                val n = when (bucket) { Engine.Bucket.DAY -> 14; Engine.Bucket.WEEK -> 10; Engine.Bucket.MONTH -> 12 }
                val agg = if (vf == null) Engine.Agg.COUNT else if (isAverageField(vf)) Engine.Agg.AVG else Engine.Agg.SUM
                val s = eng.series(entries, dfld, vf?.key, bucket, n, agg)
                val pattern = when (bucket) { Engine.Bucket.DAY -> "d"; Engine.Bucket.WEEK -> "d/M"; Engine.Bucket.MONTH -> "MMM" }
                val pts = s.map { (d, v) -> d.format(DateTimeFormatter.ofPattern(pattern, Locale.US)) to v }
                if (agg == Engine.Agg.AVG && vf?.type != FieldType.RATING) LineChart(pts) else ColumnChart(pts, format = { if (vf?.type == FieldType.CURRENCY) "%.0f".format(it) else V.fmtNumber(Engine.round1(it)) })
            }
        }
        if (view.groupBy == null && view.dateField == null && vf != null && c.id != "life_areas") item {
            SectionCard(vf.name) { BarList(entries.mapNotNull { e -> eng.num(e, vf.key)?.let { e.title to it } }.sortedByDescending { it.second }, fmt) }
        }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
