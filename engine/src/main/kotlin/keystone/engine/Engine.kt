// Copyright (C) 2026 Tylerlikesps4
// SPDX-License-Identifier: GPL-3.0-or-later
package keystone.engine

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

val KJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false; prettyPrint = false }

const val F_AREA = "_area"
const val F_PROJECT = "_project"
const val F_TAGS = "_tags"
const val TITLE = "_title"

/** Select values that mean "finished"; used by Upcoming and recurrence. */
val DONE_WORDS = setOf("done", "completed", "finished", "achieved", "given", "bought", "accepted", "rejected", "watched", "dropped", "abandoned", "100%", "cancelled")

class Engine(
    private val store: Store,
    val today: () -> LocalDate = { LocalDate.now() },
    val now: () -> Long = { System.currentTimeMillis() },
    private val newId: () -> String = { UUID.randomUUID().toString().replace("-", "").take(16) },
) {
    private val cols = linkedMapOf<String, CollectionDef>()
    private val entries = linkedMapOf<String, Entry>()
    private val settings = linkedMapOf<String, String>()
    var version: Long = 0; private set
    var onChange: (() -> Unit)? = null

    init { reload() }

    fun reload() {
        cols.clear(); entries.clear(); settings.clear()
        store.loadCollections().forEach { cols[it.id] = it }
        store.loadEntries().forEach { entries[it.id] = it }
        settings.putAll(store.loadSettings())
        changed()
    }

    private fun changed() { version++; onChange?.invoke() }

    // ---------------------------------------------------------------- collections

    fun collections(): List<CollectionDef> = cols.values.toList()
    fun collection(id: String): CollectionDef? = cols[id]
    fun hasCollection(id: String) = cols.containsKey(id)

    /** Schema fields plus the PARA/tag links the engine adds automatically. */
    fun fields(c: CollectionDef): List<Field> {
        if (!c.paraLinks) return c.fields
        val extra = mutableListOf<Field>()
        if (c.id != "areas" && cols.containsKey("areas") && c.fields.none { it.key == F_AREA })
            extra += Field(F_AREA, "Area", FieldType.RELATION, target = "areas", multi = false)
        if (c.id != "projects" && c.id != "areas" && cols.containsKey("projects") && c.fields.none { it.key == F_PROJECT })
            extra += Field(F_PROJECT, "Project", FieldType.RELATION, target = "projects", multi = false)
        if (c.id != "tags" && cols.containsKey("tags") && c.fields.none { it.key == F_TAGS })
            extra += Field(F_TAGS, "Tags", FieldType.RELATION, target = "tags", multi = true)
        return c.fields + extra
    }
    fun fields(collectionId: String): List<Field> = cols[collectionId]?.let { fields(it) } ?: emptyList()
    fun field(collectionId: String, key: String): Field? = fields(collectionId).firstOrNull { it.key == key }

    fun upsertCollection(c: CollectionDef) {
        require(c.id.isNotBlank()) { "collection id required" }
        val keys = c.fields.map { it.key }
        require(keys.size == keys.toSet().size) { "duplicate field keys in ${c.id}" }
        cols[c.id] = c; store.saveCollection(c); changed()
    }

    fun createCollection(name: String, icon: String = "folder", hub: String = "custom"): CollectionDef {
        var id = slug(name).ifEmpty { "collection" }
        var n = 2
        while (cols.containsKey(id)) id = slug(name) + "_" + n++
        val c = CollectionDef(
            id, name.trim(), icon, hub, fields = listOf(
                Field("notes", "Notes", FieldType.LONG_TEXT),
                Field("date", "Date", FieldType.DATE),
                Field("status", "Status", FieldType.SELECT, options = opts("To do", "In progress", "Done")),
            ),
            views = listOf(
                ViewDef("all", "All", ViewType.LIST),
                ViewDef("board", "Board", ViewType.BOARD, groupBy = "status"),
                ViewDef("calendar", "Calendar", ViewType.CALENDAR, dateField = "date"),
                ViewDef("table", "Table", ViewType.TABLE),
            ),
        )
        upsertCollection(c); return c
    }

    fun deleteCollection(id: String) {
        store.transaction {
            entries.values.filter { it.collection == id }.forEach { entries.remove(it.id); store.deleteEntry(it.id) }
            cols.remove(id); store.deleteCollection(id)
        }
        changed()
    }

    fun addField(collectionId: String, name: String, type: FieldType, configure: (Field) -> Field = { it }): Field {
        val c = cols[collectionId] ?: error("no collection $collectionId")
        var key = slug(name).ifEmpty { "field" }
        var n = 2
        val existing = fields(c).map { it.key }.toSet()
        while (key in existing || key.startsWith("_")) key = slug(name).trimStart('_') + "_" + n++
        val f = configure(Field(key, name.trim(), type))
        upsertCollection(c.copy(fields = c.fields + f))
        return f
    }

    fun updateField(collectionId: String, field: Field) {
        val c = cols[collectionId] ?: return
        upsertCollection(c.copy(fields = c.fields.map { if (it.key == field.key) field else it }))
    }

    fun removeField(collectionId: String, key: String) {
        val c = cols[collectionId] ?: return
        upsertCollection(c.copy(fields = c.fields.filterNot { it.key == key }, views = c.views.map { v ->
            v.copy(groupBy = v.groupBy.takeIf { it != key }, dateField = v.dateField.takeIf { it != key },
                valueField = v.valueField.takeIf { it != key }, columns = v.columns - key,
                filters = v.filters.filterNot { it.field == key }, sorts = v.sorts.filterNot { it.field == key })
        }))
        val touched = entries.values.filter { it.collection == collectionId && it.values.containsKey(key) }
            .map { it.copy(values = it.values - key) }
        touched.forEach { entries[it.id] = it }
        store.saveEntries(touched)
        changed()
    }

    fun upsertView(collectionId: String, view: ViewDef) {
        val c = cols[collectionId] ?: return
        val views = if (c.views.any { it.id == view.id }) c.views.map { if (it.id == view.id) view else it } else c.views + view
        upsertCollection(c.copy(views = views))
    }

    fun removeView(collectionId: String, viewId: String) {
        val c = cols[collectionId] ?: return
        if (c.views.size <= 1) return
        upsertCollection(c.copy(views = c.views.filterNot { it.id == viewId }))
    }

    // ---------------------------------------------------------------- entries

    fun entry(id: String): Entry? = entries[id]
    fun allEntries(includeArchived: Boolean = false): List<Entry> = entries.values.filter { includeArchived || !it.archived }
    fun entries(collectionId: String, includeArchived: Boolean = false): List<Entry> =
        entries.values.filter { it.collection == collectionId && (includeArchived || !it.archived) }
    fun archived(): List<Entry> = entries.values.filter { it.archived }.sortedByDescending { it.updated }

    fun create(collectionId: String, title: String, values: Map<String, JsonElement> = emptyMap()): Entry {
        val c = cols[collectionId] ?: error("no collection $collectionId")
        val t = now()
        val e = Entry(newId(), c.id, title.trim(), normalize(c, values), t, t)
        entries[e.id] = e; store.saveEntry(e); changed()
        return e
    }

    /** Saves an edited entry. Handles recurrence; returns the newly spawned occurrence, if any. */
    fun update(e: Entry): Entry? {
        val c = cols[e.collection] ?: error("no collection ${e.collection}")
        val before = entries[e.id]
        val saved = e.copy(values = normalize(c, e.values), updated = now(), title = e.title.trim())
        entries[saved.id] = saved; store.saveEntry(saved)
        val spawned = c.recurrence?.let { r -> spawnRecurrence(c, r, before, saved) }
        changed()
        return spawned
    }

    fun setValue(entryId: String, key: String, value: JsonElement?): Entry? {
        val e = entries[entryId] ?: return null
        val nv = if (value == null || value is JsonNull) e.values - key else e.values + (key to value)
        update(e.copy(values = nv))
        return entries[entryId]
    }

    fun rename(entryId: String, title: String) { entries[entryId]?.let { update(it.copy(title = title)) } }

    fun archive(entryId: String, archived: Boolean = true) {
        val e = entries[entryId] ?: return
        val s = e.copy(archived = archived, updated = now()); entries[s.id] = s; store.saveEntry(s); changed()
    }

    /** Deletes an entry and removes every relation that pointed at it. */
    fun delete(entryId: String) {
        store.transaction {
            entries.remove(entryId) ?: return@transaction
            store.deleteEntry(entryId)
            val fixed = mutableListOf<Entry>()
            for (e in entries.values) {
                val c = cols[e.collection] ?: continue
                var vals = e.values
                for (f in fields(c)) if (f.type == FieldType.RELATION) {
                    val ids = V.asList(vals[f.key])
                    if (entryId in ids) vals = vals + (f.key to V.list(ids - entryId))
                }
                if (vals !== e.values) fixed += e.copy(values = vals)
            }
            fixed.forEach { entries[it.id] = it }
            store.saveEntries(fixed)
        }
        changed()
    }

    fun duplicate(entryId: String): Entry? {
        val e = entries[entryId] ?: return null
        return create(e.collection, e.title + " (copy)", e.values)
    }

    /** Coerces raw values into the shape each field expects; drops unknown/computed keys. */
    fun normalize(c: CollectionDef, raw: Map<String, JsonElement>): Map<String, JsonElement> {
        val out = linkedMapOf<String, JsonElement>()
        val fs = fields(c)
        var schema = c
        for ((k, v) in raw) {
            val f = fs.firstOrNull { it.key == k } ?: continue
            if (f.computed) continue
            val cv = coerce(f, v) ?: continue
            if (f.type == FieldType.SELECT || f.type == FieldType.MULTI_SELECT) {
                // Like Notion: a value that is not an option yet becomes one.
                val names = V.asList(cv).ifEmpty { listOfNotNull(V.asString(cv)) }
                val missing = names.filter { n -> schema.fields.firstOrNull { it.key == k }?.options?.none { it.name == n } == true }
                if (missing.isNotEmpty()) {
                    schema = schema.copy(fields = schema.fields.map { if (it.key == k) it.copy(options = it.options + missing.mapIndexed { i, n -> SelectOption(n, (it.options.size + i) % 10) }) else it })
                }
            }
            out[k] = cv
        }
        if (schema !== c) { cols[c.id] = schema; store.saveCollection(schema) }
        return out
    }

    fun coerce(f: Field, v: JsonElement): JsonElement? {
        if (V.isEmpty(v) && f.type != FieldType.CHECKBOX) return null
        return when (f.type) {
            FieldType.TEXT, FieldType.LONG_TEXT, FieldType.PHONE, FieldType.FILE, FieldType.IMAGE -> V.asString(v)?.let { JsonPrimitive(it) }
            FieldType.EMAIL -> V.asString(v)?.trim()?.let { JsonPrimitive(it) }
            FieldType.URL -> V.asString(v)?.trim()?.let { JsonPrimitive(if (it.contains("://") || it.startsWith("mailto:")) it else "https://$it") }
            FieldType.NUMBER, FieldType.CURRENCY -> V.asDouble(v)?.let { JsonPrimitive(it) }
            FieldType.RATING -> V.asDouble(v)?.let { JsonPrimitive(it.coerceIn(0.0, f.max.toDouble()).toInt()) }
            FieldType.PROGRESS -> V.asDouble(v)?.let { JsonPrimitive(it.coerceIn(0.0, 100.0)) }
            FieldType.DATE -> V.asDate(v)?.let { JsonPrimitive(it.toString()) }
            FieldType.TIME -> V.parseTime(V.asString(v))?.let { JsonPrimitive(String.format(Locale.US, "%02d:%02d", it.hour, it.minute)) }
            FieldType.CHECKBOX -> JsonPrimitive(V.asBool(v))
            FieldType.SELECT -> V.asString(v)?.trim()?.takeIf { it.isNotEmpty() }?.let { JsonPrimitive(it) }
            FieldType.MULTI_SELECT -> V.list(V.asList(v).distinct())
            FieldType.RELATION -> {
                val ids = V.asList(v).distinct().filter { id -> entries[id]?.let { f.target == null || it.collection == f.target } == true }
                if (!f.multi && ids.size > 1) V.list(listOf(ids.last())) else V.list(ids)
            }
            FieldType.ROLLUP, FieldType.FORMULA -> null
        }
    }

    private fun spawnRecurrence(c: CollectionDef, r: Recurrence, before: Entry?, after: Entry): Entry? {
        fun isDone(e: Entry?): Boolean {
            if (e == null) return false
            val f = fields(c).firstOrNull { it.key == r.statusField } ?: return false
            return if (f.type == FieldType.CHECKBOX) V.asBool(e.values[f.key])
            else V.asString(e.values[f.key])?.let { s -> if (r.doneValue != null) s == r.doneValue else s.lowercase() in DONE_WORDS } == true
        }
        if (!isDone(after) || isDone(before) || before == null) return null
        val repeat = V.asString(after.values[r.repeatField]) ?: return null
        val date = V.asDate(after.values[r.dateField]) ?: today()
        val next = nextOccurrence(date, repeat) ?: return null
        val sf = fields(c).first { it.key == r.statusField }
        val reset: JsonElement = if (sf.type == FieldType.CHECKBOX) JsonPrimitive(false) else sf.options.firstOrNull()?.let { JsonPrimitive(it.name) } ?: JsonNull
        val vals = after.values + (r.dateField to JsonPrimitive(next.toString())) + (r.statusField to reset)
        val t = now()
        val spawned = Entry(newId(), c.id, after.title, normalize(c, vals.filterValues { it !is JsonNull }), t, t)
        entries[spawned.id] = spawned; store.saveEntry(spawned)
        return spawned
    }

    fun nextOccurrence(d: LocalDate, repeat: String): LocalDate? = when (repeat.lowercase()) {
        "daily" -> d.plusDays(1)
        "weekdays" -> { var n = d.plusDays(1); while (n.dayOfWeek == DayOfWeek.SATURDAY || n.dayOfWeek == DayOfWeek.SUNDAY) n = n.plusDays(1); n }
        "weekly" -> d.plusWeeks(1)
        "biweekly", "every 2 weeks" -> d.plusWeeks(2)
        "monthly" -> d.plusMonths(1)
        "quarterly" -> d.plusMonths(3)
        "every 6 months", "biannually" -> d.plusMonths(6)
        "yearly", "annually" -> d.plusYears(1)
        else -> null
    }

    // ---------------------------------------------------------------- reading values

    fun value(e: Entry, key: String): JsonElement? {
        if (key == TITLE) return JsonPrimitive(e.title)
        val f = field(e.collection, key) ?: return e.values[key]
        return when (f.type) {
            FieldType.ROLLUP -> rollup(e, f)?.let { JsonPrimitive(it) }
            FieldType.FORMULA -> formula(e, f)?.let { JsonPrimitive(it) }
            else -> e.values[key]
        }
    }

    fun num(e: Entry, key: String): Double? = V.asDouble(value(e, key))
    fun text(e: Entry, key: String): String? = V.asString(value(e, key))
    fun date(e: Entry, key: String): LocalDate? = V.asDate(value(e, key))
    fun bool(e: Entry, key: String): Boolean = V.asBool(value(e, key))
    fun list(e: Entry, key: String): List<String> = V.asList(value(e, key))

    fun relationTitles(e: Entry, key: String): List<String> = list(e, key).mapNotNull { entries[it]?.title }

    fun rollup(e: Entry, f: Field): Double? {
        val r = f.rollup ?: return null
        val src = entries.values.filter { it.collection == r.source && !it.archived && e.id in V.asList(it.values[r.via]) }
        return when (r.op) {
            RollupOp.COUNT -> src.size.toDouble()
            RollupOp.COUNT_CHECKED -> src.count { V.asBool(it.values[r.field ?: ""]) }.toDouble()
            RollupOp.PERCENT_CHECKED -> if (src.isEmpty()) 0.0 else round1(100.0 * src.count { V.asBool(it.values[r.field ?: ""]) } / src.size)
            RollupOp.PERCENT_SELECT -> if (src.isEmpty()) 0.0 else round1(100.0 * src.count { V.asString(it.values[r.field ?: ""]) == r.value } / src.size)
            RollupOp.SUM -> src.sumOf { num(it, r.field ?: "") ?: 0.0 }
            RollupOp.AVG -> src.mapNotNull { num(it, r.field ?: "") }.takeIf { it.isNotEmpty() }?.average()?.let { round1(it) }
        }
    }

    fun formula(e: Entry, f: Field): Double? { val x = f.formula ?: return null; return Formula.eval(x, e, this) }

    /** Human-readable value for a field. */
    fun display(e: Entry, f: Field): String {
        val v = value(e, f.key)
        if (V.isEmpty(v) && f.type != FieldType.CHECKBOX) return ""
        return when (f.type) {
            FieldType.CURRENCY -> V.asDouble(v)?.let { V.fmtMoney(it, f.currency) } ?: ""
            FieldType.NUMBER -> V.asDouble(v)?.let { V.fmtNumber(it) + if (f.unit.isNotEmpty()) " " + f.unit else "" } ?: ""
            FieldType.PROGRESS -> V.asDouble(v)?.let { V.fmtNumber(it) + "%" } ?: ""
            FieldType.RATING -> V.asDouble(v)?.let { "★".repeat(it.toInt()) + "☆".repeat((f.max - it.toInt()).coerceAtLeast(0)) } ?: ""
            FieldType.CHECKBOX -> if (V.asBool(v)) "✓ " + f.name else ""
            FieldType.DATE -> V.asDate(v)?.let { fmtDate(it) } ?: ""
            FieldType.RELATION -> relationTitles(e, f.key).joinToString(", ")
            FieldType.MULTI_SELECT -> V.asList(v).joinToString(", ")
            FieldType.ROLLUP -> V.asDouble(v)?.let { d ->
                when (f.rollup?.op) {
                    RollupOp.PERCENT_CHECKED, RollupOp.PERCENT_SELECT -> V.fmtNumber(d) + "%"
                    else -> V.fmtNumber(d) + if (f.unit.isNotEmpty()) " " + f.unit else ""
                }
            } ?: ""
            FieldType.FORMULA -> V.asDouble(v)?.let { d ->
                if (f.unit == "$" || f.unit == "money") V.fmtMoney(d, f.currency) else V.fmtNumber(d) + if (f.unit.isNotEmpty()) (if (f.unit == "%") "%" else " " + f.unit) else ""
            } ?: ""
            FieldType.FILE, FieldType.IMAGE -> V.asString(v)?.substringAfterLast('/')?.substringAfterLast("%2F") ?: ""
            else -> V.asString(v) ?: ""
        }
    }

    fun fmtDate(d: LocalDate): String {
        val t = today()
        return when (ChronoUnit.DAYS.between(t, d)) {
            0L -> "Today"; 1L -> "Tomorrow"; -1L -> "Yesterday"
            else -> d.format(DateTimeFormatter.ofPattern(if (d.year == t.year) "EEE, MMM d" else "MMM d, yyyy", Locale.US))
        }
    }

    /** Entries in other collections whose relation fields point at [entryId]. */
    fun backlinks(entryId: String): List<Pair<Entry, Field>> {
        val out = mutableListOf<Pair<Entry, Field>>()
        for (e in entries.values) {
            if (e.archived) continue
            val c = cols[e.collection] ?: continue
            for (f in fields(c)) if (f.type == FieldType.RELATION && entryId in V.asList(e.values[f.key])) out += e to f
        }
        return out.sortedByDescending { it.first.updated }
    }

    // ---------------------------------------------------------------- views & queries

    fun query(collectionId: String, view: ViewDef?, search: String = "", includeArchived: Boolean = false): List<Entry> {
        var list = entries(collectionId, includeArchived)
        if (view != null) for (flt in view.filters) list = list.filter { matches(it, flt) }
        if (search.isNotBlank()) {
            val q = search.trim().lowercase()
            val fs = fields(collectionId)
            list = list.filter { e -> e.title.lowercase().contains(q) || fs.any { f -> !f.computed && display(e, f).lowercase().contains(q) } }
        }
        val sorts = view?.sorts.orEmpty().ifEmpty { listOf(Sort("_updated", desc = true)) }
        return sortEntries(list, sorts)
    }

    fun sortEntries(list: List<Entry>, sorts: List<Sort>): List<Entry> {
        var cmp: Comparator<Entry> = Comparator { _, _ -> 0 }
        for (s in sorts) {
            val c = Comparator<Entry> { a, b ->
                val ka = sortKey(a, s.field); val kb = sortKey(b, s.field)
                when {
                    ka == null && kb == null -> 0
                    ka == null -> 1   // empties last regardless of direction
                    kb == null -> -1
                    else -> { val r = compareValues(ka, kb); if (s.desc) -r else r }
                }
            }
            cmp = cmp.then(c)
        }
        return list.sortedWith(cmp)
    }

    private fun sortKey(e: Entry, key: String): Comparable<*>? = when (key) {
        TITLE -> e.title.lowercase()
        "_created" -> e.created
        "_updated" -> e.updated
        else -> {
            val f = field(e.collection, key)
            when (f?.type) {
                FieldType.NUMBER, FieldType.CURRENCY, FieldType.RATING, FieldType.PROGRESS, FieldType.ROLLUP, FieldType.FORMULA -> num(e, key)
                FieldType.DATE -> date(e, key)
                FieldType.CHECKBOX -> if (bool(e, key)) 1 else 0
                FieldType.SELECT -> f.options.indexOfFirst { it.name == text(e, key) }.takeIf { it >= 0 }
                else -> text(e, key)?.lowercase()?.takeIf { it.isNotEmpty() }
            }
        }
    }

    fun matches(e: Entry, flt: Filter): Boolean {
        val f = field(e.collection, flt.field)
        val raw = value(e, flt.field)
        val t = today()
        return when (flt.op) {
            FilterOp.EMPTY -> V.isEmpty(raw)
            FilterOp.NOT_EMPTY -> !V.isEmpty(raw)
            FilterOp.CHECKED -> V.asBool(raw)
            FilterOp.UNCHECKED -> !V.asBool(raw)
            FilterOp.TODAY -> V.asDate(raw) == t
            FilterOp.THIS_MONTH -> V.asDate(raw)?.let { it.year == t.year && it.month == t.month } == true
            FilterOp.DUE_WITHIN -> V.asDate(raw)?.let { !it.isAfter(t.plusDays(flt.value?.toLongOrNull() ?: 0)) } == true
            FilterOp.IN_LAST_DAYS -> V.asDate(raw)?.let { !it.isAfter(t) && !it.isBefore(t.minusDays(flt.value?.toLongOrNull() ?: 7)) } == true
            FilterOp.HAS -> flt.value != null && flt.value in V.asList(raw)
            FilterOp.CONTAINS -> (if (f?.type == FieldType.RELATION) display(e, f) else V.asString(raw) ?: "").lowercase().contains(flt.value?.lowercase() ?: "")
            FilterOp.EQ, FilterOp.NEQ -> {
                val eq = when (f?.type) {
                    FieldType.CHECKBOX -> V.asBool(raw) == V.asBool(JsonPrimitive(flt.value ?: ""))
                    FieldType.MULTI_SELECT, FieldType.RELATION -> flt.value in V.asList(raw)
                    FieldType.DATE -> V.asDate(raw) == resolveDate(flt.value)
                    FieldType.NUMBER, FieldType.CURRENCY, FieldType.RATING, FieldType.PROGRESS, FieldType.ROLLUP, FieldType.FORMULA ->
                        V.asDouble(raw) == flt.value?.toDoubleOrNull()
                    else -> (V.asString(raw) ?: "") == (flt.value ?: "")
                }
                if (flt.op == FilterOp.EQ) eq else !eq
            }
            FilterOp.GT, FilterOp.LT, FilterOp.GTE, FilterOp.LTE -> {
                val c: Int? = if (f?.type == FieldType.DATE) {
                    val a = V.asDate(raw); val b = resolveDate(flt.value)
                    if (a == null || b == null) null else a.compareTo(b)
                } else {
                    val a = V.asDouble(raw); val b = flt.value?.toDoubleOrNull()
                    if (a == null || b == null) null else a.compareTo(b)
                }
                c != null && when (flt.op) { FilterOp.GT -> c > 0; FilterOp.LT -> c < 0; FilterOp.GTE -> c >= 0; else -> c <= 0 }
            }
        }
    }

    private fun resolveDate(s: String?): LocalDate? = when {
        s == null -> null
        s == "today" -> today()
        s.startsWith("today+") -> s.removePrefix("today+").toLongOrNull()?.let { today().plusDays(it) }
        s.startsWith("today-") -> s.removePrefix("today-").toLongOrNull()?.let { today().minusDays(it) }
        else -> V.parseDate(s)
    }

    /** Groups entries for a board view, in option order, with an empty group last. */
    fun group(list: List<Entry>, groupBy: String?, collectionId: String? = null): LinkedHashMap<String, List<Entry>> {
        val out = linkedMapOf<String, MutableList<Entry>>()
        if (groupBy.isNullOrEmpty()) { out["All"] = list.toMutableList(); return LinkedHashMap(out) }
        val colId = collectionId ?: list.firstOrNull()?.collection
        val f = colId?.let { field(it, groupBy) }
        if (f != null && (f.type == FieldType.SELECT || f.type == FieldType.MULTI_SELECT)) f.options.forEach { out[it.name] = mutableListOf() }
        if (f?.type == FieldType.CHECKBOX) { out["Not done"] = mutableListOf(); out["Done"] = mutableListOf() }
        for (e in list) {
            val keys: List<String> = when (f?.type) {
                FieldType.MULTI_SELECT -> list(e, groupBy)
                FieldType.RELATION -> relationTitles(e, groupBy)
                FieldType.CHECKBOX -> listOf(if (bool(e, groupBy)) "Done" else "Not done")
                null -> emptyList()
                else -> listOfNotNull(display(e, f).takeIf { it.isNotEmpty() })
            }.ifEmpty { listOf(NO_VALUE) }
            for (k in keys) out.getOrPut(k) { mutableListOf() }.add(e)
        }
        out[NO_VALUE]?.let { v -> out.remove(NO_VALUE); if (v.isNotEmpty()) out[NO_VALUE] = v }
        return LinkedHashMap(out)
    }

    fun calendar(list: List<Entry>, dateField: String): Map<LocalDate, List<Entry>> =
        list.mapNotNull { e -> date(e, dateField)?.let { it to e } }.groupBy({ it.first }, { it.second })

    // ---------------------------------------------------------------- stats

    fun sum(list: List<Entry>, key: String): Double = list.sumOf { num(it, key) ?: 0.0 }
    fun avg(list: List<Entry>, key: String): Double? = list.mapNotNull { num(it, key) }.takeIf { it.isNotEmpty() }?.average()

    /** Count (or sum of [valueField]) per group. */
    fun groupTotals(list: List<Entry>, groupBy: String, valueField: String? = null): List<Pair<String, Double>> =
        group(list, groupBy).map { (k, v) -> k to if (valueField == null) v.size.toDouble() else sum(v, valueField) }.filter { it.second != 0.0 }

    enum class Bucket { DAY, WEEK, MONTH }
    enum class Agg { SUM, AVG, COUNT, LAST }

    /** Time series ending today: last [n] buckets of [dateField], aggregating [valueField]. */
    fun series(list: List<Entry>, dateField: String, valueField: String?, bucket: Bucket, n: Int, agg: Agg = Agg.SUM): List<Pair<LocalDate, Double?>> {
        val t = today()
        fun start(d: LocalDate) = when (bucket) {
            Bucket.DAY -> d
            Bucket.WEEK -> d.minusDays((d.dayOfWeek.value - 1).toLong())
            Bucket.MONTH -> d.withDayOfMonth(1)
        }
        val buckets = (n - 1 downTo 0).map { i ->
            when (bucket) { Bucket.DAY -> t.minusDays(i.toLong()); Bucket.WEEK -> start(t).minusWeeks(i.toLong()); Bucket.MONTH -> start(t).minusMonths(i.toLong()) }
        }
        val grouped = list.mapNotNull { e -> date(e, dateField)?.let { start(it) to e } }.groupBy({ it.first }, { it.second })
        return buckets.map { b ->
            val es = grouped[b].orEmpty()
            val v: Double? = when (agg) {
                Agg.COUNT -> es.size.toDouble()
                Agg.SUM -> if (valueField == null) es.size.toDouble() else sum(es, valueField)
                Agg.AVG -> valueField?.let { avg(es, it) }
                Agg.LAST -> valueField?.let { vf -> es.sortedBy { date(it, dateField) }.lastOrNull()?.let { num(it, vf) } }
            }
            b to v
        }
    }

    // ---------------------------------------------------------------- search & tags

    fun search(q: String, limit: Int = 100): List<Entry> {
        if (q.isBlank()) return emptyList()
        val s = q.trim().lowercase()
        return entries.values.asSequence().filter { !it.archived }.map { e ->
            val score = when {
                e.title.lowercase() == s -> 3
                e.title.lowercase().startsWith(s) -> 2
                e.title.lowercase().contains(s) -> 1
                e.values.values.any { v -> V.asString(v)?.lowercase()?.contains(s) == true } -> 0
                else -> -1
            }
            e to score
        }.filter { it.second >= 0 }.sortedWith(compareByDescending<Pair<Entry, Int>> { it.second }.thenByDescending { it.first.updated })
            .map { it.first }.take(limit).toList()
    }

    // ---------------------------------------------------------------- settings

    fun setting(key: String): String? = settings[key]
    fun setSetting(key: String, value: String?) {
        if (value == null) settings.remove(key) else settings[key] = value
        store.setSetting(key, value); changed()
    }

    fun dashboards(): List<Dashboard> = settings["dashboards"]?.let {
        runCatching { KJson.decodeFromString<List<Dashboard>>(it) }.getOrNull()
    } ?: Presets.dashboards()

    fun saveDashboards(list: List<Dashboard>) = setSetting("dashboards", KJson.encodeToString(list))

    // ---------------------------------------------------------------- periodic notes, habits

    fun findByDate(collectionId: String, key: String, d: LocalDate): Entry? =
        entries.values.firstOrNull { it.collection == collectionId && V.asDate(it.values[key]) == d }

    /** Returns (creating if needed) the Day note for [d], linked to its Month and Year notes. */
    fun ensureDay(d: LocalDate): Entry {
        val year = findByDate("years", "date", d.withDayOfYear(1))
            ?: create("years", d.year.toString(), mapOf("date" to V.date(d.withDayOfYear(1))))
        val m1 = d.withDayOfMonth(1)
        val month = findByDate("months", "date", m1)
            ?: create("months", d.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)), mapOf("date" to V.date(m1), "year" to V.list(listOf(year.id))))
        return findByDate("days", "date", d)
            ?: create("days", d.format(DateTimeFormatter.ofPattern("EEE, MMM d yyyy", Locale.US)), mapOf("date" to V.date(d), "month" to V.list(listOf(month.id))))
    }

    fun habitDone(habitId: String, d: LocalDate): Boolean =
        findByDate("days", "date", d)?.let { habitId in V.asList(it.values["habits"]) } == true

    fun toggleHabit(habitId: String, d: LocalDate): Boolean {
        val day = ensureDay(d)
        val ids = V.asList(day.values["habits"])
        val done = habitId !in ids
        setValue(day.id, "habits", V.list(if (done) ids + habitId else ids - habitId))
        return done
    }

    /** Consecutive days done, counting back from today (today not yet done doesn't break it). */
    fun streak(habitId: String): Int {
        var d = today()
        if (!habitDone(habitId, d)) d = d.minusDays(1)
        var n = 0
        while (habitDone(habitId, d)) { n++; d = d.minusDays(1) }
        return n
    }

    fun habitWeek(habitId: String): List<Boolean> = (6 downTo 0).map { habitDone(habitId, today().minusDays(it.toLong())) }

    // ---------------------------------------------------------------- upcoming / reminders

    data class Upcoming(val entry: Entry, val field: Field, val date: LocalDate, val days: Long)

    fun isDone(e: Entry): Boolean {
        val c = cols[e.collection] ?: return false
        for (f in c.fields) {
            if (f.type == FieldType.CHECKBOX && f.key in setOf("done", "bought", "completed", "packed")) if (V.asBool(e.values[f.key])) return true
            if (f.type == FieldType.SELECT && f.key == "status") if (V.asString(e.values[f.key])?.lowercase() in DONE_WORDS) return true
        }
        return false
    }

    /** Reminder dates (fields flagged `remind`) from [overdueDays] ago through [days] ahead. Annual dates roll forward. */
    fun upcoming(days: Long = 30, overdueDays: Long = 7, collections: Set<String>? = null): List<Upcoming> {
        val t = today()
        val out = mutableListOf<Upcoming>()
        for (e in entries.values) {
            if (e.archived) continue
            if (collections != null && e.collection !in collections) continue
            val c = cols[e.collection] ?: continue
            for (f in c.fields) {
                if (f.type != FieldType.DATE || !f.remind) continue
                var d = V.asDate(e.values[f.key]) ?: continue
                if (f.annual) d = nextAnniversary(d, t)
                else if (isDone(e)) continue
                val diff = ChronoUnit.DAYS.between(t, d)
                if (diff in -overdueDays..days) out += Upcoming(e, f, d, diff)
            }
        }
        return out.sortedBy { it.date }
    }

    fun nextAnniversary(d: LocalDate, from: LocalDate): LocalDate {
        var n = safeWithYear(d, from.year)
        if (n.isBefore(from)) n = safeWithYear(d, from.year + 1)
        return n
    }

    private fun safeWithYear(d: LocalDate, y: Int): LocalDate =
        if (d.monthValue == 2 && d.dayOfMonth == 29 && !java.time.Year.isLeap(y.toLong())) LocalDate.of(y, 2, 28) else d.withYear(y)

    // ---------------------------------------------------------------- backup

    fun exportJson(pretty: Boolean = true): String {
        val b = Backup(exportedAt = now(), collections = collections(), entries = entries.values.toList(),
            settings = settings.filterKeys { !it.startsWith(PROTECTED_SETTING_PREFIX) })
        val j = if (pretty) Json(KJson) { prettyPrint = true } else KJson
        return j.encodeToString(b)
    }

    data class ImportResult(val collections: Int, val entries: Int, val settings: Int)

    /** Imports a backup. [replace] wipes everything first; otherwise merges (newer `updated` wins). */
    fun importJson(json: String, replace: Boolean): ImportResult {
        require(json.length <= MAX_IMPORT_CHARS) { "Backup is too large (over ${MAX_IMPORT_CHARS / 1_000_000} MB)" }
        require(maxNesting(json) <= MAX_JSON_DEPTH) { "Backup is malformed (nested too deeply)" }
        val b = try { KJson.decodeFromString<Backup>(json) } catch (e: Exception) { throw IllegalArgumentException("Not a Keystone backup: ${e.message}") }
        require(b.format == "keystone-backup") { "Not a Keystone backup (format=${b.format})" }
        require(b.version <= 1) { "Backup is from a newer version of Keystone (v${b.version})" }
        require(b.collections.size <= MAX_COLLECTIONS) { "Backup has too many collections (${b.collections.size})" }
        require(b.entries.size <= MAX_ENTRIES) { "Backup has too many items (${b.entries.size})" }
        val idOk = Regex("^[A-Za-z0-9_.:-]{1,80}$")
        b.collections.forEach { require(idOk.matches(it.id)) { "Backup has an invalid collection id" } }
        b.entries.forEach { require(idOk.matches(it.id)) { "Backup has an invalid item id" } }
        val knownCols = b.collections.map { it.id }.toSet() + (if (replace) emptySet() else cols.keys)
        b.entries.forEach { require(it.collection in knownCols) { "Backup has an item in an unknown collection" } }
        val safeSettings = b.settings.filterKeys { k -> !k.startsWith(PROTECTED_SETTING_PREFIX) && k.length <= 80 }
        var nc = 0; var ne = 0
        store.transaction {
            if (replace) store.clearAll()
            val existingCols = if (replace) emptyMap() else cols.toMap()
            for (c in b.collections) { if (replace || existingCols[c.id] != c) { store.saveCollection(c); nc++ } }
            val existing = if (replace) emptyMap() else entries.toMap()
            val toSave = b.entries.filter { e -> val cur = existing[e.id]; cur == null || e.updated >= cur.updated }
            store.saveEntries(toSave); ne = toSave.size
            for ((k, v) in safeSettings) store.setSetting(k, v)
        }
        reload()
        return ImportResult(nc, ne, safeSettings.size)
    }

    /** CSV of one collection with every visible field (RFC 4180 quoting). */
    fun exportCsv(collectionId: String): String {
        val c = cols[collectionId] ?: return ""
        val fs = fields(c)
        fun q(raw: String): String {
            val s = csvSafe(raw)
            return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
        }
        val sb = StringBuilder()
        sb.append((listOf(c.titleLabel) + fs.map { it.name }).joinToString(",") { q(it) }).append('\n')
        for (e in entries(collectionId)) sb.append((listOf(e.title) + fs.map { display(e, it) }).joinToString(",") { q(it) }).append('\n')
        return sb.toString()
    }

    // ---------------------------------------------------------------- presets

    /** Installs every preset collection that is missing (never touches existing ones). Returns how many were added. */
    fun installPresets(seed: Boolean = true): Int {
        var n = 0
        val fresh = cols.isEmpty()
        store.transaction {
            for (c in Presets.collections()) if (!cols.containsKey(c.id)) { cols[c.id] = c; store.saveCollection(c); n++ }
        }
        if (fresh && seed) Presets.seed(this)
        changed()
        return n
    }

    companion object {
        const val NO_VALUE = "No value"
        /** Settings with this prefix (security state) never leave the device and are never imported. */
        const val PROTECTED_SETTING_PREFIX = "sec_"
        const val MAX_IMPORT_CHARS = 50_000_000
        const val MAX_JSON_DEPTH = 64
        const val MAX_COLLECTIONS = 2_000
        const val MAX_ENTRIES = 500_000
        private val PLAIN_NUMBER = Regex("^-?\\d+(\\.\\d+)?$")
        /** Neutralises spreadsheet formula injection: a cell starting with = + - @ tab or CR is prefixed with '. */
        fun csvSafe(s: String): String =
            if (s.isNotEmpty() && s[0] in "=+-@\t\r" && !PLAIN_NUMBER.matches(s)) "'" + s else s
        /** Deepest {}/[] nesting outside strings, scanned without building a tree. */
        fun maxNesting(json: String): Int {
            var depth = 0; var max = 0; var inStr = false; var esc = false
            for (ch in json) {
                if (inStr) { if (esc) esc = false else if (ch == '\\') esc = true else if (ch == '"') inStr = false; continue }
                when (ch) { '"' -> inStr = true; '{', '[' -> { depth++; if (depth > max) max = depth }; '}', ']' -> depth-- }
            }
            return max
        }
        fun slug(s: String) = s.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(40)
        fun opts(vararg names: String) = names.mapIndexed { i, n -> SelectOption(n, i % 10) }
        fun round1(d: Double) = Math.round(d * 10) / 10.0
    }
}
