// Copyright (C) 2026 Tylerlikesps4
// SPDX-License-Identifier: GPL-3.0-or-later
package keystone.engine

import kotlinx.serialization.json.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

/** Small, null-safe helpers for reading and writing JSON field values. */
object V {
    fun str(v: String?): JsonElement = if (v.isNullOrEmpty()) JsonNull else JsonPrimitive(v)
    fun num(v: Number?): JsonElement = if (v == null) JsonNull else JsonPrimitive(v)
    fun bool(v: Boolean): JsonElement = JsonPrimitive(v)
    fun list(v: List<String>): JsonElement = JsonArray(v.map { JsonPrimitive(it) })
    fun date(d: LocalDate?): JsonElement = str(d?.toString())

    fun asString(e: JsonElement?): String? = when (e) {
        null, JsonNull -> null
        is JsonPrimitive -> e.contentOrNull
        is JsonArray -> e.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString(", ")
        is JsonObject -> e.toString()
    }

    fun asDouble(e: JsonElement?): Double? = when (e) {
        is JsonPrimitive -> if (e.isString) e.content.trim().replace(",", "").toDoubleOrNull() else e.doubleOrNull ?: e.booleanOrNull?.let { if (it) 1.0 else 0.0 }
        else -> null
    }

    fun asBool(e: JsonElement?): Boolean = when (e) {
        is JsonPrimitive -> e.booleanOrNull ?: (e.contentOrNull?.lowercase() in setOf("true", "yes", "1", "done", "x"))
        else -> false
    }

    fun asList(e: JsonElement?): List<String> = when (e) {
        is JsonArray -> e.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.filter { it.isNotEmpty() }
        is JsonPrimitive -> e.contentOrNull?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        else -> emptyList()
    }

    fun asDate(e: JsonElement?): LocalDate? = parseDate(asString(e))

    fun parseDate(s: String?): LocalDate? {
        if (s.isNullOrBlank()) return null
        return try { LocalDate.parse(s.trim().take(10)) } catch (_: DateTimeParseException) { null }
    }

    fun parseTime(s: String?): LocalTime? {
        if (s.isNullOrBlank()) return null
        val t = s.trim()
        return try {
            val parts = t.split(":")
            if (parts.size < 2) return null
            LocalTime.of(parts[0].toInt(), parts[1].take(2).toInt())
        } catch (_: Exception) { null }
    }

    fun isEmpty(e: JsonElement?): Boolean = when (e) {
        null, JsonNull -> true
        is JsonPrimitive -> e.contentOrNull.isNullOrBlank()
        is JsonArray -> e.isEmpty()
        is JsonObject -> e.isEmpty()
    }

    fun fmtNumber(d: Double): String =
        if (d == Math.floor(d) && !d.isInfinite() && Math.abs(d) < 1e15) d.toLong().toString()
        else String.format(java.util.Locale.US, "%.2f", d).trimEnd('0').trimEnd('.')

    fun fmtMoney(d: Double, symbol: String): String {
        val neg = d < 0
        val s = String.format(java.util.Locale.US, "%,.2f", Math.abs(d))
        return (if (neg) "-" else "") + symbol + s
    }
}
