package ai.foxtouch.tools

import android.content.Context
import ai.foxtouch.agent.ToolDefinition
import ai.foxtouch.permission.RiskLevel
import kotlinx.serialization.json.JsonObject

class ListAppsTool(
    private val context: Context,
) : PhoneTool {

    override val definition = ToolDefinition(
        name = "list_apps",
        description = "List installed apps on the device. Returns app names and package names. " +
            "Use the optional 'query' parameter to filter by name. " +
            "Useful for finding the exact package name to use with launch_app.",
        parameters = toolParameters {
            string("query", "Optional search query to filter apps by name (case-insensitive)")
        },
    )

    override val riskLevel = RiskLevel.LOW
    override val isReadOnly = true

    override suspend fun execute(args: JsonObject): ToolResult {
        val query = args.requireString("query")?.lowercase()
        val pm = context.packageManager

        val apps = pm.getInstalledApplications(0)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { appInfo ->
                val label = pm.getApplicationLabel(appInfo).toString()
                label to appInfo.packageName
            }
            .filter { (label, pkg) ->
                if (query != null) {
                    label.lowercase().contains(query) || pkg.lowercase().contains(query)
                } else true
            }
            .sortedBy { it.first }

        if (apps.isEmpty()) {
            return ToolResult(if (query != null) "No apps found matching '$query'" else "No launchable apps found")
        }

        val sb = StringBuilder()
        sb.appendLine("Found ${apps.size} app(s):")
        for ((label, pkg) in apps) {
            sb.appendLine("- $label ($pkg)")
        }
        return ToolResult(sb.toString())
    }
}
