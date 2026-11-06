// Copyright (C) 2026 Tylerlikesps4
// SPDX-License-Identifier: GPL-3.0-or-later
package keystone.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import keystone.engine.CollectionDef
import keystone.engine.Entry
import keystone.engine.KJson
import keystone.engine.Store

/** Offline-first SQLite persistence. Schemas and entries are stored as JSON documents. */
class SqliteStore(context: Context) : SQLiteOpenHelper(context, "keystone.db", null, 1), Store {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE collections (id TEXT PRIMARY KEY, json TEXT NOT NULL)")
        db.execSQL("CREATE TABLE entries (id TEXT PRIMARY KEY, collection TEXT NOT NULL, updated INTEGER NOT NULL, json TEXT NOT NULL)")
        db.execSQL("CREATE INDEX entries_collection ON entries(collection)")
        db.execSQL("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    override fun loadCollections(): List<CollectionDef> = readableDatabase.rawQuery("SELECT json FROM collections", null).use { c ->
        buildList { while (c.moveToNext()) runCatching { add(KJson.decodeFromString(CollectionDef.serializer(), c.getString(0))) } }
    }

    override fun saveCollection(c: CollectionDef) {
        writableDatabase.insertWithOnConflict("collections", null, ContentValues().apply {
            put("id", c.id); put("json", KJson.encodeToString(CollectionDef.serializer(), c))
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun deleteCollection(id: String) { writableDatabase.delete("collections", "id=?", arrayOf(id)) }

    override fun loadEntries(): List<Entry> = readableDatabase.rawQuery("SELECT json FROM entries", null).use { c ->
        buildList { while (c.moveToNext()) runCatching { add(KJson.decodeFromString(Entry.serializer(), c.getString(0))) } }
    }

    override fun saveEntry(e: Entry) {
        writableDatabase.insertWithOnConflict("entries", null, ContentValues().apply {
            put("id", e.id); put("collection", e.collection); put("updated", e.updated)
            put("json", KJson.encodeToString(Entry.serializer(), e))
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun saveEntries(list: List<Entry>) { transaction { list.forEach { saveEntry(it) } } }

    override fun deleteEntry(id: String) { writableDatabase.delete("entries", "id=?", arrayOf(id)) }

    override fun loadSettings(): Map<String, String> = readableDatabase.rawQuery("SELECT key, value FROM settings", null).use { c ->
        buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) }
    }

    override fun setSetting(key: String, value: String?) {
        if (value == null) writableDatabase.delete("settings", "key=?", arrayOf(key))
        else writableDatabase.insertWithOnConflict("settings", null, ContentValues().apply { put("key", key); put("value", value) }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun clearAll() {
        val db = writableDatabase
        db.delete("collections", null, null); db.delete("entries", null, null); db.delete("settings", null, null)
    }

    override fun <T> transaction(block: () -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        try { val r = block(); db.setTransactionSuccessful(); return r } finally { db.endTransaction() }
    }
}
