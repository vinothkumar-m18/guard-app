package com.vinoth.guardapp

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class ReflectionActivity : Activity() {

    companion object {
        private const val MIN_REFLECTION_CHARS = 3
    }

    private var domain: String = ""
    private val handler = Handler(Looper.getMainLooper())
    private var tickRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reflection)

        domain = intent.getStringExtra("domain") ?: ""

        val domainText = findViewById<TextView>(R.id.domainText)
        val timerText = findViewById<TextView>(R.id.timerText)
        val proceedButton = findViewById<Button>(R.id.proceedButton)
        val neverMindButton = findViewById<Button>(R.id.neverMindButton)
        val reflectionInput = findViewById<EditText>(R.id.reflectionInput)

        domainText.text = "Blocked: $domain"
        proceedButton.visibility = View.GONE

        proceedButton.setOnClickListener {
            val reflection = reflectionInput.text.toString().trim()
            if (reflection.length < MIN_REFLECTION_CHARS) {
                Toast.makeText(
                    this,
                    "Please write at least $MIN_REFLECTION_CHARS characters about what you are feeling",
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

        startTicking(timerText, proceedButton)
    }

    private fun startTicking(timerText: TextView, proceedButton: Button) {
        tickRunnable = object : Runnable {
            override fun run() {
                val delayEndsAt = FrictionState.getDelayEndsAt(domain)
                if (delayEndsAt == null) {
                    timerText.text = "--:--"
                    handler.postDelayed(this, 1000)
                    return
                }

                val remainingMs = delayEndsAt - System.currentTimeMillis()
                if (remainingMs <= 0) {
                    timerText.text = "00:00 (Delay Finished)"
                    proceedButton.visibility = View.VISIBLE
                    proceedButton.isEnabled = true
                    proceedButton.text = "Proceed anyway"
                    return
                }

                val totalSeconds = remainingMs / 1000
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                timerText.text = String.format("%02d:%02d", minutes, seconds)
                proceedButton.visibility = View.GONE

                handler.postDelayed(this, 1000)
            }
        }
        handler.post(tickRunnable!!)
    }

    override fun onDestroy() {
        super.onDestroy()
        tickRunnable?.let { handler.removeCallbacks(it) }
    }
}