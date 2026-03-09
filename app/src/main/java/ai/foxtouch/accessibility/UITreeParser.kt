package ai.foxtouch.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Parses AccessibilityNodeInfo tree into a compact text representation.
 * Each node gets an incrementing ID that can be referenced by click/type tools.
 *
 * Output format:
 * ```
 * [1] FrameLayout
 *   [2] TextView "Settings" (0,80,1080,160) [CLICKABLE]
 *   [3] RecyclerView [SCROLLABLE]
 *     [4] LinearLayout [CLICKABLE]
 *       [5] TextView "Wi-Fi"
 * ```
 */
object UITreeParser {

    private const val OWN_PACKAGE = "ai.foxtouch"

    fun parse(
        root: AccessibilityNodeInfo,
        nodeMap: MutableMap<Int, AccessibilityNodeInfo>,
        allocateId: () -> Int,
        excludePackage: String = OWN_PACKAGE,
    ): String {
        val sb = StringBuilder()
        parseNode(root, sb, nodeMap, allocateId, depth = 0, excludePackage = excludePackage)
        return sb.toString()
    }

    private fun parseNode(
        node: AccessibilityNodeInfo,
        sb: StringBuilder,
        nodeMap: MutableMap<Int, AccessibilityNodeInfo>,
        allocateId: () -> Int,
        depth: Int,
        excludePackage: String,
    ) {
        // Skip own UI nodes to prevent the AI from reading its own overlay
        if (node.packageName?.toString() == excludePackage) return

        val id = allocateId()
        nodeMap[id] = node

        val indent = "  ".repeat(depth)
        val className = node.className?.toString()?.substringAfterLast('.') ?: "Unknown"
        val text = node.text?.toString()
        val contentDesc = node.contentDescription?.toString()
        val bounds = Rect().also { node.getBoundsInScreen(it) }

        sb.append("$indent[$id] $className")

        // Show text or content description
        if (!text.isNullOrBlank()) {
            val truncated = if (text.length > 80) text.take(80) + "..." else text
            sb.append(" \"$truncated\"")
        } else if (!contentDesc.isNullOrBlank()) {
            val truncated = if (contentDesc.length > 80) contentDesc.take(80) + "..." else contentDesc
            sb.append(" desc=\"$truncated\"")
        }

        // Show bounds for all elements (allows coordinate-based clicking)
        sb.append(" (${bounds.left},${bounds.top},${bounds.right},${bounds.bottom})")

        // Show flags
        val flags = mutableListOf<String>()
        if (node.isClickable) flags.add("CLICKABLE")
        if (node.isLongClickable) flags.add("LONG_CLICKABLE")
        if (node.isScrollable) flags.add("SCROLLABLE")
        if (node.isEditable) flags.add("EDITABLE")
        if (node.isCheckable) {
            flags.add(if (node.isChecked) "CHECKED" else "UNCHECKED")
        }
        if (node.isFocused) flags.add("FOCUSED")
        if (!node.isEnabled) flags.add("DISABLED")
        if (flags.isNotEmpty()) {
            sb.append(" [${flags.joinToString(",")}]")
        }

        sb.appendLine()

        // Recurse children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            // Skip nodes that aren't visible or important for accessibility
            if (!child.isVisibleToUser) continue
            parseNode(child, sb, nodeMap, allocateId, depth + 1, excludePackage)
        }
    }
}
