# RXPlayer

基于 Jetpack Compose、ExoPlayer 和 Material 3 的功能丰富的 Android 视频播放器。

## 功能特性

- **本地视频浏览** — 通过 SAF（存储访问框架）浏览和播放设备存储中的视频
- **文件夹管理** — 递归文件夹扫描，支持缩略图预览和可自定义的网格布局
- **场景检测** — 智能（像素差异直方图）和间隔两种场景检测模式，带时间轴预览
- **流媒体缓存** — 通过 ExoPlayer DownloadManager 缓存 HLS/DASH/渐进式流媒体，支持离线播放
- **流媒体导出** — 将缓存的 HLS 流导出为拼接的 TS 文件，支持离线分片 URL
- **视频播放器** — 全功能 ExoPlayer，支持手势控制、隐私遮罩、编码/分辨率信息、播放列表
- **播放列表** — 创建和管理自定义视频播放列表
- **观看历史** — 观看记录追踪
- **设置** — 主题（系统/浅色/深色）、自动播放、分析模式等

## 技术栈

| 组件 | 库 |
|---|---|
| UI | Jetpack Compose + Material 3 |
| 播放器 | Media3 ExoPlayer 1.5.1 |
| 依赖注入 | Hilt 2.53.1 (KSP) |
| 数据库 | Room 2.6.1 (KSP) |
| 导航 | Navigation Compose 2.8.5 |
| 图片加载 | Coil Compose 2.7.0 |
| 构建 | Gradle 8.11.1 + AGP 8.7.3 |
| 语言 | Kotlin 2.1.0, JVM 17 |
| 最低 SDK | 26 (Android 8.0) |
| 目标 SDK | 36 (Android 15) |

## 构建

```bash
./gradlew assembleDebug
```

安装到连接的设备：

```bash
./gradlew installDebug
```

## 许可证

MIT
