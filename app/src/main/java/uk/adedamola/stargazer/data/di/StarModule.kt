package uk.adedamola.stargazer.data.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import uk.adedamola.stargazer.data.local.database.AppDatabase
import uk.adedamola.stargazer.data.remote.api.GitHubApiService
import uk.adedamola.stargazer.data.repository.StarEngine
import uk.adedamola.stargazer.data.remote.AndroidConnectivity
import uk.adedamola.stargazer.data.remote.Connectivity
import javax.inject.Qualifier
import javax.inject.Singleton

/** The app-wide scope that star and unstar requests run in, so leaving a screen never cancels one. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StarScope

@Module
@InstallIn(SingletonComponent::class)
object StarModule {

    @Provides
    @Singleton
    fun provideConnectivity(@ApplicationContext context: Context): Connectivity = AndroidConnectivity(context)

    @Provides
    @Singleton
    @StarScope
    fun provideStarScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun provideStarEngine(
        api: GitHubApiService,
        database: AppDatabase,
        connectivity: Connectivity,
        @StarScope scope: CoroutineScope,
    ): StarEngine = StarEngine(api, database, connectivity, System::currentTimeMillis, scope, { delay(it) })
}
