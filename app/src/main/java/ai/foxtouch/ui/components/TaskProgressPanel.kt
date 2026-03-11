package ai.foxtouch.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.foxtouch.R
import ai.foxtouch.data.db.entity.TaskEntity
import ai.foxtouch.ui.screens.chat.TaskProgress

/**
 * Shared task progress composable used in both ChatScreen and overlay.
 *
 * @param compact When true, uses tighter spacing and smaller text (for overlay).
 *   When false, wraps in a Surface with header row and expanded task details.
 */
@Composable
fun TaskProgressPanel(
    tasks: List<TaskEntity>,
    progress: TaskProgress,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (tasks.isEmpty()) return

    if (compact) {
        CompactTaskPanel(tasks, progress, modifier)
    } else {
        FullTaskPanel(tasks, progress, modifier)
    }
}

@Composable
private fun CompactTaskPanel(
    tasks: List<TaskEntity>,
    progress: TaskProgress,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        // Progress bar
        val fraction = if (progress.total > 0) progress.completed.toFloat() / progress.total else 0f
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )

        Spacer(Modifier.height(4.dp))

        // Summary + expand
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.tasks_progress, progress.completed, progress.total),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!expanded) {
            // Current + next task
            val currentTask = tasks.firstOrNull { it.status == "in_progress" }
            val nextTask = tasks.firstOrNull { it.status == "pending" }
            if (currentTask != null) {
                CompactTaskLine(stringResource(R.string.task_label_now), currentTask)
            }
            if (nextTask != null) {
                CompactTaskLine(stringResource(R.string.task_label_next), nextTask)
            }
        } else {
            tasks.forEach { task ->
                CompactTaskLine(null, task)
            }
        }
    }
}

@Composable
private fun CompactTaskLine(label: String?, task: TaskEntity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (icon, tint) = taskStatusIcon(task.status)
        Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = tint)
        Spacer(Modifier.width(4.dp))
        if (label != null) {
            Text(
                text = "$label: ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = task.title,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FullTaskPanel(
    tasks: List<TaskEntity>,
    progress: TaskProgress,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(true) }
    // Auto-expand when new tasks appear or any become in_progress
    val hasInProgress = tasks.any { it.status == "in_progress" }
    androidx.compose.runtime.LaunchedEffect(tasks.size, hasInProgress) {
        if (tasks.isNotEmpty()) expanded = true
    }

    Surface(
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Checklist,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.tasks_progress, progress.completed, progress.total),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.weight(1f))
                if (progress.inProgress > 0) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(if (expanded) R.string.collapse else R.string.expand),
                    modifier = Modifier.size(20.dp),
                )
            }

            // Progress bar
            if (progress.total > 0) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress.completed.toFloat() / progress.total },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
            }

            // Expanded task list
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    tasks.forEach { task ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val (icon, tint) = taskStatusIcon(task.status)
                            Icon(icon, contentDescription = task.status, modifier = Modifier.size(16.dp), tint = tint)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    textDecoration = if (task.status == "completed") TextDecoration.LineThrough else null,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun taskStatusIcon(status: String) = when (status) {
    "completed" -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
    "in_progress" -> Icons.Default.PlayArrow to MaterialTheme.colorScheme.tertiary
    "failed" -> Icons.Default.Error to MaterialTheme.colorScheme.error
    else -> Icons.Default.Pending to MaterialTheme.colorScheme.onSurfaceVariant
}
