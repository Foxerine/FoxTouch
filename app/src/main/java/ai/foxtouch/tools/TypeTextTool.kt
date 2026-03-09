package ai.foxtouch.tools

import ai.foxtouch.accessibility.AccessibilityBridge
import ai.foxtouch.agent.ToolDefinition
import ai.foxtouch.permission.RiskLevel
import kotlinx.serialization.json.JsonObject

class TypeTextTool : PhoneTool {

    override val definition = ToolDefinition(
        name = "type_text",
        description = "Type text into the currently focused input field, or into a specific element by ID.",
        parameters = toolParameters {
            string("text", "The text to type", required = true)
            int("element_id", "Optional: element ID to focus before typing")
        },
    )

    override val riskLevel = RiskLevel.HIGH

    override suspend fun execute(args: JsonObject): ToolResult {
        val text = args.requireString("text")
            ?: return ToolResult("Error: text is required")
        val elementId = args.requireInt("element_id")

        return ToolResult(try {
            if (elementId != null) {
                AccessibilityBridge.clickNode(elementId)
                kotlinx.coroutines.delay(200)
            }
            val success = AccessibilityBridge.typeText(text)
            if (success) "Typed \"$text\" successfully"
            else "Failed to type text - no focused input field found"
        } catch (e: Exception) {
            "Error typing text: ${e.message}"
        })
    }
}
