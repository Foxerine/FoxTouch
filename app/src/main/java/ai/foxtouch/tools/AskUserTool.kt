package ai.foxtouch.tools

import ai.foxtouch.agent.ToolDefinition
import ai.foxtouch.permission.RiskLevel
import kotlinx.serialization.json.JsonObject

/**
 * Ask the user a question and block until they respond.
 *
 * Mirrors Claude Code's AskUserQuestion tool. The LLM calls this when it
 * needs clarification. The tool emits an AgentState.AskingUser state,
 * the UI shows a question card with suggested options and a free-text input,
 * and the tool blocks on [awaitAnswer] until the user submits their response.
 */
class AskUserTool(
    private val onQuestionAsked: (question: String, suggestions: List<String>) -> Unit,
    private val awaitAnswer: suspend () -> String,
) : PhoneTool {

    override val definition = ToolDefinition(
        name = "ask_user",
        description = "Ask the user a question and wait for their answer. " +
            "Rules: Ask exactly ONE question per call. Keep it short and specific. " +
            "Suggested responses must be 1-5 words each (e.g. \"Yes\", \"Option A\", \"Skip\"). " +
            "Do NOT bundle multiple questions — make separate ask_user calls for each.",
        parameters = toolParameters {
            string("question", "A single, specific question", required = true)
            stringArray(
                "suggested_responses",
                "Up to 4 short suggested answers (1-5 words each)",
            )
        },
    )

    override val riskLevel = RiskLevel.LOW
    override val isReadOnly = true

    override suspend fun execute(args: JsonObject): ToolResult {
        val question = args.requireString("question")
            ?: return ToolResult("Error: question parameter is required")
        val suggestions = args.optionalStringArray("suggested_responses").take(4)
        onQuestionAsked(question, suggestions)
        return ToolResult(awaitAnswer())
    }
}
