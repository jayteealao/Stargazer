package uk.adedamola.stargazer.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import uk.adedamola.stargazer.data.auth.AccessKeyProvider
import uk.adedamola.stargazer.data.auth.AndroidKeystoreCipher
import uk.adedamola.stargazer.data.auth.TokenManager
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

/** The DataStore that holds only the encrypted access key. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AccessKeyStore

@Module
@InstallIn(SingletonComponent::class)
object SecureStorageModule {

    /**
     * Directory under `filesDir` that holds the key file and nothing else.
     * `res/xml/data_extraction_rules.xml` and `res/xml/backup_rules.xml` exclude it from backup.
     */
    const val KEY_DIR = "access_key"
    const val KEY_FILE = "github_access_key.preferences_pb"
    const val KEY_ALIAS = "stargazer_github_access_key"

    fun keyFile(context: Context): File = File(File(context.filesDir, KEY_DIR), KEY_FILE)

    /** A corrupt key file reads as empty, which means signed out. */
    fun createAccessKeyDataStore(scope: CoroutineScope, file: () -> File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = scope,
            produceFile = file,
        )

    @Provides
    @Singleton
    @AccessKeyStore
    fun provideAccessKeyDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        createAccessKeyDataStore(CoroutineScope(SupervisorJob() + Dispatchers.IO)) { keyFile(context) }

    @Provides
    @Singleton
    fun provideTokenManager(@AccessKeyStore dataStore: DataStore<Preferences>): TokenManager =
        TokenManager(
            dataStore = dataStore,
            cipher = AndroidKeystoreCipher(KEY_ALIAS),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )

    @Provides
    fun provideAccessKeyProvider(tokenManager: TokenManager): AccessKeyProvider = tokenManager
}
