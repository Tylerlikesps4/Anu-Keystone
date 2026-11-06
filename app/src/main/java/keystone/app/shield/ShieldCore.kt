// Copyright (C) 2026 Tylerlikesps4
// SPDX-License-Identifier: GPL-3.0-or-later
package keystone.app.shield

/** Kotlin face of the Rust core (libshield.so). Pure packet logic; all sockets stay in Kotlin. */
object ShieldCore {
    val available: Boolean = runCatching { System.loadLibrary("shield"); true }.getOrDefault(false)

    @JvmStatic private external fun nLoadRules(text: String): Long
    @JvmStatic private external fun nFreeRules(handle: Long)
    @JvmStatic private external fun nRuleCount(handle: Long): Int
    @JvmStatic private external fun nDecide(handle: Long, packet: ByteArray, appBlocked: Boolean): ByteArray
    @JvmStatic private external fun nWrap(flow: ByteArray, answer: ByteArray): ByteArray

    class Flow(val raw: ByteArray) {
        val srcPort get() = ((raw[0].toInt() and 0xff) shl 8) or (raw[1].toInt() and 0xff)
        val v6 get() = raw[2].toInt() == 1
        private val n get() = if (v6) 16 else 4
        val src: ByteArray get() = raw.copyOfRange(3, 3 + n)
        val dst: ByteArray get() = raw.copyOfRange(3 + n, 3 + 2 * n)
        val dstPort get() = ((raw[3 + 2 * n].toInt() and 0xff) shl 8) or (raw[4 + 2 * n].toInt() and 0xff)
        val length get() = 5 + 2 * n
    }

    sealed interface Verdict {
        data object Drop : Verdict
        class Reply(val packet: ByteArray) : Verdict
        class Forward(val flow: Flow, val name: String, val query: ByteArray) : Verdict
    }

    class Rules internal constructor(internal val handle: Long) : AutoCloseable {
        val count: Int get() = nRuleCount(handle)
        override fun close() = nFreeRules(handle)
    }

    fun loadRules(text: String) = Rules(nLoadRules(text))

    fun decide(rules: Rules, packet: ByteArray, appBlocked: Boolean): Verdict {
        val r = nDecide(rules.handle, packet, appBlocked)
        if (r.isEmpty()) return Verdict.Drop
        return when (r[0].toInt()) {
            1 -> Verdict.Reply(r.copyOfRange(1, r.size))
            2 -> {
                val flow = Flow(r.copyOfRange(1, r.size))
                val at = 1 + flow.length
                val nameLen = ((r[at].toInt() and 0xff) shl 8) or (r[at + 1].toInt() and 0xff)
                val name = String(r, at + 2, nameLen, Charsets.US_ASCII)
                Verdict.Forward(Flow(r.copyOfRange(1, at)), name, r.copyOfRange(at + 2 + nameLen, r.size))
            }
            else -> Verdict.Drop
        }
    }

    fun wrap(flow: Flow, answer: ByteArray): ByteArray = nWrap(flow.raw, answer)
}
