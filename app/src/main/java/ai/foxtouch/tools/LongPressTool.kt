package ai.foxtouch.tools

import ai.foxtouch.accessibility.AccessibilityBridge
import ai.foxtouch.agent.ToolDefinition
import ai.foxtouch.permission.RiskLevel
import kotlinx.serialization.json.JsonObject

class LongPressTool : PhoneTool {

    override val definition = ToolDefinition(
        name = "long_press",
        description = "Perform a long press gesture at a specific screen coordinate. Useful for triggering context menus, drag operations, or other long-press actions.",
        parameters = toolParameters {
            int("x", "X coordinate to long press", required = true)
            int("y", "Y coordinate to long press", required = true)
            int("duration_ms", "Long press duration in milliseconds (default: 1000)")
        },
    )

    override val riskLevel = RiskLevel.HIGH

    override suspend fun execute(args: JsonObject): ToolResult {
        val x = args.requireFloat("x") ?: return ToolResult("Error: x is required")
        val y = args.requireFloat("y") ?: return ToolResult("Error: y is required")
        val durationMs = args.optionalLong("duration_ms", 1000L)

        return ToolResult(try {
            AccessibilityBridge.longPress(x, y, durationMs)
            "Long pressed at ($x,$y) for ${durationMs}ms"
        } catch (e: Exception) {
            "Error long pressing: ${e.message}"
        })
    }
}
