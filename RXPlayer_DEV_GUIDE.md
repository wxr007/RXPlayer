# RXPlayer 开发环境与项目文档

> 生成日期：2026-05-28

---

## 一、开发环境概览

### 1.1 本地环境检查结果

| 项目 | 检测值 | 说明 |
|---|---|---|
| **操作系统** | Windows 10.0.26200 | x64 |
| **JDK** | OpenJDK 17.0.18 (Microsoft LTS) | `D:\AndroidSdk\jdk17` |
| **Android SDK** | `D:\AndroidSdk` | ANDROID_HOME |
| **ADB** | 36.0.2 | `D:\AndroidSdk\platform-tools\adb.exe` |
| **NDK** | 27.0.12077973 | 可选依赖（本工程暂不需要） |
| **Gradle** | 未安装全局版本 | 使用 Gradle Wrapper (内置于项目) |

### 1.2 已安装 SDK 平台

| API Level | Android 版本 | 目录 |
|---|---|---|
| 23 | Android 6.0 | `android-23` |
| 28 | Android 9 | `android-28` |
| 29 | Android 10 | `android-29` |
| 33 | Android 13 | `android-33` |
| 34 | Android 14 | `android-34` |
| 35 | Android 15 | `android-35` |
| 36 | Android 16 | `android-36` |
| 37 (Preview) | Android 17 | `android-37.0` |

### 1.3 已安装 Build-Tools

| 版本 | 用途 |
|---|---|
| 28.0.3 | 旧项目兼容 |
| 29.0.2 | 旧项目兼容 |
| 30.0.3 | 旧项目兼容 |
| 33.0.1 | Android 13 |
| 34.0.0 | Android 14 |
| **35.0.0** ✅ | 本项目使用（最新稳定版） |

### 1.4 工具链依赖

| 工具 | 版本 | 下载源 |
|---|---|---|
| **Gradle Wrapper** | 8.11.1 | Gradle 官方 |
| **Android Gradle Plugin (AGP)** | 8.7.3 | Google Maven |
| **Kotlin** | 2.1.0 | Kotlin 官方 (通过 Gradle Plugin 引入) |

---

## 二、项目技术选型

| 分层 | 选型 | 版本 |
|---|---|---|
| 构建系统 | Gradle + Kotlin DSL | Gradle 8.11.1 |
| UI 框架 | Jetpack Compose + Material 3 | Compose BOM 2024.12.01 |
| 导航 | Compose Navigation | 2.8.5 |
| 播放器 | AndroidX Media3 ExoPlayer | 1.5.1 |
| 图片加载 | Coil (Compose) | 2.7.0 |
| 本地数据库 | Room | 2.6.1 |
| 依赖注入 | Hilt | 2.53.1 |
| 生命周期 | AndroidX Lifecycle | 2.8.7 |
| 构建配置 | KSP (Kotlin Symbol Processing) | 2.1.0-1.0.29 |
| 镜头检测 | `MediaMetadataRetriever` + Bitmap 像素比较 | Android SDK 内置 |

## 三、项目配置 (build.gradle.kts)

### 3.1 根项目 Gradle 配置

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "RXPlayer"
include(":app")
```

```kotlin
// build.gradle.kts (Project level)
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
    id("com.google.dagger.hilt.android") version "2.53.1" apply false
}
```

### 3.2 App 模块 Gradle 配置

```kotlin
// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.rxplayer.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rxplayer.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Media3 ExoPlayer
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")

    // Coil (Image/Thumbnail Loading)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.53.1")
    ksp("com.google.dagger:hilt-compiler:2.53.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
}
```

### 3.3 Gradle Wrapper 配置

```
# gradle/wrapper/gradle-wrapper.properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.11.1-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

---

## 四、需求功能总览

### 4.1 功能清单

| 模块 | 功能 | 优先级 |
|---|---|---|
| **首页** | 视频文件夹列表（卡片式布局） | P0 |
| | 文件夹最多显示 4 张视频缩略图 (2×2) | P0 |
| | SAF 添加文件夹 | P0 |
| | 文件夹排序（名称/视频数/添加时间） | P1 |
| | 文件夹长按：删除/重命名 | P1 |
| **视频列表** | 网格/列表切换 | P0 |
| | 缩略图 + 文件名 + 时长 + 大小 + 分辨率 | P0 |
| | 收藏标记（星标） | P1 |
| | 播放进度显示 | P1 |
| | 搜索过滤 | P1 |
| | 排序（名称/日期/大小） | P1 |
| | 长按：收藏/分享/删除/详情 | P2 |
| **播放器** | ExoPlayer 全屏播放 | P0 |
| | 自动横屏 | P0 |
| | 标准播放控件 | P0 |
| | **长按加速**：长按右半屏 → 2.0x（默认），上下滑动调节 1.0x~4.0x | P0 |
| | 亮度手势（左半屏） | P1 |
| | 音量手势（右半屏非长按状态） | P1 |
| | 播放速度菜单（0.5x / 1.0x / 1.5x / 2.0x） | P1 |
| | 屏幕锁定 | P1 |
| | 文件夹内自动连播 | P1 |
| | 画中画 (PiP) | P2 |
| | **视频镜头分析**：自动检测切换镜头时间点，生成缩略图时间轴 | P1 |
| | **时间轴预览图**：镜头缩略图横向排列于视频下方，点击跳转 | P1 |
| **收藏** | 收藏/取消收藏 | P0 |
| | 收藏列表（底部 Tab） | P1 |
| **历史** | 自动记录播放进度 | P0 |
| | 断点续播 | P0 |
| | 历史列表（底部 Tab） | P1 |
| **设置** | 主题切换（浅色/深色/跟随系统） | P1 |
| | 默认排序方式 | P2 |
| | 权限管理引导 | P2 |
| | 关于页 | P2 |

### 4.2 长按加速详细交互

```
┌──────────────────────────────┐
│         │                    │
│   亮度   │   长按加速区       │
│  调节区  │  (屏幕右半侧)      │
│  (左半侧) │                    │
│          │                    │
└──────────────────────────────┘
```

| 动作 | 效果 |
|---|---|
| 长按右半屏 ≥500ms | 进入加速模式，速度 2.0x |
| 保持按压 + 向上滑动 | 加速递增：2.0x→2.5x→3.0x→3.5x→4.0x |
| 保持按压 + 向下滑动 | 减速递减：2.0x→1.5x→1.0x |
| 小幅上下微调 | 在相邻档位间切换 |
| 松开手指 | 恢复 1.0x 原始速度 |

> 速度档位：1.0x → 1.5x → 2.0x → 2.5x → 3.0x → 3.5x → 4.0x

加速时屏幕中央显示 HUD 指示器，如 `⏩ 2.5x`（半透明背景）。

### 4.3 视频镜头分析与时间轴预览

#### 4.3.1 功能概述

播放视频时自动检测镜头切换（场景变化）的时间点，在每个切换点截取缩略图，以时间轴形式横向排列在播放器下方。用户点击缩略图可跳转到对应时间点。

#### 4.3.2 交互示意

```
┌──────────────────────────────────┐
│                                  │
│            ExoPlayer             │
│          全屏视频播放             │
│                                  │
├──────────────────────────────────┤
│   ┌──┐  ┌──┐  ┌──┐  ┌──┐  ┌──┐ │
│   │T1│  │T2│  │T3│  │T4│  │T5│ │
│   └──┘  └──┘  └──┘  └──┘  └──┘ │
│  00:12  01:35  03:48  05:20  07:55│
│         ↕ 当前进度指示           │
├──────────────────────────────────┤
│  ▶ ═══●══════════════════════   │
│  00:12 / 10:30                ≡ │
└──────────────────────────────────┘
```

- 缩略图横向可滚动（`LazyRow`）
- 每张缩略图尺寸：80×45 dp（保持 16:9）
- 缩略图下方显示时间戳标签
- 当前播放位置对应的场景高亮/标记
- 点击缩略图 → ExoPlayer seek 到对应时间点

#### 4.3.3 场景检测算法

| 步骤 | 说明 |
|------|------|
| **帧采样** | 每 500ms 从视频中抽取一帧（使用 `MediaMetadataRetriever`） |
| **帧对比** | 将相邻帧缩放到 16×16 像素，逐像素计算 RGB 差值 |
| **差异归一化** | 差值总和 ÷ (16×16×3×255)，得到 0~1 的差异度 |
| **阈值判定** | 差异度 > 0.35 判定为镜头切换 |
| **缩略图生成** | 在切换点截取 120×68 像素 JPEG 缩略图，缓存到应用缓存目录 |

> 算法对标 `detect_scenes.py` 中的 `ContentDetector` 原理，但使用纯 Android API（`MediaMetadataRetriever` + Bitmap 像素比较），无需引入 FFmpeg 等第三方 native 库。

#### 4.3.4 性能与缓存

| 项目 | 策略 |
|------|------|
| **检测时机** | 首次播放视频时后台异步执行（`Dispatchers.IO`），结果缓存至应用缓存目录 |
| **缓存目录** | `context.cacheDir/scene_thumbnails/` |
| **缓存文件** | `scene_{index}_{timestampMs}.jpg` |
| **缓存清理** | 随应用缓存自动清理，或视频被删除时清理 |
| **重复检测** | 对同一视频只检测一次，检测结果保存到 Room 数据库 `scene_points` 表 |
| **进度反馈** | 检测过程通过回调/Flow 向外暴露进度（0~100%），UI 显示 Loading 指示器 |

#### 4.3.5 数据流

```
VideoPlayed
    ↓
SceneDetector.detectScenes(videoUri)
    ↓ (后台线程)
逐帧采样 → 逐帧对比 → 差异度 > 阈值?
    ├─ 否 → 继续
    └─ 是 → 记录时间戳 + 截取缩略图(cache) + 存入Room
    ↓
List<ScenePoint> 返回给 ViewModel
    ↓
UI 层渲染 TimelinePreviewBar
    ↓
用户点击缩略图 → onSceneClick(timestampMs) → player.seekTo(timestampMs)
```

#### 4.3.6 相关源码文件

| 文件 | 职责 |
|------|------|
| `media/SceneDetector.kt` | 场景检测核心算法：帧采样、像素对比、阈值判定、缩略图生成 |
| `media/SceneData.kt` | 场景点数据类定义 |
| `data/db/ScenePointDao.kt` | Room DAO，负责场景点的持久化存储和查询 |
| `ui/components/TimelinePreviewBar.kt` | 时间轴预览图横向可滚动 UI 组件 |
| `ui/screens/PlayerScreen.kt` | 播放器页面，集成 ExoPlayer + TimelinePreviewBar |
| `viewmodel/PlayerViewModel.kt` | 管理播放状态、场景检测任务、缓存逻辑 |

---

## 六、导航结构

```
RXPlayerApp
├── BottomNavigation (Scaffold)
│   ├── 首页 Tab (Home)
│   │   ├── FolderListScreen      ← 文件夹列表
│   │   └── VideoListScreen       ← 视频列表 (点击文件夹)
│   ├── 收藏 Tab (Favorites)
│   │   └── FavoriteListScreen
│   ├── 历史 Tab (History)
│   │   └── HistoryListScreen
│   └── 设置 Tab (Settings)
│       └── SettingsScreen
│
└── FullScreen (无 BottomBar)
    └── PlayerScreen              ← 播放器全屏页面
```

路由定义：

```kotlin
sealed class Route(val route: String) {
    object FolderList : Route("folders")
    object VideoList : Route("videos/{folderPath}") {
        fun createRoute(folderPath: String) = "videos/$folderPath"
    }
    object Favorites : Route("favorites")
    object History : Route("history")
    object Settings : Route("settings")
    object Player : Route("player/{videoPath}") {
        fun createRoute(videoPath: String) = "player/$videoPath"
    }
}
```

---

## 七、权限声明

```xml
<!-- AndroidManifest.xml -->

<!-- Android 13+ 细粒度媒体权限 -->
<uses-permission android:maxSdkVersion="32"
    android:name="android.permission.READ_EXTERNAL_STORAGE" />

<!-- Android 13 (API 33) 及以上 -->
<uses-permission android:minSdkVersion="33"
    android:name="android.permission.READ_MEDIA_VIDEO" />

<!-- 后台播放 -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />

<!-- 画中画 -->
<uses-permission android:name="android.permission.PICTURE_IN_PICTURE" />

<!-- 锁屏控制 -->
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

---

## 八、开发指令速查

```bash
# 拉取 Gradle Wrapper（首次初始化）
gradle wrapper --gradle-version=8.11.1

# 清理构建
./gradlew clean

# 编译
./gradlew assembleDebug

# 安装到模拟器/设备
./gradlew installDebug

# 运行 lint 检查
./gradlew lint

# 生成依赖报告
./gradlew app:dependencies
```

---

## 九、版本兼容性对照表

| 项目 | 要求 | 本地是否满足 |
|---|---|---|
| JDK ≥ 17 | AGP 8.x 必需 | ✅ JDK 17.0.18 |
| Gradle 8.9+ | AGP 8.7.x 必需 | ✅ 通过 Wrapper 使用 8.11.1 |
| Kotlin 2.0+ | Compose 编译器集成 | ✅ Kotlin 2.1.0 |
| compileSdk 36 | Android 16 API | ✅ 已安装 |
| buildTools 35.0.0 | 最新稳定 | ✅ 已安装 |
| MinSdk 26 | Android 8.0 | 需设备/模拟器支持 |

---

> 如有版本更新需求，可通过 `sdkmanager` 安装新版本：
> ```
> "D:\AndroidSdk\cmdline-tools\latest\bin\sdkmanager.bat" "platforms;android-XX" "build-tools;XX.0.0"
> ```
