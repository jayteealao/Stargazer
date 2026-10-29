package uk.adedamola.stargazer.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * The earlier pasted-token version's preferences file (`auth_preferences`), and the "data owner
 * unknown" marker.
 *
 * The old file may still hold that version's token, which is read once to learn whose data is on
 * the phone and then removed. The file is left out of backup and device transfer, so the token
 * never travels in a backup. The marker, set when that token no longer works, lives in its own
 * file ([markers], `account_owner`), which is backed up like the account record. A marker that an
 * earlier build wrote into the old file is moved on the first read. The token is never logged.
 */
class LegacyAccountStore(
    private val oldFile: DataStore<Preferences>,
    private val markers: DataStore<Preferences>,
) {

    suspend fun oldToken(): String? = oldFile.data.first()[OLD_TOKEN]?.takeIf { it.isNotBlank() }

    suspend fun clearOldToken() {
        oldFile.edit { it.remove(OLD_TOKEN) }
    }

    suspend fun ownerUnknown(): Boolean {
        moveMarkerFromOldFile()
        return markers.data.first()[OWNER_UNKNOWN] ?: false
    }

    suspend fun setOwnerUnknown(value: Boolean) {
        markers.edit { if (value) it[OWNER_UNKNOWN] = true else it.remove(OWNER_UNKNOWN) }
        oldFile.edit { it.remove(OWNER_UNKNOWN) }
    }

    private suspend fun moveMarkerFromOldFile() {
        if (oldFile.data.first()[OWNER_UNKNOWN] != true) return
        markers.edit { it[OWNER_UNKNOWN] = true }
        oldFile.edit { it.remove(OWNER_UNKNOWN) }
    }

    companion object {
        /** The old file; `res/xml` backup rules exclude it. */
        const val FILE_NAME = "auth_preferences"
        /** The backed-up file of the owner-unknown marker. */
        const val MARKER_FILE_NAME = "account_owner"
        val OLD_TOKEN = stringPreferencesKey("github_token")
        val OWNER_UNKNOWN = booleanPreferencesKey("data_owner_unknown")
    }
}
