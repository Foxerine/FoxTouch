package ai.foxtouch.assistant

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.foxtouch.agent.AgentOutput
import ai.foxtouch.agent.AgentRunner
import ai.foxtouch.agent.AgentState
import ai.foxtouch.agent.ApprovalResponse
import ai.foxtouch.data.preferences.AppSettings
import ai.foxtouch.ui.components.DEFAULT_SUGGESTIONS
import ai.foxtouch.ui.components.OrbState
import ai.foxtouch.ui.components.SuggestionChips
import ai.foxtouch.ui.components.VoiceOrb
import ai.foxtouch.voice.TtsManager
import ai.foxtouch.voice.VoiceInputManager
import kotlinx.coroutines.flow.first

/**
 * Google Assistant-style bottom panel overlay.
 *
 * The top ~60% of the screen is a transparent, touch-pass-through scrim so the background
 * app remains visible and accessible. The bottom ~40% contains the assistant panel with:
 * - Voice orb + status
 * - Recent response text
 * - Tool approval card (when waiting)
 * - Suggestion chips
 * - Text input + microphone
 */
@Composable
fun AssistantOverlayPanel(
    agentRunner: AgentRunner,
    ttsManager: TtsManager,
    voiceInputManager: VoiceInputManager,
    appSettings: AppSettings,
    onDismiss: () -> Unit,
) {
    val agentState by agentRunner.state.collectAsState()
    val isSpeaking by ttsManager.isSpeaking.collectAsState()

    var inputText by remember { mutableStateOf("") }
    val isBusy by agentRunner.isBusy.collectAsState()
    val responseMessages = remember { mutableStateListOf<AssistantMessage>() }

    // Collect agent outputs for local overlay display
    LaunchedEffect(Unit) {
        agentRunner.outputFlow.collect { output ->
            val ttsEnabled = appSettings.isTtsEnabled.first()
            when (output) {
                is AgentOutput.Text -> {
                    responseMessages.add(AssistantMessage(role = "assistant", content = output.content))
                    if (ttsEnabled) ttsManager.speak(output.content)
                }
                is AgentOutput.ToolExecution -> {
                    responseMessages.add(
                        AssistantMessage(
                            role = "tool",
                            content = output.result.take(200),
                            toolName = output.toolName,
                        ),
                    )
                }
                is AgentOutput.Error -> {
                    responseMessages.add(AssistantMessage(role = "assistant", content = output.message))
                }
            }
        }
    }

    // Map agent + TTS state to orb visual
    val orbState = when {
        isSpeaking -> OrbState.Speaking
        agentState is AgentState.Error -> OrbState.Error
        agentState is AgentState.Thinking -> OrbState.Processing
        agentState is AgentState.Acting -> OrbState.Processing
        agentState is AgentState.WaitingApproval -> OrbState.Listening
        agentState is AgentState.PlanReview -> OrbState.Listening
        agentState is AgentState.AskingUser -> OrbState.Listening
        agentState is AgentState.ConfirmingCompletion -> OrbState.Listening
        voiceInputManager.isListening -> OrbState.Listening
        else -> OrbState.Idle
    }

    // Collect voice input results
    LaunchedEffect(Unit) {
        voiceInputManager.results.collect { text ->
            if (text.isNotBlank()) {
                inputText = ""
                sendToAgent(
                    text = text,
                    agentRunner = agentRunner,
                    messages = responseMessages,
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Tap scrim to dismiss (covers full area behind the panel)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onDismiss),
        )

        // Bottom panel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
        ) {
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Drag handle
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                        )

                        Spacer(Modifier.height(12.dp))

                        // Close button row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // Voice orb
                        VoiceOrb(
                            state = orbState,
                            size = 72.dp,
                        )

                        Spacer(Modifier.height(4.dp))

                        // Status text
                        val statusText = when (orbState) {
                            OrbState.Idle -> "How can I help?"
                            OrbState.Listening -> "Listening..."
                            OrbState.Processing -> {
                                val currentState = agentState
                                if (currentState is AgentState.Acting) "Running: ${currentState.toolName}"
                                else "Thinking..."
                            }
                            OrbState.Speaking -> "Speaking..."
                            OrbState.Error -> "Something went wrong"
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(Modifier.height(12.dp))

                        // Response area (scrollable, limited height)
                        if (responseMessages.isNotEmpty()) {
                            val listState = rememberLazyListState()
                            LaunchedEffect(responseMessages.size) {
                                listState.animateScrollToItem(responseMessages.size - 1)
                            }

                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                items(responseMessages) { msg ->
                                    AssistantMessageBubble(msg)
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                        }

                        // Tool approval card
                        val approval = agentState as? AgentState.WaitingApproval
                        if (approval != null) {
                            ToolApprovalCard(
                                toolName = approval.toolName,
                                args = approval.args.toString().take(200),
                                onAllow = { agentRunner.respondToApproval(ApprovalResponse.ALLOW) },
                                onDeny = { agentRunner.respondToApproval(ApprovalResponse.DENY) },
                                onAlwaysAllow = { agentRunner.respondToApproval(ApprovalResponse.ALWAYS_ALLOW) },
                            )
                            Spacer(Modifier.height(8.dp))
                        }

                        // Suggestion chips (only when idle and no recent messages)
                        if (orbState == OrbState.Idle && responseMessages.isEmpty()) {
                            SuggestionChips(
                                suggestions = DEFAULT_SUGGESTIONS,
                                onChipClick = { suggestion ->
                                    sendToAgent(
                                        text = suggestion,
                                        agentRunner = agentRunner,
                                        messages = responseMessages,
                                    )
                                },
                            )
                            Spacer(Modifier.height(8.dp))
                        }

                        // Text input row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Ask FoxTouch...") },
                                shape = RoundedCornerShape(24.dp),
                                maxLines = 2,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(
                                    onSend = {
                                        if (inputText.isNotBlank() && !isBusy) {
                                            val text = inputText
                                            inputText = ""
                                            sendToAgent(
                                                text = text,
                                                agentRunner = agentRunner,
                                                messages = responseMessages,
                                            )
                                        }
                                    },
                                ),
                            )

                            Spacer(Modifier.width(8.dp))

                            // Mic / Send toggle
                            if (inputText.isBlank()) {
                                IconButton(
                                    onClick = {
                                        if (voiceInputManager.isListening) {
                                            voiceInputManager.stopVoiceInput()
                                        } else {
                                            voiceInputManager.startVoiceInput()
                                        }
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                ) {
                                    Icon(
                                        if (voiceInputManager.isListening) Icons.Default.MicOff else Icons.Default.Mic,
                                        contentDescription = if (voiceInputManager.isListening) "Stop" else "Speak",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        if (!isBusy) {
                                            val text = inputText
                                            inputText = ""
                                            sendToAgent(
                                                text = text,
                                                agentRunner = agentRunner,
                                                messages = responseMessages,
                                            )
                                        }
                                    },
                                    enabled = !isBusy,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (!isBusy) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant,
                                        ),
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send",
                                        tint = if (!isBusy) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Internal data & helpers ──────────────────────────────────────────────────

data class AssistantMessage(
    val role: String, // "user", "assistant", "tool"
    val content: String,
    val toolName: String? = null,
)

private fun sendToAgent(
    text: String,
    agentRunner: AgentRunner,
    messages: MutableList<AssistantMessage>,
) {
    messages.add(AssistantMessage(role = "user", content = text))
    // Uses startTurn() → applicationScope. Outputs arrive via outputFlow,
    // collected by the LaunchedEffect in AssistantOverlayPanel.
    agentRunner.startTurn(text, isPlanMode = false)
}

@Composable
private fun AssistantMessageBubble(message: AssistantMessage) {
    val isUser = message.role == "user"
    val isTool = message.role == "tool"

    val bgColor = when {
        isUser -> MaterialTheme.colorScheme.primaryContainer
        isTool -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val textColor = when {
        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        isTool -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        if (isTool && message.toolName != null) {
            Text(
                text = message.toolName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = bgColor,
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ToolApprovalCard(
    toolName: String,
    args: String,
    onAllow: () -> Unit,
    onDeny: () -> Unit,
    onAlwaysAllow: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Tool: $toolName",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = args,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onAllow,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text("Allow")
                }
                OutlinedButton(
                    onClick = onDeny,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Deny")
                }
                TextButton(onClick = onAlwaysAllow) {
                    Text("Always", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
