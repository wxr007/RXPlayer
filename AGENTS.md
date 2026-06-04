# AGENTS.md — RXPlayer

## Build & Run Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Install to connected device/emulator
./gradlew installDebug

# Clean build
./gradlew clean

# Run lint checks
./gradlew lint

# Generate dependency report
./gradlew app:dependencies

# Build and run (assumes connected device)
./gradlew installDebug && adb shell monkey -p com.rxplayer.app 1
```

**Note:** This project does not yet have test infrastructure configured. No test runner, test dependencies (JUnit, Espresso), or test source sets exist. Tests should be added under `app/src/test/` (unit) and `app/src/androidTest/` (instrumented). Add JUnit 5 + MockK for ViewModel tests, Compose UI Test for screenshot/components, and Room test helpers for DAO tests.

## Workflow Rule

操作顺序：**先改 versionName，再 `./gradlew installDebug` 编译安装，最后 `git add` + `git commit` 提交。**

每次修改完成后需要在 `app/build.gradle.kts` 中递增 `versionName`（如 1.0.0 → 1.0.1 → 1.0.2）。

After every code change, run `./gradlew installDebug` to compile and install, then `git add` + `git commit` with a descriptive message. Do not skip compilation or commit unless the user explicitly says otherwise.

每次修改完成后必须将修改提交到 git，并在 AGENTS.md 中记录本次修改的内容摘要。请不要跳过提交步骤。

## Project Technical Stack

- **Build:** Gradle 8.11.1 + Kotlin DSL, AGP 8.7.3
- **Language:** Kotlin 2.1.0, JVM target 17, compileSdk 36, minSdk 26
- **UI:** Jetpack Compose + Material 3 (BOM 2024.12.01)
- **Navigation:** Navigation Compose 2.8.5
- **DI:** Hilt 2.53.1 (KSP), `hilt-navigation-compose` 1.2.0
- **Database:** Room 2.6.1 (KSP), database name: `rxplayer.db`
- **Player:** Media3 ExoPlayer 1.5.1
- **Image loading:** Coil Compose 2.7.0
- **Lifecycle:** lifecycle-runtime-compose 2.8.7, lifecycle-viewmodel-compose 2.8.7

## Code Style Guidelines

### Imports
- **No wildcard imports.** Every import must be explicit (fully qualified single-class).
- Group imports: Android SDK → AndroidX → Compose → Third-party → Project-internal.
- Separate groups with blank lines where logical.

### Formatting
- **4-space indentation**, no tabs.
- Continuation indentation is also 4 spaces.
- Modifier chaining uses dot notation on separate lines when >2 modifiers.
- One blank line between top-level declarations; blank lines between logical blocks inside functions.

### Types
- Prefer `data class` for models/entities/DTOs.
- Prefer `sealed class` for constrained hierarchies (e.g., navigation routes).
- Expose `StateFlow<T>` publicly, back with private `MutableStateFlow<T>` named `_name`.
- Use `Flow` for Room DAO reactive queries.
- Prefer `suspend` functions over blocking calls for Room/IO operations.

### Naming Conventions

| Category | Convention | Examples |
|---|---|---|
| Classes/Interfaces | `PascalCase` | `SceneDetector`, `ScenePointDao`, `TimelinePreviewBar` |
| Functions/Methods | `camelCase` | `loadFolders()`, `detectScenes()`, `observeScenes()` |
| Properties/Variables | `camelCase` | `videoPath`, `currentPosition`, `thumbnailPath` |
| Private MutableStateFlow backing fields | Underscore prefix `_` | `_scenes`, `_folders`, `_analyzingProgress` |
| Public exposed StateFlow | Same name, no underscore | `scenes`, `folders`, `analyzingProgress` |
| Top-level color/theme vals | `PascalCase` | `Purple80`, `SurfaceDark` |
| Composable functions | `PascalCase` | `PlayerScreen()`, `TimelinePreviewBar()` |

### Package Structure
- `com.rxplayer.app.<feature>` — flat, feature-oriented subpackages:
  - `navigation/` — routes + nav graph
  - `ui/screens/` — full-screen composables
  - `ui/components/` — reusable composables
  - `ui/theme/` — theming (Color, Theme)
  - `data/model/` — data classes
  - `data/settings/` — SharedPreferences-based settings (SettingsManager)
  - `data/db/` — Room entities, DAOs, Database
  - `data/repository/` — repository layer
  - `media/` — scene detection, media analysis
  - `viewmodel/` — ViewModels per screen
  - `di/` — Hilt DI modules

### Dependency Injection (Hilt)
- Application: `@HiltAndroidApp class XxxApp : Application()`
- Activity: `@AndroidEntryPoint class XxxActivity : ComponentActivity()`
- ViewModel: `@HiltViewModel class XxxViewModel @Inject constructor(...) : ViewModel()`
- Module: `@Module @InstallIn(SingletonComponent::class) object XxxModule` with `@Provides`
- Use `@Singleton` for services shared app-wide.
- Use `@ApplicationContext` qualifier for `Context` injection.
- In composables: `val vm: XxxViewModel = hiltViewModel()` (scoped to NavBackStackEntry).

### Compose Patterns
- **State:** `val value by viewModel.state.collectAsState()` in composables.
- **Effects:** `LaunchedEffect` for side effects, `DisposableEffect` for cleanup, `remember` for caching.
- **Navigation:** `NavHost` + `composable()` with `navArgument`, `navController.navigate()`.
- **Bottom nav:** `NavigationBar` + `NavigationBarItem` with `popUpTo(findStartDestination)`.
- **Lazy lists:** `LazyColumn`/`LazyRow` with `items()`, use `key` parameter.
- **Grid:** `LazyVerticalGrid` with `GridCells.Fixed(n)`. For scene grids, determine n dynamically (4 landscape, 5 portrait) via `BitmapFactory.Options.inJustDecodeBounds` on first thumbnail.
- **Interop:** `AndroidView` for embedding Android Views (e.g., `PlayerView`).
- **Image loading:** Coil `AsyncImage` with `ImageRequest.Builder(context)`.
- **Modifiers:** Chain with dot notation; avoid deeply nested Modifier calls.
- **Top bar:** Use `CompactTopAppBar(title, onBack, actions)` in `ui/components/` for all screens. Custom 40dp row with `statusBarsPadding()` — avoids M3 `TopAppBar` default 64dp height.
- **NavHost animations:** Disable default 700ms fade with `fadeIn/Out(tween(0))` to prevent mis-tap during page transitions.
- **Buttons in PlayerScreen:** Analyze button uses `Icons.Default.FlashOn` (lightning bolt) instead of `Icons.Default.Refresh`. Privacy mask button toggles `Visibility`/`VisibilityOff`.
- **Video info in PlayerScreen:** Resolution, codec, frame rate, and decoder type are displayed as a compact text line below the progress bar (non-fullscreen) or below the slider (fullscreen). The video info button (Info icon) has been removed. Data gathered from `Player.Listener.onVideoSizeChanged` and `AnalyticsListener.onVideoDecoderInitialized`.

### Coroutines
- ViewModel scope: `viewModelScope.launch { ... }`
- Background work: `withContext(Dispatchers.IO) { ... }`
- Room queries: DAO methods return `Flow` for reactive or `suspend` for one-shot.
- No `GlobalScope`; prefer structured concurrency with `viewModelScope` or custom `CoroutineScope`.
- No `async`/`await` unless parallel decomposition is needed.
- Use `delay()` for polling loops (e.g., player position tracking).

### Room Database
- `@Entity(tableName = "tbl_name")` with `@PrimaryKey(autoGenerate = true)`.
- DAO interface with `@Dao`, `@Query` (raw SQL), `@Insert(onConflict = ...)`, `@Delete`.
- Reactive queries return `Flow<List<T>>`.
- Database version starts at 1; use `exportSchema = false` during development.
- DAOs are exposed via `@Provides` in the Hilt module.
- Compound primary keys for multi-source data: `@Entity(primaryKeys = ["id", "folderPath"])`.
- Use `@Transaction` + delete + insert for replacing a folder's video list (`replaceFolder()`).
- LIKE queries must escape `_` and `%` with `ESCAPE '\\'` in SQL.

### Error Handling
- Use Kotlin null-safety (`?.`, `?:`, `.toLongOrNull()`) as the primary error handling mechanism.
- `try-catch` only when interacting with platform APIs that throw checked exceptions.
- Return `emptyList()` on failure instead of throwing or propagating null.
- No custom exception classes; no `Result<T>` wrapper type.

### Thumbnail & MediaMetadataRetriever Safety
- `ThumbnailCache.decodeWithRetriever()` must always call `retriever.release()` in `finally` block — native resource leak causes crash.
- Always check `fileExists(videoPath)` before creating `MediaMetadataRetriever` to avoid native resource drain on deleted files.
- `fileExists()` handles both `content://` SAF URIs (`openFileDescriptor`) and local file paths (`File.exists()`).
- `syncFolderFromMediaStore()` must use `videoDao.replaceFolder()` (delete + insert transaction) — NOT `insertAll()` — to clean up stale Room rows for externally-deleted videos.
- Wrap `ThumbnailCache.getThumbnail()` calls in UI `LaunchedEffect` with try-catch for defense-in-depth.

### Scene Thumbnail Aspect Ratio
- `ThumbnailCache.decodeWithRetriever()` must always call `retriever.release()` in `finally` block.
- `SceneDetector.saveThumbnail()` scales preserving aspect ratio (longest edge ≤ `maxOf(w, h)`, default 240px) — uses `minOf(maxDimension / maxOf(bitmap.w, bitmap.h), 1f)` as scale factor.
- `SceneThumbnail` in `TimelinePreviewBar` determines aspect ratio via `BitmapFactory.Options.inJustDecodeBounds` instead of hardcoded `16f/9f`.
- `SceneGrid` reads first thumbnail dimensions to choose 4 columns (landscape) or 5 columns (portrait).

### SAF Folder Scanning
- **scanSafFolderWithProgress()**: Insert placeholder `VideoFolder` first (so UI shows it immediately), then scan with progress callback, then update Room.
- Progress is a `Map<String, Float>` in `HomeViewModel._scanProgress`, keyed by folder path.
- Only generate thumbnails for the first 4 videos (`coverPaths.size < 4`).
- `coverPaths` stores cached JPEG file paths (from `ThumbnailCache.getCachedPath()`), NOT raw video paths.
- Coil `AsyncImage` in `ThumbnailCell` loads cached JPEG directly — no need for `MediaMetadataRetriever` in UI.

### Folder Settings Persistence (Room)
- `FolderEntity` stores per-folder settings keyed by `path` primary key: `displayMode` (0=fit, 1=crop), `columns` (Int), `thumbnailOrientation` (0=landscape, 1=portrait), `sortBy` (String), `sortAscending` (Int), `autoFullscreen` (0=off, 1=on), `playbackMode` (0=single, 1=loop-one, 2=sequential, 3=list-loop).
- `VideoListViewModel` exposes `StateFlow` for each setting; changes are persisted immediately to Room via selective `update*()` DAO methods.
- `syncFolders()` preserves all existing setting values via a settings map to prevent `REPLACE` from resetting them.
- `playbackMode` and `autoFullscreen` are passed as nav arguments from `VideoListScreen` → `PlayerScreen`.
- DB versions: displayMode (v5), columns+sort (v6), thumbnailOrientation (v7 or later). New columns added via ALTER TABLE migration in `AppDatabase.kt`.

### Settings System (SettingsManager)
- Stored in SharedPreferences file `"settings"`, `@Singleton` + `@Inject constructor` (Hilt auto-injects, no AppModule entry needed).
- Exposed as `StateFlow` via `MutableStateFlow` backing fields, read on init and updated on write.
- `themeMode`: `"system"` / `"light"` / `"dark"` — consumed by `RXPlayerTheme(themeMode)` to override `isSystemInDarkTheme()`.
- `autoPlay`: `Boolean` (default `true`) — consumed by `PlayerViewModel` via `settingsManager.autoPlay`, applied via `LaunchedEffect` -> `player.playWhenReady`.
- `analysisMode`: `"smart"` (pixel-diff scene detection) or `"interval"` (fixed-rate capture) — consumed by `SceneAnalyzer`, passed to `SceneDetector.detectScenes(mode)`.
- `analysisInterval`: `Int` (15-60, default 30) — only used when `analysisMode == "interval"`, passed as `intervalSec` to `SceneDetector.detectScenes()`, multiplied by `1000L` for millis.

### Scene Detection Modes
- **"smart"**: Compares 32×32 pixel histograms every 500ms, captures when diff > 0.25 threshold (default). Produces variable number of scenes per video.
- **"interval"**: Captures one frame every `intervalSec` seconds regardless of scene change. Simpler, deterministic number of scenes (`durationMs / intervalMs`).
- Selected via SettingsScreen, persisted in SharedPreferences.

### Stream Download & Caching (DownloadManager)
- Uses **ExoPlayer DownloadManager** for HLS/DASH and progressive stream caching.
- DownloadManager created with old-style constructor: `DownloadManager(context, databaseProvider, cache, upstreamFactory, Executors.newSingleThreadExecutor())` — must call `resumeDownloads()` to start processing.
- `CacheModule.kt` provides `DownloadManager`, `SimpleCache` (with `ExoDatabaseProvider`), and `CacheDataSource.Factory` all as `@Singleton`.
- **StreamViewModel**: `pollDownloadStates()` loops every 1s querying `DownloadIndex.getDownloads()` to track DOWNLOADING/COMPLETED states for all streams. Completed downloads update `streamDao.updateCachedPath(sid, sid.toString())` — cache path is the stream ID string, not a file path.
- **PlayerViewModel**: Uses `DownloadManager.Listener` for real-time progress callbacks per stream. Falls back to polling (`pollDownloadProgress()`) for re-entry scenarios.
- `cachedPath` is a numeric string (stream ID) for DM downloads → playback uses original URL (CacheDataSource serves cached segments). For file-based progressive downloads, `cachedPath` is a file path → playback uses `Uri.fromFile()`.
- `resolveStreamUri()` in PlayerViewModel: if `cachedPath.toLongOrNull() != null` (DM cached), returns original URL; else if local file exists, returns `Uri.fromFile()`; else returns original URL.
- `SimpleCache` stored in `context.cacheDir/exoplayer_cache/` with 500MB LRU evictor.
- `ExoDatabaseProvider` stores download index in `downloads` table within app's private database.

### Player Initialization & Playlist
- **Two-phase init in `PlayerScreen.kt`**: Phase 1 (in `LaunchedEffect(Unit)`) immediately calls `player.setMediaItem(uri)` + `player.prepare()` so user sees video content right away, avoiding the ~5s black screen from `syncFolderFromMediaStore`. Phase 2 (same `LaunchedEffect`) waits for `folderVideos` StateFlow via `snapshotFlow { folderVideos }.firstOrNull { it.isNotEmpty() }`, then uses `player.addMediaItems()` to insert other folder videos around the current one without timeline rebuild.
- **Why addMediaItems over setMediaItems**: `player.setMediaItems()` replaces the entire playlist, causing brief black screen as ExoPlayer rebuilds its timeline. `addMediaItems` inserts items into the existing timeline — no disruption to the currently-playing video.
- **Playlist insertion strategy**: Videos before the current video's folder index get inserted at index 0 (shifting current video up). Videos after get appended at `player.mediaItemCount`. No `player.prepare()` is needed after insertion since the player is already prepared.
- **Playback mode → repeatMode mapping**: `0`(single)→`REPEAT_MODE_OFF`, `1`(loop-one)→`REPEAT_MODE_ONE`, `2`(sequential)→`REPEAT_MODE_OFF`, `3`(list-loop)→`REPEAT_MODE_ALL`. Set once at init; no changes needed after folder playlist is built.
- **PlaylistId support**: `PlayerScreen` accepts optional `playlistId` param. When `> 0`, `PlayerViewModel` loads videos via `PlaylistDao.getVideosInPlaylistSnapshot()` instead of `VideoRepository.getVideosInFolderSnapshot()`, building the same playlist structure for sequential/loop modes.

### Playlist Feature
- **Data layer**: `PlaylistEntity` (auto-generated id, name, createdAt) + `PlaylistVideoEntity` (composite PK: playlistId, filePath) + `PlaylistWithCount` (DAO query result with videoCount).
- **DAO**: `PlaylistDao` with `getAllPlaylistsWithCount()`, `insertPlaylist()`, `deletePlaylistById()` + `clearPlaylist()`, `getVideosInPlaylist()`, `addVideoToPlaylist()`, `removeVideoFromPlaylist()`.
- **ViewModel**: `PlaylistViewModel` exposes `playlists: StateFlow<List<PlaylistWithCount>>` and `playlistVideos: StateFlow<List<Video>>`. `Video` mapping via `PlaylistVideoEntity.toVideo()`.
- **UI reuse**: `PlaylistsScreen` uses `LazyVerticalGrid` 2 columns with `PlaylistCard` (16:9 icon + count badge, FolderCard-style). `PlaylistDetailScreen` uses `LazyVerticalGrid` 2 columns + `VideoGridItem` with `onRemoveFromPlaylistClick`. `VideoGridItem` in `VideoListScreen` accepts optional `onAddToPlaylistClick`/`onRemoveFromPlaylistClick`.
- **Add flow**: From `VideoListScreen` → `AddToPlaylistDialog` (select existing or create new). `onAddToPlaylistClick` triggers dialog, `VideoListViewModel.addVideoToPlaylist()`/`createPlaylist()`.
- **DB version**: v10 added playlists + playlist_videos tables via MIGRATION_9_10. v11 removed FavoriteEntity from entities list (table abandoned in-place, no migration needed).
- **Navigation**: Bottom nav "播放列表" replaces old "收藏". Route `Playlists` + `PlaylistDetail(playlistId, playlistName)` with Base64-encoded name.
- **Cleanup**: Old `FavoritesScreen.kt` and `FavoriteEntity.kt` deleted. `FavoriteEntity::class` removed from AppDatabase entities annotation.

### Stream Export Fixes (v1.0.8)
- **Bug fix: HLS segment URLs resolved against master playlist instead of media playlist** — `exportHls()` now uses `effectiveBaseUrl` (a local `var`) that gets updated to `absoluteMediaPlaylistUrl` after master→media playlist resolution, so segment URLs are correctly resolved relative to the media playlist location.
- **Bug fix: `resolveUrl()` compilation error** — Replaced `baseUri.resolve(url)` (ambiguous with `File.resolve()`) with manual path construction via `baseUri.buildUpon().encodedPath(...)` to avoid the `java.io.File` vs `android.net.Uri` method conflict.
- `resolveUrl()` now strips query/fragment via `buildUpon().clearQuery().fragment(null)` for all resolved URLs (both absolute and relative) to prevent `DataSpec(Uri)` errors.

### Offline HLS Export (v1.0.9 – v1.0.11)
- **Pre-cache segment URLs when caching**: `cacheStream()` now also calls `saveSegmentUrlsForStream()` to resolve and persist all HLS segment URLs to a file (`cacheDir/segment_urls/<streamId>`). This avoids re-fetching playlists during export.
- **Offline export support**: `exportHls()` tries `loadSegmentUrls(streamId)` first — if saved URLs exist, it reads segments directly from `CacheDataSource` (SimpleCache) without needing to fetch playlists from network. Falls back to playlist resolution only if no saved URLs are found.
- `deleteStream()` also cleans up the corresponding segment URL file.
- **Multi-variant URL saving**: `resolveAllVariantUrls()` resolves ALL variants in the master playlist, not just the first one. Segment URL file stores variant-prefixed URLs (`0|url`, `1|url`). During export, for each segment position, iterates through all variants' URLs until a cache hit is found — this ensures the correct variant (the one cached by `DownloadManager`) is used regardless of which variant was selected during caching.
- **Direct TS concatenation**: Replaced `MediaMuxer` remuxing with raw TS segment concatenation. This completely eliminates `MediaMuxer.stop()` errors and codec compatibility issues on devices with non-standard MediaCodec implementations (e.g., Huawei). Output file uses `.ts` content (SAF `video/*` MIME type), playable in VLC/MX Player etc.
- **Robust error handling**: `muxer.stop()`/`release()` wrapped in try-catch. Invalid/empty segment files skipped. Per-segment download failure tolerant (tries next variant).

### Code Generation & Plugins
- **KSP** for Room compiler (`room-compiler`) and Hilt compiler (`hilt-compiler`).
- Kotlin Compose Compiler Plugin (`org.jetbrains.kotlin.plugin.compose`) — no separate compose-compiler version needed with Kotlin 2.1+.
