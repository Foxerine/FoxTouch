package ai.foxtouch.data.repository

import ai.foxtouch.data.db.dao.TaskDao
import ai.foxtouch.data.db.entity.TaskEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao,
) {
    fun getAll(): Flow<List<TaskEntity>> = taskDao.getAll()

    fun getBySession(sessionId: String): Flow<List<TaskEntity>> = taskDao.getBySession(sessionId)

    suspend fun getById(id: String): TaskEntity? = taskDao.getById(id)

    suspend fun create(
        title: String,
        description: String = "",
        sessionId: String? = null,
        sortOrder: Int = 0,
    ): TaskEntity {
        val task = TaskEntity(
            title = title,
            description = description,
            sessionId = sessionId,
            sortOrder = sortOrder,
        )
        taskDao.insert(task)
        return task
    }

    suspend fun getNextSortOrder(sessionId: String): Int = taskDao.getMaxSortOrder(sessionId) + 1

    suspend fun updateStatus(id: String, status: String) = taskDao.updateStatus(id, status)

    suspend fun delete(id: String) = taskDao.deleteById(id)

    suspend fun deleteBySession(sessionId: String) = taskDao.deleteBySession(sessionId)

    suspend fun deleteAll() = taskDao.deleteAll()
}
