[**中文**](README_CN.md) | **English**

# RXPlayer

A feature-rich Android video player app built with Jetpack Compose, ExoPlayer, and Material 3.

## Features

- **Local video browsing** — Browse and play videos from device storage via SAF (Storage Access Framework)
- **Folder management** — Recursive folder scanning with thumbnail previews, customizable grid layout
- **Scene detection** — Smart (pixel-diff histogram) and interval-based scene detection with timeline preview
- **Stream caching** — Cache HLS/DASH/progressive streams for offline playback via ExoPlayer DownloadManager
- **Stream export** — Export cached HLS streams as concatenated TS files, with offline segment URL support
- **Video player** — Full-featured ExoPlayer with gesture controls, privacy mask, codec/resolution info, playlist support
- **Playlists** — Create and manage custom video playlists
- **History** — Watch history tracking
- **Settings** — Theme (system/light/dark), auto-play, analysis mode, and more

## Tech Stack

| Component | Library |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Player | Media3 ExoPlayer 1.5.1 |
| DI | Hilt 2.53.1 (KSP) |
| Database | Room 2.6.1 (KSP) |
| Navigation | Navigation Compose 2.8.5 |
| Image loading | Coil Compose 2.7.0 |
| Build | Gradle 8.11.1 + AGP 8.7.3 |
| Language | Kotlin 2.1.0, JVM 17 |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 36 (Android 15) |

## Build

```bash
./gradlew assembleDebug
```

Install to connected device:

```bash
./gradlew installDebug
```

## License

MIT
