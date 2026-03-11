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
        description = "Click or tap on the screen. " +
            "Specify element_id to click a UI element from read_screen, " +
            "OR specify x/y coordinates to tap a screen location.",
        parameters = toolParameters {
            int("element_id", "Element ID from read_screen output (e.g., [5] -> element_id=5). " +
                "Use this OR x/y coordinates, not both.")
            int("x", "X coordinate to tap (use with y, alternative to element_id)")
            int("y", "Y coordinate to tap (use with x, alternative to element_id)")
            enum("feedback", listOf("none", "before_wait", "after_wait"),
                "Screenshot feedback mode: 'none' = no screenshot (default), " +
                "'before_wait' = capture screenshot immediately after click then wait, " +
                "'after_wait' = wait first then capture screenshot")
            int("wait_ms", "Wait duration in milliseconds after the click (default 0, max 10000). " +
                "Recommended 300-500ms for UI animations, 1000+ for page loads.")
            boolean("show_elements", "Draw element boundaries on feedback screenshot (default false)")
        },
    )

    override val riskLevel = RiskLevel.HIGH

    override suspend fun execute(args: JsonObject): ToolResult {
        val elementId = args.requireInt("element_id")?.takeIf { it > 0 }
        val x = args.requireInt("x")
        val y = args.requireInt("y")
        val feedback = args.requireString("feedback") ?: "none"
        val waitMs = args.optionalLong("wait_ms", 0L)
        val showElements = args.optionalBoolean("show_elements", false)

        return when {
            elementId != null -> {
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
                captureFeedbackIfRequested(resultText, clickX, clickY, feedback, waitMs, showElements, appSettings)
            }
            x != null && y != null -> {
                val clickX = x.toFloat()
                val clickY = y.toFloat()
                val resultText = try {
                    AccessibilityBridge.clickCoordinate(clickX, clickY)
                    "Tapped at coordinates ($x, $y)"
                } catch (e: Exception) {
                    "Error tapping at ($x, $y): ${e.message}"
                }
                captureFeedbackIfRequested(resultText, clickX, clickY, feedback, waitMs, showElements, appSettings)
            }
            else -> ToolResult("Error: specify either element_id or both x and y coordinates.")
        }
    }
}

internal suspend fun captureFeedbackIfRequested(
    resultText: String,
    clickX: Float,
    clickY: Float,
    feedback: String,
    waitMs: Long,
    showElements: Boolean,
    appSettings: AppSettings?,
): ToolResult {
    val clampedWait = waitMs.coerceIn(0, 10_000)

    if (feedback == "none") {
        if (clampedWait > 0) delay(clampedWait)
        return ToolResult(resultText)
    }

    val captureScreenshot: suspend () -> ToolResult = {
        try {
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

    return when (feedback) {
        "before_wait" -> {
            val result = captureScreenshot()
            if (clampedWait > 0) delay(clampedWait)
            result
        }
        else -> { // "after_wait"
            if (clampedWait > 0) delay(clampedWait)
            captureScreenshot()
        }
    }
}
