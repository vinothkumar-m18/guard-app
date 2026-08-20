package com.vinoth.guardapp

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

object OverlayHelper {

    private const val MIN_REFLECTION_CHARS = 10
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var currentDomain: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var tickRunnable: Runnable? = null

    fun canShowOverlay(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun showFrictionOverlay(context: Context, domain: String) {
        handler.post {
            if (overlayView != null) {
                FileLog.write(context, "OverlayHelper: overlay already showing, ignoring request for $domain")
                return@post
            }

            if (!canShowOverlay(context)) {
                FileLog.write(context, "OverlayHelper: overlay permission missing for $domain, falling back")
                return@post
            }

            val appContext = context.applicationContext
            val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val inflater = LayoutInflater.from(appContext)
            val view = inflater.inflate(R.layout.activity_reflection, null)

            val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_FULLSCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            )
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE

            val domainText = view.findViewById<TextView>(R.id.domainText)
            val timerText = view.findViewById<TextView>(R.id.timerText)
            val proceedButton = view.findViewById<Button>(R.id.proceedButton)
            val neverMindButton = view.findViewById<Button>(R.id.neverMindButton)
            val reflectionInput = view.findViewById<EditText>(R.id.reflectionInput)

            domainText.text = "Blocked: $domain"
            proceedButton.isEnabled = false

            proceedButton.setOnClickListener {
                val reflection = reflectionInput.text.toString().trim()
                if (reflection.length < MIN_REFLECTION_CHARS) {
                    Toast.makeText(
                        appContext,
                        "Please write at least $MIN_REFLECTION_CHARS characters about what you are feeling",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                FileLog.write(appContext, "REFLECTION: user proceeded for $domain | reflection: \"$reflection\"")
                FrictionState.grantTemporaryAllow(domain)
                removeOverlay(appContext)
            }

            neverMindButton.setOnClickListener {
                val reflection = reflectionInput.text.toString().trim()
                FileLog.write(appContext, "REFLECTION: user chose never-mind for $domain | reflection: \"$reflection\"")
                FrictionState.clearAttempt(domain)
                removeOverlay(appContext)
            }

            try {
                wm.addView(view, params)
                windowManager = wm
                overlayView = view
                currentDomain = domain
                FileLog.write(appContext, "OverlayHelper: overlay shown successfully for $domain")
                startTicking(appContext, domain, timerText, proceedButton)
            } catch (e: Exception) {
                FileLog.write(appContext, "OverlayHelper: addView FAILED for $domain: ${e.message}")
            }
        }
    }

    private fun startTicking(context: Context, domain: String, timerText: TextView, proceedButton: Button) {
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
                    timerText.text = "00:00"
                    proceedButton.isEnabled = true
                    proceedButton.text = "Proceed anyway"
                    return
                }

                val totalSeconds = remainingMs / 1000
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                timerText.text = String.format("%02d:%02d", minutes, seconds)
                proceedButton.isEnabled = false
                proceedButton.text = "Proceeding locked ($totalSeconds s)"

                handler.postDelayed(this, 1000)
            }
        }
        handler.post(tickRunnable!!)
    }

    private fun removeOverlay(context: Context) {
        handler.post {
            tickRunnable?.let { handler.removeCallbacks(it) }
            tickRunnable = null

            try {
                if (overlayView != null && windowManager != null) {
                    windowManager?.removeView(overlayView)
                    FileLog.write(context, "OverlayHelper: overlay dismissed cleanly")
                }
            } catch (e: Exception) {
                FileLog.write(context, "OverlayHelper: removeView error: ${e.message}")
            } finally {
                overlayView = null
                windowManager = null
                currentDomain = null
            }
        }
    }
}