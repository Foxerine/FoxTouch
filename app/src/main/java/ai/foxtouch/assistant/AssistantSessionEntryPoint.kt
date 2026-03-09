package ai.foxtouch.assistant

import ai.foxtouch.agent.AgentRunner
import ai.foxtouch.data.preferences.AppSettings
import ai.foxtouch.voice.TtsManager
import ai.foxtouch.voice.VoiceInputManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt entry point for [FoxTouchAssistantSession].
 *
 * VoiceInteractionSession is not a standard Hilt-supported component, so we use
 * [dagger.hilt.android.EntryPointAccessors.fromApplication] to retrieve singletons.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AssistantSessionEntryPoint {
    fun agentRunner(): AgentRunner
    fun ttsManager(): TtsManager
    fun voiceInputManager(): VoiceInputManager
    fun appSettings(): AppSettings
}
