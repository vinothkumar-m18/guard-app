package com.vinoth.guardapp

import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream

class GuardVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var running = false
    private var packetThread: Thread? = null

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        FileLog.write(this, "Service onStartCommand called")
        if (running) {
            FileLog.write(this, "Already running")
            return START_STICKY
        }
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        FileLog.write(this, "startVpn() begin")

        val builder = Builder()
            .setSession("Guard")
            .addAddress("10.0.0.2", 32)
            .addDnsServer("8.8.8.8")
            // Route only DNS port traffic: achieved by routing narrow /32 host routes for
            // known DoH resolver IPs, plus relying on addDnsServer() above to capture
            // regular port-53 DNS queries issued by the OS. Everything else bypasses the tunnel.
            .addRoute("8.8.8.8", 32)   // Google DNS / DoH
            .addRoute("8.8.4.4", 32)   // Google DNS / DoH (secondary)
            .addRoute("1.1.1.1", 32)   // Cloudflare DNS / DoH
            .addRoute("1.0.0.1", 32)   // Cloudflare DNS / DoH (secondary)

        vpnInterface = try {
            builder.establish()
        } catch (e: Exception) {
            FileLog.write(this, "establish() threw: ${e.message}")
            null
        }

        if (vpnInterface == null) {
            FileLog.write(this, "vpnInterface is NULL - establish failed")
            return
        }

        FileLog.write(this, "VPN established successfully (DNS-scoped routing)")
        running = true
        packetThread = Thread { runPacketLoop() }
        packetThread?.start()
    }

    private fun runPacketLoop() {
        val fd = vpnInterface ?: return
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buffer = ByteArray(32767)

        try {
            while (running) {
                val length = input.read(buffer)
                if (length > 0) {
                    DnsHandler.handlePacket(this, buffer, length, output)
                }
            }
        } catch (e: Exception) {
            FileLog.write(this, "Packet loop stopped: ${e.message}")
        }
    }

    override fun onDestroy() {
        running = false
        packetThread?.interrupt()
        vpnInterface?.close()
        vpnInterface = null
        super.onDestroy()
    }
}
