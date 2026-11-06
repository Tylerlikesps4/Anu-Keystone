// Copyright (C) 2026 Tylerlikesps4
// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
package keystone.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import keystone.app.Ks
import keystone.engine.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun EntryScreen(id: String, isNew: Boolean) {
    tick()
    val nav = LocalNav.current
    val eng = Ks.engine
    val ctx = LocalContext.current
    val e = eng.entry(id) ?: run { Screen("Deleted") { EmptyHint("This item was deleted.") }; return }
    val c = eng.collection(e.collection) ?: return
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var title by remember(id) { mutableStateOf(e.title) }

    // Remove brand-new entries the user left completely blank.
    DisposableEffect(id) {
        onDispose {
            val cur = eng.entry(id)
            val stillOpen = nav.currentBackStack.value.any { it.destination.route?.startsWith("entry/") == true && it.arguments?.getString("id") == id }
            if (!stillOpen && isNew && cur != null && cur.title.isBlank() && cur.values.values.all { V.isEmpty(it) || it == JsonPrimitive(false) }) eng.delete(id)
        }
    }

    Screen(c.name, actions = {
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text(if (e.archived) "Restore from archive" else "Archive") }, leadingIcon = { Icon(Icons.Outlined.Archive, null) },
                    onClick = { menu = false; eng.archive(id, !e.archived); if (!e.archived) nav.popBackStack() })
                DropdownMenuItem(text = { Text("Duplicate") }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                    onClick = { menu = false; eng.duplicate(id)?.let { nav.openEntry(it.id) } })
                DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = {
                    menu = false
                    val text = buildString {
                        appendLine(e.title)
                        eng.fields(c).forEach { f -> eng.display(e, f).takeIf { it.isNotEmpty() }?.let { appendLine("${f.name}: $it") } }
                    }
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share"))
                })
                DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; confirmDelete = true })
            }
        }
    }) { pad ->
        LazyColumn(Modifier.padding(pad).imePadding(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                TextField(title, { title = it; eng.rename(id, it) }, Modifier.fillMaxWidth(), placeholder = { Text(c.titleLabel) },
                    textStyle = TextStyle(fontSize = 24.sp), colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface))
                if (e.archived) AssistChip(onClick = { eng.archive(id, false) }, label = { Text("Archived · tap to restore") })
            }
            items(eng.fields(c).filter { !it.hidden }, key = { it.key }) { f -> FieldEditor(e, f) }
            item { LinkedAdders(e, c) }
            item { Backlinks(e) }
        }
    }
    if (confirmDelete) ConfirmDialog("Delete “${e.title.ifBlank { "Untitled" }}”?", "This can't be undone. Links to it will be removed. Archive instead to keep it.",
        onDismiss = { confirmDelete = false }) { nav.popBackStack(); eng.delete(id) }
}

/** Quick “add linked item” buttons for collections that roll up into this one (tasks for a project, resources for a collection…). */
@Composable
fun LinkedAdders(e: Entry, c: CollectionDef) {
    val nav = LocalNav.current
    val eng = Ks.engine
    val pairs = c.fields.mapNotNull { it.rollup }.map { it.source to it.via }.distinct().filter { (s, _) -> eng.hasCollection(s) }
    val extra = when (c.id) { "areas" -> listOf("tasks" to F_AREA, "projects" to F_AREA); "tags" -> listOf("tasks" to F_TAGS, "resources" to F_TAGS); else -> emptyList() }
    val all = (pairs + extra).distinct()
    if (all.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        all.forEach { (src, via) ->
            val sc = eng.collection(src) ?: return@forEach
            AssistChip(onClick = { nav.newEntry(src, mapOf(via to V.list(listOf(e.id)))) }, label = { Text("+ ${sc.titleLabel} in ${sc.name}") }, leadingIcon = { Icon(iconFor(sc.icon), null, Modifier.size(16.dp)) })
        }
    }
}

@Composable
fun Backlinks(e: Entry) {
    val eng = Ks.engine
    val links = eng.backlinks(e.id)
    if (links.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Linked here (${links.size})", style = MaterialTheme.typography.titleSmall)
        links.groupBy { it.first.collection }.forEach { (cid, list) ->
            val c = eng.collection(cid) ?: return@forEach
            Text("${c.name} · ${list.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            list.take(30).forEach { (le, _) -> EntryRow(le, c, cardFields(c, null, 2), doneSpec(c)) }
        }
    }
}

@Composable
fun FieldLabel(f: Field) {
    Text(f.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
}

@Composable
fun FieldEditor(e: Entry, f: Field) {
    val eng = Ks.engine
    val ctx = LocalContext.current
    fun set(v: JsonElement?) { eng.setValue(e.id, f.key, v) }
    Column {
        when (f.type) {
            FieldType.TEXT, FieldType.EMAIL, FieldType.PHONE, FieldType.URL -> {
                var t by remember(e.id, f.key) { mutableStateOf(eng.text(e, f.key) ?: "") }
                OutlinedTextField(t, { t = it; set(if (it.isBlank()) null else JsonPrimitive(it)) }, Modifier.fillMaxWidth(), label = { Text(f.name) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = when (f.type) { FieldType.EMAIL -> KeyboardType.Email; FieldType.PHONE -> KeyboardType.Phone; FieldType.URL -> KeyboardType.Uri; else -> KeyboardType.Text }),
                    trailingIcon = {
                        val v = eng.text(e, f.key)
                        if (!v.isNullOrBlank() && f.type != FieldType.TEXT) IconButton(onClick = {
                            val uri = when (f.type) { FieldType.EMAIL -> "mailto:$v"; FieldType.PHONE -> "tel:$v"; else -> v }
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
                        }) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Open") }
                    })
            }
            FieldType.LONG_TEXT -> {
                var t by remember(e.id, f.key) { mutableStateOf(eng.text(e, f.key) ?: "") }
                OutlinedTextField(t, { t = it; set(if (it.isBlank()) null else JsonPrimitive(it)) }, Modifier.fillMaxWidth().heightIn(min = 96.dp), label = { Text(f.name) })
            }
            FieldType.NUMBER, FieldType.CURRENCY -> {
                var t by remember(e.id, f.key) { mutableStateOf(eng.num(e, f.key)?.let { V.fmtNumber(it) } ?: "") }
                OutlinedTextField(t, { s -> t = s; set(s.replace(",", "").toDoubleOrNull()?.let { JsonPrimitive(it) }) }, Modifier.fillMaxWidth(), label = { Text(f.name) }, singleLine = true,
                    prefix = if (f.type == FieldType.CURRENCY) ({ Text(f.currency) }) else null,
                    suffix = if (f.unit.isNotEmpty()) ({ Text(f.unit) }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
            FieldType.DATE -> DateField(f.name, eng.date(e, f.key)) { set(it?.let { d -> JsonPrimitive(d.toString()) }) }
            FieldType.TIME -> TimeField(f.name, eng.text(e, f.key)) { set(it?.let { t -> JsonPrimitive(t) }) }
            FieldType.SELECT -> {
                FieldLabel(f)
                val cur = eng.text(e, f.key)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    f.options.forEach { o ->
                        FilterChip(selected = cur == o.name, onClick = { set(if (cur == o.name) null else JsonPrimitive(o.name)) }, label = { Text(o.name) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = paletteColor(o.color).copy(alpha = 0.3f)))
                    }
                    NewOptionChip(f) { set(JsonPrimitive(it)) }
                }
            }
            FieldType.MULTI_SELECT -> {
                FieldLabel(f)
                val cur = eng.list(e, f.key)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    f.options.forEach { o ->
                        val on = o.name in cur
                        FilterChip(selected = on, onClick = { set(V.list(if (on) cur - o.name else cur + o.name)) }, label = { Text(o.name) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = paletteColor(o.color).copy(alpha = 0.3f)))
                    }
                    NewOptionChip(f) { set(V.list(cur + it)) }
                }
            }
            FieldType.CHECKBOX -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { set(JsonPrimitive(!eng.bool(e, f.key))) }) {
                Text(f.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(eng.bool(e, f.key), { set(JsonPrimitive(it)) })
            }
            FieldType.RATING -> {
                FieldLabel(f)
                val cur = eng.num(e, f.key)?.toInt() ?: 0
                Row {
                    (1..f.max).forEach { i ->
                        Icon(if (i <= cur) Icons.Outlined.Star else Icons.Outlined.StarOutline, "$i of ${f.max}",
                            tint = if (i <= cur) Amber else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(if (f.max > 5) 28.dp else 34.dp).clickable { set(if (cur == i) null else JsonPrimitive(i)) }.padding(2.dp))
                    }
                }
            }
            FieldType.PROGRESS -> {
                FieldLabel(f)
                var p by remember(e.id, f.key) { mutableFloatStateOf((eng.num(e, f.key) ?: 0.0).toFloat()) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(p, { p = it }, Modifier.weight(1f), valueRange = 0f..100f, steps = 19, onValueChangeFinished = { set(JsonPrimitive(p.toDouble())) })
                    Text("${p.toInt()}%", Modifier.width(48.dp))
                }
            }
            FieldType.RELATION -> RelationField(e, f)
            FieldType.FILE, FieldType.IMAGE -> FileField(e, f)
            FieldType.ROLLUP, FieldType.FORMULA -> {
                val s = eng.display(e, f)
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Functions, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(f.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(s.ifEmpty { "—" }, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
fun NewOptionChip(f: Field, onAdd: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    AssistChip(onClick = { open = true }, label = { Text("+") })
    if (open) TextInputDialog("New ${f.name} option", label = "Option", onDismiss = { open = false }) { onAdd(it) }
}

@Composable
fun DateField(label: String, value: LocalDate?, onChange: (LocalDate?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val eng = Ks.engine
    OutlinedCard(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Event, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value?.let { eng.fmtDate(it) + "  ·  " + it } ?: "Not set", style = MaterialTheme.typography.bodyLarge)
            }
            if (value != null) IconButton(onClick = { onChange(null) }) { Icon(Icons.Outlined.Close, "Clear") }
        }
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (value ?: eng.today()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }; open = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { onChange(eng.today()); open = false }) { Text("Today") } }) {
            DatePicker(state)
        }
    }
}

@Composable
fun TimeField(label: String, value: String?, onChange: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedCard(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value ?: "Not set", style = MaterialTheme.typography.bodyLarge)
            }
            if (value != null) IconButton(onClick = { onChange(null) }) { Icon(Icons.Outlined.Close, "Clear") }
        }
    }
    if (open) {
        val t = V.parseTime(value)
        val state = rememberTimePickerState(initialHour = t?.hour ?: 22, initialMinute = t?.minute ?: 0, is24Hour = true)
        AlertDialog(onDismissRequest = { open = false }, text = { TimePicker(state) },
            confirmButton = { TextButton(onClick = { onChange("%02d:%02d".format(state.hour, state.minute)); open = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } })
    }
}

@Composable
fun RelationField(e: Entry, f: Field) {
    val eng = Ks.engine
    val nav = LocalNav.current
    var picking by remember { mutableStateOf(false) }
    val ids = eng.list(e, f.key)
    FieldLabel(f)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ids.mapNotNull { eng.entry(it) }.forEach { t ->
            InputChip(selected = false, onClick = { nav.openEntry(t.id) }, label = { Text(t.title.ifBlank { "Untitled" }) },
                trailingIcon = { Icon(Icons.Outlined.Close, "Unlink", Modifier.size(16.dp).clickable { eng.setValue(e.id, f.key, V.list(ids - t.id)) }) })
        }
        AssistChip(onClick = { picking = true }, label = { Text(if (ids.isEmpty()) "Link ${eng.collection(f.target ?: "")?.titleLabel ?: "item"}" else "+") }, leadingIcon = { Icon(Icons.Outlined.Link, null, Modifier.size(16.dp)) })
    }
    if (picking) RelationPicker(f, ids, onDismiss = { picking = false }) { newIds -> eng.setValue(e.id, f.key, V.list(newIds)) }
}

@Composable
fun RelationPicker(f: Field, selected: List<String>, onDismiss: () -> Unit, onChange: (List<String>) -> Unit) {
    val eng = Ks.engine
    val target = eng.collection(f.target ?: "") ?: return
    var q by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf(selected) }
    val options = remember(q, Ks.version.longValue) { eng.query(target.id, null, q).sortedBy { it.title.lowercase() } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Link ${target.name}") }, text = {
        Column {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search or create") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                if (q.isNotBlank() && options.none { it.title.equals(q.trim(), true) }) item {
                    TextButton(onClick = {
                        val n = eng.create(target.id, q.trim())
                        sel = if (f.multi) sel + n.id else listOf(n.id); onChange(sel); q = ""
                        if (!f.multi) onDismiss()
                    }) { Text("+ Create “${q.trim()}”") }
                }
                items(options, key = { it.id }) { o ->
                    val on = o.id in sel
                    Row(Modifier.fillMaxWidth().clickable {
                        sel = if (f.multi) (if (on) sel - o.id else sel + o.id) else (if (on) emptyList() else listOf(o.id))
                        onChange(sel)
                        if (!f.multi && !on) onDismiss()
                    }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (f.multi) Checkbox(on, null) else RadioButton(on, null)
                        Spacer(Modifier.width(8.dp))
                        Text(o.title.ifBlank { "Untitled" })
                    }
                }
                if (options.isEmpty() && q.isBlank()) item { EmptyHint("No ${target.name.lowercase()} yet. Type a name to create one.") }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}

@Composable
fun FileField(e: Entry, f: Field) {
    val eng = Ks.engine
    val ctx = LocalContext.current
    val v = eng.text(e, f.key)
    var linkDialog by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            eng.setValue(e.id, f.key, JsonPrimitive(uri.toString()))
        }
    }
    FieldLabel(f)
    if (f.type == FieldType.IMAGE && !v.isNullOrBlank()) {
        AsyncImage(model = v, contentDescription = f.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(14.dp)))
        Spacer(Modifier.height(6.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!v.isNullOrBlank() && f.type == FieldType.FILE) {
            AssistChip(onClick = {
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(v)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
            }, label = { Text(eng.display(e, f).take(28)) }, leadingIcon = { Icon(Icons.Outlined.Description, null, Modifier.size(16.dp)) })
        }
        OutlinedButton(onClick = { picker.launch(if (f.type == FieldType.IMAGE) arrayOf("image/*") else arrayOf("*/*")) }) { Text(if (v.isNullOrBlank()) "Choose" else "Replace") }
        if (f.type == FieldType.IMAGE) TextButton(onClick = { linkDialog = true }) { Text("Paste link") }
        if (!v.isNullOrBlank()) IconButton(onClick = { eng.setValue(e.id, f.key, null) }) { Icon(Icons.Outlined.Close, "Remove") }
    }
    if (linkDialog) TextInputDialog("Image link", initial = v ?: "", label = "https://…", onDismiss = { linkDialog = false }) { eng.setValue(e.id, f.key, JsonPrimitive(it)) }
}
