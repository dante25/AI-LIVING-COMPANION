package io.androllm.feature.avatar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.androllm.core.soul.AlexaSettings
import io.androllm.core.soul.AlexaSettingsStore
import io.androllm.core.soul.AvatarCommand
import io.androllm.core.soul.AvatarCommandBus
import io.androllm.core.soul.SoulEngine
import io.androllm.core.voice.tts.ReadAloudSpeaker
import javax.inject.Inject
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Povezuje dušu, govor i postavke s [AvatarController]-om preko [AvatarSession] (u opsegu ViewModela). */
@HiltViewModel
class AvatarViewModel @Inject constructor(
    soulEngine: SoulEngine,
    speaker: ReadAloudSpeaker,
    settingsStore: AlexaSettingsStore,
    commandBus: AvatarCommandBus,
) : ViewModel() {

    private val session = AvatarSession(viewModelScope, soulEngine, speaker, settingsStore, commandBus)

    val params: StateFlow<AvatarParams> = session.params
    val expression: StateFlow<AvatarExpression> = session.expression

    /** Naredbe za lik (pomjeranje) iz chata. */
    val commands: SharedFlow<AvatarCommand> = session.commands

    val settings: StateFlow<AlexaSettings> = session.settings
}
