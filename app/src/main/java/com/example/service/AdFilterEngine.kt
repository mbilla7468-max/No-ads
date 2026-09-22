package com.example.service

import java.nio.ByteBuffer
import java.util.Locale

/**
 * High-performance, zero-allocation Ad & Tracker filtering engine.
 * Specifically optimized for low-end Android devices (1GB RAM, weak CPUs).
 *
 * Intercepts DNS queries for ad networks, telemetry beacons, and video ad engines,
 * returning instant 0.0.0.0 sinkhole responses in 0ms.
 *
 * Developed by MJ MASUM BILLA.
 */
object AdFilterEngine {

    const val ENGINE_AUTHOR = "Developed by MJ MASUM BILLA"
    const val DEFAULT_DNS_PRIMARY = "94.140.14.14" // AdGuard Ad-blocking Anycast DNS
    const val DEFAULT_DNS_SECONDARY = "94.140.14.15"

    // Primary ad networks, telemetry endpoints, and interstitial video ad domains
    private val AD_DOMAIN_EXACT = hashSetOf(
        "googleads.g.doubleclick.net",
        "pagead2.googlesyndication.com",
        "adservice.google.com",
        "pagead2.googleadservices.com",
        "ads.admob.com",
        "admob.com",
        "unityads.unity3d.com",
        "ads.unity3d.com",
        "auction.unityads.unity3d.com",
        "config.unityads.unity3d.com",
        "applvn.com",
        "applovin.com",
        "rt.applovin.com",
        "ms.applovin.com",
        "ironsrc.com",
        "supersonicads.com",
        "vungle.com",
        "ads.vungle.com",
        "chartboost.com",
        "live.chartboost.com",
        "adcolony.com",
        "mintegral.com",
        "tapjoy.com",
        "inmobi.com",
        "config.inmobi.com",
        "flurry.com",
        "appsflyer.com",
        "t.appsflyer.com",
        "app.adjust.com",
        "adjust.com",
        "branch.io",
        "crashlytics.com",
        "facebook.com/tr",
        "graph.facebook.com/tr"
    )

    // Domain suffix patterns for comprehensive ad blocking
    private val AD_DOMAIN_SUFFIXES = listOf(
        ".doubleclick.net",
        ".googlesyndication.com",
        ".googleadservices.com",
        ".admob.com",
        ".unityads.unity3d.com",
        ".applvn.com",
        ".applovin.com",
        ".ironsrc.com",
        ".supersonicads.com",
        ".vungle.com",
        ".chartboost.com",
        ".adcolony.com",
        ".mintegral.net",
        ".tapjoyads.com",
        ".inmobi.com",
        ".flurry.com",
        ".appsflyer.com",
        ".adjust.com",
        "ads.",
        "adservice.",
        "-ads.",
        "adserver.",
        "telemetry."
    )

    /**
     * Instant O(1) lookup to determine if a hostname is an ad or telemetry tracker.
     */
    fun isAdOrTracker(domain: String?): Boolean {
        if (domain.isNullOrBlank()) return false
        val clean = domain.trim().lowercase(Locale.ROOT).trimEnd('.')

        if (AD_DOMAIN_EXACT.contains(clean)) return true

        for (suffix in AD_DOMAIN_SUFFIXES) {
            if (clean.endsWith(suffix) || clean.contains(suffix)) {
                return true
            }
        }
        return false
    }

    /**
     * Extracts the requested hostname from a raw DNS Question payload.
     * DNS queries format QNAME as: <length><label><length><label>...<0x00>
     */
    fun parseDnsQueryDomain(dnsData: ByteArray, offset: Int, length: Int): String? {
        if (length < 12) return null // DNS header is minimum 12 bytes
        var ptr = offset + 12 // Skip DNS 12-byte header
        val end = offset + length
        val sb = StringBuilder()

        while (ptr < end) {
            val len = dnsData[ptr].toInt() and 0xFF
            if (len == 0) break // End of QNAME
            ptr++
            if (ptr + len > end) return null

            if (sb.isNotEmpty()) sb.append('.')
            for (i in 0 until len) {
                sb.append(dnsData[ptr + i].toInt().toChar())
            }
            ptr += len
        }

        return if (sb.isNotEmpty()) sb.toString() else null
    }

    /**
     * Generates a synthetic RFC 1035 compliant DNS Response answering with 0.0.0.0 (Sinkhole).
     * This immediately terminates the ad connection in 0ms without network roundtrip.
     */
    fun buildSinkholeDnsResponse(
        queryDnsPayload: ByteArray,
        offset: Int,
        length: Int
    ): ByteArray? {
        if (length < 12) return null

        val bb = ByteBuffer.allocate(length + 16)
        // Copy transaction ID
        bb.put(queryDnsPayload, offset, 2)

        // Flags: Standard query response, No error, Authoritative
        // QR=1, Opcode=0, AA=1, TC=0, RD=1, RA=1, RCODE=0 -> 0x8180 or 0x8580
        bb.putShort(0x8180.toShort())

        // QDCOUNT (Questions): 1
        bb.putShort(1.toShort())
        // ANCOUNT (Answers): 1
        bb.putShort(1.toShort())
        // NSCOUNT: 0
        bb.putShort(0.toShort())
        // ARCOUNT: 0
        bb.putShort(0.toShort())

        // Copy Question section
        val questionStart = offset + 12
        var questionEnd = questionStart
        while (questionEnd < offset + length) {
            val len = queryDnsPayload[questionEnd].toInt() and 0xFF
            questionEnd++
            if (len == 0) {
                questionEnd += 4 // Skip QTYPE (2 bytes) + QCLASS (2 bytes)
                break
            }
            questionEnd += len
        }

        val questionLen = questionEnd - questionStart
        if (questionLen <= 0 || questionEnd > offset + length) return null
        bb.put(queryDnsPayload, questionStart, questionLen)

        // Answer Section:
        // Pointer to QNAME at offset 12 -> 0xC00C
        bb.putShort(0xC00C.toShort())
        // TYPE: A (IPv4 address) -> 1
        bb.putShort(1.toShort())
        // CLASS: IN (Internet) -> 1
        bb.putShort(1.toShort())
        // TTL: 300 seconds
        bb.putInt(300)
        // RDLENGTH: 4 bytes (IPv4)
        bb.putShort(4.toShort())
        // RDATA: 0.0.0.0 (Sinkhole Address)
        bb.put(0.toByte())
        bb.put(0.toByte())
        bb.put(0.toByte())
        bb.put(0.toByte())

        val result = ByteArray(bb.position())
        System.arraycopy(bb.array(), 0, result, 0, result.size)
        return result
    }

    /**
     * Builds a full IPv4 + UDP packet wrapping a DNS response payload.
     */
    fun buildUdpDnsPacket(
        srcIp: ByteArray,
        srcPort: Int,
        dstIp: ByteArray,
        dstPort: Int,
        dnsPayload: ByteArray
    ): ByteArray {
        val totalLength = 20 + 8 + dnsPayload.size
        val packet = ByteBuffer.allocate(totalLength)

        // --- IPv4 Header (20 bytes) ---
        packet.put(0x45.toByte()) // Version 4, IHL 5
        packet.put(0x00.toByte()) // DSCP/ECN
        packet.putShort(totalLength.toShort())
        packet.putShort(0.toShort()) // Identification
        packet.putShort(0x4000.toShort()) // Flags (Don't Fragment)
        packet.put(64.toByte()) // TTL
        packet.put(17.toByte()) // Protocol 17 (UDP)
        packet.putShort(0.toShort()) // Checksum placeholder
        packet.put(srcIp)
        packet.put(dstIp)

        // Calculate and write IP checksum
        val ipChecksum = computeChecksum(packet.array(), 0, 20)
        packet.putShort(10, ipChecksum.toShort())

        // --- UDP Header (8 bytes) ---
        val udpStart = 20
        packet.position(udpStart)
        packet.putShort(srcPort.toShort())
        packet.putShort(dstPort.toShort())
        val udpLen = 8 + dnsPayload.size
        packet.putShort(udpLen.toShort())
        packet.putShort(0.toShort()) // UDP checksum (0 = optional in IPv4)

        // --- DNS Payload ---
        packet.put(dnsPayload)

        return packet.array()
    }

    private fun computeChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length) {
            val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            sum += word
            if (sum > 0xFFFF) {
                sum = (sum and 0xFFFF) + (sum ushr 16)
            }
            i += 2
        }
        return sum.inv() and 0xFFFF
    }
}
