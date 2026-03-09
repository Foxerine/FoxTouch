package ai.foxtouch.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ai.foxtouch.R
import ai.foxtouch.agent.AgentState

private val ColorIdle = Color(0xFF9E9E9E)
private val ColorThinking = Color(0xFF42A5F5)
private val ColorActing = Color(0xFF66BB6A)
private val ColorWaiting = Color(0xFFFFA726)
private val ColorError = Color(0xFFEF5350)

@Composable
fun agentStateColor(state: AgentState): Color = when (state) {
    is AgentState.Idle -> ColorIdle
    is AgentState.Thinking -> ColorThinking
    is AgentState.Acting -> ColorActing
    is AgentState.WaitingApproval,
    is AgentState.PlanReview,
    is AgentState.AskingUser,
    is AgentState.ConfirmingCompletion -> ColorWaiting
    is AgentState.Error -> ColorError
}

@Composable
fun agentStateLabel(state: AgentState): String = when (state) {
    is AgentState.Idle -> "Idle"
    is AgentState.Thinking -> "Thinking..."
    is AgentState.Acting -> "Running: ${state.toolName}"
    is AgentState.WaitingApproval -> "Needs approval"
    is AgentState.PlanReview -> "Plan ready"
    is AgentState.AskingUser -> "Question for you"
    is AgentState.ConfirmingCompletion -> "Task complete?"
    is AgentState.Error -> "Error"
}

/** Localized version of [agentStateLabel] using string resources. */
@Composable
fun agentStateLabelRes(state: AgentState): String = when (state) {
    is AgentState.Idle -> stringResource(R.string.state_idle)
    is AgentState.Thinking -> stringResource(R.string.state_thinking)
    is AgentState.Acting -> stringResource(R.string.state_acting, state.toolName)
    is AgentState.WaitingApproval -> stringResource(R.string.state_needs_approval)
    is AgentState.PlanReview -> stringResource(R.string.state_plan_ready)
    is AgentState.AskingUser -> stringResource(R.string.state_asking_user)
    is AgentState.ConfirmingCompletion -> stringResource(R.string.state_confirming_completion)
    is AgentState.Error -> stringResource(R.string.state_error)
}

/**
 * Animated orb that reflects agent state with a glowing ring.
 * Used by both the floating overlay (collapsed) and the expanded panel header.
 */
@Composable
fun AgentStatusOrb(
    state: AgentState,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
) {
    val stateColor by animateColorAsState(
        targetValue = agentStateColor(state),
        label = "orbColor",
    )

    val shouldPulse = state is AgentState.Thinking
            || state is AgentState.WaitingApproval
            || state is AgentState.PlanReview
            || state is AgentState.AskingUser
            || state is AgentState.ConfirmingCompletion

    val infiniteTransition = rememberInfiniteTransition(label = "orbPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowAlpha",
    )

    val effectiveGlowAlpha = when {
        state is AgentState.Idle -> 0f
        shouldPulse -> glowAlpha
        else -> 0.4f
    }
    val effectiveScale = if (shouldPulse) pulseScale else 1f

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        // Glow ring
        Canvas(modifier = Modifier.size(size)) {
            val ringRadius = (this.size.minDimension / 2) * effectiveScale
            drawCircle(
                color = stateColor.copy(alpha = effectiveGlowAlpha * 0.5f),
                radius = ringRadius,
            )
            drawCircle(
                color = stateColor.copy(alpha = effectiveGlowAlpha),
                radius = ringRadius * 0.9f,
                style = Stroke(width = 3.dp.toPx()),
            )
        }

        // Inner circle with icon
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(size * 0.7f),
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "FoxTouch",
                modifier = Modifier.padding(4.dp),
            )
        }
    }
}
