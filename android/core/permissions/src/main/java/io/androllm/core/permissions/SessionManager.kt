package io.androllm.core.permissions

import io.androllm.core.tools.api.ToolPermission
import io.androllm.core.tools.policy.ToolSessionState
import javax.inject.Inject
import javax.inject.Singleton

/** What the periodic timer should do after [SessionManager.tick]. */
enum class IdleAction {
    NONE,

    /** Session ended because of 15 minutes without activity (grants were reset). */
    SESSION_ENDED,

    /** 60 minutes without any activity: stop the voice assistant service. Reported once per idle period. */
    STOP_SERVICE
}

/**
 * Tracks the user's session for the permission policies.
 *
 * - A session ends after [SESSION_IDLE_MS] (15 min) without activity or when
 *   the chat is closed ([endSession]); ending it forgets every "once per
 *   session" grant.
 * - After [SERVICE_IDLE_MS] (60 min) without any activity [tick] reports
 *   [IdleAction.STOP_SERVICE] so the caller can stop the voice service.
 *
 * Time comes from [clock] (milliseconds) so tests can control it.
 */
@Singleton
class SessionManager(private val clock: () -> Long) : ToolSessionState {

    @Inject
    constructor() : this({ System.currentTimeMillis() })

    private val lock = Any()
    private var lastActivity: Long = clock()
    private var currentId: Long = 1L
    private var active = false
    private var serviceStopSignalled = false
    private val grants = mutableSetOf<ToolPermission>()

    /** Reports activity. Starts a new session if the previous one expired or ended. */
    override fun touch() {
        synchronized(lock) {
            val now = clock()
            expireIfIdle(now)
            if (!active) active = true
            lastActivity = now
            serviceStopSignalled = false
        }
    }

    override fun sessionId(): Long = synchronized(lock) {
        expireIfIdle(clock())
        currentId
    }

    override fun isGranted(permission: ToolPermission): Boolean = synchronized(lock) {
        expireIfIdle(clock())
        permission in grants
    }

    override fun grant(permission: ToolPermission) {
        synchronized(lock) {
            expireIfIdle(clock())
            grants += permission
        }
    }

    /** True while a session is running (and has not idled out). */
    fun isSessionActive(): Boolean = synchronized(lock) {
        expireIfIdle(clock())
        active
    }

    /** Ends the session now (chat closed): forgets all session grants. */
    fun endSession() {
        synchronized(lock) { finishSession() }
    }

    /** Called periodically (about once a minute) by the application. */
    fun tick(): IdleAction = synchronized(lock) {
        val now = clock()
        val idle = now - lastActivity
        val ended = expireIfIdle(now)
        when {
            idle >= SERVICE_IDLE_MS && !serviceStopSignalled -> {
                serviceStopSignalled = true
                IdleAction.STOP_SERVICE
            }
            ended -> IdleAction.SESSION_ENDED
            else -> IdleAction.NONE
        }
    }

    /** Must be called with [lock] held. Returns true when a session was ended. */
    private fun expireIfIdle(now: Long): Boolean {
        if (active && now - lastActivity >= SESSION_IDLE_MS) {
            finishSession()
            return true
        }
        return false
    }

    private fun finishSession() {
        if (active) currentId++
        active = false
        grants.clear()
    }

    companion object {
        const val SESSION_IDLE_MS = 15 * 60 * 1000L
        const val SERVICE_IDLE_MS = 60 * 60 * 1000L
    }
}
