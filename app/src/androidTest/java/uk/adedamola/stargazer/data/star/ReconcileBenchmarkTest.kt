package uk.adedamola.stargazer.data.star

import uk.adedamola.stargazer.data.repository.AccountRepository
import uk.adedamola.stargazer.ui.screens.HomeGitHubSearch
import uk.adedamola.stargazer.ui.screens.HomeRemovals
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.paging.AsyncPagingDataDiffer
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadState
import androidx.room.withTransaction
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.adedamola.stargazer.data.auth.AccessSession
import uk.adedamola.stargazer.data.auth.AuthInterceptor
import uk.adedamola.stargazer.data.auth.FIXTURE_USER_ID
import uk.adedamola.stargazer.data.auth.FakeConnectivity
import uk.adedamola.stargazer.data.auth.Scripted
import uk.adedamola.stargazer.data.auth.ScriptedNetwork
import uk.adedamola.stargazer.data.auth.TokenManagerFixture
import uk.adedamola.stargazer.data.auth.fakeKey
import uk.adedamola.stargazer.data.auth.graphQlApi
import uk.adedamola.stargazer.data.auth.inMemoryDatabase
import uk.adedamola.stargazer.data.auth.listsJson
import uk.adedamola.stargazer.data.auth.profileJson
import uk.adedamola.stargazer.data.auth.seedRepos
import uk.adedamola.stargazer.data.auth.starredPages
import uk.adedamola.stargazer.data.di.NetworkModule
import uk.adedamola.stargazer.data.local.database.AccountRecord
import uk.adedamola.stargazer.data.local.database.AppDatabase
import uk.adedamola.stargazer.data.local.database.SyncMetadata
import uk.adedamola.stargazer.data.paging.StarredReposRemoteMediator
import uk.adedamola.stargazer.data.remote.api.GitHubApiService
import uk.adedamola.stargazer.data.remote.model.GitHubRepository
import uk.adedamola.stargazer.data.repository.AccountDataRefresher
import uk.adedamola.stargazer.data.repository.GitHubRepositoryImpl
import uk.adedamola.stargazer.data.repository.GitHubSearch
import uk.adedamola.stargazer.data.repository.OrganizationRepository
import uk.adedamola.stargazer.data.repository.StarEngine
import uk.adedamola.stargazer.data.repository.StarListRepository
import uk.adedamola.stargazer.data.repository.StarReconciler
import uk.adedamola.stargazer.ui.screens.HomeViewModel
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * Times one pull-to-refresh of Home on a 3,000-repo starred list served by a scripted GitHub,
 * through the real view model, paging stack and database. Opt-in: it runs only with
 * `-e benchmark true`, so a normal suite run skips it.
 *
 * Two scenarios, each 3 discarded warm-up runs and 10 measured runs, the database re-seeded before
 * every run outside the timed window:
 * - `refresh-full-list`: nothing stored yet, so the refresh reads the whole list.
 * - `refresh-synced-list`: the whole list already stored and synced.
 *
 * Per run: wall time, process CPU time, and peak Java heap growth (sampled every 10 ms after a
 * collection) with the native heap change. Results go to logcat and to
 * `files/benchmark/<scenario>.txt`.
 */
@OptIn(ExperimentalPagingApi::class)
@RunWith(AndroidJUnit4::class)
class ReconcileBenchmarkTest {

    private companion object {
        const val TAG = "RefreshBenchmark"
        const val REPOS = 3_000
        const val WARMUPS = 3
        const val RUNS = 10
        val IDS = (1..REPOS).map { 900_000 + it }
    }

    private lateinit var keys: TokenManagerFixture
    private lateinit var db: AppDatabase
    private lateinit var scope: CoroutineScope

    private val pages = starredPages(IDS)
    private val network = ScriptedNetwork { request ->
        pages(request) ?: when (request.url.encodedPath) {
            "/user" -> Scripted(200, profileJson(FIXTURE_USER_ID, "ada-builds", "Ada Okafor"))
            "/graphql" -> Scripted(200, listsJson(FIXTURE_USER_ID, REPOS, emptyList()))
            else -> Scripted(404, """{"message":"Not Found"}""")
        }
    }

    @Before
    fun setUp() = runBlocking<Unit> {
        assumeTrue("opt-in benchmark: pass -e benchmark true", InstrumentationRegistry.getArguments().getString("benchmark") == "true")
        keys = TokenManagerFixture()
        keys.tokenManager.saveToken(fakeKey())
        db = inMemoryDatabase()
        db.accountDao().replace(AccountRecord(FIXTURE_USER_ID, "ada-builds", "", 1L))
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        if (!::db.isInitialized) return
        scope.cancel()
        db.close()
        keys.close()
    }

    @Test
    fun refreshFullList() = measure("refresh-full-list") { clear() }

    @Test
    fun refreshSyncedList() = measure("refresh-synced-list") {
        clear()
        seedRepos(db, IDS)
        db.syncMetadataDao().updateMetadata(
            SyncMetadata(dataType = "starred_repos", isInitialSyncComplete = true, newestStarredAt = 1_760_000_000_000L - 1)
        )
        // The last finished reconcile was for this sign-in, so nothing asks first.
        db.syncMetadataDao().updateMetadata(
            SyncMetadata(dataType = "starred_repos_reconcile", lastSyncTimestamp = 1L, isInitialSyncComplete = true)
        )
    }

    private suspend fun clear() {
        db.withTransaction {
            db.repositoryTagDao().deleteAll()
            db.repositoryDao().deleteAll()
            db.syncMetadataDao().deleteAll()
        }
    }

    /** The production GitHub client with release-level logging, answered by [network]. */
    private fun api(): GitHubApiService {
        val client = NetworkModule.buildGitHubClient(
            authInterceptor = AuthInterceptor(keys.tokenManager),
            loggingInterceptor = NetworkModule.createLoggingInterceptor(false, HttpLoggingInterceptor.Logger { }),
            terminal = network,
        )
        return NetworkModule.provideGitHubApiService(NetworkModule.provideRetrofit(client, NetworkModule.provideJson()))
    }

    private data class Run(val wallMs: Double, val cpuMs: Long, val heapPeakMb: Double, val nativeDeltaMb: Double)

    private fun measure(scenario: String, seed: suspend () -> Unit) = runBlocking<Unit> {
        val api = api()
        val mediator = StarredReposRemoteMediator(api, db)
        val connectivity = FakeConnectivity()
        val engine = StarEngine(api, db, connectivity, System::currentTimeMillis, scope, { delay(it) })
        val viewModel = withContext(Dispatchers.Main) {
            HomeViewModel(
                savedStateHandle = SavedStateHandle(),
                gitHubRepository = GitHubRepositoryImpl(db.repositoryDao(), mediator, api),
                organizationRepository = OrganizationRepository(db.repositoryDao(), db.tagDao(), db.repositoryTagDao(), db.searchPresetDao()),
                accessSession = AccessSession(keys.tokenManager),
                accounts = AccountRepository(db.accountDao(), db.gitHubProfileDao(), db.repositoryDao()),
                starListRepository = StarListRepository(db.starListDao()),
                refresher = AccountDataRefresher(api, graphQlApi(keys.tokenManager, network), db) { 1L },
                starEngine = engine,
                searchFactory = HomeGitHubSearch.Factory(GitHubSearch(api, db.repositoryDao()), connectivity, engine),
                removalsFactory = HomeRemovals.Factory(StarReconciler(api, db, engine)),
            )
        }
        val differ = AsyncPagingDataDiffer(
            diffCallback = object : DiffUtil.ItemCallback<GitHubRepository>() {
                override fun areItemsTheSame(oldItem: GitHubRepository, newItem: GitHubRepository) = oldItem.id == newItem.id
                override fun areContentsTheSame(oldItem: GitHubRepository, newItem: GitHubRepository) = oldItem == newItem
            },
            updateCallback = object : ListUpdateCallback {
                override fun onInserted(position: Int, count: Int) {}
                override fun onRemoved(position: Int, count: Int) {}
                override fun onMoved(fromPosition: Int, toPosition: Int) {}
                override fun onChanged(position: Int, count: Int, payload: Any?) {}
            },
            mainDispatcher = Dispatchers.Main,
            workerDispatcher = Dispatchers.Default,
        )
        val sawLoading = AtomicBoolean(false)
        val latest = java.util.concurrent.atomic.AtomicReference<androidx.paging.CombinedLoadStates?>(null)
        val collector = launch(Dispatchers.Main) { viewModel.repositories.collectLatest { differ.submitData(it) } }
        val watcher = launch(Dispatchers.Main) {
            differ.loadStateFlow.collect {
                latest.set(it)
                if (it.mediator?.refresh is LoadState.Loading) sawLoading.set(true)
            }
        }

        fun idle(): Boolean {
            val states = latest.get() ?: return false
            return states.mediator?.refresh !is LoadState.Loading &&
                states.refresh !is LoadState.Loading &&
                !viewModel.syncProgress.value.isLoading &&
                !viewModel.reconciling.value
        }

        suspend fun settle() = withTimeout(120_000) { while (!idle()) delay(20) }

        // Home is open and its first load is over before anything is timed.
        seed()
        settle()

        val runs = mutableListOf<Run>()
        repeat(WARMUPS + RUNS) { index ->
            seed()
            delay(300)
            settle()
            Runtime.getRuntime().gc()
            System.gc()
            delay(200)
            val runtime = Runtime.getRuntime()
            val heapStart = runtime.totalMemory() - runtime.freeMemory()
            val nativeStart = Debug.getNativeHeapAllocatedSize()
            val sampling = AtomicBoolean(true)
            val heapPeak = AtomicLong(heapStart)
            val sampler = thread(name = "heap-sampler") {
                while (sampling.get()) {
                    heapPeak.accumulateAndGet(runtime.totalMemory() - runtime.freeMemory()) { a, b -> maxOf(a, b) }
                    Thread.sleep(10)
                }
            }

            sawLoading.set(false)
            val cpuStart = Process.getElapsedCpuTime()
            val wallStart = SystemClock.elapsedRealtimeNanos()
            withContext(Dispatchers.Main) {
                differ.refresh()
                viewModel.refresh()
            }
            withTimeout(120_000) { while (!(sawLoading.get() && idle())) delay(5) }
            val wallEnd = SystemClock.elapsedRealtimeNanos()
            val cpuEnd = Process.getElapsedCpuTime()

            sampling.set(false)
            sampler.join()
            val nativeEnd = Debug.getNativeHeapAllocatedSize()
            assertEquals("the whole list is stored after the refresh", REPOS, db.repositoryDao().getRepositoryCount())
            if (index >= WARMUPS) {
                runs += Run(
                    wallMs = (wallEnd - wallStart) / 1_000_000.0,
                    cpuMs = cpuEnd - cpuStart,
                    heapPeakMb = (heapPeak.get() - heapStart) / 1_048_576.0,
                    nativeDeltaMb = (nativeEnd - nativeStart) / 1_048_576.0,
                )
            }
        }
        collector.cancel()
        watcher.cancel()
        report(scenario, runs)
    }

    private fun percentile(values: List<Double>, p: Double): Double {
        val sorted = values.sorted()
        val rank = (p / 100.0 * (sorted.size - 1))
        val low = sorted[kotlin.math.floor(rank).toInt()]
        val high = sorted[kotlin.math.ceil(rank).toInt()]
        return low + (high - low) * (rank - kotlin.math.floor(rank))
    }

    private fun report(scenario: String, runs: List<Run>) {
        fun line(name: String, values: List<Double>) =
            "%s median=%.1f p95=%.1f p99=%.1f".format(name, percentile(values, 50.0), percentile(values, 95.0), percentile(values, 99.0))
        val text = buildString {
            appendLine("scenario=$scenario runs=${runs.size} warmups=$WARMUPS repos=$REPOS")
            appendLine(line("wall_ms", runs.map { it.wallMs }))
            appendLine(line("cpu_ms", runs.map { it.cpuMs.toDouble() }))
            appendLine(line("heap_peak_mb", runs.map { it.heapPeakMb }))
            appendLine(line("native_delta_mb", runs.map { it.nativeDeltaMb }))
            runs.forEachIndexed { i, r ->
                appendLine("run=${i + 1} wall_ms=%.1f cpu_ms=%d heap_peak_mb=%.2f native_delta_mb=%.2f".format(r.wallMs, r.cpuMs, r.heapPeakMb, r.nativeDeltaMb))
            }
        }
        text.lines().forEach { Log.i(TAG, it) }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(File(context.filesDir, "benchmark").apply { mkdirs() }, "$scenario.txt").writeText(text)
    }
}
