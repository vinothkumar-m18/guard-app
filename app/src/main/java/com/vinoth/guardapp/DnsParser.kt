package com.vinoth.guardapp

object DnsParser {

    /**
     * Attempts to extract a queried domain name from a raw IP packet.
     * Returns null if this isn't a UDP/DNS packet, or parsing fails.
     */
    fun extractDomain(packet: ByteArray, length: Int): String? {
        if (length < 28) return null // too short to contain IP+UDP+DNS headers

        // --- IP header ---
        val ipVersion = (packet[0].toInt() shr 4) and 0xF
        if (ipVersion != 4) return null // only handling IPv4 for now

        val ipHeaderLength = (packet[0].toInt() and 0xF) * 4 // IHL field, in 32-bit words
        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) return null // 17 = UDP

        // --- UDP header ---
        val udpStart = ipHeaderLength
        if (udpStart + 8 > length) return null

        val destPort = ((packet[udpStart + 2].toInt() and 0xFF) shl 8) or
                       (packet[udpStart + 3].toInt() and 0xFF)
        if (destPort != 53) return null // only interested in DNS (port 53) queries

        // --- DNS payload ---
        val dnsStart = udpStart + 8
        if (dnsStart + 12 > length) return null // DNS header is 12 bytes

        // DNS header: skip 12 bytes (ID, flags, counts) to reach the question section
        var pos = dnsStart + 12
        val domain = StringBuilder()

        while (pos < length) {
            val labelLength = packet[pos].toInt() and 0xFF
            if (labelLength == 0) break // end of domain name
            pos++
            if (pos + labelLength > length) return null

            if (domain.isNotEmpty()) domain.append(".")
            for (i in 0 until labelLength) {
                domain.append(packet[pos + i].toInt().toChar())
            }
            pos += labelLength
        }

        return if (domain.isNotEmpty()) domain.toString() else null
    }
}
