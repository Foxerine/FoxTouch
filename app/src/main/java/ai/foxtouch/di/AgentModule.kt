package ai.foxtouch.di

import android.content.Context
import ai.foxtouch.agent.AgentDocsManager
import ai.foxtouch.agent.AgentRunner
import ai.foxtouch.agent.DeviceContext
import ai.foxtouch.data.preferences.ApiKeyStore
import ai.foxtouch.data.preferences.AppSettings
import ai.foxtouch.data.repository.SessionRepository
import ai.foxtouch.data.repository.TaskRepository
import ai.foxtouch.permission.PermissionStore
import ai.foxtouch.tools.ToolRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AgentModule {

    @Provides
    @Singleton
    fun provideToolRegistry(permissionStore: PermissionStore): ToolRegistry =
        ToolRegistry(permissionStore)

    @Provides
    @Singleton
    fun provideAgentRunner(
        @ApplicationContext appContext: Context,
        @ApplicationScope applicationScope: CoroutineScope,
        httpClient: HttpClient,
        json: Json,
        apiKeyStore: ApiKeyStore,
        appSettings: AppSettings,
        toolRegistry: ToolRegistry,
        deviceContext: DeviceContext,
        taskRepository: TaskRepository,
        sessionRepository: SessionRepository,
        agentDocsManager: AgentDocsManager,
    ): AgentRunner = AgentRunner(appContext, applicationScope, httpClient, json, apiKeyStore, appSettings, toolRegistry, deviceContext, taskRepository, sessionRepository, agentDocsManager)
}
