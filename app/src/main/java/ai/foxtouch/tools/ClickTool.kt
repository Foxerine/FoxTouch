package ai.foxtouch.tools

import ai.foxtouch.accessibility.AccessibilityBridge
import ai.foxtouch.accessibility.ScreenAnnotator
import ai.foxtouch.agent.ToolDefinition
import ai.foxtouch.data.preferences.AppSettings
import ai.foxtouch.permission.RiskLevel
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject

class ClickTool(
    private val appSettings: AppSettings? = null,
) : PhoneTool {

    override val definition = ToolDefinition(
        name = "click",
        description = "Click/tap a UI element by its [ID] from read_screen output, or tap at specific screen coordinates (x, y). " +
            "Provide either element_id OR both x and y, not both. " +
            "Set feedback=true to capture a post-click screenshot with the click position marked (red crosshair) and coordinate grid. " +
            "Add show_elements=true to also render element boundaries on the feedback screenshot.",
        parameters = toolParameters {
            int("element_id", "The element ID from read_screen output (e.g., [5] -> element_id=5)")
            int("x", "X coordinate for direct tap (use instead of element_id)")
            int("y", "Y coordinate for direct tap (use instead of element_id)")
            boolean("feedback", "Capture a post-click screenshot showing click position and coordinate grid (default false)")
            boolean("show_elements", "Draw element boundaries on feedback screenshot (default false, requires feedback=true)")
        },
    )

    override val riskLevel = RiskLevel.HIGH

    override suspend fun execute(args: JsonObject): ToolResult {
        val elementId = args.requireInt("element_id")
        val x = args.requireInt("x")
        val y = args.requireInt("y")
        val feedback = args.optionalBoolean("feedback", false)
        val showElements = args.optionalBoolean("show_elements", false)

        if (elementId != null && (x != null || y != null)) {
            return ToolResult("Error: Provide either element_id OR both x and y, not both.")
        }
        if (elementId == null && (x == null || y == null)) {
            return ToolResult("Error: Provide either element_id, or both x and y coordinates.")
        }

        val clickX: Float
        val clickY: Float
        if (elementId != null) {
            val bounds = AccessibilityBridge.getElementBounds(elementId)
            clickX = bounds?.centerX()?.toFloat() ?: 0f
            clickY = bounds?.centerY()?.toFloat() ?: 0f
        } else {
            clickX = x!!.toFloat()
            clickY = y!!.toFloat()
        }

        val resultText = try {
            if (elementId != null) {
                val success = AccessibilityBridge.clickNode(elementId)
                if (success) "Clicked element [$elementId] successfully"
                else "Failed to click element [$elementId] - element not found or not clickable"
            } else {
                AccessibilityBridge.clickCoordinate(clickX, clickY)
                "Tapped at coordinates ($x, $y)"
            }
        } catch (e: Exception) {
            if (elementId != null) "Error clicking element [$elementId]: ${e.message}"
            else "Error tapping at ($x, $y): ${e.message}"
        }

        if (feedback) {
            delay(500)
            try {
                // Re-read UI tree so element overlay is fresh
                if (showElements) AccessibilityBridge.readUITree()

                val captured = AccessibilityBridge.captureScreenBitmap(appSettings)
                val elementInfos = if (showElements) {
                    AccessibilityBridge.getElementOverlayInfos()
                } else {
                    emptyList()
                }

                val options = ScreenAnnotator.AnnotationOptions(
                    grid = true,
                    elements = showElements,
                    labels = false,
                    clickX = clickX,
                    clickY = clickY,
                )
                val annotated = ScreenAnnotator.annotate(
                    captured.bitmap,
                    captured.originalWidth,
                    captured.originalHeight,
                    elementInfos,
                    options,
                )
                val base64 = AccessibilityBridge.encodeBitmapToBase64(annotated)
                captured.bitmap.recycle()
                annotated.recycle()
                return ToolResult(
                    text = "$resultText\n\nFeedback screenshot captured. Click position marked at (${clickX.toInt()}, ${clickY.toInt()}).",
                    imageBase64 = base64,
                )
            } catch (e: Exception) {
                return ToolResult("$resultText\n\nFeedback screenshot failed: ${e.message}")
            }
        }

        return ToolResult(resultText)
    }
}
