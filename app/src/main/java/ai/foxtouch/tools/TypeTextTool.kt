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
        description = "Type text into an input field. " +
            "Optionally focus an element first by element_id or x/y coordinates. " +
            "If no focus target is specified, types into the currently focused field. " +
            "Set paste=true to type from clipboard content instead of text parameter.",
        parameters = toolParameters {
            string("text", "The text to type (ignored if paste=true)", required = true)
            int("element_id", "Element ID to focus before typing (from read_screen)")
            int("x", "X coordinate to tap for focus (use with y, alternative to element_id)")
            int("y", "Y coordinate to tap for focus (use with x, alternative to element_id)")
            boolean("paste", "Use clipboard content instead of text (default false)")
            enum("feedback", listOf("none", "before_wait", "after_wait"),
                "Screenshot feedback mode: 'none' = no screenshot (default), " +
                "'before_wait' = capture screenshot immediately after typing then wait, " +
                "'after_wait' = wait first then capture screenshot")
            int("wait_ms", "Wait duration in milliseconds after typing (default 0, max 10000).")
            boolean("show_elements", "Draw element boundaries on feedback screenshot (default false)")
        },
    )

    override val riskLevel = RiskLevel.HIGH

    override suspend fun execute(args: JsonObject): ToolResult {
        var text = args.requireString("text") ?: ""
        val elementId = args.requireInt("element_id")?.takeIf { it > 0 }
        val x = args.requireInt("x")
        val y = args.requireInt("y")
        val paste = args.optionalBoolean("paste", false)
        val feedback = args.requireString("feedback") ?: "none"
        val waitMs = args.optionalLong("wait_ms", 0L)
        val showElements = args.optionalBoolean("show_elements", false)

        if (paste) {
            text = AccessibilityBridge.readClipboard()
                ?: return ToolResult("Error: clipboard is empty")
        }

        if (text.isEmpty()) return ToolResult("Error: text is empty")

        var focusX = 0f
        var focusY = 0f

        val resultText = try {
            when {
                elementId != null -> {
                    val bounds = AccessibilityBridge.getElementBounds(elementId)
                    focusX = bounds?.centerX()?.toFloat() ?: 0f
                    focusY = bounds?.centerY()?.toFloat() ?: 0f
                    AccessibilityBridge.clickNode(elementId)
                    delay(200)
                }
                x != null && y != null -> {
                    focusX = x.toFloat()
                    focusY = y.toFloat()
                    AccessibilityBridge.clickCoordinate(focusX, focusY)
                    delay(200)
                }
            }
            val success = typeWithFallback(text)
            val source = if (paste) " (from clipboard)" else ""
            if (success) "Typed \"${text.take(100)}\"$source successfully"
            else "Failed to type text - no focused input field found"
        } catch (e: Exception) {
            "Error typing text: ${e.message}"
        }

        return captureFeedbackIfRequested(resultText, focusX, focusY, feedback, waitMs, showElements, appSettings)
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
