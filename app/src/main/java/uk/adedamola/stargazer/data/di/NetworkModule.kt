package uk.adedamola.stargazer.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import uk.adedamola.stargazer.BuildConfig
import uk.adedamola.stargazer.data.auth.AccessEndedInterceptor
import uk.adedamola.stargazer.data.auth.AuthInterceptor
import uk.adedamola.stargazer.data.auth.DeviceFlowApi
import uk.adedamola.stargazer.data.auth.DeviceFlowSignIn
import uk.adedamola.stargazer.data.remote.api.GitHubApiService
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** The HTTP client and Retrofit for github.com sign-in, which never carry an access key. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SignInHttp

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // Network timeout constants (in seconds)
    private const val CONNECT_TIMEOUT_SECONDS = 30L
    private const val READ_TIMEOUT_SECONDS = 30L
    private const val WRITE_TIMEOUT_SECONDS = 30L

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Full bodies in debug builds, request lines only in release. The Authorization header is
     * redacted in every build type, so no log line carries the access key.
     */
    fun createLoggingInterceptor(
        debug: Boolean,
        logger: HttpLoggingInterceptor.Logger = HttpLoggingInterceptor.Logger.DEFAULT
    ): HttpLoggingInterceptor = HttpLoggingInterceptor(logger).apply {
        level = if (debug) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.BASIC
        redactHeader("Authorization")
    }

    /**
     * The GitHub API client. [accessEnded] sees each response with the key it was sent with.
     * [terminal] is for tests only: it answers requests in place of the network, after every
     * other interceptor has run.
     */
    fun buildGitHubClient(
        authInterceptor: Interceptor,
        loggingInterceptor: Interceptor,
        terminal: Interceptor? = null,
        accessEnded: Interceptor? = null
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .apply { accessEnded?.let { addInterceptor(it) } }
        .addInterceptor(loggingInterceptor)
        .apply { terminal?.let { addInterceptor(it) } }
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /** Base URL of GitHub's sign-in endpoints; the API client never talks to this host. */
    const val SIGN_IN_BASE_URL = "https://github.com/"

    /**
     * Request lines only, in every build type: the token response body carries the new access
     * key, so the sign-in client never logs bodies.
     */
    fun createSignInLoggingInterceptor(
        logger: HttpLoggingInterceptor.Logger = HttpLoggingInterceptor.Logger.DEFAULT
    ): HttpLoggingInterceptor = HttpLoggingInterceptor(logger).apply {
        level = HttpLoggingInterceptor.Level.BASIC
        redactHeader("Authorization")
    }

    /**
     * The github.com sign-in client. It asks for JSON and strips any Authorization header, so a
     * stored key never reaches the sign-in endpoints. [terminal] is for tests only.
     */
    fun buildSignInClient(
        loggingInterceptor: Interceptor,
        terminal: Interceptor? = null
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("Accept", "application/json")
                    .removeHeader("Authorization")
                    .build()
            )
        }
        .addInterceptor(loggingInterceptor)
        .apply { terminal?.let { addInterceptor(it) } }
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    fun createSignInRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(SIGN_IN_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    @SignInHttp
    fun provideSignInOkHttpClient(): OkHttpClient = buildSignInClient(createSignInLoggingInterceptor())

    @Provides
    @Singleton
    @SignInHttp
    fun provideSignInRetrofit(@SignInHttp client: OkHttpClient, json: Json): Retrofit =
        createSignInRetrofit(client, json)

    @Provides
    @Singleton
    fun provideDeviceFlowApi(@SignInHttp retrofit: Retrofit): DeviceFlowApi =
        retrofit.create(DeviceFlowApi::class.java)

    /** A new sign-in per call: each run requests its own code. */
    @Provides
    fun provideDeviceFlowSignIn(api: DeviceFlowApi): DeviceFlowSignIn =
        DeviceFlowSignIn(api, BuildConfig.GITHUB_APP_CLIENT_ID)

    @Provides
    @Singleton
    fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor =
        createLoggingInterceptor(BuildConfig.DEBUG)

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        loggingInterceptor: HttpLoggingInterceptor,
        accessEndedInterceptor: AccessEndedInterceptor
    ): OkHttpClient = buildGitHubClient(authInterceptor, loggingInterceptor, accessEnded = accessEndedInterceptor)

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(GitHubApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideGitHubApiService(retrofit: Retrofit): GitHubApiService {
        return retrofit.create(GitHubApiService::class.java)
    }
}
