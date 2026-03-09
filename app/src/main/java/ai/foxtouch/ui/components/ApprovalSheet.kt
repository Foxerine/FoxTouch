package ai.foxtouch.ui.components

import android.widget.ImageView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ai.foxtouch.tools.ToolDisplayRegistry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalSheet(
    toolName: String,
    args: JsonObject,
    sheetState: SheetState,
    showJsonMode: Boolean = false,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onAlwaysAllow: () -> Unit,
    onAllowAll: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "Approval Required",
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(Modifier.height(16.dp))

            if (showJsonMode) {
                // Debug mode: raw JSON display
                Text(
                    text = "The agent wants to execute:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = toolName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = args.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 10,
                )
            } else {
                // Show app icon for launch_app
                if (toolName == "launch_app") {
                    val context = LocalContext.current
                    val packageName = args["package_name"]?.jsonPrimitive?.content ?: ""
                    val appIcon = remember(packageName) {
                        try {
                            context.packageManager.getApplicationIcon(packageName)
                        } catch (_: Exception) { null }
                    }
                    if (appIcon != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AndroidView(
                                factory = { ctx ->
                                    ImageView(ctx).apply {
                                        setImageDrawable(appIcon)
                                        scaleType = ImageView.ScaleType.FIT_CENTER
                                    }
                                },
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = ToolDisplayRegistry.getApprovalMessage(toolName, args),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        Text(
                            text = ToolDisplayRegistry.getApprovalMessage(toolName, args),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                } else {
                    // User-friendly display for other tools
                    Text(
                        text = ToolDisplayRegistry.getApprovalMessage(toolName, args),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = ToolDisplayRegistry.getDisplayName(toolName),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))

            // Primary actions: Deny / Allow
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onDeny,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Deny")
                }

                Spacer(Modifier.width(8.dp))

                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Allow")
                }
            }

            Spacer(Modifier.height(8.dp))

            // Allow All Tools (switches to YOLO mode)
            TextButton(
                onClick = onAllowAll,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Allow All Tools (YOLO)")
            }
        }
    }
}
