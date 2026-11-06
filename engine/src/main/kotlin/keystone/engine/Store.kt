// Copyright (C) 2026 Tylerlikesps4
// SPDX-License-Identifier: GPL-3.0-or-later
package keystone.engine

/** Persistence boundary. The Android app implements it with SQLite; tests use [MemoryStore]. */
interface Store {
    fun loadCollections(): List<CollectionDef>
    fun saveCollection(c: CollectionDef)
    fun deleteCollection(id: String)
    fun loadEntries(): List<Entry>
    fun saveEntry(e: Entry)
    fun saveEntries(list: List<Entry>) { list.forEach { saveEntry(it) } }
    fun deleteEntry(id: String)
    fun loadSettings(): Map<String, String>
    fun setSetting(key: String, value: String?)
    fun clearAll()
    fun <T> transaction(block: () -> T): T = block()
}

class MemoryStore : Store {
    val collections = linkedMapOf<String, CollectionDef>()
    val entries = linkedMapOf<String, Entry>()
    val settings = linkedMapOf<String, String>()
    override fun loadCollections() = collections.values.toList()
    override fun saveCollection(c: CollectionDef) { collections[c.id] = c }
    override fun deleteCollection(id: String) { collections.remove(id) }
    override fun loadEntries() = entries.values.toList()
    override fun saveEntry(e: Entry) { entries[e.id] = e }
    override fun deleteEntry(id: String) { entries.remove(id) }
    override fun loadSettings(): Map<String, String> = settings.toMap()
    override fun setSetting(key: String, value: String?) { if (value == null) settings.remove(key) else settings[key] = value }
    override fun clearAll() { collections.clear(); entries.clear(); settings.clear() }
}
