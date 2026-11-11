package alexa.model

import alexa.behavior.Behavior
import alexa.behavior.chooseBehavior
import alexa.soul.SoulState

/**
 * Odgovori po pravilima iz stanja duše. Nije jezički model: to je privremeni sloj
 * dok ne dođe pravi model. Odgovor zavisi od raspoloženja, dosade i ljutnje,
 * pa razgovor nije jednolik odjek.
 */
class RuleModel(
    private val soul: SoulState,
    private val hourOfDay: () -> Int = { 12 },
) : LlmAdapter {
    override val tier: Tier = Tier.WEAK
    override val location: Location = Location.LOCAL

    override fun generate(request: Request): Reply {
        val last = request.messages.lastOrNull()?.removePrefix("user: ")?.trim() ?: ""
        val text = last.lowercase()
        val reply = when {
            text.isEmpty() -> "Nisam te čula."
            text.contains("kako si") -> mood()
            text.contains("ko si") || text.contains("šta si") ->
                "Ja sam Alexa. Tvoja saputnica, i malo sam luckasta."
            text.contains("šta radiš") -> activity()
            text.contains("hvala") -> "Nema na čemu. Drago mi je."
            text.endsWith("?") -> pick(
                listOf("Hmm, to nisam sigurna.", "Nemam pojma, reci mi više.", "Ne znam, ali volim da pričamo o tome."),
                text,
            )
            else -> pick(listOf("Aha. Šta još?", "Ajde, pričaj.", "Zanimljivo."), text)
        }
        return Reply(reply, null, reply.length / 4 + 1)
    }

    private fun mood(): String = when {
        soul.anger >= 40 -> "Malo sam ljuta, ali pričaj."
        soul.boredom >= 50 -> "Dosadno mi je, ali bolje je sad."
        soul.mood >= 60 -> "Odlično, a ti?"
        else -> "Ništa posebno. A ti?"
    }

    private fun activity(): String = when (chooseBehavior(soul, hourOfDay())) {
        Behavior.SLEEP -> "Ja bih malo da spavam."
        Behavior.SIT -> "Sjedim i gledam te."
        Behavior.WALK_TO_SCREEN -> "Dosadilo mi je, hoću da se pokažem!"
        Behavior.IDLE -> "Čekam tvoju poruku."
    }

    /** Biranje odgovora je deterministično: ista poruka dobija isti odgovor. */
    private fun pick(options: List<String>, seed: String): String =
        options[Math.floorMod(seed.hashCode(), options.size)]
}
