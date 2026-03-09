package ai.foxtouch.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import ai.foxtouch.agent.AgentDocsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class AgentDocsViewModel @Inject constructor(
    private val agentDocsManager: AgentDocsManager,
) : ViewModel() {

    private val _agentsContent = MutableStateFlow(agentDocsManager.readAgents())
    val agentsContent: StateFlow<String> = _agentsContent.asStateFlow()

    private val _memoryContent = MutableStateFlow(agentDocsManager.readMemory())
    val memoryContent: StateFlow<String> = _memoryContent.asStateFlow()

    fun saveAgents(content: String) {
        agentDocsManager.writeAgents(content)
        _agentsContent.value = content
    }

    fun saveMemory(content: String) {
        agentDocsManager.writeMemory(content)
        _memoryContent.value = content
    }

    fun refresh() {
        _agentsContent.value = agentDocsManager.readAgents()
        _memoryContent.value = agentDocsManager.readMemory()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentDocsScreen(
    onBack: () -> Unit,
    viewModel: AgentDocsViewModel = hiltViewModel(),
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Instructions", "Memory")

    val agentsContent by viewModel.agentsContent.collectAsState()
    val memoryContent by viewModel.memoryContent.collectAsState()

    var editingAgents by remember(agentsContent) { mutableStateOf(agentsContent) }
    var editingMemory by remember(memoryContent) { mutableStateOf(memoryContent) }

    val hasChanges = when (selectedTab) {
        0 -> editingAgents != agentsContent
        1 -> editingMemory != memoryContent
        else -> false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agent Docs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            if (hasChanges) {
                FloatingActionButton(
                    onClick = {
                        when (selectedTab) {
                            0 -> viewModel.saveAgents(editingAgents)
                            1 -> viewModel.saveMemory(editingMemory)
                        }
                    },
                ) {
                    Icon(Icons.Default.Save, "Save")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }

            Text(
                text = when (selectedTab) {
                    0 -> "User-defined instructions for the AI agent (agents.md)"
                    1 -> "AI's persistent memory across sessions (memory.md)"
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            when (selectedTab) {
                0 -> OutlinedTextField(
                    value = editingAgents,
                    onValueChange = { editingAgents = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    placeholder = { Text("Write instructions for the AI agent here...\n\nExample:\n- Always respond in Chinese\n- Be concise\n- Ask before deleting anything") },
                )
                1 -> OutlinedTextField(
                    value = editingMemory,
                    onValueChange = { editingMemory = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    placeholder = { Text("AI memory will appear here...\n\nThe AI can write notes, patterns, and preferences to this file.") },
                )
            }
        }
    }
}
