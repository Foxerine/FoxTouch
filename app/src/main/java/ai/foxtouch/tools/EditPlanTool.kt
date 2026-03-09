package ai.foxtouch.tools

import ai.foxtouch.agent.ToolDefinition
import ai.foxtouch.permission.RiskLevel
import kotlinx.serialization.json.JsonObject
import java.io.File

/**
 * Write or update the plan markdown file.
 *
 * The plan file is the LLM's working document during a planning session.
 * Each call overwrites the entire file with the provided content.
 * This is separate from the task list — the plan file is a free-form
 * markdown document describing the approach, while tasks are individual
 * trackable execution steps.
 */
class EditPlanTool(
    private val getPlanFile: () -> File,
) : PhoneTool {

    override val definition = ToolDefinition(
        name = "edit_plan",
        description = "Write or update the plan markdown file. " +
            "The plan file is your working document for the current planning session. " +
            "Write the full plan content — this overwrites the existing file. " +
            "Use markdown formatting for structure. " +
            "This is separate from tasks — the plan describes your approach, " +
            "while tasks track individual execution steps.",
        parameters = toolParameters {
            string("content", "The full markdown content to write to the plan file", required = true)
        },
    )

    override val riskLevel = RiskLevel.LOW
    override val isReadOnly = true

    override suspend fun execute(args: JsonObject): ToolResult {
        val content = args.requireString("content")
            ?: return ToolResult("Error: content parameter is required")
        val file = getPlanFile()
        file.parentFile?.mkdirs()
        file.writeText(content)
        return ToolResult("Plan file updated (${content.length} chars)")
    }
}
