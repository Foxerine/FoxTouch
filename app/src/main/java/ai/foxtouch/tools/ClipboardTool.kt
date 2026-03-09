package ai.foxtouch.tools

import ai.foxtouch.accessibility.AccessibilityBridge
import ai.foxtouch.agent.ToolDefinition
import ai.foxtouch.permission.RiskLevel
import kotlinx.serialization.json.JsonObject

class ClipboardTool : PhoneTool {

    override val definition = ToolDefinition(
        name = "clipboard",
        description = "Read or write the system clipboard. " +
            "To read: call with no arguments or action=\"read\". " +
            "To write: pass text and action=\"write\".",
        parameters = toolParameters {
            enum("action", listOf("read", "write"), "read or write (default read)")
            string("text", "Text to write to clipboard (required when action=write)")
        },
    )

    override val riskLevel = RiskLevel.MEDIUM

    override suspend fun execute(args: JsonObject): ToolResult {
        val action = args.requireString("action") ?: "read"
        val text = args.requireString("text")

        return when (action) {
            "write" -> {
                if (text.isNullOrEmpty()) {
                    return ToolResult("Error: text is required when action=write")
                }
                AccessibilityBridge.writeClipboard(text)
                ToolResult("Clipboard set to: \"${text.take(100)}\"${if (text.length > 100) "..." else ""}")
            }
            "read" -> {
                val content = AccessibilityBridge.readClipboard()
                if (content.isNullOrEmpty()) {
                    ToolResult("Clipboard is empty")
                } else {
                    ToolResult("Clipboard content: \"$content\"")
                }
            }
            else -> ToolResult("Error: action must be 'read' or 'write'")
        }
    }
}
