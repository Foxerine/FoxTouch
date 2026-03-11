package ai.foxtouch.ui.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.foxtouch.R
import ai.foxtouch.agent.AgentState
import ai.foxtouch.data.db.entity.TaskEntity
import ai.foxtouch.data.preferences.AgentMode
import ai.foxtouch.tools.ToolDisplayRegistry
import ai.foxtouch.ui.components.AgentModeToggle
import ai.foxtouch.ui.components.TaskProgressPanel
import ai.foxtouch.ui.components.agentStateColor
import ai.foxtouch.ui.components.agentStateLabelRes
import ai.foxtouch.ui.screens.chat.TaskProgress
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography

/**
 * Compact overlay bar — replaces both orb and panel.
 *
 * Collapsed: thin status bar with colored dot + label + expand/stop buttons.
 * Expanded: shows approval card, plan review, user question, or message preview.
 */
@Composable
fun OverlayBar(
    state: AgentState,
    isBusy: Boolean,
    agentMode: AgentMode = AgentMode.NORMAL,
    isExpanded: Boolean,
    lastMessage: String?,
    tasks: List<TaskEntity> = emptyList(),
    taskProgress: TaskProgress = TaskProgress(),
    onCycleAgentMode: () -> Unit = {},
    onToggleExpand: () -> Unit,
    onStop: () -> Unit,
    onOpenApp: () -> Unit,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onAlwaysAllow: () -> Unit,
    onPlanApproveNormal: () -> Unit,
    onPlanApproveYolo: () -> Unit,
    onPlanReject: () -> Unit,
    onUserAnswer: (String) -> Unit,
    onCompletionConfirm: () -> Unit = {},
    onCompletionReject: (String) -> Unit = {},
    onCompletionDismiss: () -> Unit = {},
    showCompletionSuccess: Boolean = false,
    onDragDelta: (Float) -> Unit = {},
    onDragReset: () -> Unit = {},
) {
    Surface(
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            // ── Drag handle ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            // Negative dragAmount = finger moving up = overlay moves up (params.y increases)
                            onDragDelta(-dragAmount)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { onDragReset() })
                    },
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .width(32.dp)
                        .height(4.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                ) {}
            }

            // ── Collapsed bar: dot + label + actions ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Status dot — green when completion success
                val dotColor = if (showCompletionSuccess) {
                    androidx.compose.ui.graphics.Color(0xFF66BB6A)
                } else {
                    agentStateColor(state)
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = dotColor,
                    modifier = Modifier.size(10.dp),
                ) {}

                Spacer(Modifier.width(8.dp))

                // Status label
                AnimatedContent(
                    targetState = showCompletionSuccess,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "statusLabel",
                    modifier = Modifier.weight(1f),
                ) { isSuccess ->
                    Text(
                        text = if (isSuccess) stringResource(R.string.completion_confirmed) else agentStateLabelRes(state),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isSuccess) {
                            androidx.compose.ui.graphics.Color(0xFF66BB6A)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }

                // Agent mode toggle
                AgentModeToggle(
                    mode = agentMode,
                    onCycle = onCycleAgentMode,
                )

                Spacer(Modifier.width(4.dp))

                // Stop button (when busy)
                if (isBusy) {
                    IconButton(onClick = onStop, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = stringResource(R.string.stop),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                // Open app
                IconButton(onClick = onOpenApp, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = stringResource(R.string.open_foxtouch),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Expand/collapse
                IconButton(onClick = onToggleExpand, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        contentDescription = stringResource(if (isExpanded) R.string.collapse else R.string.expand),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // ── Expanded content ──
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 6.dp)) {
                    // Task progress
                    if (tasks.isNotEmpty()) {
                        TaskProgressPanel(
                            tasks = tasks,
                            progress = taskProgress,
                            compact = true,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }

                    // Message preview — scrollable, max 2 visible lines
                    if (lastMessage != null) {
                        Box(
                            modifier = Modifier
                                .heightIn(max = 40.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = 6.dp),
                        ) {
                            Text(
                                text = lastMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Approval card
                    if (state is AgentState.WaitingApproval) {
                        InlineApprovalCard(
                            state = state,
                            onApprove = onApprove,
                            onDeny = onDeny,
                            onAlwaysAllow = onAlwaysAllow,
                        )
                    }

                    // Plan review
                    if (state is AgentState.PlanReview) {
                        InlinePlanReview(
                            planContent = state.planContent,
                            onApproveNormal = onPlanApproveNormal,
                            onApproveYolo = onPlanApproveYolo,
                            onReject = onPlanReject,
                        )
                    }

                    // User question
                    if (state is AgentState.AskingUser) {
                        InlineUserQuestion(
                            question = state.question,
                            suggestions = state.suggestions,
                            onAnswer = onUserAnswer,
                        )
                    }

                    // Completion confirmation
                    if (state is AgentState.ConfirmingCompletion) {
                        InlineCompletionConfirm(
                            summary = state.summary,
                            onConfirm = onCompletionConfirm,
                            onReject = onCompletionReject,
                            onDismiss = onCompletionDismiss,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineApprovalCard(
    state: AgentState.WaitingApproval,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onAlwaysAllow: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = ToolDisplayRegistry.getApprovalMessage(state.toolName, state.args),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedButton(
                onClick = onDeny,
                modifier = Modifier.weight(1f),
                contentPadding = ButtonDefaults.TextButtonContentPadding,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(stringResource(R.string.deny), style = MaterialTheme.typography.labelSmall)
            }

            Button(
                onClick = onApprove,
                modifier = Modifier.weight(1f),
                contentPadding = ButtonDefaults.TextButtonContentPadding,
            ) {
                Text(stringResource(R.string.approve), style = MaterialTheme.typography.labelSmall)
            }

            TextButton(
                onClick = onAlwaysAllow,
                contentPadding = ButtonDefaults.TextButtonContentPadding,
            ) {
                Text(stringResource(R.string.always), style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun InlinePlanReview(
    planContent: String,
    onApproveNormal: () -> Unit,
    onApproveYolo: () -> Unit,
    onReject: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.plan_ready_for_review),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        // Compact plan preview (rendered Markdown)
        if (planContent.isNotBlank()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 120.dp)
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                tonalElevation = 1.dp,
            ) {
                val scrollState = rememberLazyListState()
                val smallTypography = markdownTypography(
                    h1 = MaterialTheme.typography.labelLarge,
                    h2 = MaterialTheme.typography.labelMedium,
                    h3 = MaterialTheme.typography.labelSmall,
                    paragraph = MaterialTheme.typography.bodySmall,
                    text = MaterialTheme.typography.bodySmall,
                    ordered = MaterialTheme.typography.bodySmall,
                    bullet = MaterialTheme.typography.bodySmall,
                    list = MaterialTheme.typography.bodySmall,
                )
                LazyColumn(
                    state = scrollState,
                    modifier = Modifier.padding(8.dp),
                ) {
                    item {
                        Markdown(
                            content = planContent,
                            typography = smallTypography,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedButton(
                onClick = onReject,
                modifier = Modifier.weight(1f),
                contentPadding = ButtonDefaults.TextButtonContentPadding,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.reject), style = MaterialTheme.typography.labelSmall)
            }

            Button(
                onClick = onApproveNormal,
                modifier = Modifier.weight(1f),
                contentPadding = ButtonDefaults.TextButtonContentPadding,
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.approve_plan), style = MaterialTheme.typography.labelSmall)
            }

            Button(
                onClick = onApproveYolo,
                modifier = Modifier.weight(1f),
                contentPadding = ButtonDefaults.TextButtonContentPadding,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                ),
            ) {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.auto_mode), style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun InlineUserQuestion(
    question: String,
    suggestions: List<String>,
    onAnswer: (String) -> Unit,
) {
    var customAnswer by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = question,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(6.dp))

        // Suggested answers as compact buttons
        suggestions.forEach { suggestion ->
            OutlinedButton(
                onClick = { onAnswer(suggestion) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                contentPadding = ButtonDefaults.TextButtonContentPadding,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Custom answer input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = customAnswer,
                onValueChange = { customAnswer = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.type_answer_hint), style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                shape = RoundedCornerShape(8.dp),
            )

            Spacer(Modifier.width(6.dp))

            Button(
                onClick = { if (customAnswer.isNotBlank()) onAnswer(customAnswer) },
                enabled = customAnswer.isNotBlank(),
                contentPadding = ButtonDefaults.TextButtonContentPadding,
            ) {
                Text(stringResource(R.string.send), style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun InlineCompletionConfirm(
    summary: String,
    onConfirm: () -> Unit,
    onReject: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var rejectReason by remember { mutableStateOf("") }
    var showRejectInput by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.completion_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(6.dp))

        if (!showRejectInput) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedButton(
                    onClick = { showRejectInput = true },
                    modifier = Modifier.weight(1f),
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                ) {
                    Text(stringResource(R.string.completion_not_done), style = MaterialTheme.typography.labelSmall)
                }

                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.completion_confirmed), style = MaterialTheme.typography.labelSmall)
                }

                TextButton(
                    onClick = onDismiss,
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                ) {
                    Text(stringResource(R.string.dismiss), style = MaterialTheme.typography.labelSmall)
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = rejectReason,
                    onValueChange = { rejectReason = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.completion_reason_hint), style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    shape = RoundedCornerShape(8.dp),
                )

                Spacer(Modifier.width(6.dp))

                Button(
                    onClick = { onReject(rejectReason.ifBlank { "Not done yet" }) },
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                ) {
                    Text(stringResource(R.string.send), style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}
