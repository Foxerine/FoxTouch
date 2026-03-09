package ai.foxtouch.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

private val Context.apiKeyDataStore: DataStore<Preferences> by preferencesDataStore(name = "api_keys")

@Singleton
class ApiKeyStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val aead: Aead by lazy {
        val keysetHandle: KeysetHandle = AndroidKeysetManager.Builder()
            .withSharedPref(context, "foxtouch_keyset", "foxtouch_keyset_prefs")
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri("android-keystore://foxtouch_master_key")
            .build()
            .keysetHandle
        keysetHandle.getPrimitive(Aead::class.java)
    }

    private val dataStore get() = context.apiKeyDataStore

    suspend fun saveApiKey(provider: String, apiKey: String) {
        val encrypted = aead.encrypt(apiKey.toByteArray(Charsets.UTF_8), provider.toByteArray())
        val encoded = Base64.getEncoder().encodeToString(encrypted)
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("${provider}_api_key")] = encoded
        }
    }

    fun getApiKey(provider: String): Flow<String?> = dataStore.data.map { prefs ->
        val encoded = prefs[stringPreferencesKey("${provider}_api_key")] ?: return@map null
        try {
            val encrypted = Base64.getDecoder().decode(encoded)
            String(aead.decrypt(encrypted, provider.toByteArray()), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getApiKeyOnce(provider: String): String? =
        getApiKey(provider).first()

    suspend fun deleteApiKey(provider: String) {
        dataStore.edit { prefs ->
            prefs.remove(stringPreferencesKey("${provider}_api_key"))
        }
    }
}
