package ai.foxtouch.voice

import android.content.Context
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceInputManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recognizer: SpeechRecognizerManager,
) {
    val recognitionState get() = recognizer.state
    val results: Flow<String> get() = recognizer.results
    val isListening: Boolean get() = recognizer.isListening
    val isRecognitionAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun startVoiceInput() = recognizer.startListening()
    fun stopVoiceInput() = recognizer.stopListening()
    fun toggleVoiceInput() {
        if (isListening) stopVoiceInput() else startVoiceInput()
    }
    fun destroy() = recognizer.destroy()
}
