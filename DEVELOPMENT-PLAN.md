# 酒馆 AI (TavernAndroid) 开发计划

> 更新于 2026-07-12 | 基于 DEV-LOG 深度复盘 + Phase X1-X4 STscript 命令引擎全量落地 + v1.4.0 打 tag（tag 已随 X4 完整体移到 HEAD `62a4caf`）+ 审计工作流 bug 修复三连 + 冷启动 ANR 修复 + 设备 smoke 复验
> 当前状态：v1.3.1 收口加固全量完成并打 tag；v1.4.0 已发布，versionCode 24，`v1.4.0` tag 指向 HEAD `62a4caf`（含 X4 完整体 + 审计修复）；Phase X STscript 命令引擎 X1-X4 已完成（X1 解析器 / X2 内置命令 / X3 宏系统 / X4 编辑 UI），仅 X4 参数级补全与 X5 脚本市场未启动；本轮 `assembleDebug` / `testDebugUnitTest`（~1113 tests）/ `lintDebug` / `detekt`（213 文件 0 code smells）全绿；设备 smoke 已复验 APK 安装启动无 crash、冷启动 +4s / 热启动 +2.4s 无 ANR（验证 `841af3f` 冷启动 runBlocking 修复生效）、首页渲染正常；A0–A7 架构整改与端口-适配器迁移保持已完成；工作树干净，全部已 push 到 origin/main。
> **核心原则：扩展功能永远往后推，重点永远是优化目前版本，确保目前的功能能够使用，并且尽量使这些功能获得更好的体验。**
> **验收原则：没有证据只能写"未验收"，文档勾选不算结果；"通过"只能来自实际命令、测试报告、设备手测或代码证据。**

---

## 一、当前进度总览

### 已完成（历史 Phases）
- Phase 1 稳定性修复 ✅ | Phase 2 ChatViewModel 拆分（974→379 行）✅
- Phase 2.5 PromptBuilder 重构（628→291 行）✅ | Phase 2.6 Repository 修复 ✅
- Phase 3 VN 模式补全 ✅ | Phase 4 UX 润色 ✅
- Phase 5 测试补全（DAO 0→100%）✅ | Phase 6 性能优化 ✅
- Quick Replies / STscript Lite：持久化 + 聊天页 + 管理页 + 自动触发 + 备份恢复 + 多轮收口 ✅

### 已完成（架构整改 A0–A7）
- A0 架构护栏 ✅（UI 禁直依 ChatApiService/DAO，架构测试锁边界）
- A1 数据迁移策略 ✅（移除破坏性 fallback，补 v2-v7 迁移入口）
- A2 聊天生成拆分 ✅（send/continue/regenerate 编排已在 coordinator，manager 365 行为薄胶水）
- A3 reasoning 收口 ✅（GenerationContext 随请求传递）
- A4 Prompt 可解释化 ✅（PromptSection + trace）
- A5 世界书匹配引擎 ✅（WorldBookMatcher + MatchTrace）
- A6 automation id ✅（DB v31→v32）
- A7 配置档案建模 ✅（ApiConfigProfile + 迁移 + SettingsVM 接入）

### 已完成（Phase X STscript 命令引擎 X1-X4，v1.4.0）
- X1 解析器 ✅ | X2 内置命令 ✅（`/delay` `/cancel` `/clearvar` `/if`）
- X3 宏系统 ✅（`/macro` `/call` 单行+多行 block，变量调用时解析，深度上限 16，`/send` 等不安全命令仍走权限/auto-run 检查）
- X4 编辑 UI ✅（命令面板 chip 插入、命令参考弹窗、语法高亮命令/注释/`{{变量}}`/未知分色、行号诊断 `findUnknownCommandLines`、变量引用辅助 `collectStScriptVariableNames`、未知命令预警 `ContainsUnknownCommand`）
- 2026-07-12 审计工作流 bug 修复：QuickReplyValidation 深度上限极性反向（安全门禁误判）、ChatImporter 截断 JSON 孤儿 chat + JsonNull 转字符串、云图片 -1 length 误 drop + InputBar 双重截断、冷启动 runBlocking DataStore ANR、SearchManager 缓存并发安全、图片边界加固；X4 审计 6 finding 全修（三路径未知命令对齐 + 变量扫描加固 + memoize）

### 进行中
- **端口-适配器架构迁移收尾**：domain/usecase 与 UI 主代码均已改为经 domain port 访问 network 实现，并已随 `d24700c` 提交；后续只保留边界回归扫描与必要测试复跑。
- **A8 质量门禁复核**：本轮 `assembleDebug` / `testDebugUnitTest` / `lintDebug` / `detekt` 已全通，`:app:koverXmlReportDebug` 已生成；README 已新增本地开发门禁清单，现有 GitHub Actions CI 已同步覆盖本地 4 条门禁与 Kover；旧库覆盖安装启动、Room 迁移、首页渲染与核心流式对话 smoke 已通过，剩余主交互 smoke 与 push/PR 后真实 CI run 仍待验收。

### 当前风险与待验收
- P0 仓库健康已闭合：`d24700c` 已提交 107 文件改动，HEAD 中 6 个历史 NUL 污染 `.kt` 文件复扫均为 0，`.gitattributes` 已落入 HEAD。
- 设备 smoke 首轮使用 `Tavern_Phone` 保留旧数据覆盖安装，复现并修复 v32→v33 Room schema 校验崩溃；`MIGRATION_32_33` 已补齐 `messages` 重建、`world_book_entries.automation_id` 兜底与 `api_config_profiles` 索引一致性，迁移测试、构建、安装启动、logcat 与截图证据已写入 `DEV-LOG.md`
- 2026-06-30 核心流式对话设备 smoke 已补证：截图 `tmp/m3_fresh_dialog_bottom_visible.png` 同屏可见用户消息 `real_dialog_visible_20260630_025758` 与助手回复 `Smoke reply from local stream at 03:00:55.`；API 日志与设备数据库记录已写入 `DEV-LOG.md`
- P3-1 已降级 done：v2-v7 从未正式发布，真实迁移样本缺失作为历史风险保留，不再阻塞 v1.3.1；若后续拿到 dev build 用户库，再补真实样本迁移测试。
- UI 层残余 network 包直接依赖已清到 0，并由 `ArchitectureBoundaryTest` 锁住回归；本轮全量本地门禁已补跑通过，仍需剩余主交互设备 smoke，并在后续提交前按改动范围复核。
- branch 覆盖率、BgmPlayer 覆盖、UI Compose 测试破零均已补强；README 与现有 CI workflow 已补门禁清单，后续重点回到停止/继续/重生等剩余主交互 smoke、真实 CI run 验证与提交前边界整理。

### 待完成（远期，v1.4.0+）
- X4 后续：参数级补全（`/if` 操作符、`/delay` 单位占位）| X5 脚本市场（未启动）
- W 图像生成增强 | Y 扩展框架 | Z 发布准备

---

## 二、当前计划：v1.3.1 收口加固（优化优先，不向后开发）

> 5 个优先级层，P0 阻塞一切，逐层推进。每步完成必须跑通 P1 的 4 条门禁命令并把证据追加到 DEV-LOG。

### 执行顺序总览

| 步 | 阶段 | 主题 | 预估 | 阻塞 |
|----|------|------|------|------|
| 1 | **P0** | 仓库健康与数据完整性 | ~0.5 天 | 阻塞一切 |
| 2 | **P1** | 质量门禁跑通（A8 收口） | ~0.5–1 天 | 依赖 P0 |
| 3 | **P2** | 架构整改收口 | ~2–3 天 | 依赖 P1 绿 |
| 4 | **P3** | 历史风险与代码卫生 | ~1 天 | 可与 P2 并行 |
| 5 | **P4** | 测试覆盖盲区补强 | ~1–2 天 | 依赖 P1 绿 |

---

### P0 — 仓库健康与数据完整性（已闭合，保持防护）

#### P0-1 提交未完成的端口-适配器迁移 + 审计修复 ✅

**历史问题证据**：`git status` 曾显示多文件改动未提交，对应 A4–A7 收尾 + 审计修复 + 端口适配接线。工作树游离在版本控制外，任何回退/分支操作都会丢失这批架构整改。

**改动范围**（代码已完成，只需验证+提交）：
- `domain/port/` 5 端口接口（ChatApiPort / PromptBuilderPort / WebSearchPort / MemoryExtractorPort / LegacyConfigReaderPort）
- `network/*Adapter` 4 适配器（ChatApiServiceAdapter / PromptBuilderAdapter / WebSearchServiceAdapter + ApiConfigStore 实现 LegacyConfigReaderPort）
- `di/AppModule.kt` 绑定 5 端口
- 8 UseCase + MessageExecutionHelper 改依赖 `*Port` 而非具体实现
- SettingsViewModel / SettingsScreen 接入 profile
- DEV-LOG.md 追加 A4–A7 记录

**验收方式**：
1. `git diff --stat` 确认 24 文件
2. 本地 `.\gradlew.bat assembleDebug` 通过（证明端口适配编译正确）
3. 本地 `.\gradlew.bat testDebugUnitTest` 全绿
4. 提交，commit message 反映 "feat: 端口-适配器架构迁移 + A4-A7 收尾 + 审计修复"
5. DEV-LOG 追加命令输出摘要

**结果**：已随 `d24700c` 提交，commit 为 `feat: v1.3.1 收口加固 — M1-M5 全量完成`，共 107 files changed。

**当前复核（2026-07-01）**：当前 HEAD 为 `d24700c`；`git status --short` 仅剩 `DEV-LOG.md` / `DEVELOPMENT-PLAN.md` 文档同步改动，P0-1 已闭合。

#### P0-2 修复 HEAD 中 6 个 Kotlin 文件的 NUL 字节污染 ✅

**历史问题证据**：提交前全仓库扫描 HEAD 的 322 文件，6 个 .kt 含 NUL 字节（10 个 PNG 的 NUL 是正常二进制，忽略）：

| 文件 | HEAD 大小 | NUL 字节 | 占比 | 工作树状态 |
|------|----------|---------|------|-----------|
| `network/PromptConfig.kt` | 1467 B | 1302 | 88.8% | ✅ 已清理(165B, 0 NUL) |
| `network/PromptBuilder.kt` | 20311 B | 5347 | 26.3% | ✅ 已清理(14964B, 0 NUL) |
| `network/PromptSectionBuilder.kt` | 10875 B | 1333 | 12.3% | ✅ 已清理(9542B, 0 NUL) |
| `network/WebSearchService.kt` | 8422 B | 293 | 3.5% | ✅ 已清理(8129B, 0 NUL) |
| `test/.../PromptSectionBuilderTest.kt` | 5594 B | 4 | 0.1% | ✅ 已清理(5590B, 0 NUL) |
| `domain/usecase/MemoryExtractionUseCase.kt` | 2443 B | 2 | 0.1% | ✅ 已清理(2441B, 0 NUL) |

**影响**：git 把这些文件当二进制，`git diff` / `git log -p` / `git blame` 全部断裂，code review 无法逐行看历史。

**修复结果**：6 文件 NUL-free 版本已随 `d24700c` 落入 HEAD。

**当前复核（2026-07-01）**：对 6 个历史污染文件的 HEAD blob 复扫，NUL 计数均为 0。P0-2 已闭合。

**验收方式**：提交后重新扫描 HEAD 所有 .kt，NUL 计数应为 0：
```powershell
# 验证脚本：对每个 .kt 的 HEAD blob 检查 NUL
git ls-files "app/src/**/*.kt" | ForEach-Object {
  cmd /c "git cat-file -p HEAD:$_ > $env:TEMP\nul.chk 2>nul"
  $b = [System.IO.File]::ReadAllBytes("$env:TEMP\nul.chk")
  $n = ($b | Where-Object { $_ -eq 0 }).Count
  if($n -gt 0){ Write-Output "STILL POLLUTED: $_ ($n NULs)" }
}
# 期望：无输出
```

#### P0-3 加 .gitattributes 防止再次污染 ✅

**历史问题证据**：仓库曾无 `.gitattributes`，无 `working-tree-encoding` 约束，无 pre-commit hook 拦截 NUL。NUL 污染源头不明（疑似某编辑器/工具写入），无防护会复发。

**改动范围**：新增 `.gitattributes`：
```
*.kt     working-tree-encoding=UTF-8 diff=kotlin text eol=lf
*.kts    working-tree-encoding=UTF-8 diff=kotlin text eol=lf
*.md     text eol=lf
*.xml    text eol=lf
*.png    binary
```
可选：pre-commit hook 脚本扫描暂存文件 NUL 字节，命中则拒绝提交。

**验收方式**：`git check-attr -a app/src/main/java/com/tavern/lite/network/PromptConfig.kt` 输出 `text: set` / `diff: kotlin` / `working-tree-encoding: UTF-8`。

**当前复核（2026-06-28）**：`.gitattributes` 已在工作树新增；`git check-attr working-tree-encoding diff text eol -- PromptBuilder.kt ChatViewModel.kt ArchitectureBoundaryTest.kt` 抽查均显示 `working-tree-encoding: UTF-8`、`diff: kotlin`、`text: set`、`eol: lf`。仍需随提交落库。

**当前复核（2026-06-29）**：`.gitattributes` 仍处于未跟踪状态；防护尚未落入 HEAD，不能标为 done。

**当前复核（2026-07-01）**：`.gitattributes` 已随 `d24700c` 落入 HEAD，P0-3 已闭合。

**风险**：`working-tree-encoding=UTF-8` 要求工作树必须是 UTF-8，若将来有编辑器存成 UTF-16 会被 git 拒绝 — 正是我们要的防护。

---

### P1 — 质量门禁跑通（A8 收口）

#### P1-1 解决 Gradle 命令超时

**问题证据**：DEV-LOG 反复出现 "Gradle 命令超时，无法运行测试/编译/lint/detekt"。A8 标"进行中"。当前环境跑不通门禁，等于所有"通过"证据缺失。

**排查方向**（按概率排序）：
1. Gradle daemon 内存不足 — 检查 `gradle.properties` 的 `org.gradle.jvmargs`
2. KSP 增量缓存竞争 — DEV-LOG 多次提到，曾用 `--rerun-tasks` 解决
3. Daemon 端口冲突 — DEV-LOG 提到 "Address already in use"
4. 首次编译下载依赖慢 — 超时阈值过小

**改动范围**：
- `gradle.properties` 调整：`org.gradle.jvmargs=-Xmx4g -XX:+UseParallelGC`、`org.gradle.daemon=true`、`org.gradle.parallel=true`、`org.gradle.caching=true`
- 若 daemon 冲突：`.\gradlew.bat --stop` 后重跑
- 稳定复现超时则用 `--no-daemon` 跑一次确认基线

**验收方式**：本地 PowerShell 跑通以下四条且不超时：
```powershell
.\gradlew.bat --stop
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat detekt
.\gradlew.bat assembleDebug
```
记录实际耗时写入 DEV-LOG 作为 A8 证据。

**当前结果（2026-06-28）**：
- `.\gradlew.bat --stop`：成功，无存活 daemon。
- `.\gradlew.bat assembleDebug --no-daemon --console=plain`：BUILD SUCCESSFUL，约 1m48s。
- `.\gradlew.bat testDebugUnitTest --no-daemon --console=plain`：BUILD SUCCESSFUL，约 2m17s。
- `.\gradlew.bat lintDebug --no-daemon --console=plain`：BUILD SUCCESSFUL，约 4m58s。
- `.\gradlew.bat detekt --no-daemon --console=plain`：BUILD SUCCESSFUL，12s，任务为 UP-TO-DATE；目标检查已记录 211 Kotlin files / 0 code smells。
- `.\gradlew.bat :app:koverXmlReportDebug --no-daemon --console=plain`：BUILD SUCCESSFUL，23s；overall line 76.26%，branch 55.52%。
- 2026-06-29 设备 smoke 首段完成：旧库覆盖安装启动、Room 32→33 迁移、首页渲染通过；logcat 未见 `AndroidRuntime` / `FATAL EXCEPTION` / `ANR in com.tavern.lite` / `IllegalStateException: Migration`，截图证据为 `tmp/smoke_home_after_migration.png`。
- 2026-06-30 核心流式对话设备 smoke 通过：同一聊天屏可见用户消息 `real_dialog_visible_20260630_025758` 与助手回复 `Smoke reply from local stream at 03:00:55.`，截图为 `tmp/m3_fresh_dialog_bottom_visible.png`；API 日志和设备数据库均有对应记录。A8 仍只能标为"本地门禁已通过，设备 smoke partial"，因为停止生成、继续生成、重生、VN/BGM、设置 profile、预设预览尚未验收。

#### P1-2 把门禁固化到流程

**问题证据**：A8 目的是把审计口径固化到开发流程；本地命令已跑通，但提交前命令清单、CI 覆盖确认与设备 smoke 记录仍未完全固化。

**当前复核（2026-06-29/30）**：本轮先把提交边界、暂存/未暂存空白检查与 20 个 CRLF→LF 文件清单固化到 `DEV-LOG.md`；随后在 `README.md` 新增 `Development Gate / 开发门禁`，列出本地 4 条门禁、Kover 刷新、提交前空白检查与设备 smoke 单列验收口径。隐藏路径复查发现现有 `.github/workflows/ci.yml`，因此未新增重复 workflow，而是直接补强现有 CI：build job 已覆盖 `assembleDebug`、`testDebugUnitTest`、`lintDebug`、`detekt`、`:app:koverXmlReportDebug`，并上传 debug APK 与验证报告。`.gitattributes` 已补 `*.yml` / `*.yaml text eol=lf`，本地属性抽查显示 README、workflow、`.gitattributes` 均为 LF。P1-2 的 README + CI 固化已完成；旧库启动/迁移/首页渲染 smoke 与核心流式对话 smoke 已有独立证据，剩余主交互设备 smoke 与 push/PR 后真实 CI run 仍未验收。

**改动范围**：
- 在 `README.md` 写明提交前必跑的 4 条命令
- 复用现有 `.github/workflows/ci.yml`，让 CI 覆盖本地门禁与 Kover 刷新
- DEV-LOG 的 A8 行明确区分"本地门禁已通过"、"旧库启动 + 核心流式对话 smoke 已通过"与"剩余主交互设备 smoke 未验收"，附实际命令输出

**验收方式**：文档存在命令清单 + CI workflow 覆盖本地门禁 + 本地门禁有实际输出证据 + 设备 smoke 有独立验收记录；CI 真实通过需等 push/PR 后以 GitHub Actions run 为证据。

---

### P2 — 架构整改收口

#### P2-1 A2 收尾：提取发送/继续/重生成协调

**问题证据**：DEV-LOG A2 自标"未验收" — "发送/继续/重生成协调仍在 ChatStreamingManager"。A2 已外提 6 coordinator，但主链 send/continue/regenerate 仍在 manager 内。

**改动范围**：
- `ChatStreamingManager.kt`（当前 ~450 行）：把 `sendMessage` / `continueGeneration` / `regenerate` 的编排逻辑下沉到已有的 `GenerationSendCoordinator` / `GenerationContinuationCoordinator`
- Manager 只保留：job 生命周期管理、`streamingMutex` 守卫、状态暴露、对各 coordinator 的委托入口
- 目标：manager < 300 行

**验收方式**：
- `ChatStreamingManagerTest` 全过（行为不变）
- 新 coordinator 单测覆盖
- `(Get-Content ChatStreamingManager.kt).Count < 300`
- DEV-LOG A2 从"未验收"改"done"

**风险**：send/continue/regenerate 是核心主链，拆分必须保证 job 取消、mutex、错误传播行为完全不变。拆完必须跑全量 `testDebugUnitTest` + 真机 smoke。

**当前结论（2026-07-12，实质达成 done）**：审计确认 A2 主链编排**已实质外提**——`sendSingle` / `sendGroup` / `sendDirect` 在 `GenerationSendCoordinator`，`continueGeneration` / `regenerate` 在 `GenerationContinuationCoordinator`，manager 侧仅剩 `launchGenerationJob`（job 生命周期 + mutex + 错误分类，核心不可动）+ 薄委托。本轮 `generateImage` 改为复用 `launchGenerationJob`，消除重复 job 生命周期（`1db1017`，-13 行，378 行）。**剩余行数（378）为状态 provider 声明 + 回调声明 + KDoc + init 配线，非编排逻辑**；削到 <300 只能把声明块物理拆到另一文件，纯凑行数、配线散两处、可读性反降，判定为负收益，不做。P2-1 本来目的（主链移出 manager）已达成，`<300` 物理指标标注"实质达成，剩余为声明/配线"。`ChatStreamingManagerTest` + 全量 `testDebugUnitTest` 绿。

#### P2-2 端口-适配器收尾：清除 domain 对 network 的残余直接依赖

**问题证据**：端口适配迁移已大部分完成（8 UseCase 依赖 Port），但需审计是否有遗漏的 domain → network 直接 import。

**改动范围**：
- 扫描 `app/src/main/java/com/tavern/lite/domain/**/*.kt` 中是否还有 `import com.tavern.lite.network.*`（非 Port）
- 特别检查 `MessageExecutionHelper`、`SendMessageUseCase`、`SummaryUseCase` 是否还直接引用 `ChatApiService` 而非 `ChatApiPort`
- 找到则改为依赖对应 Port

**验收方式**：
```powershell
rg "import com.tavern.lite.network." app/src/main/java/com/tavern/lite/domain
# 期望：0 匹配（或只匹配 Port/Adapter 本身）
```
- 扩展 `ArchitectureBoundaryTest`：禁止 domain 包 import network 实现（只允许 import network.port）

#### P2-3 UI 层残余 network 依赖收敛

**问题证据**：DEV-LOG A0 记录"UI 层仍存在历史性的 ApiConfigStore、ImageGenerationService、PromptBuilder 等 network 包依赖；本轮只锁住直接网络服务和 DAO 入口，后续在 A2/A4/A7 中继续收敛"。

**当前结果（2026-06-28，完成）**：
- `ChatViewModel` / `ChatStreamingManager` / `ProactiveDialogueCoordinator` / `ChatPromptInspectorStateBuilder` 的只读配置路径已从 `ApiConfigStore` 改为 `LegacyConfigReaderPort`。
- `PromptInspectorBuilder` 已从直接调用 `PromptBuilder` object 改为注入 `PromptBuilderPort`；Prompt Inspector section 类型改为 `PromptSectionInfo`。
- `SettingsViewModel` 改为依赖 `ApiConfigStorePort`，保留配置写入与 profile 切换能力。
- 图片生成链路改为依赖 `ImageGenerationPort`；情绪检测链路改为依赖 `EmotionDetectionPort`；预设模板预览改为依赖 `TemplateRendererPort`。
- `ArchitectureBoundaryTest` 已升级为禁止 UI 主代码 import `com.tavern.lite.network.*` 实现与 DAO。
- 当前 domain/UI 主代码 `network` 直接 import 均为 0 匹配。

**改动范围**：
- 扫描 `app/src/main/java/com/tavern/lite/ui/**/*.kt` 的 `import com.tavern.lite.network.*`
- `ApiConfigStore` 已实现 `LegacyConfigReaderPort`，UI 若直接用应改为通过 Port 或 ViewModel
- `ImageGenerationService` 若被 UI 直接注入，改为经 UseCase/Coordinator
- `PromptBuilder`（object）若被 UI 直接调，改为 `PromptBuilderPort`

**验收方式**：
```powershell
rg "import com.tavern.lite.network." app/src/main/java/com/tavern/lite/ui
# 期望：0 匹配
```
- `ArchitectureBoundaryTest` 加规则：ui 包禁止 import network 实现与 DAO。
- P2-3 目标测试通过：`ArchitectureBoundaryTest`、`ChatViewModelTest`、`PromptInspectorTest`、`ChatStreamingManagerTest`、`ImageGenerationCoordinatorTest`、`ProactiveDialogueCoordinatorTest`、`VnModeManagerTest`、`PresetTemplatePreviewTest`、`PresetViewModelTest`、`SettingsViewModelTest`。

**剩余风险**：P2-3 本身已完成，且本轮全量本地门禁已通过；旧库启动与核心流式对话 smoke 已通过，停止生成、继续生成、重生、VN/BGM、设置 profile、预设预览仍需在提交前补做。

---

### P3 — 历史风险与代码卫生

#### P3-1 v2-v7 真实迁移样本补齐（或明确降级）— 降级决策 ✅

**问题证据**：DEV-LOG A1 自标"未验收" — "仓库只有 21.json 到 31.json，没有 v2-v7 Room schema，也没有真实旧库样本；当前只验证代表性旧 schema"。

**决策**：路径 B（降级）。`git tag -l` 显示最早正式发布为 `v1.0.0-beta1`（DB v8），v2-v7 从未正式发布（DEV-LOG 2026-05-23 已说明）。当前迁移链已覆盖 v2→v8 到 v33，代表性 schema 验收已足够。风险登记：若将来有用户使用未发布的 v2-v7 dev build，需补真实样本。

**验收方式**：风险清单更新，P3-1 标为降级 done。

#### P3-2 Lint 警告清理（32 → <15）

**问题证据**：DEV-LOG 2026-06-04 记录剩余 32 警告：PluralsCandidate(11) / IconLauncherShape(10) / UseTomlInstead(3) / MonochromeLauncherIcon(2) / IconDipSize(2) / UnusedAttribute(1) / ObsoleteSdkInt(1) / IconDuplicates(1) / DataExtractionRules(1)。

**改动范围**（按性价比）：
- PluralsCandidate(11)：`<string>` 改 `<plurals>`，i18n 最佳实践
- UseTomlInstead(3)：依赖迁到 `libs.versions.toml`（项目已用 TOML，补齐剩余 3 处）
- IconLauncherShape(10) + MonochromeLauncherIcon(2) + IconDipSize(2) + IconDuplicates(1)：图标资源规范化，统一生成
- 其余 4 个低优先级可留

**验收方式**：`.\gradlew.bat lintDebug` 警告数 < 15。

#### P3-3 模拟器宿主窗口可视位置修复

**问题证据**：DEV-LOG 多次提到 `Tavern_Phone` 模拟器宿主窗口落在 `originY=-720`，Computer Use 可捕获但用户侧不可见，影响 smoke 测试可信度。

**改动范围**：排查 Android Emulator/Qt 窗口状态，`start-emulator.ps1` 加窗口位置校正逻辑（或文档化为已知问题）。

**验收方式**：smoke 时模拟器窗口在可见区域。

#### P3-4 .gitignore 与已提交资源的一致性

**问题证据**：`.gitignore` 忽略 `schemas/`、`screen.png`、`smoke_seed.sql`、`start-emulator.*`、`deploy.ps1`，但工作区根目录仍有这些文件。需确认没有"忽略了但已入库"的矛盾。

**改动范围**：
- `git ls-files` 对比 `.gitignore`，确认无被忽略文件仍在版本控制
- 根目录的 `interactive-git-tutorial*.ps1`、`simple-git-setup.ps1`、`SIMPLE-GIT-GUIDE-CHINESE.md`、`setup.bat` 等临时脚本是否需要保留，建议移入 `docs/` 或删除

**验收方式**：`git check-ignore $(git ls-files)` 无输出（无被忽略的已跟踪文件）。

---

### P4 — 测试覆盖盲区补强

#### P4-1 branch 覆盖率热点补测（42.85% → 55%+）✅

**问题证据**：Kover 基线 business line 70.19% / branch 42.85%。DEVELOPMENT-PLAN 旧版已明确"按热点补分支测试"。下一轮热点：`ChatRepository`、`WorldBookRepository`、`MemoryExtractorService`、`BgmPlayer`。

**改动范围**：跑 `:app:koverHtmlReportDebug`，打开 HTML 报告按 branch 覆盖率升序，补前 5 个热点文件的边界分支测试。

**验收方式**：`:app:koverXmlReportDebug` 后 branch 覆盖率 ≥ 55%。

**当前结果**：2026-06-28 `:app:koverXmlReportDebug` 显示 overall line 76.26%、branch 55.52%，目标达成。

#### P4-2 BgmPlayer 单测（0% → 70%+）✅

**问题证据**：旧 DEVELOPMENT-PLAN 列 "BgmPlayer 当前可行动覆盖为 0%，需覆盖空路径、切歌、音量、释放和失败回退"。依赖 Android MediaPlayer，需 Robolectric。

**改动范围**：`BgmPlayerTest.kt` 用 Robolectric + mock MediaPlayer，覆盖空路径播放、切歌、音量、释放、AudioFocus 丢失回退。

**验收方式**：`testDebugUnitTest --tests *BgmPlayerTest` 通过，Kover 显示 BgmPlayer line ≥ 70%。

**当前结果**：新增 `BgmPlayerTest` 8 tests；BgmPlayer class line 86.96%，branch 59.02%。

#### P4-3 UI Compose 测试破零 ✅

**问题证据**：审计报告"UI Compose 0%"。无 `createComposeRule()` 测试。

**改动范围**：从最关键的两个纯 UI 组件起步 — `QuickReplyBar`、`MessageBubble` 的渲染测试（不依赖 ViewModel）。

**验收方式**：至少 2 个 Compose 测试通过。

**当前结果**：新增 `ChatComposeComponentsTest` 3 tests，覆盖 `QuickReplyBar`、`QuickReplyConfirmDialog`、`MessageBubble`；`testDebugUnitTest --tests com.tavern.lite.ui.screens.chat.components.ChatComposeComponentsTest` 通过。Kover 当前仍排除 UI composable `*Kt*`，因此本项以 Compose 测试通过作为验收证据。

---

## 三、已完成 Phases（历史记录）

### Phase 1：稳定性修复 (v1.2.9) ✅
1.1 DB Migration 测试 | 1.2 Backup 版本校验 | 1.3 SSE 断线重连 | 1.4 API 限流退避 | 1.5 WebSearch 测试修复 | 1.6 reasoningContent 并发安全

### Phase 2：ChatViewModel 拆分 (v1.2.9) ✅
2.1 ChatStreamingManager | 2.2 GroupChatManager（合并至 2.1）| 2.3 BranchManager | 2.4 VnModeManager | 2.5 ViewModel 瘦身 | 2.6 ChatScreen 子组件拆分
结果：ChatViewModel 974→379 行，ChatScreen 718→553 行

### Phase 2.5：PromptBuilder 重构 ✅
628→291 行，测试 16→33

### Phase 2.6：Repository 修复 ✅
BgmRepository 语义错误 + ScriptRepository 线程安全 + 34 测试

### Phase 3：VN 模式补全 (v1.3.0) ✅
3.1 VnScreen 输入框 | 3.2 BGM 播放器 | 3.3 EmotionDetector 三层检测 | 3.4 VN 入口优化 | 3.5 VN 测试

### Phase 4：UX 润色 (v1.3.0) ✅
4.1 消息分页 | 4.2 搜索失败提示 | 4.3 图像生成多 provider | 4.4 世界书匹配高亮 | 4.5 预设模板预览 | 4.6 LaunchedEffect 优化 | 4.7 API 超时可配置

### Phase 5：测试补全 (v1.3.1) ✅
5.1 DAO 测试（33 tests，0→100%）| 5.2 Migration 测试 | 5.3 VN 测试 | 5.4 Integration 测试 | 5.5 Backup 测试
Kover 基线：业务代码 line 70.19% / branch 42.85%

### Phase 6：性能优化 (v1.3.1) ✅
6.1 Room 查询优化（v30 复合索引）| 6.2 图片内存池（Coil 20% 内存 + 3% 磁盘）| 6.3 大数据量验证（1200 消息 <5s）| 6.4 长 prompt 验证（150 历史 <1s）

### 架构整改 A0–A7 ✅（详见 DEV-LOG）
A0 护栏 | A1 迁移策略 | A2 生成拆分（✅ send/continue/regenerate 已在 coordinator，P2-1 实质达成）| A3 reasoning 收口 | A4 Prompt 可解释化 | A5 世界书匹配引擎 | A6 automation id | A7 配置档案建模

---

## 四、里程碑规划

| 版本 | 内容 | 状态 |
|------|------|------|
| v1.2.9 | Phase 1 + 2（稳定性 + 架构优化）| ✅ |
| v1.3.0 | Phase 3 + 4（VN 补全 + UX 润色）| ✅ |
| v1.3.1 | Phase 5 + 6 + Quick Replies + A0-A7 + **P0-P4 收口加固** | 🔄 进行中 |
| v1.4.0+ | Phase W/X/Y/Z（新功能）| 远期，待 v1.3.1 全部 done 后推进 |

### v1.3.1 收口加固里程碑细分

| 子里程碑 | 包含任务 | 完成标志 |
|---------|---------|---------|
| **M1 仓库健康** | P0-1 + P0-2 + P0-3 | 24 文件已提交 + HEAD 无 NUL + .gitattributes 生效 |
| **M2 门禁跑通** | P1-1 + P1-2 | 4 条 Gradle 命令本地跑通 + DEV-LOG 证据齐全；旧库启动与核心流式对话 smoke 已记录，剩余主交互 smoke 单列验收 |
| **M3 架构收口** | P2-1 + P2-2 + P2-3 | manager <300 行 + domain/ui 无 network 实现直接依赖 |
| **M4 代码卫生** | P3-1 + P3-2 + P3-3 + P3-4 | lint <15 + 迁移样本决策 + gitignore 一致 |
| **M5 测试补强** | P4-1 + P4-2 + P4-3 | branch ≥55% + BgmPlayer ≥70% + Compose 测试破零 |

---

## 五、技术债务清单

| 债务 | 严重度 | 归属 | 状态 |
|------|--------|------|------|
| ~~ChatViewModel 974 行~~ | ~~高~~ | Phase 2 | ✅ 已减至 379 行 |
| ~~ChatScreen 718 行~~ | ~~中~~ | Phase 2.6 | ✅ 已减至 553 行 |
| ~~PromptBuilder 628 行~~ | ~~高~~ | Phase 2.5 | ✅ 已减至 291 行 |
| ~~BgmRepository 语义错误~~ | ~~高~~ | Phase 2.6 | ✅ 已修复 |
| ~~ScriptRepository 线程不安全~~ | ~~中~~ | Phase 2.6 | ✅ 已修复 |
| ~~BGM 无播放器~~ | ~~中~~ | Phase 3 | ✅ BgmPlayer |
| ~~EmotionDetector 纯关键词~~ | ~~低~~ | Phase 3 | ✅ 三层检测 |
| ~~DAO 零测试~~ | ~~中~~ | Phase 5 | ✅ 33 测试全覆盖 |
| ~~DB Migration 零测试~~ | ~~高~~ | Phase 1 | ✅ 21 迁移全覆盖 |
| ~~SSE 无重连~~ | ~~中~~ | Phase 1 | ✅ 指数退避 |
| ~~reasoningContent 并发风险~~ | ~~中~~ | Phase 1 | ✅ per-request 存储 |
| ~~CryptoHelper 零测试~~ | ~~高~~ | Phase 5 | ✅ 已补 |
| ~~ChatApiService 零测试~~ | ~~高~~ | Phase 5 | ✅ 已补 |
| ~~图像生成仅 DALL-E~~ | ~~低~~ | Phase 4 | ✅ 已支持 Gemini |
| ~~搜索失败无提示~~ | ~~低~~ | Phase 4 | ✅ 已补 |
| ~~HEAD 中 6 .kt 文件 NUL 污染~~ | ~~高~~ | P0-2 | ✅ 已随 `d24700c` 清到 0 |
| ~~多文件架构整改未提交~~ | ~~高~~ | P0-1 | ✅ 已随 `d24700c` 提交 |
| ~~仓库 .gitattributes 防护~~ | ~~中~~ | P0-3 | ✅ 已落入 HEAD |
| **A8 设备 smoke partial** | 高 | P1/M3 | 🟡 本地 4 条门禁已全通，README + CI 已固化流程；旧库启动/迁移/首页渲染与核心流式对话已通过，剩余主交互 smoke 与真实 CI run 待验收 |
| ~~A2 send/continue/regenerate 未外提~~ | ~~中~~ | P2-1 | ✅ 实质达成——编排已在 coordinator，manager 仅剩 job 胶水；<300 物理目标放弃（硬拆声明块为负收益） |
| **domain 残余 network 直接依赖** | 中 | P2-2 | ✅ 当前扫描 0 匹配 |
| **UI 残余 network 直接依赖** | 中 | P2-3 | ✅ 当前扫描 0 匹配，架构测试已锁住 |
| **v2-v7 真实迁移样本缺失** | 中 | P3-1 | ✅ 降级 done；真实样本作为历史风险保留 |
| **Lint 警告** | 低 | P3-2 | ✅ 已清到 14（<15） |
| **模拟器窗口位置异常** | 低 | P3-3 | ⚠️ 已知问题 |
| ~~branch 覆盖率 42.85%~~ | ~~中~~ | P4-1 | ✅ 已提升到 55.52% |
| ~~BgmPlayer 0% 覆盖~~ | ~~中~~ | P4-2 | ✅ line 86.96% |
| ~~UI Compose 测试 0~~ | ~~低~~ | P4-3 | ✅ 3 个 JVM Compose tests |

---

## 六、质量指标目标

| 指标 | v1.2.8 基线 | 当前 | v1.3.1 目标 | 责任任务 |
|------|------------|------|-----------|----------|
| 测试数量 | 762 | 1033+ | 900+ ✅ | P4 |
| ChatViewModel 行数 | 974 | 379 | <400 ✅ | — |
| ChatScreen 行数 | 718 | 553 | <500 | P2 后再评估 |
| ChatStreamingManager 行数 | — | 378 | <300 放弃 | P2-1 实质达成，剩余为声明/配线 |
| PromptBuilder 行数 | 628 | 291 | <400 ✅ | — |
| 测试覆盖率 line（Kover 业务）| 35.8% 估算 | 76.78% | 72%+ ✅ | P4-1 |
| 测试覆盖率 branch | — | 55.81% | 55%+ ✅ | P4-1 |
| DAO 测试覆盖 | 0% | 100% | 100% ✅ | — |
| DB Migration 测试 | 0 | v33 链；P3-1 降级 done | 全部 | P3-1 风险保留 |
| BgmPlayer 覆盖 | 0% | line 86.96% / branch 59.02% | 70%+ ✅ | P4-2 |
| UI Compose 测试 | 0 | 3 | ≥2 tests ✅ | P4-3 |
| Lint 错误 | 5 | 0 | 0 ✅ | — |
| Lint 警告 | 103 | 14 | <15 ✅ | P3-2 |
| HEAD 中 NUL 污染文件 | — | 0 ✅ | 0 | P0-2 |
| `.gitattributes` 防护 | 无 | HEAD 已生效 ✅ | 有 | P0-3 |
| domain→network 直接依赖 | 多处 | 0 ✅ | 0 | P2-2 |
| ui→network 实现直接依赖 | 多处 | 0 ✅ | 0 | P2-3 |
| Gradle 门禁本地跑通 | — | 4 条全通 ✅ | 4 条全通 | P1-1 |
| 设备 smoke | — | 旧库启动/迁移/首页渲染 + 核心流式对话 partial | 主路径通过 | P1/M3 |

---

## 七、下一步行动（优先级排序）

1. **M3 剩余设备 smoke** — 旧库覆盖安装启动、Room 迁移、首页渲染与一次真实可见流式对话已通过；下一步手动验证停止生成、继续生成、重生、VN/BGM、设置 profile、预设预览等剩余主路径
2. **M2 真实 CI run 验证**（P1-2）— 本地 4 条门禁已通过，README 与现有 CI workflow 已补命令清单；后续等 push/PR 验证真实 CI run，并在提交前按改动范围重跑必要门禁
3. **M4 剩余代码卫生**（P3-3）— 模拟器窗口问题处理；P3-1 已降级 done，真实 v2-v7 样本作为历史风险保留
4. **M5 v1.3.1 正式收口** — 全部 done 后提交/tag，再评估 v1.4.0

> P2-1 已实质达成 done（编排在 coordinator，manager 仅剩 job 胶水；<300 物理目标放弃，硬拆声明块为负收益）。

### 每步完成必做
- 跑 P1 的 4 条门禁命令
- 把证据（命令 + 输出摘要 + 行数/覆盖率数字）追加到 `DEV-LOG.md`
- 未跑通不许标 done；未验收项明确写"未验收"并登记风险

---

*本计划随开发进度实时更新。每完成一个任务，在此文档中标记状态并在 DEV-LOG.md 记录实际结果与证据。*
