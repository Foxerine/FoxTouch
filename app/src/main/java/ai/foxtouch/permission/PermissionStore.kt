package ai.foxtouch.permission

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.permissionDataStore: DataStore<Preferences> by preferencesDataStore(name = "tool_permissions")

@Singleton
class PermissionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore get() = context.permissionDataStore

    private val defaults = DEFAULT_TOOL_PERMISSIONS.associate { it.toolName to it.defaultPolicy }

    suspend fun getPolicy(toolName: String): PermissionPolicy {
        val prefs = dataStore.data.first()
        val stored = prefs[stringPreferencesKey(toolName)]
        return if (stored != null) {
            try {
                PermissionPolicy.valueOf(stored)
            } catch (_: IllegalArgumentException) {
                defaults[toolName] ?: PermissionPolicy.ASK_EACH_TIME
            }
        } else {
            defaults[toolName] ?: PermissionPolicy.ASK_EACH_TIME
        }
    }

    suspend fun setPolicy(toolName: String, policy: PermissionPolicy) {
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey(toolName)] = policy.name
        }
    }

    suspend fun isAllowed(toolName: String): Boolean =
        getPolicy(toolName) == PermissionPolicy.ALWAYS_ALLOW
}
