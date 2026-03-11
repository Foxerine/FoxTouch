package ai.foxtouch.data.repository

import ai.foxtouch.data.db.dao.TaskDao
import ai.foxtouch.data.db.entity.TaskEntity
import androidx.room.withTransaction
import ai.foxtouch.data.db.FoxTouchDatabase
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val database: FoxTouchDatabase,
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

    suspend fun createAll(
        tasks: List<Triple<String, String, Int>>,
        sessionId: String? = null,
    ): List<TaskEntity> {
        val entities = tasks.map { (title, description, sortOrder) ->
            TaskEntity(
                title = title,
                description = description,
                sessionId = sessionId,
                sortOrder = sortOrder,
            )
        }
        taskDao.insertAll(entities)
        return entities
    }

    suspend fun getNextSortOrder(sessionId: String): Int = taskDao.getMaxSortOrder(sessionId) + 1

    suspend fun updateStatus(id: String, status: String) = taskDao.updateStatus(id, status)

    suspend fun batchUpdateStatus(updates: List<Pair<String, String>>) {
        database.withTransaction {
            for ((id, status) in updates) {
                taskDao.updateStatus(id, status)
            }
        }
    }

    suspend fun delete(id: String) = taskDao.deleteById(id)

    suspend fun deleteByIds(ids: List<String>) = taskDao.deleteByIds(ids)

    suspend fun deleteBySession(sessionId: String) = taskDao.deleteBySession(sessionId)

    suspend fun deleteAll() = taskDao.deleteAll()
}
