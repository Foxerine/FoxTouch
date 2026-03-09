package ai.foxtouch.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Animated circular voice indicator.
 *
 * Visual states:
 * - **Idle**: subtle slow pulse (low alpha ring)
 * - **Listening**: expanding ripple rings
 * - **Processing**: rotating gradient arc
 * - **Speaking**: pulsing fill
 * - **Error**: red flash
 */
enum class OrbState { Idle, Listening, Processing, Speaking, Error }

@Composable
fun VoiceOrb(
    state: OrbState,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) {
    val transition = rememberInfiniteTransition(label = "orb")

    val pulseScale by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    OrbState.Idle -> 2000
                    OrbState.Listening -> 800
                    OrbState.Processing -> 600
                    OrbState.Speaking -> 500
                    OrbState.Error -> 300
                },
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )

    val rippleAlpha by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ripple",
    )

    val baseColor = when (state) {
        OrbState.Idle -> Color(0xFF9E9E9E)
        OrbState.Listening -> Color(0xFF2196F3)
        OrbState.Processing -> Color(0xFF7C4DFF)
        OrbState.Speaking -> Color(0xFF4CAF50)
        OrbState.Error -> Color(0xFFF44336)
    }

    Canvas(modifier = modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val baseRadius = this.size.minDimension / 2f * 0.6f

        when (state) {
            OrbState.Idle -> {
                drawCircle(
                    color = baseColor.copy(alpha = 0.3f),
                    radius = baseRadius * pulseScale,
                    center = center,
                )
                drawCircle(
                    color = baseColor.copy(alpha = 0.6f),
                    radius = baseRadius * 0.7f,
                    center = center,
                )
            }

            OrbState.Listening -> {
                // Expanding ripple ring
                val rippleRadius = baseRadius * (1f + (1f - rippleAlpha) * 0.5f)
                drawCircle(
                    color = baseColor.copy(alpha = rippleAlpha * 0.4f),
                    radius = rippleRadius,
                    center = center,
                    style = Stroke(width = 3.dp.toPx()),
                )
                // Core orb
                drawCircle(
                    color = baseColor,
                    radius = baseRadius * pulseScale,
                    center = center,
                )
            }

            OrbState.Processing -> {
                // Rotating arc
                drawArc(
                    color = baseColor.copy(alpha = 0.3f),
                    startAngle = rotation,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx()),
                )
                drawCircle(
                    color = baseColor.copy(alpha = 0.7f),
                    radius = baseRadius * 0.6f,
                    center = center,
                )
            }

            OrbState.Speaking -> {
                drawCircle(
                    color = baseColor.copy(alpha = 0.2f),
                    radius = baseRadius * (1f + pulseScale * 0.2f),
                    center = center,
                )
                drawCircle(
                    color = baseColor,
                    radius = baseRadius * pulseScale * 0.85f,
                    center = center,
                )
            }

            OrbState.Error -> {
                drawCircle(
                    color = baseColor.copy(alpha = pulseScale * 0.5f),
                    radius = baseRadius,
                    center = center,
                )
                drawCircle(
                    color = baseColor,
                    radius = baseRadius * 0.5f,
                    center = center,
                )
            }
        }
    }
}
