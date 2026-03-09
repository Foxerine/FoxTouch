package ai.foxtouch.assistant

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/**
 * Factory that creates [FoxTouchAssistantSession] instances when the user invokes the assistant.
 */
class FoxTouchSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return FoxTouchAssistantSession(this)
    }
}
