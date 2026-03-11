package ai.foxtouch.di

import android.content.Context
import androidx.room.Room
import ai.foxtouch.data.db.FoxTouchDatabase
import ai.foxtouch.data.db.dao.MessageDao
import ai.foxtouch.data.db.dao.SessionDao
import ai.foxtouch.data.db.dao.TaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FoxTouchDatabase =
        Room.databaseBuilder(
            context,
            FoxTouchDatabase::class.java,
            "foxtouch.db"
        )
            .addMigrations(FoxTouchDatabase.MIGRATION_1_2, FoxTouchDatabase.MIGRATION_2_3)
            .build()

    @Provides
    fun provideTaskDao(db: FoxTouchDatabase): TaskDao = db.taskDao()

    @Provides
    fun provideSessionDao(db: FoxTouchDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideMessageDao(db: FoxTouchDatabase): MessageDao = db.messageDao()
}
