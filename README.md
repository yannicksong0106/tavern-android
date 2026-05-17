# 酒馆 AI (Tavern Lite)

一款原生 Android 应用，重新实现 [SillyTavern](https://github.com/SillyTavern/SillyTavern) 的核心功能，以独立 APK 形式运行，无需服务端。

## 功能特性

### 已完成 (Phase A-G)

| 模块 | 功能 |
|------|------|
| **角色卡** | 创建/编辑/删除、PNG 导入导出（tEXt chunk）、chara_card_v2/v3 规范 |
| **对话系统** | SSE 流式生成（OpenAI/Claude/Ollama/自定义）、编辑/删除/重新生成、滑动切换替代回复、继续生成、对话分支 |
| **世界书** | CRUD、关键词匹配、selective logic（AND/OR/NOT）、递归深度控制 |
| **记忆系统** | 自动/手动提取、关键词检索、注入 Prompt、管理 UI |
| **正则脚本** | 正则/字面量替换、用户/助手/双向、per-character |
| **视觉体验** | 自定义背景、气泡样式、Material You 动态取色、动画 |
| **导出导入** | Markdown/HTML/TXT/JSON、单对话/批量、SillyTavern JSONL 导入 |
| **用户角色** | 多身份管理、默认角色、per-character 覆盖、Prompt 集成 |
| **作者注释** | 可配置注入深度和位置、per-character |

### 开发中

详见 [ROADMAP.md](ROADMAP.md)。

## 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **架构**: MVVM (ViewModel + StateFlow)
- **DI**: Hilt
- **数据库**: Room (v8)
- **网络**: OkHttp + SSE 流式
- **图片**: Coil
- **Markdown**: Markwon
- **最低 SDK**: 26 (Android 8.0)
- **目标 SDK**: 35

## 构建

```bash
# Debug
./gradlew assembleDebug

# Release
./gradlew assembleRelease

# 测试
./gradlew testDebugUnitTest
```

## 项目结构

```
app/src/main/java/com/tavern/lite/
├── data/
│   ├── db/          # Room 数据库、Entity、DAO
│   ├── model/       # 数据模型
│   └── repository/  # 仓库层
├── di/              # Hilt 依赖注入
├── network/         # API 服务、Prompt 构建
└── ui/
    ├── components/  # 通用组件
    ├── navigation/  # 导航图
    ├── screens/     # 各页面
    └── theme/       # 主题
```

## API 支持

| Provider | 状态 |
|----------|------|
| OpenAI (GPT-4o 等) | ✅ |
| Claude (Anthropic) | ✅ |
| Ollama (本地) | ✅ |
| 自定义 (OpenAI 兼容) | ✅ |

## 致谢

- [SillyTavern](https://github.com/SillyTavern/SillyTavern) — 原始项目
- [SillyTavern 社区](https://docs.sillytavern.app/) — 数据格式规范

## 许可证

MIT License
