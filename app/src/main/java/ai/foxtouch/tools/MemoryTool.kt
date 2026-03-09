package ai.foxtouch.tools

import ai.foxtouch.agent.ToolDefinition
import ai.foxtouch.permission.RiskLevel
import kotlinx.serialization.json.JsonObject

class ReadMemoryTool(
    private val readMemory: () -> String,
) : PhoneTool {

    override val definition = ToolDefinition(
        name = "read_memory",
        description = "Read memory.md — your persistent memory across conversations. " +
            "Contains notes, patterns, and user preferences you have saved. " +
            "Read this at the start of complex tasks to recall past context.",
        parameters = emptyParameters,
    )

    override val riskLevel = RiskLevel.LOW
    override val isReadOnly = true

    override suspend fun execute(args: JsonObject): ToolResult {
        val content = readMemory()
        return ToolResult(if (content.isBlank()) "(memory.md is empty)" else content)
    }
}

class WriteMemoryTool(
    private val readMemory: () -> String,
    private val writeMemory: (String) -> Unit,
) : PhoneTool {

    override val definition = ToolDefinition(
        name = "write_memory",
        description = "Write to memory.md — your persistent memory. " +
            "Use 'overwrite' mode to replace the entire file, " +
            "or 'append' mode to add content at the end. " +
            "Save important patterns, user preferences, and lessons learned. " +
            "Keep content organized with markdown headings.",
        parameters = toolParameters {
            string("content", "The markdown content to write", required = true)
            enum("mode", listOf("overwrite", "append"),
                "Write mode: 'overwrite' replaces the file, 'append' adds to the end",
                required = true)
        },
    )

    override val riskLevel = RiskLevel.LOW
    override val isReadOnly = true

    override suspend fun execute(args: JsonObject): ToolResult {
        val content = args.requireString("content")
            ?: return ToolResult("Error: content parameter is required")
        val mode = args.requireString("mode") ?: "overwrite"

        when (mode) {
            "append" -> {
                val existing = readMemory()
                val separator = if (existing.isNotBlank()) "\n\n" else ""
                writeMemory(existing + separator + content)
            }
            else -> writeMemory(content)
        }
        return ToolResult("memory.md updated (mode=$mode, ${content.length} chars written)")
    }
}
