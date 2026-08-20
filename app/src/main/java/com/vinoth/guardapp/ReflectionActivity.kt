package com.vinoth.guardapp

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ReflectionActivity : AppCompatActivity() {

    private lateinit var domain: String
    private lateinit var timerText: TextView
    private lateinit var domainText: TextView
    private lateinit var proceedButton: Button
    private lateinit var neverMindButton: Button
    private lateinit var reflectionInput: EditText
    private val handler = Handler(Looper.getMainLooper())
    private var tickRunnable: Runnable? = null

    private val MIN_REFLECTION_CHARS = 10

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reflection)

        domain = intent.getStringExtra("domain") ?: run {
            finish()
            return
        }

        domainText = findViewById(R.id.domainText)
        timerText = findViewById(R.id.timerText)
        proceedButton = findViewById(R.id.proceedButton)
        neverMindButton = findViewById(R.id.neverMindButton)
        reflectionInput = findViewById(R.id.reflectionInput)

        domainText.text = "Blocked: $domain"

        proceedButton.setOnClickListener {
            val reflection = reflectionInput.text.toString().trim()
            if (reflection.length < MIN_REFLECTION_CHARS) {
                Toast.makeText(
                    this,
                    "Please write a bit more about what you're feeling first",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            FileLog.write(this, "REFLECTION: user proceeded for $domain | reflection: \"$reflection\"")
            FrictionState.grantTemporaryAllow(domain)
            finish()
        }

        neverMindButton.setOnClickListener {
            val reflection = reflectionInput.text.toString().trim()
            FileLog.write(this, "REFLECTION: user chose never-mind for $domain | reflection: \"$reflection\"")
            FrictionState.clearAttempt(domain)
            finish()
        }

        startTicking()
    }

    private fun startTicking() {
        tickRunnable = object : Runnable {
            override fun run() {
                updateTimerDisplay()
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(tickRunnable!!)
    }

    private fun updateTimerDisplay() {
        val delayEndsAt = FrictionState.getDelayEndsAt(domain)
        if (delayEndsAt == null) {
            timerText.text = "--:--"
            return
        }
        val remainingMs = delayEndsAt - System.currentTimeMillis()
        if (remainingMs <= 0) {
            timerText.text = "Time's up"
            proceedButton.visibility = android.view.View.VISIBLE
            stopTicking()
            return
        }
        val totalSeconds = remainingMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        timerText.text = String.format("%02d:%02d", minutes, seconds)
    }

    private fun stopTicking() {
        tickRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun onDestroy() {
        stopTicking()
        super.onDestroy()
    }
}
