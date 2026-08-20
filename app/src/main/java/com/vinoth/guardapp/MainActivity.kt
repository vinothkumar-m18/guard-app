package com.vinoth.guardapp
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
class MainActivity : AppCompatActivity() {
    private val VPN_REQUEST_CODE = 100
    private val NOTIF_PERMISSION_REQUEST_CODE = 101
    private val OVERLAY_REQUEST_CODE = 102
    private lateinit var statusLog: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusLog = findViewById(R.id.statusLog)
        requestNotificationPermissionIfNeeded()
        requestOverlayPermissionIfNeeded()
        val startButton = findViewById<Button>(R.id.startVpnButton)
        startButton.setOnClickListener {
            FileLog.clear(this)
            FileLog.write(this, "Button clicked")
            refreshLog()
            startVpnFlow()
        }
        refreshLog()
    }
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                FileLog.write(this, "Requesting POST_NOTIFICATIONS permission")
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIF_PERMISSION_REQUEST_CODE
                )
            } else {
                FileLog.write(this, "Notification permission already granted")
            }
        } else {
            FileLog.write(this, "Notification permission not needed (API < 33)")
        }
    }
    private fun requestOverlayPermissionIfNeeded() {
        if (!Settings.canDrawOverlays(this)) {
            FileLog.write(this, "Overlay permission NOT granted - requesting via system settings")
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_REQUEST_CODE)
        } else {
            FileLog.write(this, "Overlay permission already granted")
        }
    }
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIF_PERMISSION_REQUEST_CODE) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            FileLog.write(this, "Notification permission result: granted=$granted")
            refreshLog()
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
        if (requestCode == OVERLAY_REQUEST_CODE) {
            val granted = Settings.canDrawOverlays(this)
            FileLog.write(this, "Overlay permission result: granted=$granted")
            refreshLog()
        }
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            FileLog.write(this, "Starting VPN service")
            refreshLog()
            val serviceIntent = Intent(this, GuardVpnService::class.java)
            startService(serviceIntent)
        }
    }
}
