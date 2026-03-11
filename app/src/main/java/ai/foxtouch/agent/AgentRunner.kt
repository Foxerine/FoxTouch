package ai.foxtouch.agent

import android.content.Context
import android.util.Log
import ai.foxtouch.accessibility.AccessibilityBridge
import ai.foxtouch.accessibility.OverlayController
import ai.foxtouch.accessibility.TouchAnimationOverlay
import ai.foxtouch.data.db.entity.TaskEntity
import ai.foxtouch.data.preferences.AgentMode
import ai.foxtouch.data.preferences.ApiKeyStore
import ai.foxtouch.data.preferences.AppSettings
import ai.foxtouch.data.repository.SessionRepository
import ai.foxtouch.data.repository.TaskRepository
import ai.foxtouch.di.ApplicationScope
import ai.foxtouch.permission.PermissionPolicy
import ai.foxtouch.tools.ToolDisplayRegistry
import ai.foxtouch.tools.ToolRegistry
import ai.foxtouch.tools.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AgentState {
    data object Idle : AgentState
    data object Thinking : AgentState
    data class Acting(val toolName: String, val displayText: String = toolName) : AgentState
    data class WaitingApproval(
        val toolName: String,
        val args: JsonObject,
        val callId: String,
    ) : AgentState
    /** The LLM called exit_plan_mode — waiting for user to approve/reject/modify. */
    data class PlanReview(val planContent: String, val suggestSave: Boolean = false) : AgentState
    /** The LLM called ask_user — waiting for user to answer a question. */
    data class AskingUser(val question: String, val suggestions: List<String> = emptyList()) : AgentState
    /** The LLM called confirm_completion — waiting for user to confirm task completion. */
    data class ConfirmingCompletion(val summary: String) : AgentState
    data object Error : AgentState
}

sealed interface AgentOutput {
    data class Text(val content: String) : AgentOutput
    data class ToolExecution(
        val toolName: String,
        val args: JsonObject,
        val result: String,
        val imageBase64: String? = null,
    ) : AgentOutput
    data class Error(val message: String) : AgentOutput
}

/** User's response to an approval request. */
enum class ApprovalResponse { ALLOW, DENY, ALWAYS_ALLOW, ALLOW_ALL }

/** User's response to a plan review. */
sealed interface PlanApprovalResponse {
    data class ApproveNormal(val saveAsSkill: Boolean = false) : PlanApprovalResponse
    data class ApproveYolo(val saveAsSkill: Boolean = false) : PlanApprovalResponse
    data object Reject : PlanApprovalResponse
    data class Modify(val reason: String) : PlanApprovalResponse
}

/** User's response to a completion confirmation. */
sealed interface CompletionResponse {
    data object Confirmed : CompletionResponse
    data class NotDone(val reason: String) : CompletionResponse
    data object Dismissed : CompletionResponse
}

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel = LogLevel.DEBUG,
    val message: String,
)

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

@Singleton
class AgentRunner @Inject constructor(
    @ApplicationContext private val appContext: Context,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val httpClient: HttpClient,
    private val json: Json,
    private val apiKeyStore: ApiKeyStore,
    private val appSettings: AppSettings,
    private val toolRegistry: ToolRegistry,
    private val deviceContext: DeviceContext,
    private val taskRepository: TaskRepository,
    private val sessionRepository: SessionRepository,
    private val agentDocsManager: AgentDocsManager,
) {
    companion object {
        private const val TAG = "FoxTouch.Agent"

        /** Tools that physically interact with the screen — overlay must hide during execution. */
        private val INTERACTION_TOOL_NAMES = setOf(
            "click_element", "tap", "type_text", "type_at", "long_press", "scroll", "swipe", "pinch",
            "back", "home",
        )
    }

    private val _state = MutableStateFlow<AgentState>(AgentState.Idle)
    val state: StateFlow<AgentState> = _state.asStateFlow()

    val approvalChannel = Channel<ApprovalResponse>(capacity = 1)
    val planApprovalChannel = Channel<PlanApprovalResponse>(capacity = 1)
    val userAnswerChannel = Channel<String>(capacity = 1)
    val completionChannel = Channel<CompletionResponse>(capacity = 1)

    private val _outputFlow = MutableSharedFlow<AgentOutput>(extraBufferCapacity = Int.MAX_VALUE)
    val outputFlow: SharedFlow<AgentOutput> = _outputFlow.asSharedFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _logFlow = MutableSharedFlow<LogEntry>(replay = 256, extraBufferCapacity = 64)
    val logFlow: SharedFlow<LogEntry> = _logFlow.asSharedFlow()

    data class ContextUsage(val usedTokens: Int, val threshold: Int, val remainingPercent: Int)
    private var lastPromptTokens: Int = 0
    private val _contextUsage = MutableStateFlow<ContextUsage?>(null)
    val contextUsage: StateFlow<ContextUsage?> = _contextUsage.asStateFlow()

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: String? get() = _currentSessionId.value

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val sessionTasks: StateFlow<List<TaskEntity>> = _currentSessionId
        .flatMapLatest { id ->
            if (id != null) taskRepository.getBySession(id) else flowOf(emptyList())
        }
        .stateIn(applicationScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setCurrentSessionId(sessionId: String?) {
        _currentSessionId.value = sessionId
    }

    private fun agentLog(message: String, level: LogLevel = LogLevel.DEBUG) {
        when (level) {
            LogLevel.DEBUG -> Log.d(TAG, message)
            LogLevel.INFO -> Log.i(TAG, message)
            LogLevel.WARN -> Log.w(TAG, message)
            LogLevel.ERROR -> Log.e(TAG, message)
        }
        _logFlow.tryEmit(LogEntry(message = message, level = level))
    }

    private val turnMutex = Mutex()
    private var currentTurnJob: Job? = null

    private val conversationHistory = mutableListOf<ChatMessage>()
    private var currentProvider: LLMProvider? = null
    private var proxiedHttpClient: HttpClient? = null

    init {
        // Invalidate cached provider when the selected provider changes
        applicationScope.launch {
            appSettings.provider.distinctUntilChanged().collect {
                proxiedHttpClient?.close()
                proxiedHttpClient = null
                currentProvider = null
            }
        }
    }

    private fun buildHttpClientWithProxy(proxyStr: String): HttpClient {
        return HttpClient(OkHttp) {
            install(ContentNegotiation) { json(json) }
            engine {
                config {
                    val parts = proxyStr.replace("http://", "").replace("https://", "").split(":")
                    val host = parts.getOrNull(0) ?: return@config
                    val port = parts.getOrNull(1)?.toIntOrNull() ?: 7890
                    proxy(java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress(host, port)))
                }
            }
        }
    }

    private suspend fun getEffectiveHttpClient(providerName: String): HttpClient {
        val proxyStr = appSettings.getProviderProxyOnce(providerName)
        if (proxyStr.isBlank()) return httpClient

        proxiedHttpClient?.let { return it }

        val client = buildHttpClientWithProxy(proxyStr)
        proxiedHttpClient = client
        return client
    }

    private suspend fun getOrCreateProvider(): LLMProvider {
        currentProvider?.let { return it }
        val providerName = appSettings.getProviderOnce()
        val apiKey = apiKeyStore.getApiKey(providerName).first()
            ?: throw IllegalStateException("No API key configured for $providerName. Go to Settings to add one.")
        val customBaseUrl = appSettings.getProviderBaseUrlOnce(providerName)
        val proxyStr = appSettings.getProviderProxyOnce(providerName).ifBlank { null }
        val client = getEffectiveHttpClient(providerName)

        val provider = when (providerName) {
            "gemini" -> GeminiProvider(client, json, apiKey,
                baseUrl = customBaseUrl.ifBlank { "https://generativelanguage.googleapis.com/v1beta" },
                onLog = { msg -> agentLog("[Gemini] $msg") })
            "openai" -> OpenAICompatibleProvider(json, apiKey,
                baseUrl = customBaseUrl.ifBlank { "https://api.openai.com/v1" },
                proxyUrl = proxyStr)
            "claude" -> ClaudeProvider(client, json, apiKey,
                baseUrl = customBaseUrl.ifBlank { "https://api.anthropic.com/v1" })
            else -> OpenAICompatibleProvider(json, apiKey, id = providerName,
                baseUrl = customBaseUrl.ifBlank { "https://api.openai.com/v1" },
                proxyUrl = proxyStr)
        }
        currentProvider = provider
        return provider
    }

    fun clearHistory() {
        conversationHistory.clear()
        lastPromptTokens = 0
        _contextUsage.value = null
    }

    /** Show a highlight border around the target element for click approval preview. */
    private fun showClickHighlight(args: kotlinx.serialization.json.JsonObject) {
        val elementId = args["element_id"]?.jsonPrimitive?.content?.toIntOrNull()
        if (elementId != null) {
            val bounds = AccessibilityBridge.getElementBounds(elementId)
            if (bounds != null) {
                TouchAnimationOverlay.showHighlight(bounds.left, bounds.top, bounds.right, bounds.bottom)
            }
        }
    }

    /** Called by UI to send the user's approval decision. Safe to call from multiple UIs. */
    fun respondToApproval(response: ApprovalResponse) {
        approvalChannel.trySend(response)
    }

    /** Called by UI to respond to a plan review. Immediately transitions state to avoid overlap. */
    fun respondToPlanApproval(response: PlanApprovalResponse) {
        if (_state.value is AgentState.PlanReview) {
            _state.value = AgentState.Acting("exit_plan_mode")
        }
        planApprovalChannel.trySend(response)
    }

    /** Called by ExitPlanModeTool to show the plan for user review. */
    fun emitPlanReviewState(planContent: String, suggestSave: Boolean = false) {
        planApprovalChannel.tryReceive() // drain any stale approval
        _state.value = AgentState.PlanReview(planContent, suggestSave)
    }

    /** Called by AskUserTool to show a question to the user. */
    fun emitAskUserState(question: String, suggestions: List<String> = emptyList()) {
        userAnswerChannel.tryReceive() // drain any stale answer
        _state.value = AgentState.AskingUser(question, suggestions)
    }

    /** Called by UI to respond to an ask_user question. Immediately transitions state to avoid overlap. */
    fun respondToUserQuestion(answer: String) {
        if (_state.value is AgentState.AskingUser) {
            _state.value = AgentState.Acting("ask_user")
        }
        userAnswerChannel.trySend(answer)
    }

    /** Called by ConfirmCompletionTool to show a completion summary for user review. */
    fun emitConfirmCompletionState(summary: String) {
        completionChannel.tryReceive() // drain any stale response
        _state.value = AgentState.ConfirmingCompletion(summary)
    }

    /** Called by UI to respond to a completion confirmation. Immediately transitions state to avoid overlap. */
    fun respondToCompletion(response: CompletionResponse) {
        if (_state.value is AgentState.ConfirmingCompletion) {
            _state.value = AgentState.Acting("confirm_completion")
        }
        completionChannel.trySend(response)
    }

    // ── Task status reminder ────────────────────────────────────

    /**
     * Build a transient system message with current task status.
     * Injected before each LLM call so the model is always aware of task progress.
     * Returns null if there are no tasks.
     */
    private fun buildTaskReminder(): String? {
        val tasks = sessionTasks.value
        if (tasks.isEmpty()) return null

        val sb = StringBuilder("[TASK STATUS]\n")
        sb.appendLine("Current tasks (update each task to 'completed' IMMEDIATELY after finishing it, not in batch):")
        for (task in tasks) {
            val icon = when (task.status) {
                "completed" -> "[DONE]"
                "in_progress" -> "[IN PROGRESS]"
                "failed" -> "[FAILED]"
                else -> "[PENDING]"
            }
            sb.appendLine("  $icon ${task.title} (id=${task.id})")
        }
        val stale = tasks.filter { it.status == "completed" || it.status == "failed" }
        if (stale.size == tasks.size) {
            sb.appendLine("\nAll tasks are done. Call confirm_completion if the overall goal is achieved.")
        }
        return sb.toString()
    }

    // ── Plan file management ─────────────────────────────────────

    private var currentPlanFilePath: File? = null

    fun getOrCreatePlanFile(sessionId: String): File {
        val dir = File(appContext.filesDir, "plans")
        dir.mkdirs()
        val file = File(dir, "$sessionId.md")
        if (!file.exists()) file.createNewFile()
        currentPlanFilePath = file
        return file
    }

    fun readPlanFile(): String {
        return currentPlanFilePath?.takeIf { it.exists() }?.readText() ?: ""
    }

    // ── AI-initiated plan mode entry ─────────────────────────────

    /**
     * Set by EnterPlanModeTool — signals runTurnInternal to switch
     * from normal mode to plan mode on the next iteration.
     */
    @Volatile
    private var pendingPlanModeEntry: Boolean = false

    /** The mode that was active before entering plan mode, so we can restore it on exit. */
    @Volatile
    private var prePlanMode: AgentMode? = null

    fun requestPlanModeEntry() {
        pendingPlanModeEntry = true
    }

    /**
     * Set by ExitPlanModeTool when user approves — signals runTurnInternal
     * to switch from plan mode to execution mode on the next iteration.
     */
    @Volatile
    private var pendingModeSwitch: AgentMode? = null

    fun setPendingModeSwitch(mode: AgentMode) {
        pendingModeSwitch = mode
    }

    /** Returns the mode that was active before entering plan mode. */
    fun getPrePlanMode(): AgentMode = prePlanMode ?: AgentMode.NORMAL

    fun startTurn(userMessage: String, isPlanMode: Boolean = false) {
        applicationScope.launch {
            turnMutex.withLock {
                if (_isBusy.value) return@launch
                _isBusy.value = true
            }
            AgentForegroundService.start(appContext)
            currentTurnJob = launch {
                try {
                    runTurnInternal(userMessage, isPlanMode) { output ->
                        _outputFlow.emit(output)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    agentLog("Agent turn failed: ${e.message}", LogLevel.ERROR)
                    _outputFlow.emit(AgentOutput.Error("Agent error: ${e.message}"))
                } finally {
                    _state.value = AgentState.Idle
                    _isBusy.value = false
                }
            }
        }
    }

    fun cancelCurrentTurn() {
        // Unblock any pending ask_user / confirm_completion before cancelling
        drainPendingInteractions()
        currentTurnJob?.cancel()
        currentTurnJob = null
    }

    /**
     * If the agent is blocked waiting for user interaction (ask_user, confirm_completion),
     * send a default response to unblock the tool so cancellation can proceed cleanly.
     */
    fun drainPendingInteractions() {
        if (_state.value is AgentState.AskingUser) {
            respondToUserQuestion("(User did not answer)")
        }
        if (_state.value is AgentState.ConfirmingCompletion) {
            respondToCompletion(CompletionResponse.Dismissed)
        }
    }

    private suspend fun performCompaction(provider: LLMProvider, currentModel: String): String? {
        val compactModel = appSettings.getCompactModelOnce().ifBlank { currentModel }
        val compactPrompt = agentDocsManager.readCompactPrompt()
        val conversationText = ContextCompactor.formatConversationForSummary(conversationHistory)
        val messages = listOf(
            ChatMessage(role = "system", content = compactPrompt),
            ChatMessage(role = "user", content = conversationText),
        )
        return try {
            val sb = StringBuilder()
            provider.chat(messages, emptyList(), compactModel, streaming = false, thinking = false).collect { event ->
                if (event is LLMEvent.TextDelta) sb.append(event.text)
            }
            val summary = sb.toString()
            if (summary.isBlank()) null else summary
        } catch (e: Exception) {
            agentLog("Compaction failed: ${e.message}", LogLevel.ERROR)
            null
        }
    }

    private suspend fun runTurnInternal(
        userMessage: String,
        isPlanMode: Boolean,
        onOutput: suspend (AgentOutput) -> Unit,
    ) {
        val provider = getOrCreateProvider()
        val model = appSettings.getModelOnce()
        var mode = appSettings.getAgentModeOnce()
        var effectivePlanMode = isPlanMode || mode == AgentMode.PLAN

        val streaming = appSettings.getStreamingEnabledOnce()
        val thinking = appSettings.getThinkingEnabledOnce()
        agentLog("Turn started: model=$model, provider=${provider.id}, mode=$mode, plan=$effectivePlanMode, streaming=$streaming, thinking=$thinking", LogLevel.INFO)

        // Initialize system prompt on first message
        if (conversationHistory.isEmpty()) {
            val deviceContextBlock = deviceContext.gather()
            val basePrompt = SystemPrompts.buildAgentSystemPrompt(deviceContextBlock)
            val systemPrompt = if (effectivePlanMode) {
                basePrompt + "\n\n" + SystemPrompts.PLAN_MODE_PROMPT
            } else {
                basePrompt
            }
            conversationHistory.add(ChatMessage(role = "system", content = systemPrompt))
            agentLog("System prompt initialized (${systemPrompt.length} chars)")
        }

        conversationHistory.add(ChatMessage(role = "user", content = userMessage))
        agentLog("User: ${userMessage.take(100)}")

        // Reset pending switches
        pendingModeSwitch = null
        pendingPlanModeEntry = false

        var iterations = 0
        val maxIterations = appSettings.getMaxIterationsOnce()

        while (iterations < maxIterations) {
            iterations++
            currentCoroutineContext().ensureActive()
            _state.value = AgentState.Thinking

            // Recompute available tools each iteration — plan approval may switch modes
            val availableTools = if (effectivePlanMode) {
                toolRegistry.getReadOnlyTools()
            } else {
                toolRegistry.getAllTools()
            }
            val toolDefinitions = availableTools.map { it.definition }
            // Inject task status as a transient system reminder (second segment)
            val taskReminder = buildTaskReminder()
            if (taskReminder != null) {
                // Remove previous task reminder if present
                conversationHistory.removeAll { it.role == "system" && it.content?.startsWith("[TASK STATUS]") == true }
                conversationHistory.add(ChatMessage(role = "system", content = taskReminder))
            }

            agentLog("Iteration $iterations/$maxIterations, sending to LLM (${conversationHistory.size} msgs, ${toolDefinitions.size} tools)")

            // Collect the full LLM response
            val textBuffer = StringBuilder()
            val allToolCalls = mutableListOf<ToolCall>()
            var llmError: String? = null

            try {
                provider.chat(conversationHistory, toolDefinitions, model, streaming, thinking).collect { event ->
                    when (event) {
                        is LLMEvent.TextDelta -> textBuffer.append(event.text)
                        is LLMEvent.ToolCallRequest -> allToolCalls.addAll(event.calls)
                        is LLMEvent.Usage -> {
                            agentLog("Usage: ${event.promptTokens} prompt, ${event.completionTokens} completion, ${event.totalTokens} total")
                            lastPromptTokens = event.promptTokens
                            _currentSessionId.value?.let { sid ->
                                applicationScope.launch { sessionRepository.updateTokenCount(sid, event.promptTokens) }
                            }
                        }
                        is LLMEvent.Error -> {
                            agentLog("LLM error: ${event.message}", LogLevel.ERROR)
                            llmError = event.message
                        }
                        is LLMEvent.Done -> {}
                    }
                }
            } catch (e: CancellationException) {
                agentLog("Turn cancelled by user", LogLevel.INFO)
                throw e
            } catch (e: Exception) {
                agentLog("LLM request failed: ${e.message}", LogLevel.ERROR)
                _state.value = AgentState.Error
                onOutput(AgentOutput.Error("LLM request failed: ${e.message}"))
                return
            }

            // If the LLM returned an error and no useful content, report and stop
            if (llmError != null && textBuffer.isEmpty() && allToolCalls.isEmpty()) {
                _state.value = AgentState.Error
                onOutput(AgentOutput.Error(llmError!!))
                return
            }

            val responseText = textBuffer.toString()
            val calls = allToolCalls.ifEmpty { null }
            agentLog("LLM response: ${responseText.length} chars text, ${calls?.size ?: 0} tool calls")

            if (responseText.isNotBlank()) {
                onOutput(AgentOutput.Text(responseText))
            }

            if (calls.isNullOrEmpty()) {
                if (responseText.isBlank()) {
                    agentLog("LLM returned empty response (no text, no tools)", LogLevel.WARN)
                    onOutput(AgentOutput.Error("Model returned empty response. Try again or switch models."))
                    _state.value = AgentState.Error
                    return
                }
                conversationHistory.add(ChatMessage(role = "assistant", content = responseText))
                agentLog("Turn complete (no tool calls)", LogLevel.INFO)
                _state.value = AgentState.Idle
                return
            }

            // Assistant message with tool calls (and optional text)
            conversationHistory.add(ChatMessage(
                role = "assistant",
                content = responseText.ifBlank { null },
                toolCalls = calls,
            ))

            // Execute each tool call
            var pendingScreenshot: String? = null
            var taskConfirmedThisTurn = false

            for (call in calls) {
                val displayText = ToolDisplayRegistry.formatArgs(call.name, call.arguments)
                _state.value = AgentState.Acting(call.name, displayText)
                agentLog("Tool call: ${call.name}(${call.arguments})")

                val tool = availableTools.find { it.definition.name == call.name }
                if (tool == null) {
                    agentLog("Unknown tool: ${call.name}", LogLevel.WARN)
                    val errorResult = "Error: Unknown tool '${call.name}'"
                    conversationHistory.add(ChatMessage(role = "tool", content = errorResult, toolCallId = call.id, toolName = call.name))
                    onOutput(AgentOutput.Error(errorResult))
                    continue
                }

                // Permission check — YOLO mode skips all approval
                val permitted = if (mode == AgentMode.YOLO) {
                    agentLog("YOLO: auto-approve ${call.name}")
                    true
                } else {
                    val policy = toolRegistry.getPermissionPolicy(call.name)
                    agentLog("Permission ${call.name}: $policy")
                    when (policy) {
                        PermissionPolicy.ALWAYS_ALLOW -> true
                        PermissionPolicy.NEVER_ALLOW -> false
                        PermissionPolicy.ASK_EACH_TIME -> {
                            _state.value = AgentState.WaitingApproval(call.name, call.arguments, call.id)
                            // Show highlight on target element for click tool
                            if (call.name == "click_element" || call.name == "tap") {
                                showClickHighlight(call.arguments)
                            }
                            agentLog("Waiting for approval: ${call.name}")
                            val response = approvalChannel.receive()
                            // Clear highlight after approval decision
                            TouchAnimationOverlay.hideHighlight()
                            when (response) {
                                ApprovalResponse.ALLOW -> true
                                ApprovalResponse.DENY -> false
                                ApprovalResponse.ALWAYS_ALLOW -> {
                                    toolRegistry.setPermissionPolicy(call.name, PermissionPolicy.ALWAYS_ALLOW)
                                    agentLog("Set ${call.name} to ALWAYS_ALLOW", LogLevel.INFO)
                                    true
                                }
                                ApprovalResponse.ALLOW_ALL -> {
                                    // Switch to YOLO mode for the rest of this turn only (not persisted)
                                    mode = AgentMode.YOLO
                                    agentLog("Switched to YOLO mode for this turn (Allow All)", LogLevel.INFO)
                                    true
                                }
                            }
                        }
                    }
                }

                if (!permitted) {
                    agentLog("Tool denied: ${call.name}", LogLevel.WARN)
                    val deniedResult = "Tool '${call.name}' was denied by user."
                    conversationHistory.add(ChatMessage(role = "tool", content = deniedResult, toolCallId = call.id, toolName = call.name))
                    onOutput(AgentOutput.Error(deniedResult))
                    continue
                }

                _state.value = AgentState.Acting(call.name, displayText)
                agentLog("Executing: ${call.name}")

                // Hide overlay during interaction tools to avoid self-clicking
                val isInteractionTool = call.name in INTERACTION_TOOL_NAMES
                val result: ToolResult = try {
                    if (isInteractionTool) {
                        OverlayController.withOverlaysHidden { tool.execute(call.arguments) }
                    } else {
                        tool.execute(call.arguments)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    agentLog("Tool ${call.name} failed: ${e.message}", LogLevel.ERROR)
                    ToolResult("Error executing ${call.name}: ${e.message}")
                }
                agentLog("${call.name} done (${result.text.length} chars, img=${result.imageBase64?.let { "${it.length / 1024}KB" } ?: "null"})")

                conversationHistory.add(ChatMessage(role = "tool", content = result.text, toolCallId = call.id, toolName = call.name))
                onOutput(AgentOutput.ToolExecution(call.name, call.arguments, result.text, result.imageBase64))

                // Track user-confirmed completion
                if (call.name == "confirm_completion" && result.text.startsWith("TASK_COMPLETE")) {
                    taskConfirmedThisTurn = true
                }

                // Collect screenshot for injection as user message (images can only be user messages)
                if (result.imageBase64 != null) {
                    pendingScreenshot = result.imageBase64
                }
            }

            // Inject screenshot as a user message after all tool results
            // (LLM APIs only support images in user messages, not tool results)
            if (pendingScreenshot != null) {
                conversationHistory.add(ChatMessage(
                    role = "user",
                    content = "Here is the screenshot of the current screen:",
                    imageBase64 = pendingScreenshot,
                ))
                agentLog("Injected screenshot as user message")
            }

            // Stop immediately when user confirms task completion —
            // don't give the LLM another turn to generate more actions.
            if (taskConfirmedThisTurn) {
                agentLog("User confirmed completion — stopping", LogLevel.INFO)
                if (appSettings.getAutoDeleteTasksOnCompletionOnce()) {
                    _currentSessionId.value?.let { sessionId ->
                        taskRepository.deleteBySession(sessionId)
                        agentLog("Auto-deleted tasks for session $sessionId", LogLevel.INFO)
                    }
                }
                _state.value = AgentState.Idle
                return
            }

            // ── Context compaction check ──
            val effectivePromptTokens = if (lastPromptTokens > 0) lastPromptTokens
                else ContextCompactor.estimateTokens(conversationHistory)
            val userThreshold = appSettings.getCompactThresholdOnce()
            val customCtxWindow = appSettings.getCustomContextWindowOnce()
            val compactThreshold = if (userThreshold > 0) userThreshold
                else ModelTokenLimits.getDefaultThreshold(model, customCtxWindow)
            val remaining = ((1.0 - effectivePromptTokens.toDouble() / compactThreshold) * 100).toInt().coerceAtLeast(0)
            _contextUsage.value = ContextUsage(effectivePromptTokens, compactThreshold, remaining)

            if (effectivePromptTokens >= compactThreshold && conversationHistory.size > 3) {
                agentLog("Context compaction triggered: $effectivePromptTokens / $compactThreshold tokens", LogLevel.INFO)
                val summary = performCompaction(provider, model)
                if (summary != null) {
                    conversationHistory.clear()
                    val deviceContextBlock = deviceContext.gather()
                    val basePrompt = SystemPrompts.buildAgentSystemPrompt(deviceContextBlock)
                    val systemPrompt = if (effectivePlanMode) {
                        basePrompt + "\n\n" + SystemPrompts.PLAN_MODE_PROMPT
                    } else {
                        basePrompt
                    }
                    conversationHistory.add(ChatMessage(role = "system", content = systemPrompt))
                    conversationHistory.add(ChatMessage(role = "user", content = ContextCompactor.buildContinuationMessage(summary)))
                    lastPromptTokens = 0
                    _contextUsage.value = null
                    onOutput(AgentOutput.Text("[Context compacted]"))
                    agentLog("Compaction complete, resuming with ${conversationHistory.size} messages", LogLevel.INFO)
                    continue
                }
            }

            // Check if AI proactively entered plan mode
            if (pendingPlanModeEntry) {
                pendingPlanModeEntry = false
                effectivePlanMode = true
                prePlanMode = mode
                mode = AgentMode.PLAN
                appSettings.setAgentMode(AgentMode.PLAN)
                agentLog("AI entered plan mode proactively, restricting to read-only + plan tools", LogLevel.INFO)
            }

            // Check if plan was approved — switch from plan mode to execution mode
            val modeSwitch = pendingModeSwitch
            if (modeSwitch != null) {
                pendingModeSwitch = null
                effectivePlanMode = false
                mode = modeSwitch
                appSettings.setAgentMode(modeSwitch)
                prePlanMode = null
                agentLog("Plan approved! Switching to $modeSwitch mode, unlocking all tools", LogLevel.INFO)
            }
        }

        agentLog("Max iterations reached ($maxIterations)", LogLevel.WARN)
        _state.value = AgentState.Idle
        onOutput(AgentOutput.Error("Reached maximum iterations ($maxIterations). Stopping."))
    }
}
