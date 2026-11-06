// Copyright (C) 2026 Tylerlikesps4
// SPDX-License-Identifier: GPL-3.0-or-later
package keystone.engine

import java.time.LocalDate

/** Cross-collection calculations used by dashboard widgets. */
object Insights {
    data class Money(val income: Double, val expense: Double) { val net get() = income - expense }

    fun money(e: Engine, month: LocalDate = e.today()): Money {
        var inc = 0.0; var exp = 0.0
        for (t in e.entries("transactions")) {
            val d = e.date(t, "date") ?: continue
            if (d.year != month.year || d.month != month.month) continue
            val amt = e.num(t, "amount") ?: continue
            if (e.text(t, "kind") == "Income") inc += amt else exp += Math.abs(amt)
        }
        return Money(inc, exp)
    }

    data class BudgetLine(val budget: Entry, val category: String, val limit: Double, val spent: Double) {
        val ratio get() = if (limit <= 0) 0.0 else spent / limit
    }

    fun budgets(e: Engine, month: LocalDate = e.today()): List<BudgetLine> = e.entries("budgets").mapNotNull { b ->
        val cat = e.text(b, "category") ?: b.title
        val limit = e.num(b, "limit") ?: return@mapNotNull null
        val spent = e.entries("transactions").filter { t ->
            val d = e.date(t, "date")
            d != null && d.year == month.year && d.month == month.month && e.text(t, "kind") != "Income" && e.text(t, "category") == cat
        }.sumOf { Math.abs(e.num(it, "amount") ?: 0.0) }
        BudgetLine(b, cat, limit, spent)
    }

    data class WheelSlice(val name: String, val current: Double, val target: Double)

    fun wheel(e: Engine): List<WheelSlice> = e.query("life_areas", null).sortedBy { it.created }.map {
        WheelSlice(it.title, e.num(it, "current") ?: 0.0, e.num(it, "target") ?: 0.0)
    }

    fun todayTasks(e: Engine): List<Entry> = e.entries("tasks").filter {
        val d = e.date(it, "due"); d != null && !d.isAfter(e.today()) && e.text(it, "status") != "Done"
    }.sortedWith(compareBy<Entry> { e.date(it, "due") }.thenByDescending { listOf("Low", "Medium", "High", "Urgent").indexOf(e.text(it, "priority")) })

    fun activeHabits(e: Engine): List<Entry> = e.entries("habits").filter { e.bool(it, "active") }.sortedBy { it.created }

    /** Plain-text CV assembled from the CV collections. */
    fun cvText(e: Engine): String {
        val sb = StringBuilder()
        val name = e.setting("profile_name") ?: "Your Name"
        sb.appendLine(name.uppercase())
        e.setting("cv_headline")?.let { sb.appendLine(it) }
        e.setting("cv_contact")?.let { sb.appendLine(it) }
        e.setting("cv_summary")?.takeIf { it.isNotBlank() }?.let { sb.appendLine().appendLine("SUMMARY").appendLine(it) }
        val exp = e.query("cv_experience", null).sortedByDescending { e.date(it, "start") }
        if (exp.isNotEmpty()) {
            sb.appendLine().appendLine("EXPERIENCE")
            for (x in exp) {
                val end = if (e.bool(x, "current")) "Present" else e.date(x, "end")?.let { "${it.month.name.take(3).lowercase().replaceFirstChar(Char::uppercase)} ${it.year}" } ?: ""
                val start = e.date(x, "start")?.let { "${it.month.name.take(3).lowercase().replaceFirstChar(Char::uppercase)} ${it.year}" } ?: ""
                sb.appendLine("${x.title} — ${e.text(x, "company") ?: ""}  ($start – $end)".trim())
                e.text(x, "summary")?.let { sb.appendLine(it) }
                sb.appendLine()
            }
        }
        val edu = e.query("cv_education", null).sortedByDescending { e.date(it, "end") }
        if (edu.isNotEmpty()) {
            sb.appendLine("EDUCATION")
            for (x in edu) {
                sb.appendLine("${x.title} — ${e.text(x, "school") ?: ""} ${e.date(x, "end")?.year ?: ""}".trim())
                e.text(x, "details")?.let { sb.appendLine(it) }
            }
            sb.appendLine()
        }
        val skills = e.entries("cv_skills")
        if (skills.isNotEmpty()) {
            sb.appendLine("SKILLS")
            e.group(skills, "category", "cv_skills").filter { it.value.isNotEmpty() }.forEach { (k, v) -> sb.appendLine("$k: ${v.joinToString(", ") { it.title }}") }
        }
        return sb.toString().trimEnd() + "\n"
    }
}
