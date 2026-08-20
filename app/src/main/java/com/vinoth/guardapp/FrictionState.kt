package com.vinoth.guardapp
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

data class DomainAttempt(
    val delayEndsAt: Long
)

object FrictionState {
    private val attempts = ConcurrentHashMap<String, DomainAttempt>()

    private const val DELAY_MIN_MS = 5 * 60 * 1000L
    private const val DELAY_MAX_MS = 15 * 60 * 1000L

    // Rolling-activity session: once granted, stays active as long as new blocked-domain
    // queries keep arriving within IDLE_GAP_MS of each other - this covers a page's own
    // CDN/ad/analytics subdomains, which fire in rapid bursts across many DIFFERENT root
    // domains (not just subdomains of the confirmed site). If activity goes quiet for longer
    // than IDLE_GAP_MS, the session lapses and the next blocked domain requires a fresh delay,
    // treating a pause-then-new-request as a new deliberate decision rather than a continuation
    // of the same page load. MAX_SESSION_MS is a hard ceiling so continuous active browsing
    // can't extend the session forever.
    // KNOWN LIMITATION: this cannot distinguish "same page loading resources" from "fast
    // deliberate hop to a new site within the idle gap" - both look like continued activity.
    private const val IDLE_GAP_MS = 45 * 1000L
    private const val MAX_SESSION_MS = 25 * 60 * 1000L

    @Volatile private var sessionStartedAt: Long? = null
    @Volatile private var lastActivityAt: Long? = null

    private fun isSessionActive(now: Long): Boolean {
        val started = sessionStartedAt ?: return false
        val last = lastActivityAt ?: return false
        if (now - started > MAX_SESSION_MS) return false
        if (now - last > IDLE_GAP_MS) return false
        return true
    }

    // Returns true if this is a brand-new attempt (caller should fire the notification)
    fun registerAttemptIfNew(domain: String): Boolean {
        if (attempts.containsKey(domain)) {
            return false // already pending delay, not new
        }
        val now = System.currentTimeMillis()
        val delay = Random.nextLong(DELAY_MIN_MS, DELAY_MAX_MS + 1)
        attempts[domain] = DomainAttempt(delayEndsAt = now + delay)
        return true
    }

    // What DnsHandler should do right now for this domain. Ignores which specific domain is
    // asking - if the rolling session is still active, this query is allowed AND it refreshes
    // lastActivityAt, extending the session for the next query.
    fun shouldAllow(domain: String): Boolean {
        val now = System.currentTimeMillis()
        if (!isSessionActive(now)) return false
        lastActivityAt = now
        return true
    }

    fun isDelayServed(domain: String): Boolean {
        val attempt = attempts[domain] ?: return false
        return System.currentTimeMillis() >= attempt.delayEndsAt
    }

    fun getDelayEndsAt(domain: String): Long? {
        return attempts[domain]?.delayEndsAt
    }

    // Called by ReflectionActivity once user confirms after delay is served.
    // Starts a fresh rolling session (both the ceiling clock and the activity clock).
    fun grantTemporaryAllow(domain: String) {
        val now = System.currentTimeMillis()
        sessionStartedAt = now
        lastActivityAt = now
        attempts.remove(domain)
    }

    // Called by ReflectionActivity when user declines (Never Mind). Clears the attempt entirely
    // so a future visit starts a fresh delay window.
    fun clearAttempt(domain: String) {
        attempts.remove(domain)
    }
}
