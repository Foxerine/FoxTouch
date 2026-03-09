package ai.foxtouch.tools

import ai.foxtouch.agent.ToolDefinition
import ai.foxtouch.permission.RiskLevel
import kotlinx.serialization.json.JsonObject

/**
 * Allows the AI to proactively enter plan mode from normal/YOLO mode.
 *
 * Mirrors Claude Code's EnterPlanMode tool. When the LLM determines that
 * a task is complex and requires investigation/planning before execution,
 * it calls this tool to switch into plan mode. The next loop iteration
 * will restrict available tools to read-only + plan tools.
 */
class EnterPlanModeTool(
    private val onEnterPlanMode: () -> Unit,
) : PhoneTool {

    override val definition = ToolDefinition(
        name = "enter_plan_mode",
        description = "Switch to plan mode for complex tasks that require investigation and planning " +
            "before execution. Call this when you determine the task is multi-step, ambiguous, " +
            "or requires understanding the current state before acting. " +
            "After calling this, you will only have access to read-only observation tools " +
            "and planning tools (edit_plan, create_task, ask_user, exit_plan_mode). " +
            "You will NOT be able to perform actions (click, type, scroll, etc.) until the " +
            "plan is approved by the user via exit_plan_mode.",
        parameters = emptyParameters,
    )

    override val riskLevel = RiskLevel.LOW
    override val isReadOnly = false // Available in normal mode (non-read-only tools)

    override suspend fun execute(args: JsonObject): ToolResult {
        onEnterPlanMode()
        return ToolResult("Switched to plan mode. You now have access to observation and planning tools only. " +
            "Follow this workflow:\n" +
            "1. **Understand**: Use read_screen to observe the current state. Use ask_user to clarify requirements.\n" +
            "2. **Design**: Use edit_plan to write your plan as a structured markdown document.\n" +
            "3. **Track**: Use create_task for each execution step.\n" +
            "4. **Submit**: Call exit_plan_mode to present the plan for user approval.\n\n" +
            "You MUST NOT use any interaction tools (click, type_text, scroll, etc.) until the plan is approved.")
    }
}
