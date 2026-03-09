package ai.foxtouch.tools

import ai.foxtouch.accessibility.AccessibilityBridge
import ai.foxtouch.agent.ToolDefinition
import ai.foxtouch.permission.RiskLevel
import kotlinx.serialization.json.JsonObject

class ScrollTool : PhoneTool {

    override val definition = ToolDefinition(
        name = "scroll",
        description = "Scroll the screen or a scrollable element in a direction.",
        parameters = toolParameters {
            enum("direction", listOf("up", "down", "left", "right"), "Scroll direction", required = true)
            int("element_id", "Optional: scrollable element ID. If omitted, scrolls the main content.")
        },
    )

    override val riskLevel = RiskLevel.MEDIUM

    override suspend fun execute(args: JsonObject): ToolResult {
        val direction = args.requireString("direction")
            ?: return ToolResult("Error: direction is required")
        val elementId = args.requireInt("element_id")

        return ToolResult(try {
            val success = AccessibilityBridge.scroll(direction, elementId)
            if (success) "Scrolled $direction successfully"
            else "Failed to scroll $direction"
        } catch (e: Exception) {
            "Error scrolling: ${e.message}"
        })
    }
}
