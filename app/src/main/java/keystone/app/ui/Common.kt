// Copyright (C) 2026 Tylerlikesps4
// SPDX-License-Identifier: GPL-3.0-or-later
package keystone.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import keystone.app.Ks
import keystone.engine.*
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.cos
import kotlin.math.sin

val LocalNav = staticCompositionLocalOf<NavHostController> { error("no nav") }

/** Display string for a field by key ("" if the field was removed). */
fun disp(e: Entry, key: String): String = Ks.engine.field(e.collection, key)?.let { Ks.engine.display(e, it) } ?: ""

/** Read inside a composable to recompose whenever the engine changes. */
@Composable
fun tick(): Long = Ks.version.longValue

fun NavHostController.openEntry(id: String) = navigate("entry/$id")
fun NavHostController.openCollection(id: String, view: String? = null) = navigate("col/$id" + (view?.let { "?view=$it" } ?: ""))

/** Creates a blank entry (optionally pre-linked) and opens it. Blank entries are removed when left empty. */
fun NavHostController.newEntry(collection: String, values: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(), title: String = "") {
    val e = Ks.engine.create(collection, title, values)
    navigate("entry/${e.id}?new=true")
}

@Composable
fun SelectChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text, maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelMedium, color = color,
        modifier = modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Fields shown on a card/table for a view. */
fun cardFields(c: CollectionDef, view: ViewDef?, max: Int = 3): List<Field> {
    val all = Ks.engine.fields(c)
    if (view != null && view.columns.isNotEmpty()) return view.columns.mapNotNull { k -> all.firstOrNull { it.key == k } }
    return all.filter { !it.hidden && it.type !in setOf(FieldType.LONG_TEXT, FieldType.FILE, FieldType.IMAGE, FieldType.URL) && !it.key.startsWith("_") && it.key != view?.groupBy }.take(max)
}

/** How (if at all) entries of a collection can be ticked off from a list. */
class DoneSpec(val isDone: (Entry) -> Boolean, val toggle: (Entry) -> Unit)

fun doneSpec(c: CollectionDef): DoneSpec? {
    val eng = Ks.engine
    c.fields.firstOrNull { it.type == FieldType.CHECKBOX && it.key in setOf("done", "bought", "learned", "completed") }?.let { f ->
        return DoneSpec({ eng.bool(it, f.key) }, { e -> eng.setValue(e.id, f.key, JsonPrimitive(!eng.bool(e, f.key))) })
    }
    val status = c.fields.firstOrNull { it.key == "status" && it.type == FieldType.SELECT } ?: return null
    val done = status.options.firstOrNull { it.name.lowercase() in setOf("done", "completed", "finished", "achieved") } ?: return null
    val first = status.options.first().name
    return DoneSpec({ eng.text(it, "status") == done.name }, { e ->
        eng.setValue(e.id, "status", JsonPrimitive(if (eng.text(e, "status") == done.name) first else done.name))
    })
}

fun optionColor(f: Field, value: String): Color = paletteColor(f.options.firstOrNull { it.name == value }?.color ?: value.hashCode())

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FieldChips(e: Entry, fields: List<Field>) {
    val eng = Ks.engine
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (f in fields) {
            when (f.type) {
                FieldType.SELECT -> eng.text(e, f.key)?.let { SelectChip(it, optionColor(f, it)) }
                FieldType.MULTI_SELECT -> eng.list(e, f.key).take(3).forEach { SelectChip(it, optionColor(f, it)) }
                FieldType.PROGRESS, FieldType.ROLLUP, FieldType.FORMULA -> {
                    val s = eng.display(e, f)
                    if (s.isNotEmpty()) {
                        val pct = f.type == FieldType.PROGRESS || f.unit == "%" || f.rollup?.op in setOf(RollupOp.PERCENT_CHECKED, RollupOp.PERCENT_SELECT)
                        if (pct) MiniProgress((eng.num(e, f.key) ?: 0.0) / 100.0, s)
                        else MetaText(f.name + " " + s)
                    }
                }
                FieldType.CHECKBOX -> if (eng.bool(e, f.key)) MetaText("✓ " + f.name)
                FieldType.DATE -> eng.display(e, f).takeIf { it.isNotEmpty() }?.let { d ->
                    val overdue = eng.date(e, f.key)?.isBefore(eng.today()) == true && f.remind && !eng.isDone(e)
                    MetaText(d, icon = Icons.Outlined.Event, color = if (overdue) MaterialTheme.colorScheme.error else null)
                }
                else -> eng.display(e, f).takeIf { it.isNotEmpty() }?.let { MetaText(it) }
            }
        }
    }
}

@Composable
fun MetaText(text: String, icon: ImageVector? = null, color: Color? = null) {
    val c = color ?: MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, Modifier.size(13.dp), tint = c); Spacer(Modifier.width(3.dp)) }
        Text(text, style = MaterialTheme.typography.labelMedium, color = c, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun MiniProgress(fraction: Double, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LinearProgressIndicator(progress = { fraction.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.width(56.dp).height(5.dp).clip(CircleShape), drawStopIndicator = {})
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntryRow(e: Entry, c: CollectionDef, fields: List<Field>, done: DoneSpec? = null, onLongClick: (() -> Unit)? = null, showCollection: Boolean = false) {
    val nav = LocalNav.current
    Surface(
        shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).combinedClickable(onClick = { nav.openEntry(e.id) }, onLongClick = onLongClick),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (done != null) {
                val d = done.isDone(e)
                IconButton(onClick = { done.toggle(e) }, modifier = Modifier.size(32.dp)) {
                    Icon(if (d) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, if (d) "Mark not done" else "Mark done",
                        tint = if (d) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline)
                }
                Spacer(Modifier.width(6.dp))
            } else if (showCollection) {
                Icon(iconFor(c.icon), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(e.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = if (done?.isDone?.invoke(e) == true) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                if (showCollection) Text(c.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (fields.isNotEmpty()) { Spacer(Modifier.height(4.dp)); FieldChips(e, fields) }
            }
        }
    }
}

@Composable
fun SectionCard(title: String, icon: ImageVector? = null, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null, onTitleClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).let { m -> if (onTitleClick != null) m.combinedClickableCompat(onTitleClick) else m })
                action?.invoke()
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickableCompat(onClick: () -> Unit) = this.combinedClickable(onClick = onClick)

@Composable
fun EmptyHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
}

@Composable
fun ProgressLine(label: String, fraction: Double, trailing: String, color: Color = MaterialTheme.colorScheme.primary, onClick: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().let { if (onClick != null) it.combinedClickableCompat(onClick) else it }.padding(vertical = 5.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(progress = { fraction.toFloat().coerceIn(0f, 1f) }, color = color, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), drawStopIndicator = {})
    }
}

// ---------------------------------------------------------------- charts (drawn with Canvas; no chart library)

@Composable
fun BarList(data: List<Pair<String, Double>>, format: (Double) -> String = { V.fmtNumber(it) }) {
    val max = data.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        data.forEachIndexed { i, (k, v) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(k, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(110.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.weight(1f).height(14.dp)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth((v / max).toFloat().coerceIn(0.02f, 1f)).clip(RoundedCornerShape(4.dp)).background(paletteColor(i + 1)))
                }
                Text(format(v), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp).widthIn(min = 44.dp))
            }
        }
    }
}

@Composable
fun ColumnChart(points: List<Pair<String, Double?>>, height: Int = 120, color: Color = MaterialTheme.colorScheme.primary, format: (Double) -> String = { V.fmtNumber(it) }) {
    val max = points.mapNotNull { it.second }.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column {
        Row(Modifier.fillMaxWidth().height(height.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            points.forEach { (_, v) ->
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                    if (v != null && v > 0) {
                        Text(format(v), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        Box(Modifier.fillMaxWidth().fillMaxHeight((v / max).toFloat().coerceIn(0.03f, 0.85f)).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(color))
                    } else Box(Modifier.fillMaxWidth().height(2.dp).background(grid))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            points.forEach { (k, _) -> Text(k, fontSize = 9.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
    }
}

@Composable
fun LineChart(points: List<Pair<String, Double?>>, height: Int = 120, color: Color = MaterialTheme.colorScheme.primary) {
    val vals = points.mapNotNull { it.second }
    if (vals.isEmpty()) { EmptyHint("No data yet"); return }
    val min = vals.min(); val max = vals.max()
    val span = (max - min).takeIf { it > 0 } ?: 1.0
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column {
        Canvas(Modifier.fillMaxWidth().height(height.dp)) {
            val stepX = if (points.size > 1) size.width / (points.size - 1) else size.width
            for (i in 0..3) { val y = size.height * i / 3f; drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f) }
            val path = Path(); var started = false
            points.forEachIndexed { i, (_, v) ->
                if (v == null) return@forEachIndexed
                val x = i * stepX; val y = (size.height * (1 - (v - min) / span)).toFloat() * 0.85f + size.height * 0.075f
                if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
                drawCircle(color, 5f, Offset(x, y))
            }
            drawPath(path, color, style = Stroke(width = 4f))
        }
        Row(Modifier.fillMaxWidth()) {
            Text(points.first().first, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("${V.fmtNumber(min)} – ${V.fmtNumber(max)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(points.last().first, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
}

/** Wheel of Life radar: current (filled) vs target (outline), scale 0-10. */
@Composable
fun RadarChart(slices: List<Insights.WheelSlice>, size: Int = 240) {
    if (slices.size < 3) { EmptyHint("Add at least three life areas"); return }
    val cur = MaterialTheme.colorScheme.primary
    val tgt = MaterialTheme.colorScheme.secondary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Canvas(Modifier.size(size.dp)) {
            val cx = this.size.width / 2; val cy = this.size.height / 2; val r = minOf(cx, cy) * 0.92f
            val n = slices.size
            fun pt(i: Int, v: Double): Offset {
                val a = -Math.PI / 2 + 2 * Math.PI * i / n
                return Offset(cx + (r * v / 10 * cos(a)).toFloat(), cy + (r * v / 10 * sin(a)).toFloat())
            }
            for (ring in 1..5) {
                val p = Path(); for (i in 0 until n) { val o = pt(i, ring * 2.0); if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y) }; p.close()
                drawPath(p, grid, style = Stroke(1.5f))
            }
            for (i in 0 until n) drawLine(grid, Offset(cx, cy), pt(i, 10.0), 1.5f)
            fun poly(sel: (Insights.WheelSlice) -> Double): Path { val p = Path(); slices.forEachIndexed { i, s -> val o = pt(i, sel(s)); if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y) }; p.close(); return p }
            drawPath(poly { it.target }, tgt, style = Stroke(3f))
            drawPath(poly { it.current }, cur.copy(alpha = 0.35f))
            drawPath(poly { it.current }, cur, style = Stroke(4f))
        }
        Spacer(Modifier.height(8.dp))
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            slices.forEach { s -> Text("${s.name} ${V.fmtNumber(s.current)}/${V.fmtNumber(s.target)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(cur)); Text("  Now   ", style = MaterialTheme.typography.labelSmall)
            Box(Modifier.size(10.dp).clip(CircleShape).border(2.dp, tgt, CircleShape)); Text("  Target", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String = "Delete", onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun TextInputDialog(title: String, initial: String = "", label: String = "Name", onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var t by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(t, { t = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(enabled = t.isNotBlank(), onClick = { onDone(t.trim()); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
