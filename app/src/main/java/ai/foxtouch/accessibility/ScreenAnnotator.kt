package ai.foxtouch.accessibility

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

/**
 * Draws coordinate grids, element boundaries, click markers, and labels
 * onto screenshot bitmaps for LLM spatial understanding.
 *
 * All coordinate parameters use the **original screen coordinate space**
 * (before the bitmap was scaled to max 1080px). Mapping to bitmap
 * coordinates is handled internally.
 */
object ScreenAnnotator {

    private const val DEFAULT_GRID_SPACING = 200

    /**
     * Annotation options — each flag independently controls a layer.
     * The AI selects which layers to enable per read_screen call.
     */
    data class AnnotationOptions(
        val grid: Boolean = true,
        val gridSpacing: Int = DEFAULT_GRID_SPACING,
        val elements: Boolean = false,
        val labels: Boolean = false,
        val clickableOnly: Boolean = false,
        val clickX: Float? = null,
        val clickY: Float? = null,
    )

    /**
     * Single-pass annotation entry point.
     * Creates a mutable copy of the bitmap and draws all requested layers.
     *
     * @return A new annotated Bitmap. Caller must recycle it.
     */
    fun annotate(
        bitmap: Bitmap,
        originalWidth: Int,
        originalHeight: Int,
        elementInfos: List<ElementOverlayInfo> = emptyList(),
        options: AnnotationOptions = AnnotationOptions(),
    ): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val scaleX = result.width.toFloat() / originalWidth
        val scaleY = result.height.toFloat() / originalHeight

        if (options.elements && elementInfos.isNotEmpty()) {
            val filtered = if (options.clickableOnly) {
                elementInfos.filter { it.isClickable || it.isScrollable || it.isEditable }
            } else {
                elementInfos
            }
            drawElements(canvas, filtered, scaleX, scaleY, options.labels)
        }

        if (options.grid) {
            drawGrid(canvas, result.width, result.height, originalWidth, originalHeight, options.gridSpacing)
        }

        if (options.clickX != null && options.clickY != null) {
            drawClickMarker(canvas, result.width, result.height, options.clickX, options.clickY, originalWidth, originalHeight)
        }

        return result
    }

    // Convenience wrappers for backwards compat
    fun annotateWithGrid(
        bitmap: Bitmap, originalWidth: Int, originalHeight: Int, gridSpacing: Int = DEFAULT_GRID_SPACING,
    ): Bitmap = annotate(bitmap, originalWidth, originalHeight, options = AnnotationOptions(grid = true, gridSpacing = gridSpacing))

    fun annotateWithGridAndClick(
        bitmap: Bitmap, clickX: Float, clickY: Float, originalWidth: Int, originalHeight: Int, gridSpacing: Int = DEFAULT_GRID_SPACING,
    ): Bitmap = annotate(bitmap, originalWidth, originalHeight, options = AnnotationOptions(grid = true, gridSpacing = gridSpacing, clickX = clickX, clickY = clickY))

    // ── Element overlay ─────────────────────────────────────────────────

    /** Color palette for element types */
    private const val COLOR_CLICKABLE = 0xFF4CAF50.toInt()  // green
    private const val COLOR_SCROLLABLE = 0xFF2196F3.toInt() // blue
    private const val COLOR_EDITABLE = 0xFFFF9800.toInt()   // orange
    private const val COLOR_DEFAULT = 0xFF9E9E9E.toInt()    // gray

    private fun elementColor(info: ElementOverlayInfo): Int = when {
        info.isEditable -> COLOR_EDITABLE
        info.isClickable -> COLOR_CLICKABLE
        info.isScrollable -> COLOR_SCROLLABLE
        else -> COLOR_DEFAULT
    }

    private fun drawElements(
        canvas: Canvas,
        elements: List<ElementOverlayInfo>,
        scaleX: Float,
        scaleY: Float,
        showLabels: Boolean,
    ) {
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val fillPaint = Paint().apply {
            style = Paint.Style.FILL
        }
        val idTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 20f
            isFakeBoldText = true
        }
        val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 16f
        }
        val tagBgPaint = Paint().apply {
            style = Paint.Style.FILL
        }
        val textBounds = Rect()

        for (elem in elements) {
            val color = elementColor(elem)
            val left = elem.bounds.left * scaleX
            val top = elem.bounds.top * scaleY
            val right = elem.bounds.right * scaleX
            val bottom = elem.bounds.bottom * scaleY

            // Skip tiny elements (< 4px scaled)
            if (right - left < 4f || bottom - top < 4f) continue

            // Semi-transparent fill
            fillPaint.color = color
            fillPaint.alpha = 20
            canvas.drawRect(left, top, right, bottom, fillPaint)

            // Border
            borderPaint.color = color
            borderPaint.alpha = 180
            canvas.drawRect(left, top, right, bottom, borderPaint)

            // ID tag at top-left corner
            val idLabel = "[${elem.id}]"
            idTextPaint.color = color
            idTextPaint.getTextBounds(idLabel, 0, idLabel.length, textBounds)
            val tagW = textBounds.width() + 8f
            val tagH = textBounds.height() + 6f
            // Position tag inside the element, at top-left
            val tagX = left
            val tagY = top

            tagBgPaint.color = color
            tagBgPaint.alpha = 200
            canvas.drawRoundRect(RectF(tagX, tagY, tagX + tagW, tagY + tagH), 4f, 4f, tagBgPaint)
            idTextPaint.color = Color.WHITE
            canvas.drawText(idLabel, tagX + 4f, tagY + tagH - 4f, idTextPaint)

            // Optional text/class label below the ID tag
            if (showLabels) {
                val labelParts = mutableListOf<String>()
                if (elem.className.isNotBlank() && elem.className != "View") {
                    labelParts.add(elem.className)
                }
                if (!elem.text.isNullOrBlank()) {
                    labelParts.add("\"${elem.text}\"")
                }
                if (labelParts.isNotEmpty()) {
                    val labelStr = labelParts.joinToString(" ")
                    labelTextPaint.getTextBounds(labelStr, 0, labelStr.length, textBounds)
                    val lbW = textBounds.width() + 6f
                    val lbH = textBounds.height() + 4f
                    val lbX = left
                    val lbY = tagY + tagH + 1f
                    // Don't draw if it would go off-screen
                    if (lbY + lbH < canvas.height) {
                        tagBgPaint.color = 0xCC000000.toInt()
                        canvas.drawRoundRect(RectF(lbX, lbY, lbX + lbW, lbY + lbH), 3f, 3f, tagBgPaint)
                        canvas.drawText(labelStr, lbX + 3f, lbY + lbH - 3f, labelTextPaint)
                    }
                }
            }
        }
    }

    // ── Grid ────────────────────────────────────────────────────────────

    private fun drawGrid(
        canvas: Canvas,
        bitmapW: Int,
        bitmapH: Int,
        originalW: Int,
        originalH: Int,
        gridSpacing: Int,
    ) {
        val scaleX = bitmapW.toFloat() / originalW
        val scaleY = bitmapH.toFloat() / originalH

        val linePaint = Paint().apply {
            color = 0x50808080
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 24f
            isAntiAlias = true
        }

        val bgPaint = Paint().apply {
            color = 0xCCFFFFFF.toInt()
            style = Paint.Style.FILL
        }

        val textBounds = Rect()

        // Vertical grid lines
        var origX = gridSpacing
        while (origX < originalW) {
            val sx = origX * scaleX
            canvas.drawLine(sx, 0f, sx, bitmapH.toFloat(), linePaint)
            val label = origX.toString()
            textPaint.getTextBounds(label, 0, label.length, textBounds)
            val lx = sx - textBounds.width() / 2f
            val ly = textBounds.height() + 4f
            canvas.drawRect(lx - 2f, 0f, lx + textBounds.width() + 2f, ly + 4f, bgPaint)
            canvas.drawText(label, lx, ly, textPaint)
            origX += gridSpacing
        }

        // Horizontal grid lines
        var origY = gridSpacing
        while (origY < originalH) {
            val sy = origY * scaleY
            canvas.drawLine(0f, sy, bitmapW.toFloat(), sy, linePaint)
            val label = origY.toString()
            textPaint.getTextBounds(label, 0, label.length, textBounds)
            val ly = sy + textBounds.height() / 2f
            canvas.drawRect(0f, ly - textBounds.height() - 2f, textBounds.width() + 6f, ly + 4f, bgPaint)
            canvas.drawText(label, 2f, ly, textPaint)
            origY += gridSpacing
        }
    }

    // ── Click marker ────────────────────────────────────────────────────

    private fun drawClickMarker(
        canvas: Canvas,
        bitmapW: Int,
        bitmapH: Int,
        clickX: Float,
        clickY: Float,
        originalW: Int,
        originalH: Int,
    ) {
        val scaleX = bitmapW.toFloat() / originalW
        val scaleY = bitmapH.toFloat() / originalH
        val sx = clickX * scaleX
        val sy = clickY * scaleY

        val crosshairPaint = Paint().apply {
            color = Color.RED
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }

        canvas.drawLine(sx, 0f, sx, bitmapH.toFloat(), crosshairPaint)
        canvas.drawLine(0f, sy, bitmapW.toFloat(), sy, crosshairPaint)

        val circlePaint = Paint().apply {
            color = 0x40FF0000
            style = Paint.Style.FILL
        }
        canvas.drawCircle(sx, sy, 20f, circlePaint)
        canvas.drawCircle(sx, sy, 20f, crosshairPaint)

        val label = "(${clickX.toInt()}, ${clickY.toInt()})"
        val textPaint = Paint().apply {
            color = Color.RED
            textSize = 28f
            isAntiAlias = true
        }
        val bgPaint = Paint().apply {
            color = 0xDDFFFFFF.toInt()
            style = Paint.Style.FILL
        }
        val textBounds = Rect()
        textPaint.getTextBounds(label, 0, label.length, textBounds)

        val labelX = if (sx + 30 + textBounds.width() < bitmapW) sx + 30 else sx - 30 - textBounds.width()
        val labelY = if (sy - 10 > textBounds.height()) sy - 10 else sy + textBounds.height() + 10

        canvas.drawRect(
            labelX - 4f, labelY - textBounds.height() - 4f,
            labelX + textBounds.width() + 4f, labelY + 4f, bgPaint,
        )
        canvas.drawText(label, labelX, labelY, textPaint)
    }
}
