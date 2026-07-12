# Tavern Lite (酒馆 Lite)

> **v1.4.0** — SillyTavern 安卓原生客户端，持续迭代中。
>
> Contact: yannicksong0106@163.com

基于开源项目 [SillyTavern](https://github.com/SillyTavern/SillyTavern) 的 Android 端重新实现，以独立 APK 形式运行，无需服务端。由一名大学生独立开发，持续迭代中。如有问题或建议，欢迎提 issue 或邮件联系。

A native Android reimplementation of [SillyTavern](https://github.com/SillyTavern/SillyTavern) — standalone APK, no server required.

---

## Features / 功能特性

| Module / 模块 | Description / 功能 |
|------|------|
| **角色卡** | 创建/编辑/删除，PNG 导入导出，chara_card_v2 spec |
| **对话系统** | SSE 流式传输 (OpenAI/Claude/Ollama/自定义)，编辑/删除/重新生成/继续生成，聊天分支 |
| **三级预设** | Chat > Character > Global 层级合并，scope 筛选 Tab |
| **世界书** | CRUD，关键词匹配，选择逻辑 (AND/OR/NOT)，递归深度控制 |
| **记忆系统** | 自动/手动提取，关键词检索，Prompt 注入，MemoryAtom 结构化存储 |
| **正则脚本** | 正则/字面替换，用户/助手/双向，按角色 |
| **群聊** | 多角色群聊，健谈度控制，主动发言调度 |
| **TTS 语音** | Android 原生 TTS，语速/音调调节，自动语言检测 |
| **LaTeX** | `$...$` 行内 / `$$...$$` 块级数学公式渲染 |
| **分支/书签** | 聊天分支创建/切换，消息书签 |
| **导出导入** | Markdown/HTML/TXT/JSON，单条/批量，SillyTavern JSONL 导入 |
| **用户角色** | 多身份，默认角色，按角色覆盖 |
| **作者注释** | 可配置注入深度和位置 |
| **视觉体验** | 自定义背景，气泡样式，Material You 动态颜色 |

---

## Tech Stack / 技术栈

- **Language**: Kotlin 2.1.0
- **UI**: Jetpack Compose + Material 3
- **Architecture**: MVVM + UseCase + Repository + Hilt DI
- **Database**: Room (v21)
- **Network**: OkHttp + SSE streaming
- **Image**: Coil
- **Markdown**: Markwon (LaTeX + HTML + Strikethrough)
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 35

## Build / 构建

```bash
# Debug
./gradlew assembleDebug

# Release
./gradlew assembleRelease

# Tests
./gradlew testDebugUnitTest
```

## Development Gate / 开发门禁

Before committing v1.3.x stabilization work, run the local gate from the repository root and record real evidence in `DEV-LOG.md`.
GitHub Actions mirrors this gate in `.github/workflows/ci.yml` for pushes to `main` and pull requests targeting `main`.

PowerShell on Windows:

```powershell
.\gradlew.bat assembleDebug --no-daemon --console=plain
.\gradlew.bat testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat lintDebug --no-daemon --console=plain
.\gradlew.bat detekt --no-daemon --console=plain
```

For coverage-sensitive changes, also refresh Kover:

```powershell
.\gradlew.bat :app:koverXmlReportDebug --no-daemon --console=plain
```

Before staging or committing, check repository hygiene:

```powershell
git diff --cached --check
git diff --check
```

Manual device smoke is tracked separately in `DEV-LOG.md`; do not mark streaming chat, stop/continue/regenerate, VN/BGM, settings profile, or preset preview as verified without a real device or emulator pass.

## API Support / API 支持

| Provider | Status |
|----------|--------|
| OpenAI (GPT-4o etc.) | ✅ |
| Claude (Anthropic) | ✅ |
| Ollama (Local) | ✅ |
| Custom (OpenAI-compatible) | ✅ |
| OpenRouter | ✅ |

## Download / 下载

[GitHub Releases](https://github.com/yannicksong0106/tavern-android/releases) — 下载最新版 APK 安装即可。

## Acknowledgements / 致谢

- [SillyTavern](https://github.com/SillyTavern/SillyTavern) — Original project
- [SillyTavern Community](https://docs.sillytavern.app/) — Data format specifications

## License / 许可证

MIT License
