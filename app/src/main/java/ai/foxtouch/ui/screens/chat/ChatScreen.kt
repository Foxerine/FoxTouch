package ai.foxtouch.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.content.Intent
import android.net.Uri
import ai.foxtouch.R
import ai.foxtouch.agent.AgentState
import ai.foxtouch.agent.LogEntry
import ai.foxtouch.agent.LogLevel
import ai.foxtouch.data.db.entity.SessionEntity
import ai.foxtouch.data.db.entity.TaskEntity
import ai.foxtouch.data.preferences.AgentMode
import ai.foxtouch.ui.components.AgentModeToggle
import ai.foxtouch.ui.components.ApprovalSheet
import ai.foxtouch.ui.components.DEFAULT_SUGGESTIONS
import ai.foxtouch.ui.components.InputBar
import ai.foxtouch.ui.components.SuggestionChips
import ai.foxtouch.ui.components.TaskProgressPanel
import ai.foxtouch.ui.components.VoiceInputButton
import ai.foxtouch.ui.screens.chat.components.MessageItem
import ai.foxtouch.ui.screens.chat.components.ToolCallCard
import ai.foxtouch.voice.RecognitionState
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val agentState by viewModel.agentState.collectAsState()
    val agentMode by viewModel.agentMode.collectAsState()
    val isTtsEnabled by viewModel.isTtsEnabled.collectAsState()
    val recognitionState by viewModel.recognitionState.collectAsState()
    val sessions by viewModel.allSessions.collectAsState()
    val sessionSizes by viewModel.sessionSizes.collectAsState()
    val sessionTasks by viewModel.sessionTasks.collectAsState()
    val taskProgress by viewModel.taskProgress.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val contextUsagePercent by viewModel.contextUsagePercent.collectAsState()
    val isDebugJson by viewModel.isDebugJsonDisplay.collectAsState()
    val isDebugMode by viewModel.isDebugMode.collectAsState()
    val inputText = uiState.inputDraft
    var showMenu by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val approvalSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Auto-scroll to bottom on new messages
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    // Show error snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.dismissError()
        }
    }

    // Update input text with partial voice recognition and show errors
    LaunchedEffect(recognitionState) {
        when (recognitionState) {
            is RecognitionState.Partial -> {
                viewModel.updateInputDraft((recognitionState as RecognitionState.Partial).text)
            }
            is RecognitionState.Error -> {
                snackbarHostState.showSnackbar(
                    (recognitionState as RecognitionState.Error).message
                )
            }
            else -> {}
        }
    }

    // Show approval bottom sheet when agent is waiting
    val approvalState = agentState as? AgentState.WaitingApproval
    if (approvalState != null) {
        ApprovalSheet(
            toolName = approvalState.toolName,
            args = approvalState.args,
            sheetState = approvalSheetState,
            showJsonMode = isDebugJson,
            onApprove = { viewModel.approveToolCall() },
            onDeny = { viewModel.denyToolCall() },
            onAlwaysAllow = { viewModel.alwaysAllowTool() },
            onAllowAll = { viewModel.allowAllTools() },
            onDismiss = { viewModel.denyToolCall() },
        )
    }

    // Show plan review panel when LLM calls exit_plan_mode
    if (uiState.showPlanReview) {
        PlanReviewSheet(
            planContent = uiState.planContent,
            tasks = sessionTasks,
            suggestSaveAsSkill = uiState.suggestSaveAsSkill,
            onExecuteNormal = { saveAsSkill -> viewModel.executePlanNormal(saveAsSkill) },
            onExecuteYolo = { saveAsSkill -> viewModel.executePlanYolo(saveAsSkill) },
            onReject = { viewModel.rejectPlan() },
            onRequestModification = { reason -> viewModel.requestPlanModification(reason) },
            onDismiss = { viewModel.dismissPlanReview() },
        )
    }

    // Show question card when LLM calls ask_user
    val currentQuestion = uiState.userQuestion
    if (uiState.showUserQuestion && currentQuestion != null) {
        UserQuestionSheet(
            question = currentQuestion,
            suggestions = uiState.userSuggestions,
            onSubmit = { answer -> viewModel.submitUserAnswer(answer) },
            onDismiss = { viewModel.submitUserAnswer("(User dismissed the question)") },
        )
    }

    // Agent Logs bottom sheet
    if (showLogs) {
        LogsSheet(
            logs = logs,
            onClear = { viewModel.clearLogs() },
            onDismiss = { showLogs = false },
        )
    }

    // Completion confirm sheet
    val showCompletion = uiState.showCompletionConfirm
    if (showCompletion) {
        CompletionConfirmSheet(
            summary = uiState.completionSummary ?: "",
            onConfirm = { viewModel.confirmCompletion() },
            onReject = { reason -> viewModel.rejectCompletion(reason) },
            onDismiss = { viewModel.dismissCompletion() },
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SessionDrawer(
                sessions = sessions,
                sessionSizes = sessionSizes,
                currentSessionId = uiState.currentSessionId,
                onSessionSelected = { sessionId ->
                    viewModel.switchSession(sessionId)
                    scope.launch { drawerState.close() }
                },
                onNewSession = {
                    viewModel.clearChat()
                    scope.launch { drawerState.close() }
                },
                onDeleteSession = { sessionId ->
                    viewModel.deleteSession(sessionId)
                },
                onDeleteAll = {
                    viewModel.deleteAllOtherSessions()
                },
            )
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("FoxTouch", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.History, contentDescription = stringResource(R.string.chat_history))
                        }
                    },
                    actions = {
                        if (contextUsagePercent != null && contextUsagePercent!! <= 15) {
                            Text(
                                text = stringResource(R.string.compact_warning, contextUsagePercent!!),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        IconButton(onClick = { viewModel.toggleTts() }) {
                            Icon(
                                if (isTtsEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                contentDescription = stringResource(if (isTtsEnabled) R.string.tts_on else R.string.tts_off),
                            )
                        }
                        AgentModeToggle(
                            mode = agentMode,
                            onCycle = { viewModel.cycleAgentMode() },
                        )
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = null)
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.new_chat)) },
                                    leadingIcon = { Icon(Icons.Default.Add, null) },
                                    onClick = { showMenu = false; viewModel.clearChat() },
                                )
                                if (isDebugMode) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.logs_title)) },
                                        leadingIcon = { Icon(Icons.Default.Terminal, null) },
                                        onClick = { showMenu = false; showLogs = true },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.settings_title)) },
                                    leadingIcon = { Icon(Icons.Default.Settings, null) },
                                    onClick = { showMenu = false; onNavigateToSettings() },
                                )
                            }
                        }
                    },
                )
            },
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding(),
            ) {
                // Task progress panel
                TaskProgressPanel(
                    tasks = sessionTasks,
                    progress = taskProgress,
                )

                // Message list
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (uiState.messages.isEmpty()) {
                        item {
                            EmptyState(
                                onSuggestionClick = { suggestion ->
                                    viewModel.sendMessage(suggestion)
                                },
                            )
                        }
                    }

                    items(uiState.messages, key = { it.id }) { message ->
                        when {
                            message.toolName != null -> ToolCallCard(message, showJsonMode = isDebugJson, isDebugMode = isDebugMode)
                            else -> MessageItem(message)
                        }
                    }

                    // Loading indicator
                    if (uiState.isAgentBusy) {
                        item {
                            AgentStatusIndicator(agentState)
                        }
                    }
                }

                // Input bar
                Surface(
                    tonalElevation = 3.dp,
                    modifier = Modifier.navigationBarsPadding(),
                ) {
                    InputBar(
                        inputText = inputText,
                        onInputChange = { viewModel.updateInputDraft(it) },
                        onSend = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText)
                                viewModel.updateInputDraft("")
                            }
                        },
                        onStop = { viewModel.stopAgent() },
                        isAgentBusy = uiState.isAgentBusy,
                        voiceButton = {
                            VoiceInputButton(
                                isListening = viewModel.voiceInputManager.isListening,
                                onStart = { viewModel.startVoiceInput() },
                                onStop = { viewModel.stopVoiceInput() },
                                recognitionState = recognitionState,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onSuggestionClick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "FoxTouch",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.empty_state_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        SuggestionChips(
            suggestions = DEFAULT_SUGGESTIONS,
            onChipClick = onSuggestionClick,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionDrawer(
    sessions: List<SessionEntity>,
    sessionSizes: Map<String, Long>,
    currentSessionId: String?,
    onSessionSelected: (String) -> Unit,
    onNewSession: () -> Unit,
    onDeleteSession: (String) -> Unit,
    onDeleteAll: () -> Unit,
) {
    var showDeleteAllConfirm by remember { mutableStateOf(false) }

    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text(stringResource(R.string.delete_all_title)) },
            text = { Text(stringResource(R.string.delete_all_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAllConfirm = false
                        onDeleteAll()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text(stringResource(R.string.delete_all)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteAllConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    ModalDrawerSheet {
        Text(
            text = stringResource(R.string.chat_history),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp),
        )

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNewSession() }
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.new_chat), color = MaterialTheme.colorScheme.primary)
                }
            }

            if (sessions.size > 1) {
                IconButton(
                    onClick = { showDeleteAllConfirm = true },
                    modifier = Modifier.padding(end = 8.dp, top = 4.dp),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete_all),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        HorizontalDivider()

        LazyColumn {
            items(sessions, key = { it.id }) { session ->
                val isSelected = session.id == currentSessionId
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSessionSelected(session.id) },
                    color = if (isSelected)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = session.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val sizeBytes = sessionSizes[session.id] ?: 0L
                            val sizeText = if (sizeBytes > 0) " · ${formatFileSize(sizeBytes)}" else ""
                            val tokenText = if (session.lastTokenCount > 0) {
                                " · ${formatTokenCount(session.lastTokenCount)}"
                            } else ""
                            Text(
                                text = "${session.provider} / ${session.model}$tokenText$sizeText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onDeleteSession(session.id) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}

private fun formatTokenCount(tokens: Int): String = when {
    tokens < 1000 -> "$tokens tokens"
    tokens < 1_000_000 -> String.format("%.1fK tokens", tokens / 1000.0)
    else -> String.format("%.1fM tokens", tokens / 1_000_000.0)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanReviewSheet(
    planContent: String?,
    tasks: List<TaskEntity>,
    suggestSaveAsSkill: Boolean = false,
    onExecuteNormal: (saveAsSkill: Boolean) -> Unit,
    onExecuteYolo: (saveAsSkill: Boolean) -> Unit,
    onReject: () -> Unit,
    onRequestModification: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Use key=Unit to stabilize local state — planContent changes don't reset the checkbox
    var modificationText by remember { mutableStateOf("") }
    var saveAsSkill by remember { mutableStateOf(suggestSaveAsSkill) }
    // Capture planContent once when sheet opens to prevent re-renders
    val stablePlanContent = remember { planContent }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.plan_review_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.plan_review_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            // Show plan content (rendered markdown) from the plan file
            if (!stablePlanContent.isNullOrBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 2.dp,
                ) {
                    val scrollState = rememberLazyListState()
                    val smallTypography = markdownTypography(
                        h1 = MaterialTheme.typography.titleMedium,
                        h2 = MaterialTheme.typography.titleSmall,
                        h3 = MaterialTheme.typography.labelLarge,
                        h4 = MaterialTheme.typography.labelMedium,
                        h5 = MaterialTheme.typography.labelSmall,
                        h6 = MaterialTheme.typography.labelSmall,
                        paragraph = MaterialTheme.typography.bodySmall,
                        text = MaterialTheme.typography.bodySmall,
                        ordered = MaterialTheme.typography.bodySmall,
                        bullet = MaterialTheme.typography.bodySmall,
                        list = MaterialTheme.typography.bodySmall,
                    )
                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier.padding(12.dp),
                    ) {
                        item {
                            Markdown(
                                content = stablePlanContent,
                                typography = smallTypography,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Show task list if any
            if (tasks.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.tasks_count, tasks.size),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                for (task in tasks) {
                    Text(
                        text = "- ${task.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // Modification input — always visible, no button-to-field toggle
            OutlinedTextField(
                value = modificationText,
                onValueChange = { modificationText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.describe_changes)) },
                maxLines = 3,
                shape = RoundedCornerShape(12.dp),
            )
            if (modificationText.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { onRequestModification(modificationText) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.request_modification))
                }
            }
            Spacer(Modifier.height(8.dp))

            // Save as skill checkbox
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { saveAsSkill = !saveAsSkill },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = saveAsSkill, onCheckedChange = { saveAsSkill = it })
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.save_as_skill),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(4.dp))

            // Approve with manual approval (Normal)
            OutlinedButton(
                onClick = { onExecuteNormal(saveAsSkill) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.approve_manual))
            }
            Spacer(Modifier.height(8.dp))

            // Approve with auto-approve (YOLO)
            Button(
                onClick = { onExecuteYolo(saveAsSkill) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.approve_auto))
            }
            Spacer(Modifier.height(8.dp))

            // Reject
            OutlinedButton(
                onClick = onReject,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.reject_plan))
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserQuestionSheet(
    question: String,
    suggestions: List<String>,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var showCustomInput by remember { mutableStateOf(false) }
    var customAnswer by remember { mutableStateOf("") }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            Text(
                text = stringResource(R.string.foxtouch_asks),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = question,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(16.dp))

            // Suggested responses (up to 4)
            suggestions.forEachIndexed { index, suggestion ->
                OutlinedButton(
                    onClick = { onSubmit(suggestion) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = "${index + 1}. $suggestion",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(6.dp))
            }

            // Custom input option
            if (!showCustomInput) {
                OutlinedButton(
                    onClick = { showCustomInput = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.custom_response, suggestions.size + 1),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                OutlinedTextField(
                    value = customAnswer,
                    onValueChange = { customAnswer = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.type_your_answer)) },
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onSubmit(customAnswer) },
                    enabled = customAnswer.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.submit))
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AgentStatusIndicator(state: AgentState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = when (state) {
                is AgentState.Thinking -> stringResource(R.string.thinking)
                is AgentState.Acting -> state.displayText
                is AgentState.WaitingApproval -> stringResource(R.string.waiting_approval, state.toolName)
                else -> stringResource(R.string.working)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompletionConfirmSheet(
    summary: String,
    onConfirm: () -> Unit,
    onReject: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var rejectReason by remember { mutableStateOf("") }
    var showRejectInput by remember { mutableStateOf(false) }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.completion_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            // Confirm button
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(stringResource(R.string.completion_confirmed))
            }
            Spacer(Modifier.height(8.dp))

            // Not done button / reject input
            if (!showRejectInput) {
                OutlinedButton(
                    onClick = { showRejectInput = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.completion_not_done))
                }
            } else {
                OutlinedTextField(
                    value = rejectReason,
                    onValueChange = { rejectReason = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.completion_reason_hint)) },
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(onClick = { showRejectInput = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onReject(rejectReason.ifBlank { "Not done yet" }) }) {
                        Text(stringResource(R.string.send))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogsSheet(
    logs: List<LogEntry>,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val logListState = rememberLazyListState()

    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            logListState.animateScrollToItem(logs.size - 1)
        }
    }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Terminal,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.logs_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.entries_count, logs.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                val clipboardManager = LocalClipboardManager.current
                IconButton(
                    onClick = {
                        val allLogs = logs.joinToString("\n") { entry ->
                            val time = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
                                .format(java.util.Date(entry.timestamp))
                            "$time ${entry.level.name[0]} ${entry.message}"
                        }
                        clipboardManager.setText(AnnotatedString(allLogs))
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy all logs",
                        modifier = Modifier.size(16.dp),
                    )
                }
                IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Clear logs",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (logs.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_logs_yet),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                LazyColumn(
                    state = logListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(logs) { entry ->
                        val timeStr = remember(entry.timestamp) {
                            java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
                                .format(java.util.Date(entry.timestamp))
                        }
                        val levelColor = when (entry.level) {
                            LogLevel.ERROR -> MaterialTheme.colorScheme.error
                            LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
                            LogLevel.INFO -> MaterialTheme.colorScheme.primary
                            LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            text = "$timeStr ${entry.level.name[0]} ${entry.message}",
                            style = MaterialTheme.typography.labelSmall,
                            color = levelColor,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
