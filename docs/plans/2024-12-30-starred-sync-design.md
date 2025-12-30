# Starred Repositories Full Sync Design

## Overview

Modify the app's data fetching behavior to:
1. Fetch ALL starred repos on initial launch (not paginated on-demand)
2. Sort by `starred_at` timestamp (order user starred repos on GitHub)
3. Incrementally fetch only new repos on subsequent launches
4. Show repos progressively as they load with animations

## Requirements

- User has 2000+ starred repos
- Initial sync fetches everything with progress indicator
- Subsequent syncs fetch only newly starred repos
- Default sort: most recently starred first
- Progressive display with `animateItem()` on LazyColumn

---

## Database Schema Changes

### RepositoryEntity

Add new field:
```kotlin
@ColumnInfo(name = "starred_at")
val starredAt: Long? = null  // Timestamp when user starred the repo
```

### New DAO Query

```kotlin
@Query("SELECT * FROM repositories ORDER BY starred_at DESC")
fun getRepositoriesByStarredAtPaging(): PagingSource<Int, RepositoryEntity>
```

### SortOption Enum

Add `STARRED` as new default:
```kotlin
enum class SortOption {
    STARRED,  // NEW - by starred_at DESC (default)
    STARS,
    FORKS,
    UPDATED,
    CREATED,
    NAME
}
```

### Migration

Database version 4 → 5:
- Add nullable `starred_at` column
- Existing repos get `null` until re-synced

---

## API Changes

### New Accept Header

GitHub requires special header to get `starred_at`:
```
Accept: application/vnd.github.star+json
```

### Response Format Change

With this header, response wraps each repo:
```json
{
  "starred_at": "2024-01-15T10:30:00Z",
  "repo": { /* repository object */ }
}
```

### New Model

```kotlin
data class StarredRepository(
    @SerializedName("starred_at") val starredAt: String,
    @SerializedName("repo") val repo: GitHubRepository
)
```

### GitHubApiService

```kotlin
@GET("user/starred")
suspend fun getStarredRepositoriesWithTimestamp(
    @Header("Accept") accept: String = "application/vnd.github.star+json",
    @Query("page") page: Int,
    @Query("per_page") perPage: Int = 100,
    @Query("sort") sort: String = "created",
    @Query("direction") direction: String = "desc"
): List<StarredRepository>
```

---

## Sync Strategy

### Initial Sync (first launch or empty database)

1. Detect empty database or missing `starred_at` values
2. Fetch page 1 → immediately insert and display
3. Continue fetching pages 2, 3, 4... in background
4. Each page inserts immediately → UI updates progressively
5. Track progress: "Syncing... 500 of ~2400 repos"
6. Mark `isInitialSyncComplete = true` when done

### Subsequent Sync (incremental)

1. Get the most recent `starred_at` from local database
2. Fetch page 1 (newest starred repos)
3. For each repo: if `starred_at` ≤ our newest local → stop fetching
4. Otherwise insert new repos and continue to next page
5. Typically fetches only 1-2 pages (or zero if nothing new)

### Sync Metadata

Store in `sync_metadata` table:
```kotlin
@Entity(tableName = "sync_metadata")
data class SyncMetadata(
    @PrimaryKey val dataType: String,
    val lastSyncTimestamp: Long,
    val lastItemCreatedAt: String?,
    val isInitialSyncComplete: Boolean = false,
    val newestStarredAt: Long? = null
)
```

---

## RemoteMediator Changes

### New Behavior

```
REFRESH:
  - If initial sync incomplete → fetch ALL pages sequentially
  - If initial sync complete → fetch only new repos (incremental)
  - Emit progress updates via SharedFlow

APPEND/PREPEND:
  - Return MediatorResult.Success(endOfPaginationReached = true)
  - All data already in database, no lazy loading from API
```

### Progress Tracking

```kotlin
data class SyncProgress(
    val phase: SyncPhase,  // INITIAL, INCREMENTAL, COMPLETE
    val loadedCount: Int,
    val estimatedTotal: Int?  // null if unknown
)

enum class SyncPhase {
    INITIAL,
    INCREMENTAL,
    COMPLETE
}
```

---

## UI Changes

### Sync Progress Indicator

- During initial sync: "Syncing starred repos... 1,247 loaded"
- During incremental: subtle refresh indicator
- Complete: indicator disappears

### LazyColumn with Animations

```kotlin
LazyColumn {
    items(
        items = repositories,
        key = { it.id }
    ) { repo ->
        RepositoryItem(
            repo = repo,
            modifier = Modifier.animateItem()
        )
    }
}
```

### Default Sort

Change from `SortOption.STARS` to `SortOption.STARRED`
Label: "Recently Starred"

---

## Data Flow

```
App Launch
    ↓
RemoteMediator.initialize() → LAUNCH_INITIAL_REFRESH
    ↓
Check sync_metadata.isInitialSyncComplete
    ↓
┌─────────────────────┬──────────────────────────┐
│ Initial Sync        │ Incremental Sync         │
├─────────────────────┼──────────────────────────┤
│ Fetch ALL pages     │ Fetch until starred_at   │
│ sequentially        │ ≤ local newest           │
│ Insert each page    │ Insert new repos only    │
│ Update progress     │ Quick (1-2 pages max)    │
│ Mark complete       │                          │
└─────────────────────┴──────────────────────────┘
    ↓
Database has complete starred repos
    ↓
PagingSource reads from DB (sorted by starred_at)
    ↓
UI displays with animations
```

---

## Files to Modify

| File | Changes |
|------|---------|
| `RepositoryEntity.kt` | Add `starredAt` field, new DAO query |
| `AppDatabase.kt` | Version 4→5 migration |
| `GitHubApiService.kt` | New endpoint with star+json header |
| `StarredRepository.kt` | New wrapper model (create) |
| `RepositoryMappers.kt` | Map `starred_at` to entity |
| `StarredReposRemoteMediator.kt` | Full rewrite for new sync logic |
| `SyncMetadata.kt` | Add `isInitialSyncComplete`, `newestStarredAt` |
| `GitHubRepositoryImpl.kt` | Update paging source selection |
| `OrganizationRepository.kt` | Add `STARRED` sort option as default |
| `HomeViewModel.kt` | Add sync progress state, change default sort |
| `HomeScreen.kt` | Add progress indicator, `animateItem()` modifier |
