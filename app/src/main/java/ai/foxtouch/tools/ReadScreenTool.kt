package ai.foxtouch.tools

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import ai.foxtouch.accessibility.AccessibilityBridge
import ai.foxtouch.accessibility.ScreenAnnotator
import ai.foxtouch.accessibility.ScreenCaptureManager
import ai.foxtouch.agent.ToolDefinition
import ai.foxtouch.data.preferences.AppSettings
import ai.foxtouch.data.preferences.ScreenshotMode
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReadScreenTool(
    private val appContext: Context,
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
            boolean("save_to_gallery", "Save the screenshot (without annotations) to the device's photo gallery (default false)")
            int("wait_ms", "Wait this many milliseconds before reading the screen (default 0, max 10000). " +
                "Useful for waiting for animations, page loads, or network operations to complete.")
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
        val saveToGallery = args.optionalBoolean("save_to_gallery", false)
        val waitMs = args.optionalLong("wait_ms", 0L).coerceIn(0, 10_000)

        if (waitMs > 0) delay(waitMs)

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

                // Save raw (unannotated) screenshot to gallery if requested
                if (saveToGallery) {
                    try {
                        val savedPath = saveScreenshotToGallery(captured.bitmap)
                        result.appendLine("Screenshot saved to gallery: $savedPath")
                    } catch (e: Exception) {
                        result.appendLine("Failed to save screenshot: ${e.message}")
                    }
                }

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

    private fun saveScreenshotToGallery(bitmap: Bitmap): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "FoxTouch_$timestamp.png"

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Screenshots")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = appContext.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw RuntimeException("Failed to create MediaStore entry")

        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        } ?: throw RuntimeException("Failed to open output stream")

        contentValues.clear()
        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, contentValues, null, null)

        return "Pictures/Screenshots/$filename"
    }
}
