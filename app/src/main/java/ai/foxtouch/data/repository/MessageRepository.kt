package ai.foxtouch.data.repository

import ai.foxtouch.data.db.dao.MessageDao
import ai.foxtouch.data.db.entity.MessageEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(
    private val messageDao: MessageDao,
) {
    fun getBySession(sessionId: String): Flow<List<MessageEntity>> =
        messageDao.getBySession(sessionId)

    suspend fun getBySessionOnce(sessionId: String): List<MessageEntity> =
        messageDao.getBySessionOnce(sessionId)

    suspend fun addUserMessage(sessionId: String, content: String): MessageEntity {
        val message = MessageEntity(
            sessionId = sessionId,
            role = "user",
            content = content,
        )
        messageDao.insert(message)
        return message
    }

    suspend fun addAssistantMessage(sessionId: String, content: String): MessageEntity {
        val message = MessageEntity(
            sessionId = sessionId,
            role = "assistant",
            content = content,
        )
        messageDao.insert(message)
        return message
    }

    suspend fun deleteBySession(sessionId: String) = messageDao.deleteBySession(sessionId)

    suspend fun addToolMessage(
        sessionId: String,
        toolName: String,
        argsJson: String,
        resultJson: String,
        screenshotPath: String? = null,
    ): MessageEntity {
        val message = MessageEntity(
            sessionId = sessionId,
            role = "tool",
            content = resultJson,
            toolName = toolName,
            toolArgsJson = argsJson,
            toolResultJson = resultJson,
            screenshotPath = screenshotPath,
        )
        messageDao.insert(message)
        return message
    }
}
