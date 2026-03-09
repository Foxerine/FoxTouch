package ai.foxtouch.tools

import ai.foxtouch.agent.ToolDefinition
import ai.foxtouch.data.repository.TaskRepository
import ai.foxtouch.permission.RiskLevel
import kotlinx.serialization.json.JsonObject

class CreateTaskTool(
    private val taskRepository: TaskRepository,
    private val sessionId: String?,
) : PhoneTool {

    override val definition = ToolDefinition(
        name = "create_task",
        description = "Create a TODO task to track multi-step progress. Tasks are displayed in order. " +
            "Assign sequential order values (1, 2, 3...) for multi-step plans. Returns the task ID.",
        parameters = toolParameters {
            string("title", "Short task title", required = true)
            string("description", "Detailed task description")
            int("order", "Display order (1, 2, 3...). Auto-assigned if omitted.")
        },
    )

    override val riskLevel = RiskLevel.LOW
    override val isReadOnly = true

    override suspend fun execute(args: JsonObject): ToolResult {
        val title = args.requireString("title")
            ?: return ToolResult("Error: title is required")
        val description = args.requireString("description") ?: ""
        val order = args.requireInt("order")
            ?: sessionId?.let { taskRepository.getNextSortOrder(it) }
            ?: 0

        val task = taskRepository.create(
            title = title,
            description = description,
            sessionId = sessionId,
            sortOrder = order,
        )
        return ToolResult("Task created: [${task.id}] #$order $title")
    }
}

class UpdateTaskTool(
    private val taskRepository: TaskRepository,
) : PhoneTool {

    override val definition = ToolDefinition(
        name = "update_task",
        description = "Update a task's status. Use \"deleted\" to remove a task permanently.",
        parameters = toolParameters {
            string("task_id", "The task ID to update", required = true)
            enum("status", listOf("pending", "in_progress", "completed", "failed", "deleted"), "New task status", required = true)
        },
    )

    override val riskLevel = RiskLevel.LOW
    override val isReadOnly = true

    override suspend fun execute(args: JsonObject): ToolResult {
        val taskId = args.requireString("task_id")
            ?: return ToolResult("Error: task_id is required")
        val status = args.requireString("status")
            ?: return ToolResult("Error: status is required")

        val task = taskRepository.getById(taskId)
            ?: return ToolResult("Error: Task $taskId not found")

        if (status == "deleted") {
            taskRepository.delete(taskId)
            return ToolResult("Task [${task.id}] '${task.title}' deleted")
        }

        taskRepository.updateStatus(taskId, status)
        return ToolResult("Task [${task.id}] '${task.title}' updated to $status")
    }
}
