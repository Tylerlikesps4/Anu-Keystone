// Copyright (C) 2026 Tylerlikesps4
// SPDX-License-Identifier: GPL-3.0-or-later
package keystone.app.shield

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Keystone Shield firewall, DNS mode. A local VPN that only routes DNS: every lookup goes through the Rust core,
 * blocked names and blocked apps get "0.0.0.0", the rest is forwarded to the upstream resolver.
 * Nothing leaves the phone except those forwarded lookups.
 */
class ShieldVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    private var reader: Thread? = null
    private var rules: ShieldCore.Rules? = null
    private val pool = Executors.newFixedThreadPool(4)
    private val main = Handler(Looper.getMainLooper())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopEverything(); stopSelf(); return START_NOT_STICKY }
        if (tun == null) start()
        return START_STICKY
    }

    private fun start() {
        if (!ShieldCore.available) { fail("Firewall core missing from this build"); return }
        rules = ShieldCore.loadRules(ShieldFirewall.rulesText(this))
        val b = Builder().setSession("Anu Keystone Shield").setMtu(1500).setBlocking(true)
            .addAddress(V4_SELF, 32).addDnsServer(V4_DNS).addRoute(V4_DNS, 32)
            .addAddress(V6_SELF, 128).addDnsServer(V6_DNS).addRoute(V6_DNS, 128)
        runCatching { b.addDisallowedApplication(packageName) }
        if (Build.VERSION.SDK_INT >= 29) b.setMetered(false)
        tun = runCatching { b.establish() }.getOrNull() ?: run { fail("Android didn't allow the VPN. Turn it on again"); return }
        val fd = tun!!
        set(running = true)
        reader = Thread({ loop(fd) }, "shield-tun").also { it.start() }
    }

    private fun loop(fd: ParcelFileDescriptor) {
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buf = ByteArray(32767)
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val blockedApps = ShieldFirewall.blockedApps
        val r = rules ?: return
        try {
            while (!Thread.currentThread().isInterrupted) {
                val n = input.read(buf)
                if (n <= 0) continue
                val pkt = buf.copyOf(n)
                when (val v = ShieldCore.decide(r, pkt, false)) {
                    is ShieldCore.Verdict.Drop -> Unit
                    is ShieldCore.Verdict.Reply -> { write(output, v.packet); val name = blockedName(pkt); main.post { ShieldFirewall.recordBlock(name) } }
                    is ShieldCore.Verdict.Forward -> {
                        val app = appFor(cm, v.flow)
                        if (app != null && app in blockedApps) {
                            val blocked = ShieldCore.decide(r, pkt, true)
                            if (blocked is ShieldCore.Verdict.Reply) write(output, blocked.packet)
                            main.post { ShieldFirewall.recordBlock("${label(app)} · ${v.name}") }
                        } else pool.execute { forward(output, v) }
                    }
                }
            }
        } catch (_: Exception) {
            // tunnel closed
        }
    }

    /** Reads the question name straight from a query packet, for the log only. */
    private fun blockedName(pkt: ByteArray): String {
        val ihl = if ((pkt[0].toInt() shr 4) == 6) 40 else (pkt[0].toInt() and 0x0f) * 4
        var i = ihl + 8 + 12
        val sb = StringBuilder()
        while (i < pkt.size) {
            val len = pkt[i].toInt() and 0xff
            if (len == 0 || len > 63 || i + 1 + len > pkt.size) break
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(String(pkt, i + 1, len, Charsets.US_ASCII)); i += 1 + len
        }
        return sb.toString()
    }

    private fun forward(output: FileOutputStream, v: ShieldCore.Verdict.Forward) {
        for (upstream in UPSTREAMS) {
            try {
                DatagramSocket().use { s ->
                    protect(s)
                    s.soTimeout = 4000
                    s.send(DatagramPacket(v.query, v.query.size, InetSocketAddress(upstream, 53)))
                    val resp = ByteArray(4096)
                    val p = DatagramPacket(resp, resp.size)
                    s.receive(p)
                    write(output, ShieldCore.wrap(v.flow, resp.copyOf(p.length)))
                    main.post { ShieldFirewall.allowedCount.intValue++ }
                    return
                }
            } catch (_: Exception) { /* try next resolver */ }
        }
    }

    @Synchronized private fun write(out: FileOutputStream, bytes: ByteArray) { runCatching { out.write(bytes) } }

    private fun appFor(cm: ConnectivityManager, flow: ShieldCore.Flow?): String? {
        if (flow == null || Build.VERSION.SDK_INT < 29) return null
        return runCatching {
            val uid = cm.getConnectionOwnerUid(OsConstants.IPPROTO_UDP,
                InetSocketAddress(InetAddress.getByAddress(flow.src), flow.srcPort),
                InetSocketAddress(InetAddress.getByAddress(flow.dst), flow.dstPort))
            if (uid < 0) null else packageManager.getPackagesForUid(uid)?.firstOrNull()
        }.getOrNull()
    }

    private fun label(pkg: String) = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

    private fun set(running: Boolean) = main.post { ShieldFirewall.running.value = running; if (running) ShieldFirewall.lastError.value = null }
    private fun fail(msg: String) { main.post { ShieldFirewall.lastError.value = msg; ShieldFirewall.running.value = false }; stopSelf() }

    private fun stopEverything() {
        reader?.interrupt(); reader = null
        runCatching { tun?.close() }; tun = null
        rules?.close(); rules = null
        set(running = false)
    }

    override fun onRevoke() { stopEverything(); ShieldFirewall.wantOn = false; super.onRevoke() }
    override fun onDestroy() { stopEverything(); pool.shutdownNow(); pool.awaitTermination(1, TimeUnit.SECONDS); super.onDestroy() }

    companion object {
        const val ACTION_STOP = "keystone.shield.STOP"
        private const val V4_SELF = "10.111.0.2"
        private const val V4_DNS = "10.111.0.1"
        private const val V6_SELF = "fd00:6b73:6869::2"
        private const val V6_DNS = "fd00:6b73:6869::1"
        private val UPSTREAMS = listOf("1.1.1.1", "9.9.9.9")

        fun start(ctx: Context) { ShieldFirewall.wantOn = true; ctx.startService(Intent(ctx, ShieldVpnService::class.java)) }
        fun stop(ctx: Context) { ShieldFirewall.wantOn = false; ctx.startService(Intent(ctx, ShieldVpnService::class.java).setAction(ACTION_STOP)) }
    }
}
