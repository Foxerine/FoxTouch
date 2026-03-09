package ai.foxtouch.tools

import ai.foxtouch.accessibility.AccessibilityBridge
import ai.foxtouch.accessibility.ScreenAnnotator
import ai.foxtouch.agent.ToolDefinition
import ai.foxtouch.data.preferences.AppSettings
import ai.foxtouch.permission.RiskLevel
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject

class ClickElementTool(
    private val appSettings: AppSettings? = null,
) : PhoneTool {

    override val definition = ToolDefinition(
        name = "click_element",
        description = "Click a UI element by its ID from read_screen output.",
        parameters = toolParameters {
            int("element_id", "The element ID (e.g., [5] -> element_id=5)", required = true)
            boolean("feedback", "Capture a post-click screenshot showing click position and coordinate grid (default false)")
            boolean("show_elements", "Draw element boundaries on feedback screenshot (default false, requires feedback=true)")
        },
    )

    override val riskLevel = RiskLevel.HIGH

    override suspend fun execute(args: JsonObject): ToolResult {
        val elementId = args.requireInt("element_id")
            ?: return ToolResult("Error: element_id is required.")
        val feedback = args.optionalBoolean("feedback", false)
        val showElements = args.optionalBoolean("show_elements", false)

        val bounds = AccessibilityBridge.getElementBounds(elementId)
        val clickX = bounds?.centerX()?.toFloat() ?: 0f
        val clickY = bounds?.centerY()?.toFloat() ?: 0f

        val resultText = try {
            val success = AccessibilityBridge.clickNode(elementId)
            if (success) "Clicked element [$elementId] successfully"
            else "Failed to click element [$elementId] - element not found or not clickable"
        } catch (e: Exception) {
            "Error clicking element [$elementId]: ${e.message}"
        }

        return captureFeedbackIfRequested(resultText, clickX, clickY, feedback, showElements, appSettings)
    }
}

class TapTool(
    private val appSettings: AppSettings? = null,
) : PhoneTool {

    override val definition = ToolDefinition(
        name = "tap",
        description = "Tap at specific screen coordinates.",
        parameters = toolParameters {
            int("x", "X coordinate", required = true)
            int("y", "Y coordinate", required = true)
            boolean("feedback", "Capture a post-click screenshot showing click position and coordinate grid (default false)")
            boolean("show_elements", "Draw element boundaries on feedback screenshot (default false, requires feedback=true)")
        },
    )

    override val riskLevel = RiskLevel.HIGH

    override suspend fun execute(args: JsonObject): ToolResult {
        val x = args.requireInt("x")
            ?: return ToolResult("Error: x is required.")
        val y = args.requireInt("y")
            ?: return ToolResult("Error: y is required.")
        val feedback = args.optionalBoolean("feedback", false)
        val showElements = args.optionalBoolean("show_elements", false)

        val clickX = x.toFloat()
        val clickY = y.toFloat()

        val resultText = try {
            AccessibilityBridge.clickCoordinate(clickX, clickY)
            "Tapped at coordinates ($x, $y)"
        } catch (e: Exception) {
            "Error tapping at ($x, $y): ${e.message}"
        }

        return captureFeedbackIfRequested(resultText, clickX, clickY, feedback, showElements, appSettings)
    }
}

internal suspend fun captureFeedbackIfRequested(
    resultText: String,
    clickX: Float,
    clickY: Float,
    feedback: Boolean,
    showElements: Boolean,
    appSettings: AppSettings?,
): ToolResult {
    if (!feedback) return ToolResult(resultText)

    delay(500)
    return try {
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
        ToolResult(
            text = "$resultText\n\nFeedback screenshot captured. Click position marked at (${clickX.toInt()}, ${clickY.toInt()}).",
            imageBase64 = base64,
        )
    } catch (e: Exception) {
        ToolResult("$resultText\n\nFeedback screenshot failed: ${e.message}")
    }
}
