package uk.adedamola.stargazer.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * The preferences file of the earlier pasted-token version (`auth_preferences`).
 *
 * It may still hold that version's token, which is read once to learn whose data is on the
 * phone and then removed. It also holds the "data owner unknown" marker, set when that token no
 * longer works. The file is backed up, like the account record. The token is never logged.
 */
class LegacyAccountStore(private val dataStore: DataStore<Preferences>) {

    suspend fun oldToken(): String? = dataStore.data.first()[OLD_TOKEN]?.takeIf { it.isNotBlank() }

    suspend fun clearOldToken() {
        dataStore.edit { it.remove(OLD_TOKEN) }
    }

    suspend fun ownerUnknown(): Boolean = dataStore.data.first()[OWNER_UNKNOWN] ?: false

    suspend fun setOwnerUnknown(value: Boolean) {
        dataStore.edit { if (value) it[OWNER_UNKNOWN] = true else it.remove(OWNER_UNKNOWN) }
    }

    companion object {
        const val FILE_NAME = "auth_preferences"
        val OLD_TOKEN = stringPreferencesKey("github_token")
        val OWNER_UNKNOWN = booleanPreferencesKey("data_owner_unknown")
    }
}
