package ai.foxtouch.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface RecognitionState {
    data object Idle : RecognitionState
    data object Listening : RecognitionState
    data class Partial(val text: String) : RecognitionState
    data class Final(val text: String) : RecognitionState
    data class Error(val message: String) : RecognitionState
}

@Singleton
class SpeechRecognizerManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _state = MutableStateFlow<RecognitionState>(RecognitionState.Idle)
    val state: StateFlow<RecognitionState> = _state.asStateFlow()

    private val _results = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val results: Flow<String> = _results.asSharedFlow()

    val isListening: Boolean get() = _state.value is RecognitionState.Listening ||
            _state.value is RecognitionState.Partial

    private var recognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = RecognitionState.Listening
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            // Benign errors: silence timeout or no match — just return to idle
            if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                Log.d("SpeechRecognizer", "Benign error $error, returning to idle")
                _state.value = RecognitionState.Idle
                return
            }

            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Check microphone access."
                SpeechRecognizer.ERROR_CLIENT -> "Speech service unavailable. Try again."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                SpeechRecognizer.ERROR_NETWORK -> "Network error. Enable on-device recognition in Google Settings."
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy. Wait a moment."
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                else -> "Recognition error ($error)"
            }
            Log.w("SpeechRecognizer", "Error $error: $message")
            // On ERROR_CLIENT, destroy the recognizer so it's recreated next time
            if (error == SpeechRecognizer.ERROR_CLIENT) {
                destroyRecognizer()
            }
            _state.value = RecognitionState.Error(message)
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: ""
            if (text.isNotBlank()) {
                _state.value = RecognitionState.Final(text)
                _results.tryEmit(text)
                // Reset to Idle after a short delay so UI can react to Final state
                mainHandler.postDelayed({ _state.value = RecognitionState.Idle }, 300)
            } else {
                _state.value = RecognitionState.Idle
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: return
            if (text.isNotBlank()) {
                _state.value = RecognitionState.Partial(text)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun startListening() {
        // Must run on main thread — SpeechRecognizer requires it
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { startListening() }
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            _state.value = RecognitionState.Error(
                "Microphone permission required. Please grant it in Settings > Apps > FoxTouch > Permissions.",
            )
            return
        }

        // Stop any active listening first, but don't destroy the recognizer
        recognizer?.cancel()

        // Get or create recognizer
        val sr = getOrCreateRecognizer() ?: return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        _state.value = RecognitionState.Listening

        try {
            sr.startListening(intent)
        } catch (e: Exception) {
            Log.e("SpeechRecognizer", "startListening failed", e)
            destroyRecognizer()
            _state.value = RecognitionState.Error(
                "Failed to start voice input. Check Google app or speech recognition settings.",
            )
        }
    }

    /**
     * Reuse existing recognizer or create a new one.
     * Avoids the ERROR_CLIENT caused by rapid destroy+recreate cycles.
     */
    /**
     * Reuse existing recognizer or create a new one.
     * Skips [SpeechRecognizer.isRecognitionAvailable] pre-check because it returns
     * false on many Chinese ROMs and devices even when recognition works fine.
     * Instead, we try to create the recognizer directly and let actual errors
     * surface naturally via [RecognitionListener.onError].
     */
    private fun getOrCreateRecognizer(): SpeechRecognizer? {
        recognizer?.let { return it }

        val sr = try {
            // Prefer on-device recognizer (works without network, better for China market)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    Log.d("SpeechRecognizer", "Trying on-device recognizer")
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } catch (_: Exception) {
                    Log.d("SpeechRecognizer", "On-device unavailable, using default recognizer")
                    SpeechRecognizer.createSpeechRecognizer(context)
                }
            } else {
                Log.d("SpeechRecognizer", "Using default recognizer")
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        } catch (e: Exception) {
            Log.e("SpeechRecognizer", "Failed to create recognizer", e)
            _state.value = RecognitionState.Error(
                "Speech recognition unavailable. Check that a speech engine is installed (Google app, Samsung voice, etc.).",
            )
            return null
        }

        sr.setRecognitionListener(recognitionListener)
        recognizer = sr
        return sr
    }

    fun stopListening() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { stopListening() }
            return
        }

        recognizer?.apply {
            stopListening()
            cancel()
        }
        if (_state.value is RecognitionState.Listening || _state.value is RecognitionState.Partial) {
            _state.value = RecognitionState.Idle
        }
    }

    private fun destroyRecognizer() {
        recognizer?.destroy()
        recognizer = null
    }

    fun destroy() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { destroy() }
            return
        }
        recognizer?.apply {
            stopListening()
            cancel()
            destroy()
        }
        recognizer = null
    }
}
