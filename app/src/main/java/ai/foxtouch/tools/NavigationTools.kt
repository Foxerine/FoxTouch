package ai.foxtouch.tools

import ai.foxtouch.accessibility.AccessibilityBridge
import ai.foxtouch.agent.ToolDefinition
import ai.foxtouch.permission.RiskLevel
import kotlinx.serialization.json.JsonObject

class BackTool : PhoneTool {

    override val definition = ToolDefinition(
        name = "back",
        description = "Press the system back button.",
        parameters = emptyParameters,
    )

    override val riskLevel = RiskLevel.LOW

    override suspend fun execute(args: JsonObject): ToolResult {
        AccessibilityBridge.pressBack()
        return ToolResult("Pressed back button")
    }
}

class HomeTool : PhoneTool {

    override val definition = ToolDefinition(
        name = "home",
        description = "Press the system home button to return to the home screen.",
        parameters = emptyParameters,
    )

    override val riskLevel = RiskLevel.MEDIUM

    override suspend fun execute(args: JsonObject): ToolResult {
        AccessibilityBridge.pressHome()
        return ToolResult("Pressed home button")
    }
}

class LaunchAppTool : PhoneTool {

    override val definition = ToolDefinition(
        name = "launch_app",
        description = "Launch an app by its Android package name. You MUST use the package name (e.g. com.tencent.mm), NOT the display name. " +
            "Use list_apps or read_screen to find package names. " +
            "Note: System Settings (com.android.settings) is blocked — ask the user to open it manually.",
        parameters = toolParameters {
            string("package_name", "Android package name (e.g. com.tencent.mm, com.android.chrome, com.tencent.mobileqq)", required = true)
        },
    )

    override val riskLevel = RiskLevel.HIGH

    override suspend fun execute(args: JsonObject): ToolResult {
        val packageName = args.requireString("package_name")
            ?: return ToolResult("Error: package_name is required")

        if (packageName == "com.android.settings" || packageName.startsWith("com.android.settings.")) {
            return ToolResult("Error: Cannot launch Settings app for security reasons. Ask the user to open Settings manually.")
        }

        return ToolResult(try {
            val launched = AccessibilityBridge.launchApp(packageName)
            if (launched) "Launched app: $packageName"
            else "Failed to launch app: $packageName - package not found or has no launcher activity"
        } catch (e: Exception) {
            "Error launching app: ${e.message}"
        })
    }
}
