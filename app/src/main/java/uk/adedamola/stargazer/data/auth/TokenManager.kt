package uk.adedamola.stargazer.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_preferences")

/**
 * Manages GitHub personal access token storage and retrieval using DataStore.
 * Provides secure, asynchronous token management for API authentication.
 */
@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val tokenKey = stringPreferencesKey("github_token")

    /** Flow that emits the current GitHub token, or null if not set */
    val token: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[tokenKey]
    }

    /**
     * Saves a GitHub personal access token securely
     * @param token The GitHub token to store
     */
    suspend fun saveToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[tokenKey] = token
        }
    }

    /**
     * Removes the stored GitHub token (e.g., on logout)
     */
    suspend fun clearToken() {
        context.dataStore.edit { preferences ->
            preferences.remove(tokenKey)
        }
    }

    /**
     * Checks if a token is currently stored
     * @return true if a token exists, false otherwise
     */
    suspend fun hasToken(): Boolean {
        return context.dataStore.data.map { preferences ->
            preferences[tokenKey] != null
        }.first()
    }
}
