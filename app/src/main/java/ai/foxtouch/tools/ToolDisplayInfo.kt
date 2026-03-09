package ai.foxtouch.tools

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * User-friendly display info for each tool.
 * Used in ApprovalSheet, ToolCallCard, and notifications
 * when debug mode is off (friendly display).
 */
data class ToolDisplayInfo(
    val displayName: String,
    val formatArgs: (JsonObject) -> String,
    val approvalMessage: (JsonObject) -> String,
)

/**
 * Registry of user-friendly tool display information.
 * Falls back to raw tool name and JSON when no mapping exists.
 */
object ToolDisplayRegistry {

    private var appContext: Context? = null

    /** Call once from Application.onCreate() or Hilt module to provide context. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Resolve a package name to a human-readable app label, or null. */
    private fun resolveAppLabel(packageName: String): String? {
        val ctx = appContext ?: return null
        return try {
            val pm = ctx.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private val registry = mapOf<String, ToolDisplayInfo>(
        "read_screen" to ToolDisplayInfo(
            displayName = "Read Screen",
            formatArgs = { "Capturing current screen content" },
            approvalMessage = { "FoxTouch wants to read the current screen." },
        ),
        "click" to ToolDisplayInfo(
            displayName = "Tap",
            formatArgs = { args ->
                val elementId = args["element_id"]?.jsonPrimitive?.content
                val x = args["x"]?.jsonPrimitive?.content
                val y = args["y"]?.jsonPrimitive?.content
                if (elementId != null) "Tap on element #$elementId"
                else "Tap at ($x, $y)"
            },
            approvalMessage = { args ->
                val elementId = args["element_id"]?.jsonPrimitive?.content
                val x = args["x"]?.jsonPrimitive?.content
                val y = args["y"]?.jsonPrimitive?.content
                if (elementId != null) "FoxTouch wants to tap on element #$elementId."
                else "FoxTouch wants to tap at coordinates ($x, $y)."
            },
        ),
        "type_text" to ToolDisplayInfo(
            displayName = "Type Text",
            formatArgs = { args ->
                val text = args["text"]?.jsonPrimitive?.content ?: ""
                val preview = if (text.length > 50) text.take(50) + "..." else text
                "Type: \"$preview\""
            },
            approvalMessage = { args ->
                val text = args["text"]?.jsonPrimitive?.content ?: ""
                val preview = if (text.length > 80) text.take(80) + "..." else text
                "FoxTouch wants to type: \"$preview\""
            },
        ),
        "scroll" to ToolDisplayInfo(
            displayName = "Scroll",
            formatArgs = { args ->
                val direction = args["direction"]?.jsonPrimitive?.content ?: "down"
                "Scroll $direction"
            },
            approvalMessage = { args ->
                val direction = args["direction"]?.jsonPrimitive?.content ?: "down"
                "FoxTouch wants to scroll $direction."
            },
        ),
        "swipe" to ToolDisplayInfo(
            displayName = "Swipe",
            formatArgs = { args ->
                val direction = args["direction"]?.jsonPrimitive?.content
                val distance = args["distance"]?.jsonPrimitive?.content
                if (direction != null) {
                    "Swipe $direction" + if (distance != null) " (${distance}px)" else ""
                } else {
                    val sx = args["start_x"]?.jsonPrimitive?.content ?: "?"
                    val sy = args["start_y"]?.jsonPrimitive?.content ?: "?"
                    val ex = args["end_x"]?.jsonPrimitive?.content ?: "?"
                    val ey = args["end_y"]?.jsonPrimitive?.content ?: "?"
                    "Swipe ($sx,$sy) -> ($ex,$ey)"
                }
            },
            approvalMessage = { args ->
                val direction = args["direction"]?.jsonPrimitive?.content
                val distance = args["distance"]?.jsonPrimitive?.content
                if (direction != null) {
                    "FoxTouch wants to swipe $direction" +
                        if (distance != null) " (${distance}px)." else "."
                } else {
                    "FoxTouch wants to perform a swipe gesture."
                }
            },
        ),
        "long_press" to ToolDisplayInfo(
            displayName = "Long Press",
            formatArgs = { args ->
                val elementId = args["element_id"]?.jsonPrimitive?.content
                "Long press on element #$elementId"
            },
            approvalMessage = { args ->
                val elementId = args["element_id"]?.jsonPrimitive?.content
                "FoxTouch wants to long press on element #$elementId."
            },
        ),
        "pinch" to ToolDisplayInfo(
            displayName = "Pinch",
            formatArgs = { args ->
                val direction = args["direction"]?.jsonPrimitive?.content ?: "in"
                "Pinch $direction"
            },
            approvalMessage = { args ->
                val direction = args["direction"]?.jsonPrimitive?.content ?: "in"
                "FoxTouch wants to pinch $direction."
            },
        ),
        "back" to ToolDisplayInfo(
            displayName = "Back",
            formatArgs = { "Press Back button" },
            approvalMessage = { "FoxTouch wants to press the Back button." },
        ),
        "home" to ToolDisplayInfo(
            displayName = "Home",
            formatArgs = { "Press Home button" },
            approvalMessage = { "FoxTouch wants to press the Home button." },
        ),
        "launch_app" to ToolDisplayInfo(
            displayName = "Launch App",
            formatArgs = { args ->
                val pkg = args["package_name"]?.jsonPrimitive?.content ?: ""
                val label = resolveAppLabel(pkg)
                if (label != null) "Launch: $label ($pkg)" else "Launch: $pkg"
            },
            approvalMessage = { args ->
                val pkg = args["package_name"]?.jsonPrimitive?.content ?: ""
                val label = resolveAppLabel(pkg)
                if (label != null) "FoxTouch wants to launch $label ($pkg)"
                else "FoxTouch wants to launch the app: $pkg"
            },
        ),
        "list_apps" to ToolDisplayInfo(
            displayName = "List Apps",
            formatArgs = { "Search installed apps" },
            approvalMessage = { "FoxTouch wants to list installed apps." },
        ),
        "wait" to ToolDisplayInfo(
            displayName = "Wait",
            formatArgs = { args ->
                val ms = args["duration_ms"]?.jsonPrimitive?.content?.toLongOrNull() ?: 1000L
                val seconds = ms / 1000.0
                if (seconds >= 1) "等待 ${String.format("%.1f", seconds)} 秒"
                else "等待 ${ms}ms"
            },
            approvalMessage = { "FoxTouch wants to pause briefly." },
        ),
        "create_task" to ToolDisplayInfo(
            displayName = "Create Task",
            formatArgs = { args ->
                val title = args["title"]?.jsonPrimitive?.content ?: ""
                "Create task: $title"
            },
            approvalMessage = { args ->
                val title = args["title"]?.jsonPrimitive?.content ?: ""
                "FoxTouch wants to create a task: $title"
            },
        ),
        "update_task" to ToolDisplayInfo(
            displayName = "Update Task",
            formatArgs = { args ->
                val status = args["status"]?.jsonPrimitive?.content ?: ""
                "Update task → $status"
            },
            approvalMessage = { args ->
                val status = args["status"]?.jsonPrimitive?.content ?: ""
                "FoxTouch wants to mark a task as $status."
            },
        ),
        "ask_user" to ToolDisplayInfo(
            displayName = "Ask Question",
            formatArgs = { args ->
                val question = args["question"]?.jsonPrimitive?.content ?: ""
                val preview = if (question.length > 60) question.take(60) + "..." else question
                "Question: $preview"
            },
            approvalMessage = { "FoxTouch wants to ask you a question." },
        ),
        "edit_plan" to ToolDisplayInfo(
            displayName = "Edit Plan",
            formatArgs = { "Writing plan document" },
            approvalMessage = { "FoxTouch wants to edit the plan." },
        ),
        "enter_plan_mode" to ToolDisplayInfo(
            displayName = "Enter Plan Mode",
            formatArgs = { "Switching to planning mode" },
            approvalMessage = { "FoxTouch wants to enter plan mode to investigate before acting." },
        ),
        "exit_plan_mode" to ToolDisplayInfo(
            displayName = "Submit Plan",
            formatArgs = { "Presenting plan for your review" },
            approvalMessage = { "FoxTouch wants to submit the plan for your review." },
        ),
        "read_memory" to ToolDisplayInfo(
            displayName = "Read Memory",
            formatArgs = { "Reading persistent memory" },
            approvalMessage = { "FoxTouch wants to read its memory file." },
        ),
        "write_memory" to ToolDisplayInfo(
            displayName = "Write Memory",
            formatArgs = { args ->
                val mode = args["mode"]?.jsonPrimitive?.content ?: "overwrite"
                "Saving to memory ($mode)"
            },
            approvalMessage = { "FoxTouch wants to save information to its memory." },
        ),
        "read_agents" to ToolDisplayInfo(
            displayName = "Read Instructions",
            formatArgs = { "Reading user instructions" },
            approvalMessage = { "FoxTouch wants to read your instruction file." },
        ),
        "confirm_completion" to ToolDisplayInfo(
            displayName = "Confirm Completion",
            formatArgs = { args ->
                val summary = args["summary"]?.jsonPrimitive?.content ?: ""
                val preview = if (summary.length > 60) summary.take(60) + "..." else summary
                "Task done: $preview"
            },
            approvalMessage = { "FoxTouch is confirming task completion with you." },
        ),
        "list_skills" to ToolDisplayInfo(
            displayName = "List Skills",
            formatArgs = { "Checking available skills" },
            approvalMessage = { "FoxTouch wants to list saved skills." },
        ),
        "read_skill" to ToolDisplayInfo(
            displayName = "Read Skill",
            formatArgs = { args ->
                val id = args["skill_id"]?.jsonPrimitive?.content ?: ""
                "Reading skill: $id"
            },
            approvalMessage = { "FoxTouch wants to read a saved skill." },
        ),
        "save_skill" to ToolDisplayInfo(
            displayName = "Save Skill",
            formatArgs = { args ->
                val title = args["title"]?.jsonPrimitive?.content ?: ""
                "Saving skill: $title"
            },
            approvalMessage = { args ->
                val title = args["title"]?.jsonPrimitive?.content ?: ""
                "FoxTouch wants to save the current plan as a skill: $title"
            },
        ),
    )

    fun getDisplayName(toolName: String): String =
        registry[toolName]?.displayName ?: toolName

    fun formatArgs(toolName: String, args: JsonObject): String =
        try {
            registry[toolName]?.formatArgs?.invoke(args) ?: args.toString()
        } catch (_: Exception) {
            args.toString()
        }

    fun getApprovalMessage(toolName: String, args: JsonObject): String =
        try {
            registry[toolName]?.approvalMessage?.invoke(args)
                ?: "FoxTouch wants to use: $toolName"
        } catch (_: Exception) {
            "FoxTouch wants to use: $toolName"
        }
}
