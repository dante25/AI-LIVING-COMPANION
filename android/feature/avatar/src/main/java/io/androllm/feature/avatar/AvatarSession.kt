package io.androllm.feature.avatar

import android.os.SystemClock
import io.androllm.core.soul.AlexaSettings
import io.androllm.core.soul.AlexaSettingsStore
import io.androllm.core.soul.AvatarCommand
import io.androllm.core.soul.AvatarCommandBus
import io.androllm.core.soul.SoulEngine
import io.androllm.core.voice.tts.ReadAloudSpeaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Pogon lika bez Androida UI-ja: povezuje dušu, govor i postavke s [AvatarController]-om
 * i vrti animaciju (~30 fps) dok traje [scope]. Dijele ga [AvatarViewModel] (u aplikaciji)
 * i [AvatarOverlayService] (preko drugih aplikacija).
 */
class AvatarSession(
    scope: CoroutineScope,
    soulEngine: SoulEngine,
    speaker: ReadAloudSpeaker,
    settingsStore: AlexaSettingsStore,
    commandBus: AvatarCommandBus,
) {
    private val controller = AvatarController()

    val params: StateFlow<AvatarParams> = controller.params
    val expression: StateFlow<AvatarExpression> = controller.expression
    val commands: SharedFlow<AvatarCommand> = commandBus.commands

    val settings: StateFlow<AlexaSettings> = settingsStore.settings.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = AlexaSettings()
    )

    init {
        scope.launch {
            combine(soulEngine.stateFlow, soulEngine.actionFlow) { state, action -> state to action }
                .collect { (state, action) -> controller.onSoul(state, action) }
        }
        scope.launch {
            speaker.speechLevel.collect { controller.onSpeechLevel(it) }
        }
        scope.launch {
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
