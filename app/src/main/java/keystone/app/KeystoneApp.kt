// Copyright (C) 2026 Tylerlikesps4
// SPDX-License-Identifier: GPL-3.0-or-later
package keystone.app

import android.app.Application
import androidx.compose.runtime.mutableLongStateOf
import keystone.app.data.SqliteStore
import keystone.engine.Engine

/** Process-wide engine. Compose screens read [Ks.version] so they recompose after every change. */
object Ks {
    lateinit var engine: Engine
    val version = mutableLongStateOf(0L)
    val e get() = engine
}

class KeystoneApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val engine = Engine(SqliteStore(this))
        engine.installPresets(seed = true)
        engine.onChange = { Ks.version.longValue = engine.version }
        Ks.engine = engine
        Ks.version.longValue = engine.version
        Reminders.createChannel(this)
        if (engine.setting("reminders") != "off") Reminders.schedule(this, engine.setting("reminder_hour")?.toIntOrNull() ?: 9)
    }
}
