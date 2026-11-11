package io.androllm.core.tools.policy

import io.androllm.core.tools.api.ToolPermission
import io.androllm.core.tools.api.alsoRequires

/**
 * How a logical permission ([ToolPermission]) is handled when a tool needs it.
 *
 * - [OFF]: always blocked.
 * - [ASK_EVERY_TIME]: the user is asked before every single use.
 * - [SESSION]: the user is asked once per session; the grant is forgotten when
 *   the session ends (see [ToolSessionState]).
 */
enum class PermissionPolicy {
    OFF,
    ASK_EVERY_TIME,
    SESSION;

    companion object {
        /** Policy used for a permission the user has not configured. */
        val DEFAULT: PermissionPolicy = SESSION

        /** Policies the settings UI may offer for [permission]. */
        fun optionsFor(permission: ToolPermission): List<PermissionPolicy> =
            if (permission == ToolPermission.CAMERA) listOf(OFF, ASK_EVERY_TIME)
            else entries.toList()

        /**
         * Effective policy for [permission] given the user's [configured] choice.
         * The camera can never be granted persistently: it is always
         * [ASK_EVERY_TIME] (or [OFF] when the user disabled it).
         */
        fun effective(permission: ToolPermission, configured: PermissionPolicy?): PermissionPolicy {
            val policy = configured ?: if (permission == ToolPermission.CAMERA) ASK_EVERY_TIME else DEFAULT
            return if (permission == ToolPermission.CAMERA && policy == SESSION) ASK_EVERY_TIME else policy
        }
    }
}

/**
 * Per-session state the executor needs, implemented by
 * `io.androllm.core.permissions.SessionManager`. Lives in this module so
 * `core:tools` does not depend on `core:permissions`.
 */
interface ToolSessionState {
    /** Records user/assistant activity (keeps the session alive). */
    fun touch()

    /** Identifier of the current session (changes when a session ends and a new one starts). */
    fun sessionId(): Long

    /** True when [permission] was already approved in the current session. */
    fun isGranted(permission: ToolPermission): Boolean

    /** Remembers an approval of [permission] until the session ends. */
    fun grant(permission: ToolPermission)
}

/** Outcome of evaluating the policies for one tool call. */
sealed class PolicyVerdict {
    /** No policy prompt needed. */
    data object Allow : PolicyVerdict()

    /** A policy is OFF for [permission]; the call must not run. */
    data class Blocked(val permission: ToolPermission) : PolicyVerdict()

    /**
     * The user must approve first. [sessionGrantable] lists the permissions that
     * should be remembered for the session when the user approves.
     */
    data class Ask(val sessionGrantable: List<ToolPermission>) : PolicyVerdict()
}

/** Pure policy evaluation (no Android dependencies, unit-testable). */
object PermissionPolicyEvaluator {

    /** The permission plus the umbrella permissions it also requires. */
    fun requiredFor(permission: ToolPermission?): List<ToolPermission> =
        if (permission == null) emptyList() else listOf(permission) + permission.alsoRequires()

    fun evaluate(
        permission: ToolPermission?,
        policies: Map<String, PermissionPolicy>,
        session: ToolSessionState
    ): PolicyVerdict {
        val required = requiredFor(permission)
        val effective = required.map { it to PermissionPolicy.effective(it, policies[it.name]) }

        effective.firstOrNull { it.second == PermissionPolicy.OFF }?.let {
            return PolicyVerdict.Blocked(it.first)
        }

        var ask = false
        val grantable = mutableListOf<ToolPermission>()
        for ((perm, policy) in effective) {
            when (policy) {
                PermissionPolicy.ASK_EVERY_TIME -> ask = true
                PermissionPolicy.SESSION -> if (!session.isGranted(perm)) {
                    ask = true
                    grantable += perm
                }
                PermissionPolicy.OFF -> Unit
            }
        }
        return if (ask) PolicyVerdict.Ask(grantable) else PolicyVerdict.Allow
    }
}
