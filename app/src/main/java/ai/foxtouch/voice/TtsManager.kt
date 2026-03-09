package ai.foxtouch.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Android TextToSpeech engine for voice responses.
 * Thread-safe singleton; TTS callbacks arrive on an arbitrary thread.
 */
@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var tts: TextToSpeech? = null

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val utteranceCounter = AtomicInteger(0)

    init {
        tts = TextToSpeech(context) { status ->
            val ready = status == TextToSpeech.SUCCESS
            _isReady.value = ready
            if (ready) {
                val langResult = tts?.setLanguage(Locale.getDefault())
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w("TtsManager", "Default locale not supported, falling back to US English")
                    tts?.setLanguage(Locale.US)
                }
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    @Deprecated("Deprecated in API 21+", ReplaceWith("onError(utteranceId, errorCode)"))
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        _isSpeaking.value = false
                    }
                })
            }
        }
    }

    /**
     * Speak text aloud, stripping markdown formatting first.
     * Flushes any currently queued speech.
     */
    fun speak(text: String) {
        if (!_isReady.value) {
            Log.w("TtsManager", "TTS not ready, dropping: ${text.take(50)}")
            return
        }
        val clean = stripMarkdown(text)
        if (clean.isBlank()) return
        val id = "utt_${utteranceCounter.incrementAndGet()}"
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    /**
     * Queue text after currently speaking utterance.
     */
    fun speakQueued(text: String) {
        if (!_isReady.value) return
        val clean = stripMarkdown(text)
        if (clean.isBlank()) return
        val id = "utt_${utteranceCounter.incrementAndGet()}"
        tts?.speak(clean, TextToSpeech.QUEUE_ADD, null, id)
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate.coerceIn(0.25f, 4.0f))
    }

    fun setLanguage(locale: Locale) {
        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w("TtsManager", "Language $locale not supported, falling back to US English")
            tts?.setLanguage(Locale.US)
        }
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        _isReady.value = false
        _isSpeaking.value = false
    }

    companion object {
        /**
         * Remove markdown formatting so TTS reads natural text.
         */
        fun stripMarkdown(text: String): String {
            var result = text
            // Replace code blocks with "code block" placeholder
            result = result.replace(Regex("```[\\s\\S]*?```"), " code block ")
            // Replace links: [label](url) → label
            result = result.replace(Regex("\\[([^]]+)]\\(.*?\\)"), "$1")
            // Remove bold/italic markers, keep text
            result = result.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
            result = result.replace(Regex("\\*([^*]+)\\*"), "$1")
            // Remove other markdown syntax
            result = result.replace(Regex("`[^`]+`"), "")
            result = result.replace(Regex("!\\[.*?]\\(.*?\\)"), "")
            result = result.replace(Regex("#{1,6}\\s"), "")
            result = result.replace(Regex("^[\\s]*[-*+]\\s", RegexOption.MULTILINE), "")
            result = result.replace(Regex("^>\\s?", RegexOption.MULTILINE), "")
            result = result.replace(Regex("---+"), "")
            // Collapse whitespace
            result = result.replace(Regex("\\s+"), " ").trim()
            return result
        }
    }
}
