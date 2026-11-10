package alexa.actions

import alexa.permissions.Capability
import alexa.permissions.PermissionGate

/**
 * Akcije van chata (aplikacije, pretraga, slanje). Svaka akcija prolazi kroz
 * dozvole i, ako je osjetljiva, kroz potvrdu. Rezultat se prijavljuje onako
 * kako jeste: neuspjeh se ne prikazuje kao uspjeh.
 */
enum class ActionKind(val capabilities: Set<Capability>, val sensitive: Boolean) {
    OPEN_APP(setOf(Capability.APP_CONTROL), sensitive = false),
    WEB_SEARCH(setOf(Capability.WEB_SEARCH, Capability.INTERNET), sensitive = false),
    SEND_MESSAGE(setOf(Capability.APP_CONTROL), sensitive = true),
}

data class ActionResult(val ok: Boolean, val detail: String)

fun interface ActionHandler {
    fun perform(kind: ActionKind, args: Map<String, String>): ActionResult
}

sealed interface ActionOutcome {
    data class Done(val result: ActionResult) : ActionOutcome
    data class Denied(val capability: Capability) : ActionOutcome
    data object Cancelled : ActionOutcome
    data class Invalid(val reason: String) : ActionOutcome
}

private fun requiredArgs(kind: ActionKind): List<String> = when (kind) {
    ActionKind.OPEN_APP -> listOf("app")
    ActionKind.WEB_SEARCH -> listOf("query")
    ActionKind.SEND_MESSAGE -> listOf("to", "text")
}

class ActionRunner(
    private val gate: PermissionGate,
    private val handler: ActionHandler,
) {
    fun run(
        kind: ActionKind,
        args: Map<String, String>,
        nowMs: Long,
        askPermission: (Capability) -> Boolean,
        confirm: (String) -> Boolean,
    ): ActionOutcome {
        val missing = requiredArgs(kind).filter { args[it].isNullOrBlank() }
        if (missing.isNotEmpty()) return ActionOutcome.Invalid("nedostaje: ${missing.joinToString()}")

        for (capability in kind.capabilities.sortedBy { it.name }) {
            if (!gate.request(capability, nowMs, askPermission)) return ActionOutcome.Denied(capability)
        }

        if (kind.sensitive) {
            val description = "${kind.name} ${args.values.joinToString(" ")}"
            if (!gate.confirmAction(description, nowMs, confirm)) return ActionOutcome.Cancelled
        }

        return ActionOutcome.Done(handler.perform(kind, args))
    }
}
