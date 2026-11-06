// Keystone — an open-source life operating system.
// Copyright (C) 2026 Tylerlikesps4
// SPDX-License-Identifier: GPL-3.0-or-later
package keystone.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Every kind of property a collection can hold. */
@Serializable
enum class FieldType {
    TEXT, LONG_TEXT, NUMBER, CURRENCY, DATE, TIME, SELECT, MULTI_SELECT, CHECKBOX,
    RELATION, RATING, URL, EMAIL, PHONE, FILE, IMAGE, PROGRESS,
    /** Computed from entries in another collection that link to this one. */
    ROLLUP,
    /** Computed from this entry's own values (see [Formula]). */
    FORMULA
}

@Serializable
data class SelectOption(val name: String, val color: Int = 0)

@Serializable
enum class RollupOp { COUNT, COUNT_CHECKED, PERCENT_CHECKED, PERCENT_SELECT, SUM, AVG }

/**
 * Aggregates entries of [source] whose relation field [via] points at the entry being viewed.
 * [field] is the field aggregated (checkbox/number/select) and [value] the select value for PERCENT_SELECT.
 */
@Serializable
data class Rollup(
    val source: String,
    val via: String,
    val op: RollupOp,
    val field: String? = null,
    val value: String? = null,
)

@Serializable
data class Field(
    val key: String,
    val name: String,
    val type: FieldType,
    val options: List<SelectOption> = emptyList(),
    /** Target collection id for RELATION. */
    val target: String? = null,
    val multi: Boolean = true,
    val rollup: Rollup? = null,
    /** Formula expression for FORMULA, e.g. `pct(saved,target)`. */
    val formula: String? = null,
    /** Max stars for RATING. */
    val max: Int = 5,
    val currency: String = "$",
    /** Suffix shown after numbers / computed values ("kg", "%", "h"). */
    val unit: String = "",
    /** DATE fields flagged as reminders appear in "Upcoming" and notifications. */
    val remind: Boolean = false,
    /** Reminder repeats every year on the same day (birthdays, anniversaries). */
    val annual: Boolean = false,
    val hidden: Boolean = false,
) {
    val computed: Boolean get() = type == FieldType.ROLLUP || type == FieldType.FORMULA
}

@Serializable
enum class ViewType { LIST, BOARD, CALENDAR, GALLERY, TABLE, STATS }

@Serializable
enum class FilterOp {
    EQ, NEQ, CONTAINS, GT, LT, GTE, LTE, EMPTY, NOT_EMPTY, CHECKED, UNCHECKED,
    /** Date on or before today + N days (so it includes overdue). */
    DUE_WITHIN,
    /** Date within the last N days, up to today. */
    IN_LAST_DAYS,
    TODAY,
    /** Date in the current calendar month. */
    THIS_MONTH,
    /** Relation/multi-select contains value. */
    HAS,
}

@Serializable
data class Filter(val field: String, val op: FilterOp, val value: String? = null)

@Serializable
data class Sort(val field: String, val desc: Boolean = false)

@Serializable
data class ViewDef(
    val id: String,
    val name: String,
    val type: ViewType,
    val groupBy: String? = null,
    val dateField: String? = null,
    val filters: List<Filter> = emptyList(),
    val sorts: List<Sort> = emptyList(),
    /** Numeric field summed/averaged by STATS views. */
    val valueField: String? = null,
    val imageField: String? = null,
    /** Fields shown on cards / table columns. Empty = first few fields. */
    val columns: List<String> = emptyList(),
)

@Serializable
data class CollectionDef(
    val id: String,
    val name: String,
    val icon: String,
    val hub: String,
    val description: String = "",
    val fields: List<Field>,
    val views: List<ViewDef>,
    val titleLabel: String = "Name",
    /** When true the engine adds Area / Project / Tags relations (PARA). */
    val paraLinks: Boolean = true,
    /** Preset collections are recreated by "restore presets"; user ones are not. */
    val preset: Boolean = false,
    /** If set: a SELECT field + DATE field pair that makes "done" entries repeat. */
    val recurrence: Recurrence? = null,
)

/** When [statusField] becomes [doneValue] (or checkbox is checked), a copy is created with [dateField] moved by [repeatField]. */
@Serializable
data class Recurrence(val repeatField: String, val dateField: String, val statusField: String, val doneValue: String? = null)

@Serializable
data class Entry(
    val id: String,
    val collection: String,
    val title: String,
    val values: Map<String, JsonElement> = emptyMap(),
    val created: Long,
    val updated: Long,
    val archived: Boolean = false,
)

@Serializable
data class Hub(val id: String, val name: String, val icon: String, val description: String, val collections: List<String>)

@Serializable
enum class WidgetType {
    GREETING, VISION, WHEEL_OF_LIFE, CORE_VALUES, GOALS, HABITS_TODAY, TODAY_TASKS, PROJECTS,
    UPCOMING, MONEY_MONTH, BUDGETS, SLEEP_CHART, WEIGHT_CHART, READING_NOW, DAY_NOTE, QUICK_ADD,
    COLLECTION_LIST, COUNTER, STATS_CHART, LEARNING_PROGRESS, BIRTHDAYS, QUICK_LINKS,
}

@Serializable
data class Widget(
    val id: String,
    val type: WidgetType,
    val collection: String? = null,
    val view: String? = null,
    val limit: Int = 5,
    val title: String? = null,
)

@Serializable
data class Dashboard(val id: String, val name: String, val widgets: List<Widget>)

/** Full backup document. */
@Serializable
data class Backup(
    val format: String = "keystone-backup",
    val version: Int = 1,
    val exportedAt: Long,
    val collections: List<CollectionDef>,
    val entries: List<Entry>,
    val settings: Map<String, String>,
)
