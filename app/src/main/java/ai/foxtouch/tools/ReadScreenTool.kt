package ai.foxtouch.tools

import ai.foxtouch.accessibility.AccessibilityBridge
import ai.foxtouch.accessibility.ScreenAnnotator
import ai.foxtouch.accessibility.ScreenCaptureManager
import ai.foxtouch.agent.ToolDefinition
import ai.foxtouch.data.preferences.AppSettings
import ai.foxtouch.data.preferences.ScreenshotMode
import kotlinx.serialization.json.JsonObject

class ReadScreenTool(
    private val appSettings: AppSettings? = null,
) : PhoneTool {

    override val definition = ToolDefinition(
        name = "read_screen",
        description = "Read the UI element tree and optionally capture an annotated screenshot. " +
            "Returns structured text with element IDs you can reference in click/type actions. " +
            "The UI tree may be sparse or empty for some apps (WebView, games, Flutter, Canvas-based UI) — " +
            "in those cases use include_screenshot=true and rely on visual analysis with coordinate-based clicking. " +
            "Screenshot annotation layers are independently configurable.",
        parameters = toolParameters {
            boolean("include_screenshot", "Capture a screenshot (default false)")
            boolean("show_grid", "Draw coordinate grid overlay every 200px (default true when screenshot is included)")
            boolean("show_elements", "Draw element boundaries from UI tree on the screenshot, color-coded: green=clickable, blue=scrollable, orange=editable, gray=other. Each element is labeled with its [ID] (default false)")
            boolean("show_labels", "Show element text and class name labels next to boundaries. Requires show_elements=true (default false)")
            boolean("clickable_only", "Only annotate interactive elements (clickable, scrollable, editable). Requires show_elements=true (default false)")
        },
    )

    override val riskLevel = ai.foxtouch.permission.RiskLevel.LOW
    override val isReadOnly = true

    override suspend fun execute(args: JsonObject): ToolResult {
        val includeScreenshot = args.optionalBoolean("include_screenshot", false)
        val showGrid = args.optionalBoolean("show_grid", true)
        val showElements = args.optionalBoolean("show_elements", false)
        val showLabels = args.optionalBoolean("show_labels", false)
        val clickableOnly = args.optionalBoolean("clickable_only", false)

        val uiTree = AccessibilityBridge.readUITree()
        val result = StringBuilder()
        result.appendLine("## UI Tree:")
        result.appendLine(uiTree)

        var screenshotBase64: String? = null

        if (includeScreenshot) {
            val foreground = AccessibilityBridge.getForegroundPackageName()
            val mode = if (appSettings != null && foreground != null) {
                appSettings.getScreenshotMode(foreground)
            } else {
                ScreenshotMode.ACCESSIBILITY
            }
            val authorized = ScreenCaptureManager.isAuthorized
            result.appendLine()
            result.appendLine("## Screenshot[v2]: fg=$foreground, mode=$mode, mpAuth=$authorized")

            try {
                val captured = AccessibilityBridge.captureScreenBitmap(appSettings)

                // Collect element overlay data if requested
                val elementInfos = if (showElements) {
                    AccessibilityBridge.getElementOverlayInfos(clickableOnly)
                } else {
                    emptyList()
                }

                val options = ScreenAnnotator.AnnotationOptions(
                    grid = showGrid,
                    elements = showElements,
                    labels = showLabels,
                    clickableOnly = clickableOnly,
                )

                val annotated = ScreenAnnotator.annotate(
                    captured.bitmap,
                    captured.originalWidth,
                    captured.originalHeight,
                    elementInfos,
                    options,
                )
                screenshotBase64 = AccessibilityBridge.encodeBitmapToBase64(annotated)
                captured.bitmap.recycle()
                annotated.recycle()

                result.appendLine("## Coordinate System: ${captured.originalWidth}x${captured.originalHeight}px, grid every 200px")
                val layers = mutableListOf<String>()
                if (showGrid) layers.add("grid")
                if (showElements) layers.add("elements(${elementInfos.size})")
                if (showLabels) layers.add("labels")
                result.appendLine("## Annotation layers: ${layers.joinToString(", ")}")
                result.appendLine("Screenshot captured (sent as image).")
            } catch (e: Exception) {
                result.appendLine("Screenshot failed: ${e.message}")
            }
        }

        return ToolResult(text = result.toString(), imageBase64 = screenshotBase64)
    }
}
