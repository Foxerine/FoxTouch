package ai.foxtouch.data.repository

import ai.foxtouch.data.db.dao.SessionDao
import ai.foxtouch.data.db.entity.SessionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
) {
    fun getAll(): Flow<List<SessionEntity>> = sessionDao.getAll()

    suspend fun getById(id: String): SessionEntity? = sessionDao.getById(id)

    suspend fun create(
        title: String = "New Session",
        provider: String = "gemini",
        model: String = "gemini-2.5-flash",
    ): SessionEntity {
        val session = SessionEntity(
            title = title,
            provider = provider,
            model = model,
        )
        sessionDao.insert(session)
        return session
    }

    suspend fun updateTitle(id: String, title: String) {
        val session = sessionDao.getById(id) ?: return
        sessionDao.update(session.copy(title = title, updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(id: String) = sessionDao.deleteById(id)

    suspend fun deleteAllExcept(excludeId: String) = sessionDao.deleteAllExcept(excludeId)

    suspend fun deleteAll() = sessionDao.deleteAll()
}
