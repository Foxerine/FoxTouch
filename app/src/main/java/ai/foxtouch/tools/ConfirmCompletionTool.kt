package ai.foxtouch.tools

import ai.foxtouch.agent.CompletionResponse
import ai.foxtouch.agent.ToolDefinition
import ai.foxtouch.permission.RiskLevel
import kotlinx.serialization.json.JsonObject

/**
 * Confirm task completion with the user.
 *
 * The agent MUST call this tool after completing all planned tasks.
 * The tool presents a summary to the user and blocks until they respond:
 * - Confirmed: task is done, agent stops
 * - Not done + reason: agent continues working
 * - Dismissed: agent continues working
 */
class ConfirmCompletionTool(
    private val onConfirmRequested: (summary: String) -> Unit,
    private val awaitResponse: suspend () -> CompletionResponse,
) : PhoneTool {

    override val definition = ToolDefinition(
        name = "confirm_completion",
        description = "MANDATORY: Call this when you have finished ALL planned tasks. " +
            "Presents a completion summary to the user for confirmation. " +
            "The user can confirm (task done — you MUST stop), " +
            "reject (not done — continue working), or dismiss (continue working). " +
            "Do NOT end the conversation without calling this tool after completing tasks.",
        parameters = toolParameters {
            string("summary", "Brief summary of what was accomplished", required = true)
        },
    )

    override val riskLevel = RiskLevel.LOW
    override val isReadOnly = true

    override suspend fun execute(args: JsonObject): ToolResult {
        val summary = args.requireString("summary")
            ?: return ToolResult("Error: summary parameter is required")

        onConfirmRequested(summary)

        return ToolResult(when (val response = awaitResponse()) {
            is CompletionResponse.Confirmed ->
                "TASK_COMPLETE: User confirmed the task is done. " +
                    "Stop working and end with a brief closing message."
            is CompletionResponse.NotDone ->
                "TASK_NOT_COMPLETE: User says the task is not done. " +
                    "Reason: ${response.reason}. Continue working to address this."
            is CompletionResponse.Dismissed ->
                "User dismissed without confirming. Continue working on remaining items."
        })
    }
}
