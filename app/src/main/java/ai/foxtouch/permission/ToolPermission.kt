package ai.foxtouch.permission

enum class RiskLevel { LOW, MEDIUM, HIGH }

enum class PermissionPolicy {
    ALWAYS_ALLOW,
    ASK_EACH_TIME,
    NEVER_ALLOW,
}

data class ToolPermissionConfig(
    val toolName: String,
    val riskLevel: RiskLevel,
    val defaultPolicy: PermissionPolicy,
)

val DEFAULT_TOOL_PERMISSIONS = listOf(
    ToolPermissionConfig("read_screen", RiskLevel.LOW, PermissionPolicy.ALWAYS_ALLOW),
    ToolPermissionConfig("click_element", RiskLevel.HIGH, PermissionPolicy.ASK_EACH_TIME),
    ToolPermissionConfig("tap", RiskLevel.HIGH, PermissionPolicy.ASK_EACH_TIME),
    ToolPermissionConfig("type_text", RiskLevel.HIGH, PermissionPolicy.ASK_EACH_TIME),
    ToolPermissionConfig("type_at", RiskLevel.HIGH, PermissionPolicy.ASK_EACH_TIME),
    ToolPermissionConfig("clipboard", RiskLevel.MEDIUM, PermissionPolicy.ASK_EACH_TIME),
    ToolPermissionConfig("scroll", RiskLevel.MEDIUM, PermissionPolicy.ASK_EACH_TIME),
    ToolPermissionConfig("swipe", RiskLevel.MEDIUM, PermissionPolicy.ASK_EACH_TIME),
    ToolPermissionConfig("long_press", RiskLevel.HIGH, PermissionPolicy.ASK_EACH_TIME),
    ToolPermissionConfig("pinch", RiskLevel.MEDIUM, PermissionPolicy.ASK_EACH_TIME),
    ToolPermissionConfig("back", RiskLevel.LOW, PermissionPolicy.ALWAYS_ALLOW),
    ToolPermissionConfig("home", RiskLevel.MEDIUM, PermissionPolicy.ASK_EACH_TIME),
    ToolPermissionConfig("launch_app", RiskLevel.HIGH, PermissionPolicy.ASK_EACH_TIME),
    ToolPermissionConfig("wait", RiskLevel.LOW, PermissionPolicy.ALWAYS_ALLOW),
    ToolPermissionConfig("create_task", RiskLevel.LOW, PermissionPolicy.ALWAYS_ALLOW),
    ToolPermissionConfig("update_task", RiskLevel.LOW, PermissionPolicy.ALWAYS_ALLOW),
    ToolPermissionConfig("list_apps", RiskLevel.LOW, PermissionPolicy.ALWAYS_ALLOW),
    ToolPermissionConfig("ask_user", RiskLevel.LOW, PermissionPolicy.ALWAYS_ALLOW),
    ToolPermissionConfig("edit_plan", RiskLevel.LOW, PermissionPolicy.ALWAYS_ALLOW),
    ToolPermissionConfig("enter_plan_mode", RiskLevel.LOW, PermissionPolicy.ALWAYS_ALLOW),
    ToolPermissionConfig("exit_plan_mode", RiskLevel.LOW, PermissionPolicy.ALWAYS_ALLOW),
    ToolPermissionConfig("read_memory", RiskLevel.LOW, PermissionPolicy.ALWAYS_ALLOW),
    ToolPermissionConfig("write_memory", RiskLevel.LOW, PermissionPolicy.ALWAYS_ALLOW),
    ToolPermissionConfig("read_agents", RiskLevel.LOW, PermissionPolicy.ALWAYS_ALLOW),
    ToolPermissionConfig("list_skills", RiskLevel.LOW, PermissionPolicy.ALWAYS_ALLOW),
    ToolPermissionConfig("read_skill", RiskLevel.LOW, PermissionPolicy.ALWAYS_ALLOW),
    ToolPermissionConfig("save_skill", RiskLevel.LOW, PermissionPolicy.ASK_EACH_TIME),
    ToolPermissionConfig("confirm_completion", RiskLevel.LOW, PermissionPolicy.ALWAYS_ALLOW),
)
