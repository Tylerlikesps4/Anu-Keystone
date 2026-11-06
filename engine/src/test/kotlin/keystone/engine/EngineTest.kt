// Copyright (C) 2026 Tylerlikesps4
// SPDX-License-Identifier: GPL-3.0-or-later
package keystone.engine

import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import kotlin.test.*

class EngineTest {
    private val day = LocalDate.of(2026, 11, 6)
    private var clock = 1_000L
    private var ids = 0
    private fun engine(store: Store = MemoryStore(), seed: Boolean = false) =
        Engine(store, today = { day }, now = { clock++ }, newId = { "id${ids++}" }).also { it.installPresets(seed) }

    // ---------------- presets / schemas

    @Test fun presetsAreValid() {
        val cols = Presets.collections()
        assertTrue(cols.size >= 45, "expected 45+ collections, got ${cols.size}")
        val idsSet = cols.map { it.id }.toSet()
        assertEquals(cols.size, idsSet.size, "duplicate collection ids")
        for (c in cols) {
            val keys = c.fields.map { it.key }
            assertEquals(keys.size, keys.toSet().size, "duplicate keys in ${c.id}")
            assertTrue(c.views.isNotEmpty(), "${c.id} has no views")
            assertEquals(c.views.size, c.views.map { it.id }.toSet().size, "duplicate view ids in ${c.id}")
            for (f in c.fields) {
                if (f.type == FieldType.RELATION) assertTrue(f.target in idsSet, "${c.id}.${f.key} -> missing ${f.target}")
                if (f.type == FieldType.SELECT || f.type == FieldType.MULTI_SELECT) assertTrue(f.options.isNotEmpty(), "${c.id}.${f.key} no options")
                if (f.type == FieldType.ROLLUP) {
                    val r = f.rollup!!
                    assertTrue(r.source in idsSet, "${c.id}.${f.key} rollup source")
                    val src = cols.first { it.id == r.source }
                    assertTrue(r.via.startsWith("_") || src.fields.any { it.key == r.via }, "${c.id}.${f.key} via ${r.via}")
                }
                if (f.type == FieldType.FORMULA) Formula.parse(f.formula!!)
            }
            val all = keys.toSet() + setOf(F_AREA, F_PROJECT, F_TAGS)
            for (v in c.views) {
                v.groupBy?.let { assertTrue(it in all, "${c.id}/${v.id} groupBy $it") }
                v.dateField?.let { assertTrue(it in all, "${c.id}/${v.id} dateField $it") }
                v.valueField?.let { assertTrue(it in all, "${c.id}/${v.id} valueField $it") }
                v.columns.forEach { assertTrue(it in all, "${c.id}/${v.id} column $it") }
                v.filters.forEach { assertTrue(it.field in all, "${c.id}/${v.id} filter ${it.field}") }
                if (v.type == ViewType.BOARD) assertNotNull(v.groupBy)
                if (v.type == ViewType.CALENDAR) assertNotNull(v.dateField)
            }
        }
        for (h in Presets.hubs) h.collections.forEach { assertTrue(it in idsSet, "hub ${h.id} -> $it") }
        assertTrue(idsSet.all { id -> Presets.hubs.any { id in it.collections } }, "every collection is in a hub")
    }

    @Test fun everyRequestedHubExists() {
        val e = engine()
        listOf("tasks", "projects", "areas", "resources", "res_collections", "days", "months", "years", "tags", "habits", "sleep", "journal",
            "life_areas", "values", "goals", "workouts", "exercises", "recipes", "meal_plan", "jobs", "pro_contacts", "cv_experience",
            "transactions", "learning", "vocabulary", "home_tasks", "inventory", "vehicles", "car_service", "love", "people", "gifts",
            "wishlist", "bucket_list", "trips", "books", "media", "games", "warranties").forEach { assertTrue(e.hasCollection(it), it) }
        assertTrue(Presets.widgetCatalog(e).size >= 60, "60+ widgets")
        assertEquals(4, Presets.dashboards().size)
    }

    @Test fun installPresetsIsIdempotentAndSeeds() {
        val store = MemoryStore()
        val e = engine(store, seed = true)
        val n = e.collections().size
        assertEquals(0, e.installPresets())
        assertEquals(n, e.collections().size)
        assertEquals(8, e.entries("life_areas").size)
        assertTrue(e.entries("tags").isNotEmpty())
    }

    @Test fun paraLinksAreAddedAutomatically() {
        val e = engine()
        val keys = e.fields("books").map { it.key }
        assertTrue(F_AREA in keys && F_PROJECT in keys && F_TAGS in keys)
        assertFalse(F_AREA in e.fields("areas").map { it.key })
        assertFalse(F_PROJECT in e.fields("projects").map { it.key })
        assertFalse(F_TAGS in e.fields("tags").map { it.key })
    }

    // ---------------- coercion

    @Test fun valuesAreCoerced() {
        val e = engine()
        val b = e.create("books", "Dune", mapOf(
            "pages" to JsonPrimitive("412"), "rating" to JsonPrimitive(9), "started" to JsonPrimitive("2026-01-05T10:00"),
            "nonsense" to JsonPrimitive("x"), "progress" to JsonPrimitive(50), "status" to JsonPrimitive("Reading"),
        ))
        assertEquals(412.0, e.num(b, "pages"))
        assertEquals(5.0, e.num(b, "rating"), "rating clamps to max")
        assertEquals(LocalDate.of(2026, 1, 5), e.date(b, "started"))
        assertFalse(b.values.containsKey("nonsense"))
        assertFalse(b.values.containsKey("progress"), "computed fields are not stored")
        val g = e.create("games", "Hades", mapOf("store" to JsonPrimitive("store.steampowered.com/app/1145360")))
        assertEquals("https://store.steampowered.com/app/1145360", e.text(g, "store"))
        val s = e.create("sleep", "Night", mapOf("bed" to JsonPrimitive("23:30"), "wake" to JsonPrimitive("7:15")))
        assertEquals("07:15", e.text(s, "wake"))
    }

    @Test fun unknownSelectValueBecomesOption() {
        val e = engine()
        e.create("media", "Arcane", mapOf("platform" to JsonPrimitive("Crunchyroll")))
        assertTrue(e.collection("media")!!.fields.first { it.key == "platform" }.options.any { it.name == "Crunchyroll" })
    }

    // ---------------- relations, rollups, formulas

    @Test fun relationsRollupsAndBacklinks() {
        val e = engine()
        val p = e.create("projects", "Launch app")
        val t1 = e.create("tasks", "Design", mapOf(F_PROJECT to V.list(listOf(p.id)), "status" to JsonPrimitive("Done")))
        e.create("tasks", "Build", mapOf(F_PROJECT to V.list(listOf(p.id))))
        e.create("tasks", "Ship", mapOf(F_PROJECT to V.list(listOf(p.id)), "status" to JsonPrimitive("Done")))
        val pf = e.field("projects", "progress")!!
        assertEquals(66.7, e.rollup(p, pf))
        assertEquals("66.7%", e.display(p, pf))
        assertEquals(3.0, e.num(p, "task_count"))
        assertEquals(3, e.backlinks(p.id).size)
        assertEquals(listOf("Launch app"), e.relationTitles(t1, F_PROJECT))
        // single relation keeps only one id; ids from the wrong collection are dropped
        val other = e.create("projects", "Other")
        val t = e.create("tasks", "x", mapOf(F_PROJECT to V.list(listOf(p.id, other.id, t1.id))))
        assertEquals(listOf(other.id), V.asList(t.values[F_PROJECT]))
        // deleting cleans up dangling relations
        e.delete(p.id)
        assertTrue(V.asList(e.entry(t1.id)!!.values[F_PROJECT]).isEmpty())
    }

    @Test fun resourcesManagerProgress() {
        val e = engine()
        val col = e.create("res_collections", "Kotlin course", mapOf("type" to JsonPrimitive("Course")))
        repeat(4) { i -> e.create("resources", "Lesson $i", mapOf("collection" to V.list(listOf(col.id)), "done" to V.bool(i < 1))) }
        assertEquals(25.0, e.num(col, "progress"))
        assertEquals(4.0, e.num(col, "count"))
    }

    @Test fun formulas() {
        val e = engine()
        val s = e.create("sleep", "n", mapOf("bed" to JsonPrimitive("23:30"), "wake" to JsonPrimitive("07:00")))
        assertEquals(7.5, e.num(s, "hours"))
        val p = e.create("people", "Mom", mapOf("birthday" to JsonPrimitive("1970-11-10")))
        assertEquals(55.0, e.num(p, "age"))
        assertEquals(4.0, e.num(p, "bday_in"))
        val w = e.create("warranties", "TV", mapOf("expires" to JsonPrimitive("2026-12-06")))
        assertEquals(30.0, e.num(w, "days_left"))
        val sav = e.create("savings", "Trip", mapOf("target" to JsonPrimitive(2000), "saved" to JsonPrimitive(500)))
        assertEquals(25.0, e.num(sav, "pct"))
        val sub = e.create("subscriptions", "Yt", mapOf("price" to JsonPrimitive(120), "cycle" to JsonPrimitive("Yearly")))
        assertEquals(10.0, e.num(sub, "monthly"))
        assertEquals("$10.00", e.display(sub, e.field("subscriptions", "monthly")!!))
        assertNull(Formula.eval("pct(a,", s, e))
        assertNull(e.num(e.create("sleep", "empty"), "hours"))
    }

    // ---------------- views

    @Test fun filtersSortsAndGroups() {
        val e = engine()
        e.create("tasks", "Overdue", mapOf("due" to JsonPrimitive("2026-11-01"), "priority" to JsonPrimitive("High")))
        e.create("tasks", "Today", mapOf("due" to JsonPrimitive("2026-11-06"), "priority" to JsonPrimitive("Low")))
        e.create("tasks", "Later", mapOf("due" to JsonPrimitive("2026-12-01")))
        e.create("tasks", "Finished", mapOf("due" to JsonPrimitive("2026-11-02"), "status" to JsonPrimitive("Done")))
        val c = e.collection("tasks")!!
        val todayView = c.views.first { it.id == "today" }
        assertEquals(setOf("Overdue", "Today"), e.query("tasks", todayView).map { it.title }.toSet())
        val upcoming = c.views.first { it.id == "upcoming" }
        assertEquals(listOf("Overdue", "Today", "Later"), e.query("tasks", upcoming).map { it.title })
        val board = e.group(e.query("tasks", null), "status", "tasks")
        assertEquals(listOf("To do", "Doing", "Done", Engine.NO_VALUE), board.keys.toList())
        assertEquals(listOf("Finished"), board["Done"]!!.map { it.title })
        assertEquals(3, board[Engine.NO_VALUE]!!.size)
        assertEquals(listOf("To do", "Doing", "Done"), e.group(emptyList(), "status", "tasks").keys.toList())
        val cal = e.calendar(e.query("tasks", null), "due")
        assertEquals("Today", cal[day]!!.single().title)
        assertEquals(listOf("Later"), e.query("tasks", null, search = "lat").map { it.title })
        val sorted = e.sortEntries(e.query("tasks", null), listOf(Sort("priority", desc = true)))
        assertEquals("Overdue", sorted.first().title)
        assertTrue(e.matches(e.query("tasks", null).first { it.title == "Later" }, Filter("due", FilterOp.GT, "today+7")))
    }

    @Test fun statsAndSeries() {
        val e = engine()
        e.create("transactions", "Rent", mapOf("amount" to JsonPrimitive(1000), "kind" to JsonPrimitive("Expense"), "category" to JsonPrimitive("Housing"), "date" to JsonPrimitive("2026-11-01")))
        e.create("transactions", "Food", mapOf("amount" to JsonPrimitive(50), "kind" to JsonPrimitive("Expense"), "category" to JsonPrimitive("Food"), "date" to JsonPrimitive("2026-11-03")))
        e.create("transactions", "Pay", mapOf("amount" to JsonPrimitive(3000), "kind" to JsonPrimitive("Income"), "category" to JsonPrimitive("Salary"), "date" to JsonPrimitive("2026-11-05")))
        e.create("transactions", "Old", mapOf("amount" to JsonPrimitive(70), "kind" to JsonPrimitive("Expense"), "category" to JsonPrimitive("Food"), "date" to JsonPrimitive("2026-10-03")))
        val m = Insights.money(e)
        assertEquals(3000.0, m.income); assertEquals(1050.0, m.expense); assertEquals(1950.0, m.net)
        val v = e.collection("transactions")!!.views.first { it.id == "spending_by_category" }
        val totals = e.groupTotals(e.query("transactions", v), "category", "amount").toMap()
        assertEquals(mapOf("Housing" to 1000.0, "Food" to 50.0), totals)
        val series = e.series(e.query("transactions", null).filter { e.text(it, "kind") == "Expense" }, "date", "amount", Engine.Bucket.MONTH, 3)
        assertEquals(listOf(0.0, 70.0, 1050.0), series.map { it.second })
        e.create("budgets", "Food budget", mapOf("category" to JsonPrimitive("Food"), "limit" to JsonPrimitive(200)))
        val b = Insights.budgets(e).single()
        assertEquals(50.0, b.spent); assertEquals(0.25, b.ratio)
    }

    // ---------------- recurrence, periodic notes, habits, upcoming

    @Test fun recurringTaskSpawnsNextOccurrence() {
        val e = engine()
        val t = e.create("tasks", "Water plants", mapOf("repeat" to JsonPrimitive("Weekly"), "due" to JsonPrimitive("2026-11-06"), "status" to JsonPrimitive("To do")))
        val next = e.update(t.copy(values = t.values + ("status" to JsonPrimitive("Done"))))
        assertNotNull(next)
        assertEquals(LocalDate.of(2026, 11, 13), e.date(next, "due"))
        assertEquals("To do", e.text(next, "status"))
        assertNull(e.update(e.entry(t.id)!!), "saving an already-done task doesn't spawn again")
        val once = e.create("tasks", "One-off", mapOf("repeat" to JsonPrimitive("None"), "status" to JsonPrimitive("To do")))
        assertNull(e.update(once.copy(values = once.values + ("status" to JsonPrimitive("Done")))))
        assertEquals(LocalDate.of(2026, 11, 9), e.nextOccurrence(day, "Weekdays"))
    }

    @Test fun periodicNotesAndHabits() {
        val e = engine()
        val d = e.ensureDay(day)
        assertSame(d.id, e.ensureDay(day).id)
        assertEquals(1, e.entries("days").size); assertEquals(1, e.entries("months").size); assertEquals(1, e.entries("years").size)
        val month = e.entries("months").single()
        assertEquals("November 2026", month.title)
        assertEquals(1.0, e.num(month, "days"))
        val h = e.create("habits", "Read", mapOf("active" to V.bool(true)))
        assertTrue(e.toggleHabit(h.id, day.minusDays(1)))
        assertTrue(e.toggleHabit(h.id, day.minusDays(2)))
        assertEquals(2, e.streak(h.id), "today not done yet keeps streak")
        e.toggleHabit(h.id, day)
        assertEquals(3, e.streak(h.id))
        assertEquals(listOf(false, false, false, false, true, true, true), e.habitWeek(h.id))
        assertFalse(e.toggleHabit(h.id, day))
        assertEquals(2.0, e.num(e.entry(h.id)!!, "total"))
    }

    @Test fun upcomingReminders() {
        val e = engine()
        e.create("people", "Ana", mapOf("birthday" to JsonPrimitive("1999-11-17")))
        e.create("warranties", "Laptop", mapOf("expires" to JsonPrimitive("2026-11-20")))
        e.create("home_tasks", "Gutters", mapOf("due" to JsonPrimitive("2026-11-04"), "status" to JsonPrimitive("To do")))
        e.create("home_tasks", "Done one", mapOf("due" to JsonPrimitive("2026-11-07"), "status" to JsonPrimitive("Done")))
        e.create("warranties", "Far", mapOf("expires" to JsonPrimitive("2027-11-20")))
        val up = e.upcoming(30)
        assertEquals(listOf("Gutters", "Ana", "Laptop"), up.map { it.entry.title })
        assertEquals(-2, up.first().days)
        assertEquals(LocalDate.of(2026, 11, 17), up[1].date)
        assertEquals(LocalDate.of(2027, 2, 28), e.nextAnniversary(LocalDate.of(2000, 2, 29), LocalDate.of(2027, 1, 1)))
    }

    // ---------------- custom collections & schema editing

    @Test fun customCollectionsAndSchemaEdits() {
        val e = engine()
        val c = e.createCollection("Plants")
        assertEquals("plants", c.id)
        assertEquals("plants_2", e.createCollection("Plants").id)
        val f = e.addField("plants", "Water every", FieldType.NUMBER) { it.copy(unit = "days") }
        assertEquals("water_every", f.key)
        val p = e.create("plants", "Fern", mapOf("water_every" to JsonPrimitive(3), "status" to JsonPrimitive("To do")))
        assertEquals("3 days", e.display(p, f))
        e.removeField("plants", "status")
        assertFalse(e.entry(p.id)!!.values.containsKey("status"))
        assertNull(e.collection("plants")!!.views.first { it.type == ViewType.BOARD }.groupBy)
        e.upsertView("plants", ViewDef("g", "Gallery", ViewType.GALLERY))
        assertTrue(e.collection("plants")!!.views.any { it.id == "g" })
        e.removeView("plants", "g")
        e.deleteCollection("plants")
        assertNull(e.entry(p.id)); assertFalse(e.hasCollection("plants"))
        assertFailsWith<IllegalArgumentException> { e.upsertCollection(c.copy(fields = c.fields + c.fields.first())) }
    }

    @Test fun archiveAndSearch() {
        val e = engine()
        val a = e.create("books", "The Hobbit", mapOf("author" to JsonPrimitive("Tolkien")))
        e.create("games", "Hobbit game")
        assertEquals(listOf("The Hobbit", "Hobbit game").toSet(), e.search("hobbit").map { it.title }.toSet())
        assertEquals("The Hobbit", e.search("tolkien").single().title)
        e.archive(a.id)
        assertTrue(e.entries("books").isEmpty())
        assertEquals(1, e.archived().size)
        assertEquals(1, e.entries("books", includeArchived = true).size)
        e.archive(a.id, false)
        assertEquals(1, e.entries("books").size)
    }

    // ---------------- export / import

    @Test fun exportImportRoundTrip() {
        val e = engine(seed = true)
        val p = e.create("projects", "P")
        e.create("tasks", "T", mapOf(F_PROJECT to V.list(listOf(p.id)), "due" to JsonPrimitive("2026-11-10")))
        e.setSetting("vision", "Live well")
        e.saveDashboards(Presets.dashboards().take(1))
        val json = e.exportJson()
        val e2 = Engine(MemoryStore(), today = { day })
        val r = e2.importJson(json, replace = true)
        assertEquals(e.collections().size, r.collections)
        assertEquals(e.allEntries(true).size, e2.allEntries(true).size)
        assertEquals("Live well", e2.setting("vision"))
        assertEquals(1, e2.dashboards().size)
        val p2 = e2.entries("projects").single()
        assertEquals(1, e2.backlinks(p2.id).size)
        assertEquals(e.exportCsv("tasks"), e2.exportCsv("tasks"))
        assertFailsWith<IllegalArgumentException> { e2.importJson("{\"format\":\"other\",\"exportedAt\":1,\"collections\":[],\"entries\":[],\"settings\":{}}", true) }
        assertFailsWith<IllegalArgumentException> { e2.importJson("not json", true) }
    }

    @Test fun mergeImportKeepsNewer() {
        val store = MemoryStore()
        val e = engine(store)
        val t = e.create("tasks", "Old title")
        val backup = e.exportJson(pretty = false)
        e.rename(t.id, "New title")
        e.create("tasks", "Only local")
        e.importJson(backup, replace = false)
        assertEquals("New title", e.entry(t.id)!!.title, "newer local edit wins")
        assertEquals(2, e.entries("tasks").size)
    }

    @Test fun csvQuoting() {
        val e = engine()
        e.create("vocabulary", "hola", mapOf("meaning" to JsonPrimitive("hello, \"hi\"")))
        val csv = e.exportCsv("vocabulary").lines()
        assertTrue(csv[1].contains("\"hello, \"\"hi\"\"\""))
    }

    @Test fun cvText() {
        val e = engine()
        e.setSetting("profile_name", "Tyler")
        e.create("cv_experience", "Developer", mapOf("company" to JsonPrimitive("Acme"), "start" to JsonPrimitive("2024-01-01"), "current" to V.bool(true)))
        e.create("cv_skills", "Kotlin", mapOf("category" to JsonPrimitive("Technical")))
        val cv = Insights.cvText(e)
        assertTrue(cv.startsWith("TYLER"))
        assertTrue(cv.contains("Developer — Acme  (Jan 2024 – Present)"))
        assertTrue(cv.contains("Technical: Kotlin"))
    }
}
