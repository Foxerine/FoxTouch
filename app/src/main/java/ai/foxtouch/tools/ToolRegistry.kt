package ai.foxtouch.tools

import ai.foxtouch.permission.PermissionPolicy
import ai.foxtouch.permission.PermissionStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolRegistry @Inject constructor(
    private val permissionStore: PermissionStore,
) {
    private val tools = mutableListOf<PhoneTool>()

    fun register(tool: PhoneTool) {
        tools.add(tool)
    }

    fun registerAll(vararg newTools: PhoneTool) {
        for (tool in newTools) {
            tools.removeAll { it.definition.name == tool.definition.name }
            tools.add(tool)
        }
    }

    fun getAllTools(): List<PhoneTool> = tools.toList()

    fun getReadOnlyTools(): List<PhoneTool> = tools.filter { it.isReadOnly }

    fun getTool(name: String): PhoneTool? = tools.find { it.definition.name == name }

    suspend fun checkPermission(toolName: String): Boolean =
        permissionStore.isAllowed(toolName)

    suspend fun getPermissionPolicy(toolName: String): PermissionPolicy =
        permissionStore.getPolicy(toolName)

    suspend fun setPermissionPolicy(toolName: String, policy: PermissionPolicy) =
        permissionStore.setPolicy(toolName, policy)
}
