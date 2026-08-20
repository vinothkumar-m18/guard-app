package com.vinoth.guardapp

object FrictionState {

    // === TEST MODE DELAYS (8 SECONDS) ===
    // Note: Restore to (5 * 60 * 1000L / 15 * 60 * 1000L) for production release
    private const val DELAY_MIN_MS = 8_000L  // 8 seconds
    private const val DELAY_MAX_MS = 8_000L  // 8 seconds

    // Option B: 45-second idle gap before session lapses
    private const val IDLE_GAP_MS = 45_000L

    // Hard ceiling for maximum continuous session
    private const val MAX_SESSION_MS = 25 * 60 * 1000L

    private val lock = Any()
    private var pendingDomain: String? = null
    private var delayEndsAt: Long? = null
    private var sessionStartTime: Long? = null
    private var lastActivityTime: Long? = null

    fun shouldAllow(domain: String): Boolean {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val start = sessionStartTime ?: return false
            val last = lastActivityTime ?: return false

            // Check if user has been idle for more than 45 seconds
            if (now - last > IDLE_GAP_MS) {
                sessionStartTime = null
                lastActivityTime = null
                return false
            }

            // Check if 25m hard ceiling has elapsed
            if (now - start > MAX_SESSION_MS) {
                sessionStartTime = null
                lastActivityTime = null
                return false
            }

            // Still within active window -> refresh rolling timestamp
            lastActivityTime = now
            return true
        }
    }

    fun registerAttemptIfNew(domain: String): Boolean {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            if (delayEndsAt != null && now < delayEndsAt!!) {
                return false // Delay still ticking
            }

            // Set new friction period (8s)
            val delay = if (DELAY_MAX_MS > DELAY_MIN_MS) {
                DELAY_MIN_MS + (Math.random() * (DELAY_MAX_MS - DELAY_MIN_MS)).toLong()
            } else {
                DELAY_MIN_MS
            }

            pendingDomain = domain
            delayEndsAt = now + delay
            return true
        }
    }

    fun grantTemporaryAllow(domain: String) {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            sessionStartTime = now
            lastActivityTime = now
            pendingDomain = null
            delayEndsAt = null
        }
    }

    fun clearAttempt(domain: String) {
        synchronized(lock) {
            pendingDomain = null
            delayEndsAt = null
        }
    }

    fun getDelayEndsAt(domain: String): Long? {
        synchronized(lock) {
            return delayEndsAt
        }
    }

    fun isDelayOver(domain: String): Boolean {
        synchronized(lock) {
            val ends = delayEndsAt ?: return false
            return System.currentTimeMillis() >= ends
        }
    }
}