package ai.foxtouch.tools

import ai.foxtouch.agent.ToolDefinition
import ai.foxtouch.permission.RiskLevel
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject

class WaitTool : PhoneTool {

    override val definition = ToolDefinition(
        name = "wait",
        description = "Wait for a specified duration. Useful for waiting for animations, page loads, or network operations.",
        parameters = toolParameters {
            int("duration_ms", "Duration to wait in milliseconds (max: 10000)", required = true)
        },
    )

    override val riskLevel = RiskLevel.LOW
    override val isReadOnly = true

    override suspend fun execute(args: JsonObject): ToolResult {
        val durationMs = args.requireInt("duration_ms")?.toLong()
            ?: return ToolResult("Error: duration_ms is required")

        val clamped = durationMs.coerceIn(100, 10_000)
        delay(clamped)
        return ToolResult("Waited ${clamped}ms")
    }
}
