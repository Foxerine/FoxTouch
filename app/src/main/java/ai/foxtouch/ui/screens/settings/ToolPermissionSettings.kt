package ai.foxtouch.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.foxtouch.permission.DEFAULT_TOOL_PERMISSIONS
import ai.foxtouch.permission.PermissionPolicy
import ai.foxtouch.permission.PermissionStore
import ai.foxtouch.permission.RiskLevel
import kotlinx.coroutines.launch

@Composable
fun ToolPermissionSettings(
    permissionStore: PermissionStore,
    modifier: androidx.compose.ui.Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(DEFAULT_TOOL_PERMISSIONS) { config ->
            var showMenu by remember { mutableStateOf(false) }
            var currentPolicy by remember { mutableStateOf(config.defaultPolicy) }

            // Load actual policy
            remember {
                scope.launch {
                    currentPolicy = permissionStore.getPolicy(config.toolName)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showMenu = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = config.toolName,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "Risk: ${config.riskLevel.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = when (config.riskLevel) {
                                RiskLevel.LOW -> MaterialTheme.colorScheme.primary
                                RiskLevel.MEDIUM -> MaterialTheme.colorScheme.tertiary
                                RiskLevel.HIGH -> MaterialTheme.colorScheme.error
                            },
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    Text(
                        text = when (currentPolicy) {
                            PermissionPolicy.ALWAYS_ALLOW -> "Always Allow"
                            PermissionPolicy.ASK_EACH_TIME -> "Ask Each Time"
                            PermissionPolicy.NEVER_ALLOW -> "Never Allow"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        PermissionPolicy.entries.forEach { policy ->
                            DropdownMenuItem(
                                text = {
                                    Text(when (policy) {
                                        PermissionPolicy.ALWAYS_ALLOW -> "Always Allow"
                                        PermissionPolicy.ASK_EACH_TIME -> "Ask Each Time"
                                        PermissionPolicy.NEVER_ALLOW -> "Never Allow"
                                    })
                                },
                                onClick = {
                                    currentPolicy = policy
                                    scope.launch { permissionStore.setPolicy(config.toolName, policy) }
                                    showMenu = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
