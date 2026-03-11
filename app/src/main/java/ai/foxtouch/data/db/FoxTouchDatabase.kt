package ai.foxtouch.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import ai.foxtouch.data.db.dao.MessageDao
import ai.foxtouch.data.db.dao.SessionDao
import ai.foxtouch.data.db.dao.TaskDao
import ai.foxtouch.data.db.entity.MessageEntity
import ai.foxtouch.data.db.entity.SessionEntity
import ai.foxtouch.data.db.entity.TaskEntity
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SessionEntity::class,
        MessageEntity::class,
        TaskEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class FoxTouchDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun taskDao(): TaskDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN lastTokenCount INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
