package ai.foxtouch.tools

import ai.foxtouch.agent.ToolDefinition
import ai.foxtouch.permission.RiskLevel
import kotlinx.serialization.json.JsonObject

/**
 * Result from a tool execution.
 * Most tools return text only; ReadScreenTool can also include a screenshot.
 */
data class ToolResult(
    val text: String,
    val imageBase64: String? = null,
)

/**
 * Base interface for all phone tools the agent can use.
 */
interface PhoneTool {
    val definition: ToolDefinition
    val riskLevel: RiskLevel
    val isReadOnly: Boolean get() = false

    suspend fun execute(args: JsonObject): ToolResult
}
