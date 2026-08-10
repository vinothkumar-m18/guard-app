package com.vinoth.guardapp

import android.content.Context
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object DnsHandler {

    // Fallback keyword check for AMP-cache-proxied content, which uses Google's own domain
    // (cdn.ampproject.org) rather than the original site's domain, so blocklist membership
    // alone won't catch it.
    private val ampCacheKeywords = setOf("xhamster", "pornhub", "xvideos", "xnxx")

    private lateinit var blockedDomains: HashSet<String>
    private var initialized = false

    private fun ensureInit(context: Context) {
        if (!initialized) {
            blockedDomains = BlocklistLoader.load(context)
            initialized = true
        }
    }

    fun handlePacket(service: GuardVpnService, packet: ByteArray, length: Int, output: FileOutputStream) {
        ensureInit(service)

        if (length < 28) return

        val ipHeaderLength = (packet[0].toInt() and 0xF) * 4
        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) return

        val udpStart = ipHeaderLength
        if (udpStart + 8 > length) return

        val destPort = ((packet[udpStart + 2].toInt() and 0xFF) shl 8) or
                       (packet[udpStart + 3].toInt() and 0xFF)
        if (destPort != 53) return

        val dnsStart = udpStart + 8
        val domain = DnsParser.extractDomain(packet, length) ?: return
        val domainLower = domain.lowercase()

        val isBlocked = isDomainBlocked(domainLower)

        if (isBlocked) {
            FileLog.write(service, "BLOCKED: $domain")
            val response = buildNxDomainResponse(packet, length, ipHeaderLength, udpStart, dnsStart)
            output.write(response)
        } else {
            FileLog.write(service, "ALLOWED: $domain")
            relayToRealDns(service, packet, length, ipHeaderLength, udpStart, dnsStart, output)
        }
    }

    private fun isDomainBlocked(domain: String): Boolean {
        // Exact match or subdomain-of-blocklisted-domain match
        if (blockedDomains.contains(domain)) return true
        for (blocked in blockedDomains) {
            if (domain.endsWith(".$blocked")) return true
        }
        // AMP-cache proxy fallback
        if (ampCacheKeywords.any { domain.contains(it) }) return true

        return false
    }

    private fun buildNxDomainResponse(
        packet: ByteArray, length: Int, ipHeaderLength: Int, udpStart: Int, dnsStart: Int
    ): ByteArray {
        val response = packet.copyOf(length)
        for (i in 0 until 4) {
            val tmp = response[12 + i]
            response[12 + i] = response[16 + i]
            response[16 + i] = tmp
        }
        for (i in 0 until 2) {
            val tmp = response[udpStart + i]
            response[udpStart + i] = response[udpStart + 2 + i]
            response[udpStart + 2 + i] = tmp
        }
        response[dnsStart + 2] = 0x81.toByte()
        response[dnsStart + 3] = 0x83.toByte()
        response[dnsStart + 6] = 0; response[dnsStart + 7] = 0
        response[dnsStart + 8] = 0; response[dnsStart + 9] = 0
        response[dnsStart + 10] = 0; response[dnsStart + 11] = 0
        recomputeIpChecksum(response, ipHeaderLength)
        response[udpStart + 6] = 0
        response[udpStart + 7] = 0
        return response
    }

    private fun relayToRealDns(
        service: GuardVpnService, packet: ByteArray, length: Int,
        ipHeaderLength: Int, udpStart: Int, dnsStart: Int, output: FileOutputStream
    ) {
        try {
            val dnsPayload = packet.copyOfRange(dnsStart, length)
            val socket = DatagramSocket()
            service.protect(socket)
            val upstream = InetAddress.getByName("8.8.8.8")
            val outPacket = DatagramPacket(dnsPayload, dnsPayload.size, upstream, 53)
            socket.send(outPacket)
            val replyBuffer = ByteArray(512)
            val inPacket = DatagramPacket(replyBuffer, replyBuffer.size)
            socket.soTimeout = 3000
            socket.receive(inPacket)
            socket.close()
            val realAnswer = inPacket.data.copyOf(inPacket.length)
            val fullResponse = ByteArray(dnsStart + realAnswer.size)
            System.arraycopy(packet, 0, fullResponse, 0, dnsStart)
            System.arraycopy(realAnswer, 0, fullResponse, dnsStart, realAnswer.size)
            for (i in 0 until 4) {
                val tmp = fullResponse[12 + i]
                fullResponse[12 + i] = fullResponse[16 + i]
                fullResponse[16 + i] = tmp
            }
            for (i in 0 until 2) {
                val tmp = fullResponse[udpStart + i]
                fullResponse[udpStart + i] = fullResponse[udpStart + 2 + i]
                fullResponse[udpStart + 2 + i] = tmp
            }
            val udpLength = 8 + realAnswer.size
            fullResponse[udpStart + 4] = ((udpLength shr 8) and 0xFF).toByte()
            fullResponse[udpStart + 5] = (udpLength and 0xFF).toByte()
            fullResponse[udpStart + 6] = 0
            fullResponse[udpStart + 7] = 0
            val ipTotalLength = ipHeaderLength + udpLength
            fullResponse[2] = ((ipTotalLength shr 8) and 0xFF).toByte()
            fullResponse[3] = (ipTotalLength and 0xFF).toByte()
            recomputeIpChecksum(fullResponse, ipHeaderLength)
            output.write(fullResponse)
        } catch (e: Exception) {
            FileLog.write(service, "Relay failed: ${e.message}")
        }
    }

    private fun recomputeIpChecksum(packet: ByteArray, ipHeaderLength: Int) {
        packet[10] = 0
        packet[11] = 0
        var sum = 0
        var i = 0
        while (i < ipHeaderLength) {
            val word = ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        val checksum = sum.inv() and 0xFFFF
        packet[10] = ((checksum shr 8) and 0xFF).toByte()
        packet[11] = (checksum and 0xFF).toByte()
    }
}
