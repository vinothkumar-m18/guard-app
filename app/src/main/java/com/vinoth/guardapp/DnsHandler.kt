package com.vinoth.guardapp

import android.content.Context
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

object DnsHandler {

    private val ampCacheKeywords = setOf("xhamster", "pornhub", "xvideos", "xnxx")
    private lateinit var blockedDomains: HashSet<String>
    private var initialized = false
    private val relayExecutor = Executors.newFixedThreadPool(4)

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
        if (protocol != 17) return // Only UDP

        val udpStart = ipHeaderLength
        if (udpStart + 8 > length) return

        val destPort = ((packet[udpStart + 2].toInt() and 0xFF) shl 8) or
                (packet[udpStart + 3].toInt() and 0xFF)
        if (destPort != 53) return // Only DNS Port 53

        val dnsStart = udpStart + 8
        val domain = DnsParser.extractDomain(packet, length) ?: return
        val domainLower = domain.lowercase()

        val isBlocked = isDomainBlocked(domainLower)

        if (!isBlocked) {
            FileLog.write(service, "ALLOWED: $domain")
            val packetCopy = packet.copyOf(length)
            relayExecutor.execute {
                relayToRealDns(service, packetCopy, length, ipHeaderLength, udpStart, dnsStart, output)
            }
            return
        }

        // Blocked domain -> friction state machine
        if (FrictionState.shouldAllow(domainLower)) {
            FileLog.write(service, "TEMP-ALLOWED (friction window active): $domain")
            val packetCopy = packet.copyOf(length)
            relayExecutor.execute {
                relayToRealDns(service, packetCopy, length, ipHeaderLength, udpStart, dnsStart, output)
            }
            return
        }

        val isNewAttempt = FrictionState.registerAttemptIfNew(domainLower)
        if (isNewAttempt) {
            FileLog.write(service, "FRICTION START: $domain")
            if (OverlayHelper.canShowOverlay(service)) {
                OverlayHelper.showFrictionOverlay(service, domainLower)
            } else {
                NotificationHelper.showFrictionNotification(service, domainLower)
            }
        } else {
            FileLog.write(service, "FRICTION PENDING (still in delay): $domain")
        }

        val response = buildNxDomainResponse(packet, length, ipHeaderLength, udpStart, dnsStart)
        synchronized(output) {
            output.write(response)
        }
    }

    /**
     * O(levels) hierarchical lookup instead of scanning 76k list.
     * e.g., for "video.sub.pornhub.com":
     * checks "video.sub.pornhub.com", then "sub.pornhub.com", then "pornhub.com".
     */
    private fun isDomainBlocked(domain: String): Boolean {
        if (domain.isEmpty()) return false
        if (blockedDomains.contains(domain)) return true

        var current = domain
        while (true) {
            val dotIndex = current.indexOf('.')
            if (dotIndex == -1) break
            current = current.substring(dotIndex + 1)
            if (current.isNotEmpty() && blockedDomains.contains(current)) return true
        }

        if (ampCacheKeywords.any { domain.contains(it) }) return true

        return false
    }

    private fun buildNxDomainResponse(
        packet: ByteArray, length: Int, ipHeaderLength: Int, udpStart: Int, dnsStart: Int
    ): ByteArray {
        val response = packet.copyOf(length)

        // Swap IP src and dst
        for (i in 0 until 4) {
            val tmp = response[12 + i]
            response[12 + i] = response[16 + i]
            response[16 + i] = tmp
        }

        // Swap UDP ports
        for (i in 0 until 2) {
            val tmp = response[udpStart + i]
            response[udpStart + i] = response[udpStart + 2 + i]
            response[udpStart + 2 + i] = tmp
        }

        // DNS header flags: Response + Name Error (NXDOMAIN)
        response[dnsStart + 2] = 0x81.toByte()
        response[dnsStart + 3] = 0x83.toByte()

        // Clear Answer, Authority, Additional RRs count
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
            socket.soTimeout = 2500
            socket.receive(inPacket)
            socket.close()

            val realAnswer = inPacket.data.copyOf(inPacket.length)
            val fullResponse = ByteArray(dnsStart + realAnswer.size)

            System.arraycopy(packet, 0, fullResponse, 0, dnsStart)
            System.arraycopy(realAnswer, 0, fullResponse, dnsStart, realAnswer.size)

            // Swap IP src/dst
            for (i in 0 until 4) {
                val tmp = fullResponse[12 + i]
                fullResponse[12 + i] = fullResponse[16 + i]
                fullResponse[16 + i] = tmp
            }

            // Swap UDP src/dst
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

            synchronized(output) {
                output.write(fullResponse)
            }
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