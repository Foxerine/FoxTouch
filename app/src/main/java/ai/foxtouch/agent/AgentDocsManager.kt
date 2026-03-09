package ai.foxtouch.agent

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages persistent knowledge files for the AI agent.
 * - memory.md: AI can read and write (persistent memory across sessions)
 * - agents.md: AI can only read (user-defined instructions and guidelines)
 */
@Singleton
class AgentDocsManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {
    private val docsDir: File
        get() = File(appContext.filesDir, "agent_docs").also { it.mkdirs() }

    val memoryFile: File get() = File(docsDir, "memory.md")
    val agentsFile: File get() = File(docsDir, "agents.md")

    fun readMemory(): String =
        memoryFile.takeIf { it.exists() }?.readText() ?: ""

    fun writeMemory(content: String) {
        memoryFile.writeText(content)
    }

    fun readAgents(): String =
        agentsFile.takeIf { it.exists() }?.readText() ?: ""

    fun writeAgents(content: String) {
        agentsFile.writeText(content)
    }
}
