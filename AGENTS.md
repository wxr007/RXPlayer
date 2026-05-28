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
- **Interop:** `AndroidView` for embedding Android Views (e.g., `PlayerView`).
- **Image loading:** Coil `AsyncImage` with `ImageRequest.Builder(context)`.
- **Modifiers:** Chain with dot notation; avoid deeply nested Modifier calls.

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

### Error Handling
- Use Kotlin null-safety (`?.`, `?:`, `.toLongOrNull()`) as the primary error handling mechanism.
- `try-catch` only when interacting with platform APIs that throw checked exceptions.
- Return `emptyList()` on failure instead of throwing or propagating null.
- No custom exception classes; no `Result<T>` wrapper type.

### Code Generation & Plugins
- **KSP** for Room compiler (`room-compiler`) and Hilt compiler (`hilt-compiler`).
- Kotlin Compose Compiler Plugin (`org.jetbrains.kotlin.plugin.compose`) — no separate compose-compiler version needed with Kotlin 2.1+.
