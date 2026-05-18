# Tavern Lite (酒馆 Lite)

> **Debug v1.0.1** — This is a debug release for testing. Features and APIs may change. Issues and feedback are welcome!
>
> Contact: yannicksong0106@163.com

A native Android application that reimplements the core features of [SillyTavern](https://github.com/SillyTavern/SillyTavern) as a standalone APK — no server required.

> **Note:** This project is independently developed by a college student as a learning project and is actively maintained. If you have any questions or suggestions, feel free to open an issue or email yannicksong0106@163.com.

基于开源项目 [SillyTavern](https://github.com/SillyTavern/SillyTavern) 的 Android 端重新实现，以独立 APK 形式运行，无需服务端。由一名大学生独立开发，持续迭代中。如有问题或建议，欢迎提 issue 或邮件联系 yannicksong0106@163.com。

---

## Features / 功能特性

### Completed (Phase A-G) / 已完成

| Module / 模块 | Description / 功能 |
|------|------|
| **Character Card / 角色卡** | Create/edit/delete, PNG import/export (tEXt chunk), chara_card_v2/v3 spec |
| **Chat System / 对话系统** | SSE streaming (OpenAI/Claude/Ollama/Custom), edit/delete/regenerate, swipe alternatives, continue generation, branching |
| **World Book / 世界书** | CRUD, keyword matching, selective logic (AND/OR/NOT), recursion depth control |
| **Memory System / 记忆系统** | Auto/manual extraction, keyword retrieval, prompt injection, management UI |
| **Regex Script / 正则脚本** | Regex/literal replacement, user/assistant/both, per-character |
| **Visual / 视觉体验** | Custom backgrounds, bubble styles, Material You dynamic colors, animations |
| **Export/Import / 导出导入** | Markdown/HTML/TXT/JSON, single/batch, SillyTavern JSONL import |
| **User Persona / 用户角色** | Multi-identity, default persona, per-character override, prompt integration |
| **Author's Note / 作者注释** | Configurable injection depth and position, per-character |

### In Progress / 开发中

See [ROADMAP.md](ROADMAP.md) for upcoming features.

## Tech Stack / 技术栈

- **Language / 语言**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Architecture / 架构**: MVVM (ViewModel + StateFlow)
- **DI**: Hilt
- **Database / 数据库**: Room (v9)
- **Network / 网络**: OkHttp + SSE streaming
- **Image / 图片**: Coil
- **Markdown**: Markwon
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 35

## Build / 构建

```bash
# Debug
./gradlew assembleDebug

# Release
./gradlew assembleRelease

# Tests / 测试
./gradlew testDebugUnitTest
```

## Project Structure / 项目结构

```
app/src/main/java/com/tavern/lite/
├── data/
│   ├── db/          # Room database, Entity, DAO
│   ├── model/       # Data models
│   └── repository/  # Repository layer
├── di/              # Hilt dependency injection
├── network/         # API service, Prompt builder
└── ui/
    ├── components/  # Shared components
    ├── navigation/  # Navigation graph
    ├── screens/     # Screens
    └── theme/       # Theme
```

## API Support / API 支持

| Provider | Status |
|----------|--------|
| OpenAI (GPT-4o etc.) | ✅ |
| Claude (Anthropic) | ✅ |
| Ollama (Local) | ✅ |
| Custom (OpenAI-compatible) | ✅ |

## Acknowledgements / 致谢

- [SillyTavern](https://github.com/SillyTavern/SillyTavern) — Original project
- [SillyTavern Community](https://docs.sillytavern.app/) — Data format specifications

## License / 许可证

MIT License
