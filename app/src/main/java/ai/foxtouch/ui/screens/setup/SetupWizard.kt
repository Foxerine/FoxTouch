package ai.foxtouch.ui.screens.setup

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ai.foxtouch.accessibility.AccessibilityBridge
import ai.foxtouch.agent.AVAILABLE_PROVIDERS
import ai.foxtouch.agent.ModelInfo
import ai.foxtouch.agent.getDefaultModel
import ai.foxtouch.ui.screens.settings.ModelListState
import ai.foxtouch.ui.screens.settings.SettingsViewModel
import android.provider.Settings as AndroidSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardScreen(
    onSetupComplete: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    var currentStep by remember { mutableIntStateOf(0) }
    val totalSteps = 5
    val scope = rememberCoroutineScope()
    var selectedProvider by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Setup") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LinearProgressIndicator(
                progress = { (currentStep + 1).toFloat() / totalSteps },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(32.dp))

            AnimatedContent(targetState = currentStep, label = "setup_step") { step ->
                when (step) {
                    0 -> ProviderConfigStep(
                        onNext = { provider, apiKey, baseUrl, proxy ->
                            scope.launch {
                                viewModel.setProvider(provider)
                                viewModel.saveApiKey(provider, apiKey)
                                if (baseUrl.isNotBlank()) viewModel.setProviderBaseUrl(provider, baseUrl)
                                if (proxy.isNotBlank()) viewModel.setProviderProxy(provider, proxy)
                                selectedProvider = provider
                                viewModel.fetchModels(provider)
                                currentStep = 1
                            }
                        },
                    )
                    1 -> ModelSelectionStep(
                        viewModel = viewModel,
                        provider = selectedProvider,
                        onNext = { model ->
                            scope.launch {
                                viewModel.setModel(model)
                                currentStep = 2
                            }
                        },
                        onSkip = { currentStep = 2 },
                    )
                    2 -> PermissionsStep(
                        onNext = { currentStep = 3 },
                    )
                    3 -> PrivacyNoticeStep(
                        onNext = { currentStep = 4 },
                    )
                    4 -> DoneStep(
                        onComplete = {
                            scope.launch {
                                viewModel.markSetupComplete()
                            }
                            onSetupComplete()
                        },
                    )
                }
            }
        }
    }
}

// ── Step 0: Provider Configuration ──────────────────────────────────────

@Composable
private fun ProviderConfigStep(
    onNext: (provider: String, apiKey: String, baseUrl: String, proxy: String) -> Unit,
) {
    var provider by remember { mutableStateOf(AVAILABLE_PROVIDERS.first().id) }
    var apiKey by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var proxy by remember { mutableStateOf("") }
    var showDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.SmartToy,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Configure AI Provider",
            style = MaterialTheme.typography.headlineSmall,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Set up your LLM provider connection.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        // ── Required ──
        SectionLabel("Required")

        Box {
            OutlinedButton(onClick = { showDropdown = true }) {
                val displayName = AVAILABLE_PROVIDERS.find { it.id == provider }?.displayName ?: provider
                Text(displayName)
            }
            DropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }) {
                AVAILABLE_PROVIDERS.forEach { p ->
                    DropdownMenuItem(
                        text = { Text(p.displayName) },
                        onClick = { provider = p.id; showDropdown = false },
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key") },
            leadingIcon = { Icon(Icons.Default.Key, null) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))

        // ── Optional ──
        SectionLabel("Optional")

        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("Base URL") },
            leadingIcon = { Icon(Icons.Default.Link, null) },
            placeholder = { Text("Leave empty to use official endpoint") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = proxy,
            onValueChange = { proxy = it },
            label = { Text("HTTP Proxy") },
            leadingIcon = { Icon(Icons.Default.VpnLock, null) },
            placeholder = { Text("e.g., http://127.0.0.1:7890") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { onNext(provider, apiKey, baseUrl, proxy) },
            enabled = apiKey.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Next")
        }
    }
}

// ── Step 1: Model Selection ─────────────────────────────────────────────

@Composable
private fun ModelSelectionStep(
    viewModel: SettingsViewModel,
    provider: String,
    onNext: (String) -> Unit,
    onSkip: () -> Unit,
) {
    val modelListState by viewModel.modelListState.collectAsState()
    var selectedModel by remember { mutableStateOf(getDefaultModel(provider)) }
    var showCustomInput by remember { mutableStateOf(false) }
    var customModelName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.SmartToy,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Select Model",
            style = MaterialTheme.typography.headlineSmall,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Choose the AI model to use. Models marked with * are recommended.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        when (val state = modelListState) {
            is ModelListState.Loading, is ModelListState.Idle -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is ModelListState.Error -> {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.fetchModels(provider) }) {
                            Text("Retry")
                        }
                    }
                }
                SetupModelList(
                    models = state.fallbackModels,
                    selectedModel = selectedModel,
                    onSelectModel = { selectedModel = it },
                )
            }

            is ModelListState.Success -> {
                SetupModelList(
                    models = state.models,
                    selectedModel = selectedModel,
                    onSelectModel = { selectedModel = it },
                )
            }
        }

        // Custom model option
        Spacer(Modifier.height(8.dp))

        if (!showCustomInput) {
            TextButton(onClick = { showCustomInput = true }) {
                Text("Custom model...")
            }
        } else {
            OutlinedTextField(
                value = customModelName,
                onValueChange = { customModelName = it },
                label = { Text("Custom model name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (customModelName.isNotBlank()) {
                        selectedModel = customModelName.trim()
                    }
                    showCustomInput = false
                },
                enabled = customModelName.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Use Custom Model")
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { onNext(selectedModel) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Next")
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Skip (use default)")
        }
    }
}

@Composable
private fun SetupModelList(
    models: List<ModelInfo>,
    selectedModel: String,
    onSelectModel: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        models.forEach { m ->
            val isSelected = m.id == selectedModel
            ListItem(
                headlineContent = {
                    Text(
                        text = m.displayName + when {
                            m.recommended -> " *"
                            m.warning != null -> " (!)"
                            else -> ""
                        },
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            m.warning != null -> MaterialTheme.colorScheme.error
                            m.recommended -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                },
                supportingContent = if (m.warning != null) {
                    {
                        Text(
                            text = m.warning,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        )
                    }
                } else null,
                leadingContent = {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onSelectModel(m.id) },
                    )
                },
                modifier = Modifier.clickable { onSelectModel(m.id) },
            )
        }
    }
}

// ── Step 2: Permissions ─────────────────────────────────────────────────

@Composable
private fun PermissionsStep(onNext: () -> Unit) {
    val context = LocalContext.current
    val a11yConnected = AccessibilityBridge.isServiceConnected
    val canDrawOverlays = AndroidSettings.canDrawOverlays(context)
    val allGranted = a11yConnected && canDrawOverlays

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.Accessibility,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Grant Permissions",
            style = MaterialTheme.typography.headlineSmall,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "FoxTouch needs these permissions to work properly.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        // ── Accessibility Service ──
        SectionLabel("Accessibility Service")

        if (a11yConnected) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Connected", color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Text(
                text = "Required for reading screen content and performing actions.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open Accessibility Settings")
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Overlay Permission ──
        SectionLabel("Overlay Permission")

        if (canDrawOverlays) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Granted", color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Text(
                text = "Required for showing the floating status bubble during agent execution.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    context.startActivity(Intent(
                        AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Grant Overlay Permission")
            }
        }

        Spacer(Modifier.height(24.dp))

        OutlinedButton(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (allGranted) "Next" else "Skip for now")
        }
    }
}

// ── Step 3: Privacy Notice ──────────────────────────────────────────────

@Composable
private fun PrivacyNoticeStep(onNext: () -> Unit) {
    var acknowledged by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.PrivacyTip,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Privacy Notice",
            style = MaterialTheme.typography.headlineSmall,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "FoxTouch sends the following data to your chosen LLM provider:",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        val items = listOf(
            "Your messages (text and voice transcriptions)",
            "Screen content (UI element tree, including app names and text)",
            "Device information (model, battery, network status)",
            "Installed app list (names and package names)",
            "Screenshots (when visual analysis is needed)",
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEach { item ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("•", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "This data is processed according to your LLM provider's privacy policy. " +
                "FoxTouch does not store or share this data beyond what is needed for operation.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { acknowledged = !acknowledged },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = acknowledged,
                onCheckedChange = { acknowledged = it },
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "I understand and agree to proceed",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onNext,
            enabled = acknowledged,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Next")
        }
    }
}

// ── Step 4: Done ────────────────────────────────────────────────────────

@Composable
private fun DoneStep(onComplete: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "All Set!",
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "FoxTouch is ready. Start by telling it what you need.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Get Started")
        }
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    )
}
