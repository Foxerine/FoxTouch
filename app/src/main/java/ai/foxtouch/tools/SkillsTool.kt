package ai.foxtouch.tools

import ai.foxtouch.agent.SkillsManager
import ai.foxtouch.agent.ToolDefinition
import ai.foxtouch.permission.RiskLevel
import kotlinx.serialization.json.JsonObject

/**
 * List all saved skills (titles + previews).
 * The agent should call this at the start of planning to check for reusable plans.
 */
class ListSkillsTool(
    private val skillsManager: SkillsManager,
) : PhoneTool {

    override val definition = ToolDefinition(
        name = "list_skills",
        description = "List all saved skills (reusable plans). " +
            "Returns skill IDs, titles, and previews. " +
            "Call this at the START of plan mode to check if a matching skill exists " +
            "before writing a new plan from scratch.",
        parameters = emptyParameters,
    )

    override val riskLevel = RiskLevel.LOW
    override val isReadOnly = true

    override suspend fun execute(args: JsonObject): ToolResult {
        val skills = skillsManager.listSkills()
        if (skills.isEmpty()) return ToolResult("No saved skills yet.")
        return ToolResult(skills.joinToString("\n") { skill ->
            "- [${skill.id}] ${skill.title}: ${skill.preview}"
        })
    }
}

/**
 * Read the full content of a specific skill by ID.
 */
class ReadSkillTool(
    private val skillsManager: SkillsManager,
) : PhoneTool {

    override val definition = ToolDefinition(
        name = "read_skill",
        description = "Read the full content of a saved skill by ID. " +
            "Use this to load a matching skill and adapt it to the current task " +
            "instead of planning from scratch.",
        parameters = toolParameters {
            string("skill_id", "The skill ID from list_skills output", required = true)
        },
    )

    override val riskLevel = RiskLevel.LOW
    override val isReadOnly = true

    override suspend fun execute(args: JsonObject): ToolResult {
        val id = args.requireString("skill_id")
            ?: return ToolResult("Error: skill_id parameter is required")
        return ToolResult(skillsManager.readSkill(id)
            ?: "Error: Skill '$id' not found. Use list_skills to see available skills.")
    }
}

/**
 * Save the current plan as a reusable skill.
 * Agent provides a title; content is read from the current plan file.
 */
class SaveSkillTool(
    private val skillsManager: SkillsManager,
    private val readPlanFile: () -> String,
) : PhoneTool {

    override val definition = ToolDefinition(
        name = "save_skill",
        description = "Save the current plan as a reusable skill. Before saving, you MUST: " +
            "1) Call list_skills to see existing skills. " +
            "2) Evaluate if this plan is truly novel (not a duplicate). " +
            "3) Evaluate if this plan has high reuse value (not a one-off task). " +
            "4) Only save if both conditions are met, OR if the user explicitly asked to save.",
        parameters = toolParameters {
            string("title", "A descriptive title for the skill", required = true)
        },
    )

    override val riskLevel = RiskLevel.LOW
    override val isReadOnly = true

    override suspend fun execute(args: JsonObject): ToolResult {
        val title = args.requireString("title")
            ?: return ToolResult("Error: title parameter is required")
        val content = readPlanFile()
        if (content.isBlank()) {
            return ToolResult("Error: Plan file is empty. Nothing to save as a skill.")
        }
        val id = skillsManager.saveSkill(title, content)
        return ToolResult("Skill saved successfully with ID: $id")
    }
}
