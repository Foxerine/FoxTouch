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
        description = "Create one or more TODO tasks to track multi-step progress. " +
            "Use `tasks` array for batch creation, or `title` for a single task. " +
            "Assign sequential order values (1, 2, 3...) for multi-step plans.",
        parameters = toolParameters {
            string("title", "Short task title (single-task mode)")
            string("description", "Detailed task description (single-task mode)")
            int("order", "Display order (single-task mode). Auto-assigned if omitted.")
            objectArray("tasks", "Array of tasks for batch creation. Each item: {title, description, order}.") {
                string("title", "Short task title", required = true)
                string("description", "Detailed task description")
                int("order", "Display order (1, 2, 3...). Auto-assigned if omitted.")
            }
        },
    )

    override val riskLevel = RiskLevel.LOW
    override val isReadOnly = true

    override suspend fun execute(args: JsonObject): ToolResult {
        val taskItems = args.optionalObjectArray("tasks")

        if (taskItems.isNotEmpty()) {
            return batchCreate(taskItems)
        }

        // Single-task fallback
        val title = args.requireString("title")
            ?: return ToolResult("Error: provide `title` for single task or `tasks` array for batch")
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

    private suspend fun batchCreate(items: List<JsonObject>): ToolResult {
        var nextOrder = sessionId?.let { taskRepository.getNextSortOrder(it) } ?: 1

        val taskTriples = items.map { item ->
            val title = item.requireString("title")
                ?: return ToolResult("Error: each task in `tasks` array requires a `title`")
            val description = item.requireString("description") ?: ""
            val order = item.requireInt("order") ?: nextOrder++
            Triple(title, description, order)
        }

        val created = taskRepository.createAll(taskTriples, sessionId)
        val lines = created.zip(taskTriples).joinToString("\n") { (task, triple) ->
            "[${task.id}] #${triple.third} ${triple.first}"
        }
        return ToolResult("${created.size} tasks created:\n$lines")
    }
}

class UpdateTaskTool(
    private val taskRepository: TaskRepository,
) : PhoneTool {

    override val definition = ToolDefinition(
        name = "update_task",
        description = "Update one or more tasks' status. Use `updates` array for batch updates, " +
            "or `task_id`+`status` for a single update. Use status \"deleted\" to remove tasks permanently.",
        parameters = toolParameters {
            string("task_id", "The task ID to update (single-task mode)")
            enum(
                "status",
                listOf("pending", "in_progress", "completed", "failed", "deleted"),
                "New task status (single-task mode)",
            )
            objectArray("updates", "Array of updates for batch operation. Each item: {task_id, status}.") {
                string("task_id", "The task ID to update", required = true)
                enum(
                    "status",
                    listOf("pending", "in_progress", "completed", "failed", "deleted"),
                    "New task status",
                    required = true,
                )
            }
        },
    )

    override val riskLevel = RiskLevel.LOW
    override val isReadOnly = true

    override suspend fun execute(args: JsonObject): ToolResult {
        val updateItems = args.optionalObjectArray("updates")

        if (updateItems.isNotEmpty()) {
            return batchUpdate(updateItems)
        }

        // Single-task fallback
        val taskId = args.requireString("task_id")
            ?: return ToolResult("Error: provide `task_id` for single update or `updates` array for batch")
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

    private suspend fun batchUpdate(items: List<JsonObject>): ToolResult {
        val results = mutableListOf<String>()
        val toDelete = mutableListOf<String>()
        val toUpdate = mutableListOf<Pair<String, String>>()

        for (item in items) {
            val taskId = item.requireString("task_id")
                ?: return ToolResult("Error: each item in `updates` array requires a `task_id`")
            val status = item.requireString("status")
                ?: return ToolResult("Error: each item in `updates` array requires a `status`")

            val task = taskRepository.getById(taskId)
            if (task == null) {
                results.add("[${taskId}] not found (skipped)")
                continue
            }

            if (status == "deleted") {
                toDelete.add(taskId)
                results.add("[${task.id}] '${task.title}' deleted")
            } else {
                toUpdate.add(taskId to status)
                results.add("[${task.id}] '${task.title}' → $status")
            }
        }

        if (toDelete.isNotEmpty()) taskRepository.deleteByIds(toDelete)
        if (toUpdate.isNotEmpty()) taskRepository.batchUpdateStatus(toUpdate)

        return ToolResult("${items.size} tasks processed:\n${results.joinToString("\n")}")
    }
}
