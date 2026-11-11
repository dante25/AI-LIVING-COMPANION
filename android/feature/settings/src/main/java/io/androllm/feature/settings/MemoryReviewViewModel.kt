package io.androllm.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.androllm.core.common.onError
import io.androllm.core.memory.MemoryManager
import io.androllm.core.memory.MemoryStatus
import io.androllm.core.memory.model.Memory
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the "Memorija" screen: lists suggested and confirmed memories
 * and applies the user's decisions (confirm, reject, correct, edit, delete).
 * Corrected (superseded) memories are history and are not listed.
 */
@HiltViewModel
class MemoryReviewViewModel @Inject constructor(
    private val memoryManager: MemoryManager
) : ViewModel() {

    val memories: StateFlow<List<Memory>> = memoryManager.observeMemories()
        .map { list -> list.filter { it.status != MemoryStatus.CORRECTED } }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    /** Suggestion approved: from now on it may be used in Alexa's prompt. */
    fun confirm(id: String) {
        viewModelScope.launch {
            memoryManager.confirmMemory(id).onError { _message.value = "Potvrda nije uspjela." }
        }
    }

    /** Suggestion dismissed (deleted). */
    fun reject(id: String) {
        viewModelScope.launch {
            memoryManager.rejectMemory(id).onError { _message.value = "Odbacivanje nije uspjelo." }
        }
    }

    /** Explicit correction of a confirmed memory: the old claim is replaced by the new text. */
    fun correct(id: String, newContent: String) {
        viewModelScope.launch {
            memoryManager.correctMemory(id, newContent)
                .onError { _message.value = "Ispravka nije sačuvana. Provjerite tekst." }
        }
    }

    /** Edits the text of a pending suggestion before it is confirmed. */
    fun editSuggestion(id: String, newContent: String) {
        viewModelScope.launch {
            memoryManager.updateContent(id, newContent)
                .onError { _message.value = "Izmjena nije sačuvana. Provjerite tekst." }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            memoryManager.deleteMemory(id).onError { _message.value = "Brisanje nije uspjelo." }
        }
    }
}
