package ai.foxtouch.assistant

import android.service.voice.VoiceInteractionService

/**
 * System-level VoiceInteractionService that registers FoxTouch as an Android digital assistant.
 *
 * Users can select FoxTouch in Settings > Default Apps > Digital Assistant App.
 * When triggered (long-press Home, corner swipe, etc.), the system creates a
 * [FoxTouchSessionService] which in turn produces the overlay UI.
 *
 * Note: VoiceInteractionService does not support Hilt @AndroidEntryPoint.
 * No injection is needed here; dependencies are obtained via EntryPointAccessors
 * in [FoxTouchAssistantSession].
 */
class FoxTouchVoiceInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
    }
}
