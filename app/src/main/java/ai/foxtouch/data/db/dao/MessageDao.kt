package ai.foxtouch.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ai.foxtouch.data.db.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Insert
    suspend fun insert(message: MessageEntity)

    @Insert
    suspend fun insertAll(messages: List<MessageEntity>)

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getBySession(sessionId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getBySessionOnce(sessionId: String): List<MessageEntity>

    @Query("""SELECT COALESCE(SUM(LENGTH(content) + COALESCE(LENGTH(toolArgsJson), 0)
        + COALESCE(LENGTH(toolResultJson), 0)), 0) FROM messages WHERE sessionId = :sessionId""")
    suspend fun getSessionContentSize(sessionId: String): Long

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)
}
