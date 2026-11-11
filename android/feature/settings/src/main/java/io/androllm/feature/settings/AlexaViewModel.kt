package io.androllm.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.androllm.core.soul.AlexaSettings
import io.androllm.core.soul.AlexaSettingsStore
import io.androllm.core.soul.DEFAULT_ALEXA_PERSONA
import io.androllm.core.soul.SoulEngine
import io.androllm.core.soul.SoulState
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Stanje i radnje za sekciju postavki "Alexa" (karakter i duša). */
@HiltViewModel
class AlexaViewModel @Inject constructor(
    private val store: AlexaSettingsStore,
    private val soulEngine: SoulEngine
) : ViewModel() {

    val settings: StateFlow<AlexaSettings> = store.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AlexaSettings()
    )

    val soulState: StateFlow<SoulState> = soulEngine.stateFlow

    /** Sačuvaj karakter; prazan ili podrazumijevani tekst znači podrazumijevani karakter. */
    fun savePersona(text: String) {
        val value = if (text.isBlank() || text.trim() == DEFAULT_ALEXA_PERSONA) "" else text.trim()
        viewModelScope.launch { store.update { it.copy(personaOverride = value) } }
    }

    fun restoreDefaultPersona() {
        viewModelScope.launch { store.update { it.copy(personaOverride = "") } }
    }

    fun setSoulEnabled(enabled: Boolean) {
        viewModelScope.launch { store.update { it.copy(soulEnabled = enabled) } }
    }

    fun resetSoul() {
        soulEngine.reset()
    }
}
