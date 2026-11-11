package io.androllm.core.soul

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Veličina lika na ekranu (postavke "Alexa"). */
enum class AvatarSize(val dp: Int, val label: String) {
    SMALL(96, "Mali"),
    MEDIUM(140, "Srednji"),
    LARGE(200, "Veliki");

    companion object {
        fun fromName(name: String?): AvatarSize = entries.firstOrNull { it.name == name } ?: MEDIUM
    }
}

/**
 * Most između chata i plutajućeg lika: ChatViewModel šalje naredbu koju je korisnik
 * dao liku (npr. "Alexa, miči se"), a ekran s likom je izvršava.
 * Naredba koju niko ne sluša (lik isključen) se jednostavno odbacuje.
 */
@Singleton
class AvatarCommandBus @Inject constructor() {
    private val _commands = MutableSharedFlow<AvatarCommand>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val commands: SharedFlow<AvatarCommand> = _commands.asSharedFlow()

    fun send(command: AvatarCommand) {
        _commands.tryEmit(command)
    }
}

/** Kratka potvrda koju Alexa ispiše u chatu nakon naredbe za lik. */
fun avatarCommandReply(command: AvatarCommand): String = when (command) {
    AvatarCommand.COME_BACK -> "Evo me, vratila sam se."
    AvatarCommand.SIT_DOWN -> "Evo, sjedam."
    AvatarCommand.STAND_UP -> "Evo, ustala sam."
    AvatarCommand.LEAN -> "Naslonila sam se."
    else -> "Evo, pomjerila sam se."
}
