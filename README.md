# Android Music Player

一个使用 Kotlin 和 Jetpack Compose 开发的现代化安卓音乐播放器应用。

## 功能特性

### 🎵 音乐播放
- 本地音乐文件扫描和播放
- 支持常见音频格式（MP3、FLAC、OGG、OPUS、MP4）
- 播放/暂停、上一曲/下一曲控制
- 播放进度控制和时间显示
- 随机播放、单曲循环、列表循环模式

### 📚 音乐库管理
- 按歌曲、艺术家、专辑分类浏览
- 快速搜索功能
- 音乐文件排序和过滤

### 📋 播放列表
- 创建、编辑、删除自定义播放列表
- 添加/移除歌曲到播放列表
- 播放列表内歌曲排序

### 🎨 用户界面
- Material Design 3 设计规范
- 深色/浅色主题支持
- 响应式布局设计
- 直观的导航体验

### 🔧 其他功能
- 后台播放支持
- 通知栏媒体控制
- 锁屏播放控制
- 音频焦点管理

## 技术架构

### 开发技术栈
- **语言**: Kotlin
- **UI 框架**: Jetpack Compose
- **架构模式**: MVVM (Model-View-ViewModel)
- **音频播放**: AndroidX Media3 (ExoPlayer)
- **数据存储**: Room Persistence Library
- **异步处理**: Kotlin Coroutines
- **依赖注入**: 手动依赖注入

### 项目结构
```
app/src/main/java/com/example/androidmusicplayer/
├── data/                    # 数据层
│   ├── database/           # Room 数据库
│   ├── MusicScanner.kt     # 音乐文件扫描
│   └── PlaylistRepository.kt # 播放列表仓库
├── service/                # 服务层
│   └── MusicService.kt     # 音乐播放服务
├── ui/                     # UI 层
│   ├── components/         # 可复用组件
│   ├── screens/           # 屏幕组件
│   └── theme/             # 主题配置
├── utils/                  # 工具类
├── viewmodel/             # ViewModel 层
└── MainActivity.kt        # 主活动
```

## 权限要求

应用需要以下权限：
- `READ_EXTERNAL_STORAGE` / `READ_MEDIA_AUDIO` - 读取音乐文件
- `FOREGROUND_SERVICE` - 后台播放服务
- `WAKE_LOCK` - 保持播放状态
- `POST_NOTIFICATIONS` - 显示播放通知

## 安装和运行

### 环境要求
- Android Studio Arctic Fox 或更高版本
- Android SDK API 24 (Android 7.0) 或更高版本
- Kotlin 1.9.0 或更高版本

### 构建步骤
1. 克隆项目到本地
2. 使用 Android Studio 打开项目
3. 等待 Gradle 同步完成
4. 连接 Android 设备或启动模拟器
5. 点击运行按钮构建并安装应用

### 依赖库
主要依赖库包括：
- AndroidX Core KTX
- AndroidX Lifecycle
- Jetpack Compose
- AndroidX Media3 (ExoPlayer)
- Room Persistence Library
- Material Design 3

## 使用说明

1. **首次启动**: 应用会请求存储权限以扫描音乐文件
2. **浏览音乐**: 使用底部导航栏在不同视图间切换
3. **播放音乐**: 点击歌曲开始播放，使用播放界面控制播放
4. **创建播放列表**: 在播放列表页面点击"+"按钮创建新播放列表
5. **搜索音乐**: 使用搜索页面快速找到想要的歌曲

## 开发计划

### 已完成功能
- ✅ 基础项目结构和配置
- ✅ 用户界面设计和布局
- ✅ 音乐播放核心功能
- ✅ 播放列表管理
- ✅ 搜索功能
- ✅ 设置界面
- ✅ 在线音乐流媒体支持
- ✅ 性能优化和内存管理已优化
- ✅ 歌词显示功能已支持
- ✅ 音频均衡器已支持
- ✅ 错误处理和异常捕获已支持

## 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 致谢

- 感谢 [Auxio](https://github.com/OxygenCobalt/Auxio) 和 [OuterTune](https://github.com/OuterTune/OuterTune) 项目提供的设计灵感
- 感谢 Android 开发团队提供的优秀开发工具和库
- 感谢开源社区的贡献和支持

