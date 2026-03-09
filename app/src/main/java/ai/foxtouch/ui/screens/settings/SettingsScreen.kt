package ai.foxtouch.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CameraEnhance
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ai.foxtouch.MainActivity
import ai.foxtouch.R
import ai.foxtouch.accessibility.AccessibilityBridge
import ai.foxtouch.accessibility.ScreenCaptureManager
import ai.foxtouch.agent.AVAILABLE_PROVIDERS
import ai.foxtouch.data.preferences.SUPPORTED_LANGUAGES
import ai.foxtouch.agent.getDefaultModel
import ai.foxtouch.agent.getProviderInfo
import ai.foxtouch.ui.overlay.FloatingBubbleService
import android.provider.Settings as AndroidSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToAgentDocs: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val provider by viewModel.provider.collectAsState()
    val model by viewModel.model.collectAsState()
    val modelListState by viewModel.modelListState.collectAsState()
    val ttsEnabled by viewModel.ttsEnabled.collectAsState()
    val ttsSpeechRate by viewModel.ttsSpeechRate.collectAsState()
    val providerConfigs by viewModel.allProviderConfigs.collectAsState()
    val streamingEnabled by viewModel.streamingEnabled.collectAsState()
    val maxIterations by viewModel.maxIterations.collectAsState()
    val debugMode by viewModel.debugMode.collectAsState()
    val debugJsonDisplay by viewModel.debugJsonDisplay.collectAsState()
    val overlayEnabled by viewModel.overlayEnabled.collectAsState()
    val thinkingEnabled by viewModel.thinkingEnabled.collectAsState()
    val providerKeyStatus by viewModel.providerKeyStatus.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showProviderSheet by remember { mutableStateOf(false) }
    var editingKeyProvider by remember { mutableStateOf<String?>(null) }
    var showModelSheet by remember { mutableStateOf(false) }
    var showToolPermissions by remember { mutableStateOf(false) }
    var showDeviceContextPreview by remember { mutableStateOf(false) }
    var showCustomModelDialog by remember { mutableStateOf(false) }
    var versionTapCount by remember { mutableIntStateOf(0) }
    val providerInfo = getProviderInfo(provider)

    // Derive display model name from current state
    val displayModelName = when (val state = modelListState) {
        is ModelListState.Success -> state.models.find { it.id == model }?.displayName ?: model
        is ModelListState.Error -> state.fallbackModels.find { it.id == model }?.displayName ?: model
        else -> providerInfo?.models?.find { it.id == model }?.displayName ?: model
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // LLM Provider section
            SectionHeader(stringResource(R.string.section_ai_model))

            ListItem(
                headlineContent = { Text(stringResource(R.string.provider)) },
                supportingContent = {
                    val hasKey = providerKeyStatus[provider] == true
                    Column {
                        Text(providerInfo?.displayName ?: provider)
                        if (!hasKey) {
                            Text(
                                text = stringResource(R.string.key_not_configured),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                leadingContent = { Icon(Icons.Default.SmartToy, null) },
                modifier = Modifier.clickable { showProviderSheet = true },
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.model)) },
                supportingContent = {
                    if (modelListState is ModelListState.Loading) {
                        Box { CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) }
                    } else {
                        Text(displayModelName)
                    }
                },
                leadingContent = { Icon(Icons.Default.SmartToy, null) },
                modifier = Modifier.clickable {
                    viewModel.fetchModels()
                    showModelSheet = true
                },
            )

            HorizontalDivider()

            // Agent section
            SectionHeader(stringResource(R.string.section_agent))

            ListItem(
                headlineContent = { Text(stringResource(R.string.streaming)) },
                supportingContent = { Text(stringResource(if (streamingEnabled) R.string.streaming_on else R.string.streaming_off)) },
                leadingContent = { Icon(Icons.Default.Speed, null) },
                trailingContent = {
                    Switch(
                        checked = streamingEnabled,
                        onCheckedChange = { scope.launch { viewModel.setStreamingEnabled(it) } },
                    )
                },
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.max_iterations)) },
                supportingContent = {
                    Column {
                        Text(stringResource(R.string.per_turn, maxIterations))
                        Slider(
                            value = maxIterations.toFloat(),
                            onValueChange = { scope.launch { viewModel.setMaxIterations(it.toInt()) } },
                            valueRange = 10f..500f,
                            steps = 0,
                        )
                    }
                },
                leadingContent = { Icon(Icons.Default.Speed, null) },
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.thinking_mode)) },
                supportingContent = {
                    Text(stringResource(if (thinkingEnabled) R.string.thinking_mode_on else R.string.thinking_mode_off))
                },
                leadingContent = { Icon(Icons.Default.SmartToy, null) },
                trailingContent = {
                    Switch(
                        checked = thinkingEnabled,
                        onCheckedChange = { scope.launch { viewModel.setThinkingEnabled(it) } },
                    )
                },
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.agent_instructions)) },
                supportingContent = { Text(stringResource(R.string.agent_instructions_desc)) },
                leadingContent = { Icon(Icons.Default.Terminal, null) },
                modifier = Modifier.clickable { onNavigateToAgentDocs() },
            )

            HorizontalDivider()

            // Assistant section
            SectionHeader(stringResource(R.string.section_assistant))

            ListItem(
                headlineContent = { Text(stringResource(R.string.set_default_assistant)) },
                supportingContent = { Text(stringResource(R.string.set_default_assistant_desc)) },
                leadingContent = { Icon(Icons.Default.Assistant, null) },
                modifier = Modifier.clickable {
                    // Open default assistant settings
                    try {
                        context.startActivity(
                            Intent("com.android.settings.MANAGE_ASSIST").apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    } catch (_: Exception) {
                        // Fallback: open general settings
                        context.startActivity(
                            Intent(AndroidSettings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    }
                },
            )

            HorizontalDivider()

            // Voice section
            SectionHeader(stringResource(R.string.section_voice))

            ListItem(
                headlineContent = { Text(stringResource(R.string.tts_title)) },
                supportingContent = { Text(stringResource(R.string.tts_desc)) },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.VolumeUp, null) },
                trailingContent = {
                    Switch(
                        checked = ttsEnabled,
                        onCheckedChange = { scope.launch { viewModel.setTtsEnabled(it) } },
                    )
                },
            )

            if (ttsEnabled) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.speech_rate)) },
                    supportingContent = {
                        Column {
                            Text("${String.format("%.1f", ttsSpeechRate)}x")
                            Slider(
                                value = ttsSpeechRate,
                                onValueChange = { scope.launch { viewModel.setTtsSpeechRate(it) } },
                                valueRange = 0.5f..2.0f,
                                steps = 5,
                            )
                        }
                    },
                    leadingContent = { Icon(Icons.Default.Speed, null) },
                )
            }

            HorizontalDivider()

            // Permissions section
            SectionHeader(stringResource(R.string.section_permissions))

            val a11yConnected = AccessibilityBridge.isServiceConnected
            ListItem(
                headlineContent = { Text(stringResource(R.string.a11y_service)) },
                supportingContent = {
                    Text(stringResource(if (a11yConnected) R.string.a11y_connected else R.string.a11y_not_connected))
                },
                leadingContent = { Icon(Icons.Default.Accessibility, null) },
                modifier = Modifier.clickable {
                    context.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                },
            )

            val canDrawOverlays = AndroidSettings.canDrawOverlays(context)
            ListItem(
                headlineContent = { Text(stringResource(R.string.overlay_permission)) },
                supportingContent = {
                    Text(stringResource(if (canDrawOverlays) R.string.overlay_granted else R.string.overlay_not_granted))
                },
                leadingContent = { Icon(Icons.Default.Layers, null) },
                modifier = Modifier.clickable {
                    if (!canDrawOverlays) {
                        context.startActivity(Intent(
                            AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
                },
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.floating_overlay)) },
                supportingContent = {
                    Text(stringResource(if (overlayEnabled) R.string.overlay_on_desc else R.string.overlay_off_desc))
                },
                leadingContent = { Icon(Icons.Default.Layers, null) },
                trailingContent = {
                    Switch(
                        checked = overlayEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                viewModel.setOverlayEnabled(enabled)
                                if (enabled && canDrawOverlays) {
                                    FloatingBubbleService.start(context)
                                } else if (!enabled) {
                                    FloatingBubbleService.stop(context)
                                }
                            }
                        },
                    )
                },
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.tool_permissions)) },
                supportingContent = { Text(stringResource(R.string.tool_permissions_desc)) },
                leadingContent = { Icon(Icons.Default.Security, null) },
                modifier = Modifier.clickable { showToolPermissions = !showToolPermissions },
            )

            // Enhanced screenshot (MediaProjection fallback)
            var showEnhancedScreenshot by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text(stringResource(R.string.enhanced_screenshot)) },
                supportingContent = { Text(stringResource(R.string.enhanced_screenshot_desc)) },
                leadingContent = { Icon(Icons.Default.CameraEnhance, null) },
                modifier = Modifier.clickable { showEnhancedScreenshot = true },
            )

            if (showEnhancedScreenshot) {
                EnhancedScreenshotDialog(
                    viewModel = viewModel,
                    onDismiss = { showEnhancedScreenshot = false },
                )
            }

            ListItem(
                headlineContent = {
                    Text(
                        stringResource(R.string.yolo_warning_title),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                supportingContent = {
                    Text(
                        stringResource(R.string.yolo_warning_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                leadingContent = {
                    Icon(
                        Icons.Default.Security,
                        null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
            )

            HorizontalDivider()

            // Language section
            SectionHeader(stringResource(R.string.section_language))

            val appLanguage by viewModel.appLanguage.collectAsState()
            var showLanguageMenu by remember { mutableStateOf(false) }

            ListItem(
                headlineContent = { Text(stringResource(R.string.app_language)) },
                supportingContent = {
                    Text(
                        SUPPORTED_LANGUAGES.find { it.code == appLanguage }?.displayName
                            ?: "System Default",
                    )
                },
                leadingContent = { Icon(Icons.Default.Language, null) },
                modifier = Modifier.clickable { showLanguageMenu = true },
            )

            DropdownMenu(
                expanded = showLanguageMenu,
                onDismissRequest = { showLanguageMenu = false },
            ) {
                SUPPORTED_LANGUAGES.forEach { lang ->
                    DropdownMenuItem(
                        text = { Text(lang.displayName) },
                        onClick = {
                            viewModel.setAppLanguage(lang.code)
                            showLanguageMenu = false
                        },
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // About
            SectionHeader(stringResource(R.string.section_about))
            ListItem(
                headlineContent = { Text(stringResource(R.string.about_version)) },
                supportingContent = {
                    Text(stringResource(if (debugMode) R.string.about_desc_debug else R.string.about_desc))
                },
                modifier = Modifier.clickable {
                    versionTapCount++
                    if (versionTapCount >= 7 && !debugMode) {
                        scope.launch { viewModel.setDebugMode(true) }
                        versionTapCount = 0
                    }
                },
            )

            // Debug section (hidden until unlocked by tapping version 7 times)
            if (debugMode) {
                HorizontalDivider()
                SectionHeader(stringResource(R.string.section_debug))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.json_display_mode)) },
                    supportingContent = { Text(stringResource(R.string.json_display_desc)) },
                    leadingContent = { Icon(Icons.Default.BugReport, null) },
                    trailingContent = {
                        Switch(
                            checked = debugJsonDisplay,
                            onCheckedChange = { scope.launch { viewModel.setDebugJsonDisplay(it) } },
                        )
                    },
                )

                val isGatheringContext by viewModel.isGatheringContext.collectAsState()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.preview_device_context)) },
                    supportingContent = { Text(stringResource(R.string.preview_device_context_desc)) },
                    leadingContent = {
                        if (isGatheringContext) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Visibility, null)
                        }
                    },
                    modifier = Modifier.clickable {
                        viewModel.gatherDeviceContext()
                        showDeviceContextPreview = true
                    },
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.disable_debug)) },
                    supportingContent = { Text(stringResource(R.string.disable_debug_desc)) },
                    leadingContent = { Icon(Icons.Default.BugReport, null) },
                    modifier = Modifier.clickable {
                        scope.launch { viewModel.setDebugMode(false) }
                    },
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showProviderSheet) {
        ProviderManagementSheet(
            currentProvider = provider,
            providerKeyStatus = providerKeyStatus,
            providerConfigs = providerConfigs,
            onSelectProvider = { selectedProvider ->
                scope.launch {
                    viewModel.setProvider(selectedProvider)
                    viewModel.setModel(getDefaultModel(selectedProvider))
                }
                viewModel.fetchModels(selectedProvider)
            },
            onConfigureProvider = { providerId ->
                editingKeyProvider = providerId
            },
            onDismiss = { showProviderSheet = false },
        )
    }

    editingKeyProvider?.let { providerId ->
        val config = providerConfigs[providerId]
        ProviderConfigDialog(
            provider = providerId,
            initialBaseUrl = config?.baseUrl ?: "",
            initialProxy = config?.proxy ?: "",
            onDismiss = { editingKeyProvider = null },
            onSave = { apiKey, baseUrl, proxy ->
                editingKeyProvider = null
                scope.launch {
                    if (apiKey.isNotBlank()) viewModel.saveApiKey(providerId, apiKey)
                    viewModel.setProviderBaseUrl(providerId, baseUrl)
                    viewModel.setProviderProxy(providerId, proxy)
                }
            },
        )
    }

    if (showToolPermissions) {
        ToolPermissionFullDialog(
            permissionStore = viewModel.permissionStore,
            onDismiss = { showToolPermissions = false },
        )
    }

    if (showDeviceContextPreview) {
        val deviceContextText by viewModel.deviceContextPreview.collectAsState()
        val isGatheringCtx by viewModel.isGatheringContext.collectAsState()
        DeviceContextPreviewDialog(
            deviceContextText = deviceContextText,
            isLoading = isGatheringCtx,
            onDismiss = { showDeviceContextPreview = false },
        )
    }

    if (showModelSheet) {
        ModelSelectionSheet(
            state = modelListState,
            currentModel = model,
            onSelectModel = { selectedModel ->
                scope.launch { viewModel.setModel(selectedModel) }
                showModelSheet = false
            },
            onCustomModel = {
                showModelSheet = false
                showCustomModelDialog = true
            },
            onRetry = { viewModel.fetchModels() },
            onDismiss = { showModelSheet = false },
        )
    }

    if (showCustomModelDialog) {
        CustomModelDialog(
            currentModel = model,
            onDismiss = { showCustomModelDialog = false },
            onSave = { customModel ->
                scope.launch { viewModel.setModel(customModel) }
                showCustomModelDialog = false
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * Dialog for configuring a provider's API key, base URL, and proxy.
 * API key field is always empty (write-only for security). Base URL and proxy show current values.
 */
@Composable
private fun ProviderConfigDialog(
    provider: String,
    initialBaseUrl: String,
    initialProxy: String,
    onDismiss: () -> Unit,
    onSave: (apiKey: String, baseUrl: String, proxy: String) -> Unit,
) {
    var apiKey by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf(initialBaseUrl) }
    var proxy by remember { mutableStateOf(initialProxy) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "${getProviderInfo(provider)?.displayName ?: provider.replaceFirstChar { it.uppercase() }} ${stringResource(R.string.settings_title)}",
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(stringResource(R.string.api_key_label)) },
                    placeholder = { Text(stringResource(R.string.api_key_unchanged_hint)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text(stringResource(R.string.base_url)) },
                    placeholder = { Text(stringResource(R.string.base_url_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = proxy,
                    onValueChange = { proxy = it },
                    label = { Text(stringResource(R.string.http_proxy)) },
                    placeholder = { Text(stringResource(R.string.proxy_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(apiKey, baseUrl, proxy) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolPermissionFullDialog(
    permissionStore: ai.foxtouch.permission.PermissionStore,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.tool_permissions),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
            Text(
                text = stringResource(R.string.tool_permissions_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            ToolPermissionSettings(
                permissionStore = permissionStore,
                modifier = Modifier.height(400.dp),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DeviceContextPreviewDialog(
    deviceContextText: String?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.device_context_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.device_context_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                ) {
                    if (isLoading || deviceContextText == null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = androidx.compose.ui.Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.padding(12.dp),
                        ) {
                            item {
                                Text(
                                    text = deviceContextText,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

@Composable
private fun CustomModelDialog(
    currentModel: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var customModelName by remember { mutableStateOf(currentModel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_model)) },
        text = {
            OutlinedTextField(
                value = customModelName,
                onValueChange = { customModelName = it },
                label = { Text(stringResource(R.string.custom_model_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(customModelName.trim()) },
                enabled = customModelName.isNotBlank(),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderManagementSheet(
    currentProvider: String,
    providerKeyStatus: Map<String, Boolean>,
    providerConfigs: Map<String, SettingsViewModel.ProviderNetworkConfig>,
    onSelectProvider: (String) -> Unit,
    onConfigureProvider: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.provider),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )

            AVAILABLE_PROVIDERS.forEach { p ->
                val hasKey = providerKeyStatus[p.id] == true
                val isSelected = p.id == currentProvider
                val config = providerConfigs[p.id]
                val hasCustomUrl = !config?.baseUrl.isNullOrBlank()
                val hasProxy = !config?.proxy.isNullOrBlank()

                ListItem(
                    headlineContent = {
                        Text(
                            text = p.displayName,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    supportingContent = {
                        Column {
                            Text(
                                text = stringResource(
                                    if (hasKey) R.string.key_configured else R.string.key_not_configured,
                                ),
                                color = if (hasKey) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.error,
                            )
                            if (hasCustomUrl || hasProxy) {
                                val info = buildList {
                                    if (hasCustomUrl) add("URL: ${config!!.baseUrl}")
                                    if (hasProxy) add("Proxy: ${config!!.proxy}")
                                }.joinToString(" | ")
                                Text(
                                    text = info,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    },
                    leadingContent = {
                        androidx.compose.material3.RadioButton(
                            selected = isSelected,
                            onClick = { onSelectProvider(p.id) },
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = { onConfigureProvider(p.id) }) {
                            Icon(Icons.Default.Key, stringResource(R.string.configure_api_key))
                        }
                    },
                    modifier = Modifier.clickable { onSelectProvider(p.id) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelectionSheet(
    state: ModelListState,
    currentModel: String,
    onSelectModel: (String) -> Unit,
    onCustomModel: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.model),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )

            when (state) {
                is ModelListState.Loading, is ModelListState.Idle -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
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
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = onRetry) {
                                Text(stringResource(R.string.model_fetch_retry))
                            }
                        }
                    }
                    // Show fallback models below the error
                    ModelList(
                        models = state.fallbackModels,
                        currentModel = currentModel,
                        onSelectModel = onSelectModel,
                    )
                    ModelListFooter(onCustomModel)
                }

                is ModelListState.Success -> {
                    ModelList(
                        models = state.models,
                        currentModel = currentModel,
                        onSelectModel = onSelectModel,
                    )
                    ModelListFooter(onCustomModel)
                }
            }
        }
    }
}

@Composable
private fun ModelList(
    models: List<ai.foxtouch.agent.ModelInfo>,
    currentModel: String,
    onSelectModel: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        models.forEach { m ->
            val isSelected = m.id == currentModel
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
                    androidx.compose.material3.RadioButton(
                        selected = isSelected,
                        onClick = { onSelectModel(m.id) },
                    )
                },
                modifier = Modifier.clickable { onSelectModel(m.id) },
            )
        }
    }
}

@Composable
private fun ModelListFooter(onCustomModel: () -> Unit) {
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    ListItem(
        headlineContent = {
            Text(
                text = stringResource(R.string.custom_model_option),
                color = MaterialTheme.colorScheme.primary,
            )
        },
        leadingContent = { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.clickable { onCustomModel() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnhancedScreenshotDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mediaProjectionApps by viewModel.mediaProjectionApps.collectAsState()
    val isAuthorized = ScreenCaptureManager.isAuthorized
    var showAppPicker by remember { mutableStateOf(false) }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.enhanced_screenshot),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.enhanced_screenshot_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            // Authorization status
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(
                            if (isAuthorized) R.string.enhanced_screenshot_authorized
                            else R.string.enhanced_screenshot_not_authorized,
                        ),
                    )
                },
                leadingContent = {
                    Icon(
                        Icons.Default.CameraEnhance,
                        null,
                        tint = if (isAuthorized) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = if (!isAuthorized) {
                    Modifier.clickable {
                        (context as? MainActivity)?.requestScreenCapturePermission()
                    }
                } else {
                    Modifier
                },
            )

            Spacer(Modifier.height(8.dp))

            // App list
            Text(
                text = stringResource(R.string.enhanced_screenshot_apps),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))

            if (mediaProjectionApps.isEmpty()) {
                Text(
                    text = stringResource(R.string.enhanced_screenshot_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                mediaProjectionApps.forEach { packageName ->
                    val appLabel = remember(packageName) {
                        try {
                            val pm = context.packageManager
                            val info = pm.getApplicationInfo(packageName, 0)
                            pm.getApplicationLabel(info).toString()
                        } catch (_: Exception) {
                            packageName
                        }
                    }
                    ListItem(
                        headlineContent = { Text(appLabel) },
                        supportingContent = { Text(packageName, style = MaterialTheme.typography.labelSmall) },
                        trailingContent = {
                            IconButton(onClick = {
                                scope.launch {
                                    viewModel.setScreenshotMode(
                                        packageName,
                                        ai.foxtouch.data.preferences.ScreenshotMode.ACCESSIBILITY,
                                    )
                                }
                            }) {
                                Icon(Icons.Default.Close, "Remove")
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Add app button
            TextButton(
                onClick = { showAppPicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.enhanced_screenshot_add))
            }
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            excludePackages = mediaProjectionApps,
            onSelect = { packageName ->
                scope.launch {
                    viewModel.setScreenshotMode(
                        packageName,
                        ai.foxtouch.data.preferences.ScreenshotMode.MEDIA_PROJECTION,
                    )
                }
                showAppPicker = false
            },
            onDismiss = { showAppPicker = false },
        )
    }
}

@Composable
private fun AppPickerDialog(
    excludePackages: Set<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val apps = remember {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName && it.packageName !in excludePackages }
            .map { info ->
                val label = pm.getApplicationLabel(info.applicationInfo).toString()
                label to info.packageName
            }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }
    }

    var searchQuery by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.enhanced_screenshot_add)) },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search apps...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                val filtered = if (searchQuery.isBlank()) apps else apps.filter { (label, pkg) ->
                    label.contains(searchQuery, ignoreCase = true) ||
                        pkg.contains(searchQuery, ignoreCase = true)
                }
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.height(300.dp),
                ) {
                    items(filtered.size) { index ->
                        val (label, pkg) = filtered[index]
                        ListItem(
                            headlineContent = { Text(label) },
                            supportingContent = {
                                Text(pkg, style = MaterialTheme.typography.labelSmall)
                            },
                            modifier = Modifier.clickable { onSelect(pkg) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
