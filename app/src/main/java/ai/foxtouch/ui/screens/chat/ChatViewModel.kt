package ai.foxtouch.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.foxtouch.agent.AgentOutput
import ai.foxtouch.agent.AgentRunner
import ai.foxtouch.agent.AgentState
import ai.foxtouch.agent.ApprovalResponse
import ai.foxtouch.agent.CompletionResponse
import ai.foxtouch.agent.LogEntry
import ai.foxtouch.agent.PlanApprovalResponse
import ai.foxtouch.agent.SkillsManager
import android.content.Context
import ai.foxtouch.data.db.entity.SessionEntity
import ai.foxtouch.data.db.entity.TaskEntity
import ai.foxtouch.data.preferences.AgentMode
import ai.foxtouch.data.preferences.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import ai.foxtouch.data.repository.MessageRepository
import ai.foxtouch.data.repository.SessionRepository
import ai.foxtouch.data.repository.TaskRepository
import ai.foxtouch.tools.BackTool
import ai.foxtouch.tools.ClickElementTool
import ai.foxtouch.tools.TapTool
import ai.foxtouch.tools.CreateTaskTool
import ai.foxtouch.tools.HomeTool
import ai.foxtouch.tools.LaunchAppTool
import ai.foxtouch.tools.LongPressTool
import ai.foxtouch.tools.PinchTool
import ai.foxtouch.tools.ReadScreenTool
import ai.foxtouch.tools.ScrollTool
import ai.foxtouch.tools.SwipeTool
import ai.foxtouch.tools.ToolRegistry
import ai.foxtouch.tools.ClipboardTool
import ai.foxtouch.tools.TypeAtTool
import ai.foxtouch.tools.TypeTextTool
import ai.foxtouch.tools.UpdateTaskTool
import ai.foxtouch.tools.ListAppsTool
import ai.foxtouch.agent.AgentDocsManager
import ai.foxtouch.tools.ReadAgentsTool
import ai.foxtouch.tools.AskUserTool
import ai.foxtouch.tools.ConfirmCompletionTool
import ai.foxtouch.tools.EditPlanTool
import ai.foxtouch.tools.EnterPlanModeTool
import ai.foxtouch.tools.ExitPlanModeTool
import ai.foxtouch.tools.ListSkillsTool
import ai.foxtouch.tools.ReadSkillTool
import ai.foxtouch.tools.SaveSkillTool
import ai.foxtouch.tools.ReadMemoryTool
import ai.foxtouch.tools.WriteMemoryTool
import ai.foxtouch.tools.WaitTool
import ai.foxtouch.voice.RecognitionState
import ai.foxtouch.voice.TtsManager
import ai.foxtouch.voice.VoiceInputManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

data class ChatUiMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val toolName: String? = null,
    val toolArgs: JsonObject? = null,
    val isStreaming: Boolean = false,
    /** Screenshot captured by the tool (e.g. read_screen, click with feedback). */
    val imageBase64: String? = null,
)

data class ChatUiState(
    val messages: List<ChatUiMessage> = emptyList(),
    val isAgentBusy: Boolean = false,
    val currentSessionId: String? = null,
    val error: String? = null,
    val showPlanReview: Boolean = false,
    val planContent: String? = null,
    val suggestSaveAsSkill: Boolean = false,
    val showUserQuestion: Boolean = false,
    val userQuestion: String? = null,
    val userSuggestions: List<String> = emptyList(),
    val showCompletionConfirm: Boolean = false,
    val completionSummary: String? = null,
    val inputDraft: String = "",
)

data class TaskProgress(
    val total: Int = 0,
    val completed: Int = 0,
    val inProgress: Int = 0,
    val failed: Int = 0,
    val pending: Int = 0,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val agentRunner: AgentRunner,
    private val sessionRepository: SessionRepository,
    private val messageRepository: MessageRepository,
    private val taskRepository: TaskRepository,
    private val toolRegistry: ToolRegistry,
    private val appSettings: AppSettings,
    @ApplicationContext private val appContext: Context,
    val voiceInputManager: VoiceInputManager,
    private val ttsManager: TtsManager,
    private val agentDocsManager: AgentDocsManager,
    private val skillsManager: SkillsManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val agentState: StateFlow<AgentState> = agentRunner.state

    val agentMode: StateFlow<AgentMode> = appSettings.agentMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, AgentMode.NORMAL)

    val recognitionState: StateFlow<RecognitionState> = voiceInputManager.recognitionState

    val isTtsEnabled = appSettings.isTtsEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val isDebugJsonDisplay = appSettings.isDebugJsonDisplay
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isDebugMode = appSettings.isDebugMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isSpeaking: StateFlow<Boolean> = ttsManager.isSpeaking

    val allSessions: StateFlow<List<SessionEntity>> = sessionRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _sessionTasks = MutableStateFlow<List<TaskEntity>>(emptyList())
    val sessionTasks: StateFlow<List<TaskEntity>> = _sessionTasks.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _sessionSizes = MutableStateFlow<Map<String, Long>>(emptyMap())
    val sessionSizes: StateFlow<Map<String, Long>> = _sessionSizes.asStateFlow()

    val taskProgress: StateFlow<TaskProgress> = _sessionTasks
        .map { tasks ->
            TaskProgress(
                total = tasks.size,
                completed = tasks.count { it.status == "completed" },
                inProgress = tasks.count { it.status == "in_progress" },
                failed = tasks.count { it.status == "failed" },
                pending = tasks.count { it.status == "pending" },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TaskProgress())

    /** Session ID captured when a turn starts. Outputs are always saved to this session. */
    @Volatile
    private var turnSessionId: String? = null

    private val isFirstMessage = AtomicBoolean(true)
    private var taskCollectionJob: Job? = null

    init {
        registerTools()
        viewModelScope.launch {
            // If the agent is currently busy with a session, resume it instead of creating new
            val activeSessionId = agentRunner.currentSessionId
            if (agentRunner.isBusy.value && activeSessionId != null) {
                switchSession(activeSessionId)
            } else {
                startNewSession()
            }
        }
        collectVoiceResults()
        collectAgentOutputs()
        collectAgentBusy()
        collectAgentLogs()
        refreshSessionSizes()
        collectPlanReviewState()
        collectAskUserState()
        collectCompletionState()
        collectTtsSpeechRate()
    }

    private fun collectTtsSpeechRate() {
        viewModelScope.launch {
            appSettings.ttsSpeechRate.collect { rate ->
                ttsManager.setSpeechRate(rate)
            }
        }
    }

    private fun registerTools() {
        toolRegistry.registerAll(
            ReadScreenTool(appSettings),
            ClickElementTool(appSettings),
            TapTool(appSettings),
            TypeTextTool(appSettings),
            TypeAtTool(appSettings),
            ClipboardTool(),
            ScrollTool(),
            SwipeTool(),
            LongPressTool(),
            PinchTool(),
            BackTool(),
            HomeTool(),
            LaunchAppTool(),
            ListAppsTool(appContext),
            WaitTool(),
            AskUserTool(
                onQuestionAsked = { question, suggestions ->
                    agentRunner.emitAskUserState(question, suggestions)
                },
                awaitAnswer = { agentRunner.userAnswerChannel.receive() },
            ),
            EditPlanTool(
                getPlanFile = {
                    agentRunner.getOrCreatePlanFile(
                        _uiState.value.currentSessionId ?: "default",
                    )
                },
            ),
            EnterPlanModeTool(
                onEnterPlanMode = { agentRunner.requestPlanModeEntry() },
            ),
            ExitPlanModeTool(
                readPlanFile = { agentRunner.readPlanFile() },
                onPlanPresented = { content, suggestSave ->
                    agentRunner.emitPlanReviewState(content, suggestSave)
                },
                awaitApproval = { agentRunner.planApprovalChannel.receive() },
                onApproved = { yolo, saveAsSkill ->
                    agentRunner.setPendingModeSwitch(
                        if (yolo) AgentMode.YOLO else agentRunner.getPrePlanMode(),
                    )
                    if (saveAsSkill) {
                        val planContent = agentRunner.readPlanFile()
                        if (planContent.isNotBlank()) {
                            val title = planContent.lines()
                                .firstOrNull { it.startsWith("# ") }
                                ?.removePrefix("# ")
                                ?.trim()
                                ?: "Untitled Skill"
                            skillsManager.saveSkill(title, planContent)
                        }
                    }
                },
            ),
            ConfirmCompletionTool(
                onConfirmRequested = { summary ->
                    agentRunner.emitConfirmCompletionState(summary)
                },
                awaitResponse = { agentRunner.completionChannel.receive() },
            ),
            ListSkillsTool(skillsManager),
            ReadSkillTool(skillsManager),
            SaveSkillTool(skillsManager, readPlanFile = { agentRunner.readPlanFile() }),
            ReadMemoryTool(readMemory = { agentDocsManager.readMemory() }),
            WriteMemoryTool(
                readMemory = { agentDocsManager.readMemory() },
                writeMemory = { content -> agentDocsManager.writeMemory(content) },
            ),
            ReadAgentsTool(readAgents = { agentDocsManager.readAgents() }),
        )
    }

    private fun registerTaskTools(sessionId: String) {
        toolRegistry.registerAll(
            CreateTaskTool(taskRepository, sessionId),
            UpdateTaskTool(taskRepository),
        )
    }

    private fun collectVoiceResults() {
        viewModelScope.launch {
            voiceInputManager.results.collect { text ->
                if (text.isNotBlank()) {
                    updateInputDraft("")
                    sendMessage(text)
                }
            }
        }
    }

    private fun collectAgentOutputs() {
        viewModelScope.launch {
            agentRunner.outputFlow.collect { output ->
                handleAgentOutput(output)
            }
        }
    }

    private fun collectAgentLogs() {
        viewModelScope.launch {
            agentRunner.logFlow.collect { entry ->
                _logs.value = (_logs.value + entry).takeLast(200)
            }
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    private fun refreshSessionSizes() {
        viewModelScope.launch {
            val sessions = allSessions.value
            val sizes = sessions.associate { session ->
                session.id to messageRepository.getScreenshotSizeBySession(session.id)
            }
            _sessionSizes.value = sizes
        }
    }

    private fun collectAgentBusy() {
        viewModelScope.launch {
            agentRunner.isBusy.collect { busy ->
                _uiState.value = _uiState.value.copy(isAgentBusy = busy)
            }
        }
    }

    /** Observe AgentState.PlanReview — triggered by ExitPlanModeTool via the agent loop. */
    private fun collectPlanReviewState() {
        viewModelScope.launch {
            agentRunner.state.collect { state ->
                if (state is AgentState.PlanReview) {
                    _uiState.value = _uiState.value.copy(
                        showPlanReview = true,
                        planContent = state.planContent,
                        suggestSaveAsSkill = state.suggestSave,
                    )
                } else if (_uiState.value.showPlanReview) {
                    // Plan was handled (approved/rejected) elsewhere (e.g. overlay) — clear
                    _uiState.value = _uiState.value.copy(
                        showPlanReview = false,
                        planContent = null,
                        suggestSaveAsSkill = false,
                    )
                }
            }
        }
    }

    /** Observe AgentState.AskingUser — triggered by AskUserTool via the agent loop. */
    private fun collectAskUserState() {
        viewModelScope.launch {
            agentRunner.state.collect { state ->
                if (state is AgentState.AskingUser) {
                    _uiState.value = _uiState.value.copy(
                        showUserQuestion = true,
                        userQuestion = state.question,
                        userSuggestions = state.suggestions,
                    )
                } else if (_uiState.value.showUserQuestion) {
                    // Question was answered elsewhere (e.g. overlay) — clear
                    _uiState.value = _uiState.value.copy(
                        showUserQuestion = false,
                        userQuestion = null,
                        userSuggestions = emptyList(),
                    )
                }
            }
        }
    }

    /** Observe AgentState.ConfirmingCompletion — triggered by ConfirmCompletionTool. */
    private fun collectCompletionState() {
        viewModelScope.launch {
            agentRunner.state.collect { state ->
                if (state is AgentState.ConfirmingCompletion) {
                    _uiState.value = _uiState.value.copy(
                        showCompletionConfirm = true,
                        completionSummary = state.summary,
                    )
                } else if (_uiState.value.showCompletionConfirm) {
                    _uiState.value = _uiState.value.copy(
                        showCompletionConfirm = false,
                        completionSummary = null,
                    )
                }
            }
        }
    }

    fun confirmCompletion() {
        _uiState.value = _uiState.value.copy(showCompletionConfirm = false, completionSummary = null)
        agentRunner.respondToCompletion(CompletionResponse.Confirmed)
    }

    fun rejectCompletion(reason: String) {
        _uiState.value = _uiState.value.copy(showCompletionConfirm = false, completionSummary = null)
        agentRunner.respondToCompletion(CompletionResponse.NotDone(reason))
    }

    fun dismissCompletion() {
        _uiState.value = _uiState.value.copy(showCompletionConfirm = false, completionSummary = null)
        agentRunner.respondToCompletion(CompletionResponse.Dismissed)
    }

    fun submitUserAnswer(answer: String) {
        _uiState.value = _uiState.value.copy(
            showUserQuestion = false,
            userQuestion = null,
            userSuggestions = emptyList(),
        )
        agentRunner.respondToUserQuestion(answer)
    }

    private suspend fun startNewSession() {
        val session = sessionRepository.create(
            provider = appSettings.getProviderOnce(),
            model = appSettings.getModelOnce(),
        )
        registerTaskTools(session.id)
        observeSessionTasks(session.id)
        agentRunner.setCurrentSessionId(session.id)
        isFirstMessage.set(true)
        turnSessionId = session.id
        _uiState.value = _uiState.value.copy(
            currentSessionId = session.id,
            messages = emptyList(),
        )
    }

    private fun observeSessionTasks(sessionId: String) {
        taskCollectionJob?.cancel()
        taskCollectionJob = viewModelScope.launch {
            taskRepository.getBySession(sessionId).collect { tasks ->
                _sessionTasks.value = tasks
            }
        }
    }

    fun switchSession(sessionId: String) {
        if (sessionId == _uiState.value.currentSessionId) return
        viewModelScope.launch {
            // Cancel any in-flight agent turn before switching context
            agentRunner.cancelCurrentTurn()
            agentRunner.clearHistory()
            registerTaskTools(sessionId)
            observeSessionTasks(sessionId)
            agentRunner.setCurrentSessionId(sessionId)
            isFirstMessage.set(false)
            turnSessionId = sessionId

            // Load messages from DB
            val savedMessages = messageRepository.getBySessionOnce(sessionId)
            val uiMessages = savedMessages.map { entity ->
                val toolArgs = entity.toolArgsJson?.let { json ->
                    try {
                        kotlinx.serialization.json.Json.parseToJsonElement(json) as? JsonObject
                    } catch (_: Exception) { null }
                }
                val imageBase64 = entity.screenshotPath?.let { path ->
                    messageRepository.loadScreenshotBase64(path)
                }
                ChatUiMessage(
                    id = entity.id,
                    role = entity.role,
                    content = entity.toolResultJson ?: entity.content,
                    toolName = entity.toolName,
                    toolArgs = toolArgs,
                    imageBase64 = imageBase64,
                )
            }
            _uiState.value = _uiState.value.copy(
                currentSessionId = sessionId,
                messages = uiMessages,
                error = null,
            )
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            messageRepository.deleteScreenshotsBySession(sessionId)
            sessionRepository.delete(sessionId)
            if (sessionId == _uiState.value.currentSessionId) {
                clearChat()
            }
            refreshSessionSizes()
        }
    }

    fun deleteAllOtherSessions() {
        viewModelScope.launch {
            agentRunner.cancelCurrentTurn()
            agentRunner.clearHistory()
            val currentId = _uiState.value.currentSessionId
            // Delete screenshots for all sessions
            allSessions.value.forEach { session ->
                messageRepository.deleteScreenshotsBySession(session.id)
            }
            if (currentId != null) {
                // Delete all other sessions, clear current session content
                sessionRepository.deleteAllExcept(currentId)
                messageRepository.deleteBySession(currentId)
                taskRepository.deleteAll()
            } else {
                sessionRepository.deleteAll()
                taskRepository.deleteAll()
            }
            _uiState.value = _uiState.value.copy(messages = emptyList(), error = null)
            isFirstMessage.set(true)
            refreshSessionSizes()
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        // If the agent is waiting for a user answer, treat this message as the answer
        if (agentRunner.state.value is AgentState.AskingUser) {
            submitUserAnswer(text)
            return
        }

        if (agentRunner.isBusy.value) return

        // Capture session ID now — outputs will always be saved to this session,
        // even if the user switches sessions while the agent is running.
        val sessionId = _uiState.value.currentSessionId
        turnSessionId = sessionId

        val userMsg = ChatUiMessage(role = "user", content = text)
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMsg,
            error = null,
        )

        viewModelScope.launch {
            if (sessionId != null) {
                messageRepository.addUserMessage(sessionId, text)

                // Auto-name session after first user message (atomic CAS)
                if (isFirstMessage.compareAndSet(true, false)) {
                    val title = text.take(40) + if (text.length > 40) "..." else ""
                    sessionRepository.updateTitle(sessionId, title)
                }
            }
        }

        // Launch in applicationScope via startTurn — survives Activity/ViewModel destruction.
        // Outputs arrive via agentRunner.outputFlow, collected in collectAgentOutputs().
        agentRunner.startTurn(text, agentMode.value == AgentMode.PLAN)
    }

    fun approveToolCall() { agentRunner.respondToApproval(ApprovalResponse.ALLOW) }
    fun denyToolCall() { agentRunner.respondToApproval(ApprovalResponse.DENY) }
    fun alwaysAllowTool() { agentRunner.respondToApproval(ApprovalResponse.ALWAYS_ALLOW) }
    fun allowAllTools() { agentRunner.respondToApproval(ApprovalResponse.ALLOW_ALL) }

    fun startVoiceInput() = voiceInputManager.startVoiceInput()
    fun stopVoiceInput() = voiceInputManager.stopVoiceInput()

    fun toggleTts() {
        viewModelScope.launch {
            appSettings.setTtsEnabled(!isTtsEnabled.value)
        }
    }

    fun stopTts() = ttsManager.stop()

    fun speakMessage(text: String) = ttsManager.speak(text)

    private suspend fun handleAgentOutput(output: AgentOutput) {
        // Use the session ID captured at turn start, not the current session —
        // the user might have switched sessions while the agent was running.
        val sessionId = turnSessionId
        when (output) {
            is AgentOutput.Text -> {
                val msg = ChatUiMessage(role = "assistant", content = output.content)
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + msg,
                )
                if (sessionId != null) {
                    messageRepository.addAssistantMessage(sessionId, output.content)
                }
                if (isTtsEnabled.value) {
                    ttsManager.speak(output.content)
                }
            }
            is AgentOutput.ToolExecution -> {
                android.util.Log.d("ChatVM", "ToolExecution: ${output.toolName}, img=${output.imageBase64?.let { "${it.length / 1024}KB" } ?: "null"}")
                val msg = ChatUiMessage(
                    role = "tool",
                    content = output.result,
                    toolName = output.toolName,
                    toolArgs = output.args,
                    imageBase64 = output.imageBase64,
                )
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + msg,
                )
                if (sessionId != null) {
                    val screenshotPath = output.imageBase64?.let { base64 ->
                        messageRepository.saveScreenshot(msg.id, base64)
                    }
                    messageRepository.addToolMessage(
                        sessionId = sessionId,
                        toolName = output.toolName,
                        argsJson = output.args.toString(),
                        resultJson = output.result,
                        screenshotPath = screenshotPath,
                    )
                    if (screenshotPath != null) refreshSessionSizes()
                }
            }
            is AgentOutput.Error -> {
                val msg = ChatUiMessage(role = "assistant", content = "Error: ${output.message}")
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + msg,
                    error = output.message,
                )
            }
        }
    }

    fun cycleAgentMode() {
        viewModelScope.launch {
            val next = when (agentMode.value) {
                AgentMode.NORMAL -> AgentMode.PLAN
                AgentMode.PLAN -> AgentMode.YOLO
                AgentMode.YOLO -> AgentMode.NORMAL
            }
            appSettings.setAgentMode(next)
        }
    }

    fun setAgentMode(mode: AgentMode) {
        viewModelScope.launch {
            appSettings.setAgentMode(mode)
        }
    }

    /** Approve plan → Normal mode. Agent executes plan, each action needs approval. */
    fun executePlanNormal(saveAsSkill: Boolean = false) {
        _uiState.value = _uiState.value.copy(showPlanReview = false, planContent = null, suggestSaveAsSkill = false)
        agentRunner.respondToPlanApproval(PlanApprovalResponse.ApproveNormal(saveAsSkill))
    }

    /** Approve plan → YOLO mode. Agent executes plan without any approval. */
    fun executePlanYolo(saveAsSkill: Boolean = false) {
        _uiState.value = _uiState.value.copy(showPlanReview = false, planContent = null, suggestSaveAsSkill = false)
        agentRunner.respondToPlanApproval(PlanApprovalResponse.ApproveYolo(saveAsSkill))
    }

    /** Reject the plan. Agent stops and asks user what to change. */
    fun rejectPlan() {
        _uiState.value = _uiState.value.copy(showPlanReview = false, planContent = null, suggestSaveAsSkill = false)
        agentRunner.respondToPlanApproval(PlanApprovalResponse.Reject)
    }

    /** Request modifications. Agent stays in plan mode and revises the plan. */
    fun requestPlanModification(reason: String) {
        _uiState.value = _uiState.value.copy(showPlanReview = false, planContent = null, suggestSaveAsSkill = false)
        agentRunner.respondToPlanApproval(PlanApprovalResponse.Modify(reason))
    }

    fun dismissPlanReview() {
        _uiState.value = _uiState.value.copy(showPlanReview = false, planContent = null, suggestSaveAsSkill = false)
        agentRunner.respondToPlanApproval(PlanApprovalResponse.Reject)
    }

    fun stopAgent() {
        agentRunner.cancelCurrentTurn()
    }

    fun clearChat() {
        agentRunner.cancelCurrentTurn()
        agentRunner.clearHistory()
        _uiState.value = _uiState.value.copy(messages = emptyList(), error = null)
        viewModelScope.launch { startNewSession() }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun updateInputDraft(text: String) {
        _uiState.value = _uiState.value.copy(inputDraft = text)
    }

    override fun onCleared() {
        super.onCleared()
        // Note: agent turn is NOT cancelled here — it continues in applicationScope
        // and the user can still approve via notification. This is intentional.
        ttsManager.stop()
        voiceInputManager.destroy()
    }
}
