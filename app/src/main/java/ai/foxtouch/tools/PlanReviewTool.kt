package ai.foxtouch.tools

import ai.foxtouch.agent.PlanApprovalResponse
import ai.foxtouch.agent.ToolDefinition
import ai.foxtouch.permission.RiskLevel
import kotlinx.serialization.json.JsonObject

/**
 * Signal that planning is complete and present the plan for user approval.
 *
 * Mirrors Claude Code's ExitPlanMode tool. This tool reads the plan file
 * (written via edit_plan) and shows it to the user along with the task list.
 * The user can approve, reject, or request modifications.
 *
 * The tool blocks until the user responds, and returns the result as a
 * tool message to the LLM.
 */
class ExitPlanModeTool(
    private val readPlanFile: () -> String,
    private val onPlanPresented: (planContent: String, suggestSave: Boolean) -> Unit,
    private val awaitApproval: suspend () -> PlanApprovalResponse,
    private val onApproved: (yolo: Boolean, saveAsSkill: Boolean) -> Unit,
) : PhoneTool {

    override val definition = ToolDefinition(
        name = "exit_plan_mode",
        description = "Signal that planning is complete and present the plan for user approval. " +
            "This tool reads the plan file you wrote with edit_plan and shows it to the user. " +
            "Call this when you have finished writing your plan and creating all tasks. " +
            "The user will approve, reject, or request modifications. " +
            "Set suggest_save_as_skill to true if you believe this plan is a reusable, " +
            "high-frequency workflow worth saving as a skill. The user can override your suggestion. " +
            "Do NOT end planning with just text output — always call this tool.",
        parameters = toolParameters {
            boolean(
                "suggest_save_as_skill",
                "Set to true to pre-check the 'Save as Skill' checkbox for the user. " +
                    "Use this when the plan is a reusable workflow worth saving.",
            )
        },
    )

    override val riskLevel = RiskLevel.LOW
    override val isReadOnly = true

    override suspend fun execute(args: JsonObject): ToolResult {
        val planContent = readPlanFile()
        if (planContent.isBlank()) {
            return ToolResult("Error: Plan file is empty. Use edit_plan to write your plan first.")
        }

        val suggestSave = args.optionalBoolean("suggest_save_as_skill", false)
        onPlanPresented(planContent, suggestSave)

        return ToolResult(when (val response = awaitApproval()) {
            is PlanApprovalResponse.ApproveNormal -> {
                onApproved(false, response.saveAsSkill)
                "Plan APPROVED by user. Now execute each task step by step using the full set " +
                    "of interaction tools (click, type_text, scroll, launch_app, etc.). " +
                    "Follow the observe-think-act-verify cycle for each step."
            }
            is PlanApprovalResponse.ApproveYolo -> {
                onApproved(true, response.saveAsSkill)
                "Plan APPROVED by user (autonomous mode). Execute all tasks efficiently " +
                    "without waiting for further approval."
            }
            is PlanApprovalResponse.Reject -> {
                "Plan REJECTED by user. Stop and ask the user what they would like to change."
            }
            is PlanApprovalResponse.Modify -> {
                "User requested plan modifications: ${response.reason}. " +
                    "Revise the plan accordingly using edit_plan, update or recreate tasks, " +
                    "then call exit_plan_mode again with the updated plan."
            }
        })
    }
}
