package io.androllm.feature.chat.voice

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Thin holder so [ChatVoiceInput] lives and dies with the chat screen. */
@HiltViewModel
class ChatVoiceViewModel @Inject constructor(
    private val voiceInput: ChatVoiceInput
) : ViewModel() {

    val state: StateFlow<VoiceInputState> get() = voiceInput.state
    val transcripts: SharedFlow<VoiceTranscript> get() = voiceInput.transcripts

    suspend fun isMicBlockedByPolicy(): Boolean = voiceInput.isMicBlockedByPolicy()
    fun onMicPermissionGranted() = voiceInput.onMicPermissionGranted()
    fun start() = voiceInput.start()
    fun stopRecording() = voiceInput.stopRecording()
    fun confirmDownload() = voiceInput.confirmDownload()
    fun dismiss() = voiceInput.dismiss()
    fun cancel() = voiceInput.cancel()

    override fun onCleared() {
        voiceInput.release()
        super.onCleared()
    }
}
