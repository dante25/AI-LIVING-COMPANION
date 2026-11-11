package io.androllm.feature.avatar

import android.os.SystemClock
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Povezuje dušu, govor i postavke s [AvatarController]-om i vrti animaciju (~30 fps). */
@HiltViewModel
class AvatarViewModel @Inject constructor(
    soulEngine: SoulEngine,
    speaker: ReadAloudSpeaker,
    settingsStore: AlexaSettingsStore,
    commandBus: AvatarCommandBus,
) : ViewModel() {

    private val controller = AvatarController()

    val params: StateFlow<AvatarParams> = controller.params
    val expression: StateFlow<AvatarExpression> = controller.expression

    /** Naredbe za lik (pomjeranje) iz chata. */
    val commands: SharedFlow<AvatarCommand> = commandBus.commands

    val settings: StateFlow<AlexaSettings> = settingsStore.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AlexaSettings()
    )

    init {
        viewModelScope.launch {
            combine(soulEngine.stateFlow, soulEngine.actionFlow) { state, action -> state to action }
                .collect { (state, action) -> controller.onSoul(state, action) }
        }
        viewModelScope.launch {
            speaker.speechLevel.collect { controller.onSpeechLevel(it) }
        }
        viewModelScope.launch {
            while (isActive) {
                if (settings.value.showAvatar) controller.step(SystemClock.elapsedRealtime())
                delay(FRAME_MS)
            }
        }
    }

    private companion object {
        const val FRAME_MS = 33L
    }
}
