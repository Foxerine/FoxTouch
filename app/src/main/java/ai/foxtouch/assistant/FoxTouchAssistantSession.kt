package ai.foxtouch.assistant

import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import ai.foxtouch.accessibility.OverlayController
import ai.foxtouch.agent.AgentRunner
import ai.foxtouch.data.preferences.AppSettings
import ai.foxtouch.ui.theme.FoxTouchTheme
import ai.foxtouch.voice.TtsManager
import ai.foxtouch.voice.VoiceInputManager
import dagger.hilt.android.EntryPointAccessors

/**
 * The overlay session shown when the user triggers the FoxTouch assistant.
 *
 * Implements [LifecycleOwner] and [SavedStateRegistryOwner] so that Compose
 * can function correctly outside of an Activity/Fragment context.
 *
 * Uses [EntryPointAccessors.fromApplication] to obtain Hilt-managed singletons
 * since VoiceInteractionSession is not a standard Hilt component.
 */
class FoxTouchAssistantSession(context: Context) : VoiceInteractionSession(context),
    LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private val entryPoint: AssistantSessionEntryPoint by lazy {
        EntryPointAccessors.fromApplication(context, AssistantSessionEntryPoint::class.java)
    }

    private val agentRunner: AgentRunner by lazy { entryPoint.agentRunner() }
    private val ttsManager: TtsManager by lazy { entryPoint.ttsManager() }
    private val voiceInputManager: VoiceInputManager by lazy { entryPoint.voiceInputManager() }
    private val appSettings: AppSettings by lazy { entryPoint.appSettings() }

    /** Retained reference to the content view for [OverlayController] registration. */
    private var contentView: View? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onCreateContentView(): View {
        return ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@FoxTouchAssistantSession)
            setViewTreeSavedStateRegistryOwner(this@FoxTouchAssistantSession)
            setContent {
                FoxTouchTheme {
                    AssistantOverlayPanel(
                        agentRunner = agentRunner,
                        ttsManager = ttsManager,
                        voiceInputManager = voiceInputManager,
                        appSettings = appSettings,
                        onDismiss = { hide() },
                    )
                }
            }
        }.also { contentView = it }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        // Ensure the session window is visible
        try {
            val w = window?.window
            w?.setBackgroundDrawableResource(android.R.color.transparent)
        } catch (_: Exception) {
            // Some devices may not support window customization here
        }
        contentView?.let { OverlayController.register(it) }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onHide() {
        contentView?.let { OverlayController.unregister(it) }
        ttsManager.stop()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onHide()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        voiceInputManager.destroy()
        super.onDestroy()
    }
}
