package ai.foxtouch.data.repository

import android.content.Context
import android.util.Base64
import ai.foxtouch.data.db.dao.MessageDao
import ai.foxtouch.data.db.entity.MessageEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val messageDao: MessageDao,
) {
    private val screenshotDir: File by lazy {
        File(context.filesDir, "screenshots").also { it.mkdirs() }
    }

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

    suspend fun getSessionContentSize(sessionId: String): Long = messageDao.getSessionContentSize(sessionId)

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

    fun saveScreenshot(messageId: String, base64: String): String {
        val file = File(screenshotDir, "$messageId.webp")
        file.writeBytes(Base64.decode(base64, Base64.DEFAULT))
        return file.absolutePath
    }

    fun loadScreenshotBase64(path: String): String? {
        val file = File(path)
        if (!file.exists()) return null
        return Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
    }

    suspend fun deleteScreenshotsBySession(sessionId: String) {
        val messages = messageDao.getBySessionOnce(sessionId)
        messages.mapNotNull { it.screenshotPath }.forEach { path ->
            File(path).delete()
        }
    }

    suspend fun getScreenshotSizeBySession(sessionId: String): Long {
        val messages = messageDao.getBySessionOnce(sessionId)
        return messages.mapNotNull { it.screenshotPath }
            .sumOf { path -> File(path).let { if (it.exists()) it.length() else 0L } }
    }
}
