package ai.foxtouch.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import ai.foxtouch.data.db.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(session: SessionEntity)

    @Update
    suspend fun update(session: SessionEntity)

    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: String): SessionEntity?

    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getMostRecent(): SessionEntity?

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE sessions SET lastTokenCount = :tokenCount WHERE id = :id")
    suspend fun updateTokenCount(id: String, tokenCount: Int)

    @Query("DELETE FROM sessions WHERE id != :excludeId")
    suspend fun deleteAllExcept(excludeId: String)

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()
}
