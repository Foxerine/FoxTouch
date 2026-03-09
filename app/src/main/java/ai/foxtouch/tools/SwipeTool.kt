package ai.foxtouch.tools

import ai.foxtouch.accessibility.AccessibilityBridge
import ai.foxtouch.agent.ToolDefinition
import ai.foxtouch.permission.RiskLevel
import kotlinx.serialization.json.JsonObject

class SwipeTool : PhoneTool {

    override val definition = ToolDefinition(
        name = "swipe",
        description = "Perform a swipe gesture on the screen. " +
            "Use direction + distance for simple directional swipes, " +
            "or specify exact end_x/end_y for precise control.",
        parameters = toolParameters {
            int("start_x", "Start X coordinate", required = true)
            int("start_y", "Start Y coordinate", required = true)
            int("end_x", "End X coordinate (not needed if direction is set)")
            int("end_y", "End Y coordinate (not needed if direction is set)")
            enum(
                "direction", listOf("up", "down", "left", "right"),
                "Swipe direction. When set, end coordinates are auto-calculated from start + distance.",
            )
            int("distance", "Swipe distance in pixels (default: 500). Controls how far the swipe travels. " +
                "Use smaller values (~200) for gentle scrolls, larger values (~800+) for fast flings.")
            int("duration_ms", "Swipe duration in milliseconds (default: 300). " +
                "Shorter = faster/more forceful, longer = slower/gentler.")
        },
    )

    override val riskLevel = RiskLevel.MEDIUM

    override suspend fun execute(args: JsonObject): ToolResult {
        val startX = args.requireFloat("start_x") ?: return ToolResult("Error: start_x is required")
        val startY = args.requireFloat("start_y") ?: return ToolResult("Error: start_y is required")
        val durationMs = args.optionalLong("duration_ms", 300L)

        val direction = args.requireString("direction")
        val endX: Float
        val endY: Float

        if (direction != null) {
            val distance = args.optionalInt("distance", 500)
            endX = when (direction) {
                "left" -> startX - distance
                "right" -> startX + distance
                else -> startX
            }
            endY = when (direction) {
                "up" -> startY - distance
                "down" -> startY + distance
                else -> startY
            }
        } else {
            endX = args.requireFloat("end_x")
                ?: return ToolResult("Error: end_x is required when direction is not specified")
            endY = args.requireFloat("end_y")
                ?: return ToolResult("Error: end_y is required when direction is not specified")
        }

        return ToolResult(try {
            AccessibilityBridge.swipe(startX, startY, endX, endY, durationMs)
            val desc = direction ?: "($startX,$startY)->($endX,$endY)"
            "Swiped $desc over ${durationMs}ms"
        } catch (e: Exception) {
            "Error swiping: ${e.message}"
        })
    }
}
