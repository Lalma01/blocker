package com.psblock.app

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import kotlin.concurrent.thread

/**
 * Local DNS content filter — the Android replacement for editing the Windows hosts file.
 *
 * Only DNS traffic is routed through the tunnel (a /32 route to our virtual DNS address),
 * so the rest of the network is untouched and overhead stays minimal. For each query we
 * resolve the domain locally: blocked names get a 0.0.0.0 answer, everything else is
 * forwarded to a real upstream resolver. No traffic ever leaves the device for filtering
 * decisions — they are made here.
 */
class FilterVpnService : VpnService() {

    private val virtualDns = "10.111.222.2"
    private val upstreamDns = "8.8.8.8"

    @Volatile private var running = false
    private var tun: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    @Volatile private var blocked: Set<String> = emptySet()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stop()
            return START_NOT_STICKY
        }
        startForeground(NOTIF_ID, buildNotification())
        refreshBlocklist()
        if (!running) startTunnel()
        return START_STICKY
    }

    private fun refreshBlocklist() {
        // Include DoH/DoT bootstrap hostnames so their A/AAAA lookups resolve to 0.0.0.0.
        blocked = Blocklist.effectiveDomains(Config.customBlocked) + Blocklist.DOH_DOT_HOSTS
    }

    private fun buildNotification() =
        NotificationCompat.Builder(this, PsBlockApp.CH_SERVICE)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_filter_active))
            .setSmallIcon(R.drawable.ic_shield)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()

    private fun startTunnel() {
        val builder = Builder()
            .setSession("PS-BLOCK")
            .addAddress("10.111.222.1", 24)
            .addDnsServer(virtualDns)
            .addRoute(virtualDns, 32)
            .setMtu(1500)

        // Black-hole well-known DoH/DoT resolver IPs: route them into the tunnel where any
        // non-plain-DNS packet (TCP 443/853, QUIC) is dropped by the read loop, so hard-coded
        // encrypted-DNS clients fail and fall back to plain DNS — which we filter.
        for (ip in Blocklist.DOH_DOT_IPS) try { builder.addRoute(ip, 32) } catch (_: Exception) {}
        // IPv6: add a ULA address so v6 routes are valid, then black-hole v6 resolver IPs.
        try {
            builder.addAddress("fd00:5717:5749::1", 64)
            for (ip in Blocklist.DOH_DOT_IPS6) try { builder.addRoute(ip, 128) } catch (_: Exception) {}
        } catch (_: Exception) {}

        // Don't filter ourselves.
        try { builder.addDisallowedApplication(packageName) } catch (_: Exception) {}

        val pfd = try { builder.establish() } catch (e: Exception) { null } ?: return
        tun = pfd
        running = true
        worker = thread(name = "psblock-dns") { loop(pfd) }
    }

    private fun loop(pfd: ParcelFileDescriptor) {
        val input = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)
        val buf = ByteArray(32767)
        while (running) {
            val len = try { input.read(buf) } catch (e: Exception) { break }
            if (len <= 0) continue
            try { handlePacket(buf, len, output) } catch (_: Exception) {}
        }
        try { input.close() } catch (_: Exception) {}
        try { output.close() } catch (_: Exception) {}
    }

    /** Parse one IPv4/UDP/DNS packet and reply. */
    private fun handlePacket(packet: ByteArray, len: Int, output: FileOutputStream) {
        if (len < 28) return
        val version = (packet[0].toInt() ushr 4) and 0xF
        if (version != 4) return
        val ihl = (packet[0].toInt() and 0xF) * 4
        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) return // UDP only

        val udpStart = ihl
        val dstPort = ((packet[udpStart + 2].toInt() and 0xFF) shl 8) or (packet[udpStart + 3].toInt() and 0xFF)
        if (dstPort != 53) return

        val dnsStart = udpStart + 8
        val dnsLen = len - dnsStart
        if (dnsLen < 12) return
        val dns = packet.copyOfRange(dnsStart, len)

        val domain = parseQuestion(dns) ?: return
        val isBlocked = !Blocklist.isAllowed(domain) && matchesBlocked(domain)

        val responseDns = if (isBlocked) {
            Notifications.bumpDnsBlock()
            buildBlockedResponse(dns)
        } else {
            forwardUpstream(dns) ?: return
        }
        writeResponse(packet, ihl, udpStart, responseDns, output)
    }

    private fun matchesBlocked(domain: String): Boolean {
        val d = domain.lowercase()
        return blocked.any { d == it || d.endsWith(".$it") || d == "www.$it" }
    }

    /** Extract the queried domain name from a DNS message. */
    private fun parseQuestion(dns: ByteArray): String? {
        var pos = 12 // skip header
        val sb = StringBuilder()
        while (pos < dns.size) {
            val l = dns[pos].toInt() and 0xFF
            if (l == 0) break
            if (l and 0xC0 != 0) return null // compression pointer not expected in a question
            pos++
            if (pos + l > dns.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(String(dns, pos, l, Charsets.US_ASCII))
            pos += l
        }
        return if (sb.isEmpty()) null else sb.toString().lowercase()
    }

    /** Build a DNS response answering the A query with 0.0.0.0. */
    private fun buildBlockedResponse(query: ByteArray): ByteArray {
        // Find end of question section.
        var pos = 12
        while (pos < query.size && (query[pos].toInt() and 0xFF) != 0) {
            pos += (query[pos].toInt() and 0xFF) + 1
        }
        pos += 1 + 4 // null label + QTYPE + QCLASS
        val questionEnd = minOf(pos, query.size)

        val out = ByteBuffer.allocate(questionEnd + 16)
        out.put(query, 0, 2)            // ID
        out.put(0x81.toByte())          // QR=1, RD=1
        out.put(0x80.toByte())          // RA=1, RCODE=0
        out.putShort(1)                 // QDCOUNT
        out.putShort(1)                 // ANCOUNT
        out.putShort(0)                 // NSCOUNT
        out.putShort(0)                 // ARCOUNT
        out.put(query, 12, questionEnd - 12) // question
        // Answer: pointer to name, A, IN, TTL, 4 bytes 0.0.0.0
        out.putShort(0xC00C.toShort())  // name pointer to offset 12
        out.putShort(1)                 // TYPE A
        out.putShort(1)                 // CLASS IN
        out.putInt(60)                  // TTL
        out.putShort(4)                 // RDLENGTH
        out.putInt(0)                   // 0.0.0.0
        return out.array().copyOf(out.position())
    }

    /** Forward the DNS query to a real resolver through a protected socket. */
    private fun forwardUpstream(dns: ByteArray): ByteArray? {
        DatagramSocket().use { sock ->
            protect(sock)
            sock.soTimeout = 4000
            val server = InetAddress.getByName(upstreamDns)
            sock.send(DatagramPacket(dns, dns.size, server, 53))
            val resp = ByteArray(2048)
            val rp = DatagramPacket(resp, resp.size)
            sock.receive(rp)
            return resp.copyOf(rp.length)
        }
    }

    /** Wrap [responseDns] in IPv4/UDP with src/dst swapped from the original request. */
    private fun writeResponse(req: ByteArray, ihl: Int, udpStart: Int, responseDns: ByteArray, output: FileOutputStream) {
        val total = ihl + 8 + responseDns.size
        val out = ByteArray(total)
        // Copy IP header, swap addresses.
        System.arraycopy(req, 0, out, 0, ihl)
        // src <-> dst (offsets 12..15 and 16..19)
        for (i in 0 until 4) {
            out[12 + i] = req[16 + i]
            out[16 + i] = req[12 + i]
        }
        // total length
        out[2] = (total ushr 8).toByte()
        out[3] = (total and 0xFF).toByte()
        // zero IP checksum then recompute
        out[10] = 0; out[11] = 0
        val ipck = checksum(out, 0, ihl)
        out[10] = (ipck ushr 8).toByte()
        out[11] = (ipck and 0xFF).toByte()

        // UDP header: swap ports, length, checksum 0 (optional for IPv4).
        out[ihl] = req[udpStart + 2]; out[ihl + 1] = req[udpStart + 3]   // src port = old dst
        out[ihl + 2] = req[udpStart]; out[ihl + 3] = req[udpStart + 1]   // dst port = old src
        val udpLen = 8 + responseDns.size
        out[ihl + 4] = (udpLen ushr 8).toByte()
        out[ihl + 5] = (udpLen and 0xFF).toByte()
        out[ihl + 6] = 0; out[ihl + 7] = 0
        System.arraycopy(responseDns, 0, out, ihl + 8, responseDns.size)

        synchronized(this) { output.write(out, 0, total); output.flush() }
    }

    private fun checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        var remaining = length
        while (remaining > 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2; remaining -= 2
        }
        if (remaining > 0) sum += (data[i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    private fun stop() {
        running = false
        try { worker?.interrupt() } catch (_: Exception) {}
        try { tun?.close() } catch (_: Exception) {}
        tun = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        try { tun?.close() } catch (_: Exception) {}
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.psblock.app.STOP_VPN"
        private const val NOTIF_ID = 1001
    }
}
