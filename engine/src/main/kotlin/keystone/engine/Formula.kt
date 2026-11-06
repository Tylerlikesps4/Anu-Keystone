// Copyright (C) 2026 Tylerlikesps4
// SPDX-License-Identifier: GPL-3.0-or-later
package keystone.engine

import java.time.temporal.ChronoUnit

/**
 * A deliberately tiny formula language: `fn(arg, arg, ...)`, where each arg is a field key,
 * a number literal, or a nested call. Results are numbers (or null when inputs are missing).
 *
 * Functions: sum, sub, mul, div, pct(a,b) = a/b*100, min, max, round,
 * days_until(date), days_since(date), days_between(a,b), days_until_annual(date), age(date),
 * hours_between(time,time) (wraps midnight), monthly(price, cycleSelect).
 */
object Formula {
    val FUNCTIONS = listOf("sum", "sub", "mul", "div", "pct", "min", "max", "round", "days_until", "days_since",
        "days_between", "days_until_annual", "age", "hours_between", "monthly")

    fun eval(expr: String, e: Entry, eng: Engine): Double? = try { evalNode(parse(expr.trim()), e, eng) } catch (_: Exception) { null }

    sealed class Node {
        data class Call(val fn: String, val args: List<Node>) : Node()
        data class Ref(val key: String) : Node()
        data class Num(val v: Double) : Node()
    }

    fun parse(s: String): Node {
        val p = P(s); val n = p.node(); p.ws()
        require(p.i == s.length) { "unexpected '${s.substring(p.i)}'" }
        return n
    }

    private class P(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun node(): Node {
            ws()
            val start = i
            while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_' || s[i] == '.' || s[i] == '-')) i++
            val tok = s.substring(start, i)
            require(tok.isNotEmpty()) { "expected value at $start" }
            ws()
            if (i < s.length && s[i] == '(') {
                i++
                val args = mutableListOf<Node>()
                ws()
                if (i < s.length && s[i] == ')') { i++; return Node.Call(tok, args) }
                while (true) {
                    args += node(); ws()
                    require(i < s.length) { "missing )" }
                    if (s[i] == ',') { i++; continue }
                    if (s[i] == ')') { i++; break }
                    error("unexpected '${s[i]}'")
                }
                return Node.Call(tok.lowercase(), args)
            }
            return tok.toDoubleOrNull()?.let { Node.Num(it) } ?: Node.Ref(tok)
        }
    }

    private fun evalNode(n: Node, e: Entry, eng: Engine): Double? = when (n) {
        is Node.Num -> n.v
        is Node.Ref -> eng.num(e, n.key)
        is Node.Call -> call(n, e, eng)
    }

    private fun call(n: Node.Call, e: Entry, eng: Engine): Double? {
        val a = n.args
        fun num(i: Int) = evalNode(a[i], e, eng)
        fun ref(i: Int) = (a[i] as? Node.Ref)?.key ?: error("${n.fn} needs a field")
        val t = eng.today()
        return when (n.fn) {
            "sum" -> a.indices.mapNotNull { num(it) }.takeIf { it.isNotEmpty() }?.sum()
            "sub" -> { val x = num(0) ?: return null; x - (num(1) ?: 0.0) }
            "mul" -> a.indices.map { num(it) ?: return null }.fold(1.0) { acc, d -> acc * d }
            "div" -> { val x = num(0) ?: return null; val y = num(1) ?: return null; if (y == 0.0) null else Engine.round1(x / y * 100) / 100 }
            "pct" -> { val x = num(0) ?: return null; val y = num(1) ?: return null; if (y == 0.0) null else Engine.round1(100 * x / y) }
            "min" -> a.indices.mapNotNull { num(it) }.minOrNull()
            "max" -> a.indices.mapNotNull { num(it) }.maxOrNull()
            "round" -> num(0)?.let { Math.round(it).toDouble() }
            "days_until" -> eng.date(e, ref(0))?.let { ChronoUnit.DAYS.between(t, it).toDouble() }
            "days_since" -> eng.date(e, ref(0))?.let { ChronoUnit.DAYS.between(it, t).toDouble() }
            "days_between" -> { val x = eng.date(e, ref(0)) ?: return null; val y = eng.date(e, ref(1)) ?: return null; ChronoUnit.DAYS.between(x, y).toDouble() }
            "days_until_annual" -> eng.date(e, ref(0))?.let { ChronoUnit.DAYS.between(t, eng.nextAnniversary(it, t)).toDouble() }
            "age" -> eng.date(e, ref(0))?.let { ChronoUnit.YEARS.between(it, t).toDouble() }
            "hours_between" -> {
                val x = V.parseTime(eng.text(e, ref(0))) ?: return null
                val y = V.parseTime(eng.text(e, ref(1))) ?: return null
                var mins = (y.toSecondOfDay() - x.toSecondOfDay()) / 60
                if (mins < 0) mins += 24 * 60
                Engine.round1(mins / 60.0)
            }
            "monthly" -> {
                val price = num(0) ?: return null
                val factor = when (eng.text(e, ref(1))?.lowercase()) {
                    "weekly" -> 52.0 / 12; "monthly" -> 1.0; "quarterly" -> 1.0 / 3; "yearly", "annually" -> 1.0 / 12; else -> 1.0
                }
                Math.round(price * factor * 100) / 100.0
            }
            else -> null
        }
    }
}
