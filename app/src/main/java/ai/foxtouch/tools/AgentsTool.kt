package ai.foxtouch.tools

import ai.foxtouch.agent.ToolDefinition
import ai.foxtouch.permission.RiskLevel
import kotlinx.serialization.json.JsonObject

class ReadAgentsTool(
    private val readAgents: () -> String,
) : PhoneTool {

    override val definition = ToolDefinition(
        name = "read_agents",
        description = "Read agents.md — project-level instructions and guidelines defined by the user. " +
            "Contains user-defined rules, preferences, and workflows. " +
            "Read this at the start of a session to understand user expectations.",
        parameters = emptyParameters,
    )

    override val riskLevel = RiskLevel.LOW
    override val isReadOnly = true

    override suspend fun execute(args: JsonObject): ToolResult {
        val content = readAgents()
        return ToolResult(if (content.isBlank()) "(agents.md is empty — user has not configured any instructions yet)" else content)
    }
}
