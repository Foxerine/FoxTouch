package ai.foxtouch.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import ai.foxtouch.voice.RecognitionState

@Composable
fun VoiceInputButton(
    isListening: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    recognitionState: RecognitionState = RecognitionState.Idle,
    modifier: Modifier = Modifier,
) {
    val containerColor by animateColorAsState(
        targetValue = if (isListening)
            MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.secondaryContainer,
        label = "mic_color",
    )

    val scale by animateFloatAsState(
        targetValue = if (isListening) 1.1f else 1.0f,
        label = "mic_scale",
    )

    FloatingActionButton(
        onClick = { if (isListening) onStop() else onStart() },
        shape = CircleShape,
        containerColor = containerColor,
        modifier = modifier
            .size(48.dp)
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        onStart()
                    },
                )
            },
    ) {
        Icon(
            if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
            contentDescription = when {
                isListening && recognitionState is RecognitionState.Partial ->
                    "Listening: ${recognitionState.text}"
                isListening -> "Tap to stop listening"
                else -> "Tap to start voice input"
            },
        )
    }
}
