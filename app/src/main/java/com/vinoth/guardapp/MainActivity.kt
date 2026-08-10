package com.vinoth.guardapp

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val VPN_REQUEST_CODE = 100
    private lateinit var statusLog: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusLog = findViewById(R.id.statusLog)

        FileLog.clear(this)

        val startButton = findViewById<Button>(R.id.startVpnButton)
        startButton.setOnClickListener {
            FileLog.write(this, "Button clicked")
            refreshLog()
            startVpnFlow()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshLog()
    }

    private fun refreshLog() {
        statusLog.text = FileLog.readAll(this)
    }

    private fun startVpnFlow() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            FileLog.write(this, "Requesting VPN permission")
            startActivityForResult(intent, VPN_REQUEST_CODE)
        } else {
            FileLog.write(this, "Permission already granted")
            onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        FileLog.write(this, "onActivityResult: code=$resultCode")
        refreshLog()
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            FileLog.write(this, "Starting VPN service")
            refreshLog()
            val serviceIntent = Intent(this, GuardVpnService::class.java)
            startService(serviceIntent)
        }
    }
}
