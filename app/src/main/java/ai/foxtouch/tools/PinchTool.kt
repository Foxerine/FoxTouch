package ai.foxtouch.tools

import ai.foxtouch.accessibility.AccessibilityBridge
import ai.foxtouch.agent.ToolDefinition
import ai.foxtouch.permission.RiskLevel
import kotlinx.serialization.json.JsonObject

class PinchTool : PhoneTool {

    override val definition = ToolDefinition(
        name = "pinch",
        description = "Perform a pinch gesture (zoom in or out). Use start_distance > end_distance to pinch in (zoom out), or start_distance < end_distance to pinch out (zoom in).",
        parameters = toolParameters {
            int("center_x", "Center X coordinate of the pinch", required = true)
            int("center_y", "Center Y coordinate of the pinch", required = true)
            int("start_distance", "Starting distance between two fingers in pixels", required = true)
            int("end_distance", "Ending distance between two fingers in pixels", required = true)
            int("duration_ms", "Pinch duration in milliseconds (default: 500)")
        },
    )

    override val riskLevel = RiskLevel.MEDIUM

    override suspend fun execute(args: JsonObject): ToolResult {
        val centerX = args.requireFloat("center_x") ?: return ToolResult("Error: center_x is required")
        val centerY = args.requireFloat("center_y") ?: return ToolResult("Error: center_y is required")
        val startDistance = args.requireFloat("start_distance") ?: return ToolResult("Error: start_distance is required")
        val endDistance = args.requireFloat("end_distance") ?: return ToolResult("Error: end_distance is required")
        val durationMs = args.optionalLong("duration_ms", 500L)

        return ToolResult(try {
            AccessibilityBridge.pinch(centerX, centerY, startDistance, endDistance, durationMs)
            val action = if (endDistance > startDistance) "zoom in" else "zoom out"
            "Pinched ($action) at ($centerX,$centerY) from ${startDistance}px to ${endDistance}px over ${durationMs}ms"
        } catch (e: Exception) {
            "Error pinching: ${e.message}"
        })
    }
}
