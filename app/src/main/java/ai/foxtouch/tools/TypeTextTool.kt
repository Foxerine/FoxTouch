package ai.foxtouch.tools

import ai.foxtouch.accessibility.AccessibilityBridge
import ai.foxtouch.agent.ToolDefinition
import ai.foxtouch.data.preferences.AppSettings
import ai.foxtouch.permission.RiskLevel
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject

class TypeTextTool(
    private val appSettings: AppSettings? = null,
) : PhoneTool {

    override val definition = ToolDefinition(
        name = "type_text",
        description = "Type text into the currently focused input field, or focus an element by ID first then type. " +
            "Set paste=true to type from clipboard content instead of text parameter.",
        parameters = toolParameters {
            string("text", "The text to type (ignored if paste=true)", required = true)
            int("element_id", "Element ID to focus before typing")
            boolean("paste", "Use clipboard content instead of text (default false)")
            boolean("feedback", "Capture a post-typing screenshot (default false)")
            boolean("show_elements", "Draw element boundaries on feedback screenshot (default false)")
        },
    )

    override val riskLevel = RiskLevel.HIGH

    override suspend fun execute(args: JsonObject): ToolResult {
        var text = args.requireString("text") ?: ""
        val elementId = args.requireInt("element_id")?.takeIf { it > 0 }
        val paste = args.optionalBoolean("paste", false)
        val feedback = args.optionalBoolean("feedback", false)
        val showElements = args.optionalBoolean("show_elements", false)

        if (paste) {
            text = AccessibilityBridge.readClipboard()
                ?: return ToolResult("Error: clipboard is empty")
        }

        if (text.isEmpty()) return ToolResult("Error: text is empty")

        var focusX = 0f
        var focusY = 0f

        val resultText = try {
            if (elementId != null) {
                val bounds = AccessibilityBridge.getElementBounds(elementId)
                focusX = bounds?.centerX()?.toFloat() ?: 0f
                focusY = bounds?.centerY()?.toFloat() ?: 0f
                AccessibilityBridge.clickNode(elementId)
                delay(200)
            }
            val success = typeWithFallback(text)
            val source = if (paste) " (from clipboard)" else ""
            if (success) "Typed \"${text.take(100)}\"$source successfully"
            else "Failed to type text - no focused input field found"
        } catch (e: Exception) {
            "Error typing text: ${e.message}"
        }

        return captureFeedbackIfRequested(resultText, focusX, focusY, feedback, showElements, appSettings)
    }
}

class TypeAtTool(
    private val appSettings: AppSettings? = null,
) : PhoneTool {

    override val definition = ToolDefinition(
        name = "type_at",
        description = "Tap at screen coordinates to focus an input field, then type text. " +
            "Set paste=true to type from clipboard content instead of text parameter.",
        parameters = toolParameters {
            string("text", "The text to type (ignored if paste=true)", required = true)
            int("x", "X coordinate to tap for focus", required = true)
            int("y", "Y coordinate to tap for focus", required = true)
            boolean("paste", "Use clipboard content instead of text (default false)")
            boolean("feedback", "Capture a post-typing screenshot (default false)")
            boolean("show_elements", "Draw element boundaries on feedback screenshot (default false)")
        },
    )

    override val riskLevel = RiskLevel.HIGH

    override suspend fun execute(args: JsonObject): ToolResult {
        var text = args.requireString("text") ?: ""
        val x = args.requireInt("x")
            ?: return ToolResult("Error: x is required")
        val y = args.requireInt("y")
            ?: return ToolResult("Error: y is required")
        val paste = args.optionalBoolean("paste", false)
        val feedback = args.optionalBoolean("feedback", false)
        val showElements = args.optionalBoolean("show_elements", false)

        if (paste) {
            text = AccessibilityBridge.readClipboard()
                ?: return ToolResult("Error: clipboard is empty")
        }

        if (text.isEmpty()) return ToolResult("Error: text is empty")

        val clickX = x.toFloat()
        val clickY = y.toFloat()

        val resultText = try {
            AccessibilityBridge.clickCoordinate(clickX, clickY)
            delay(200)
            val success = typeWithFallback(text)
            val source = if (paste) " (from clipboard)" else ""
            if (success) "Typed \"${text.take(100)}\"$source at ($x, $y) successfully"
            else "Failed to type text - no focused input field found at ($x, $y)"
        } catch (e: Exception) {
            "Error typing text at ($x, $y): ${e.message}"
        }

        return captureFeedbackIfRequested(resultText, clickX, clickY, feedback, showElements, appSettings)
    }
}

/**
 * Try typing text with cascading fallback:
 * 1. FoxTouchIME (InputConnection.commitText — most reliable for custom fields)
 * 2. ACTION_SET_TEXT (accessibility — works for standard Android views)
 * 3. Clipboard paste (write to clipboard → paste via accessibility action)
 */
private suspend fun typeWithFallback(text: String): Boolean {
    // 1. IME
    if (AccessibilityBridge.typeTextViaIme(text)) return true

    // 2. ACTION_SET_TEXT
    if (AccessibilityBridge.typeText(text)) return true

    // 3. Clipboard paste fallback
    return AccessibilityBridge.typeTextViaPaste(text)
}
