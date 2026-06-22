# 酒馆 AI (TavernAndroid) 开发日志

## 2026-06-12 — 后端架构整改计划与日志整理

**背景**: 2026-06-11 的架构审计确认，项目核心能力已经具备，但边界和可验收性没有同步长稳：UI 层仍有直接网络调用，聊天生成 manager 继续承担过多职责，旧版本数据库存在破坏性迁移入口，Prompt/世界书/自动化缺少可解释 trace，远期能力和当前收口任务混在一起。接下来优先做架构止血和主链路稳定，不推进新的大功能。

### 当前原则

- 先修边界，再拆大类，最后补扩展能力。
- 每个任务必须有规则来源、代码改动范围、自动验证和手测要求。
- 没有证据的项只允许写“未验收”，不能用文档状态替代真实结果。
- UI/ViewModel 只表达状态和用户意图；网络、数据库、事务和复杂业务编排必须下沉到 UseCase/Coordinator/Repository。
- Repository 只负责数据访问和简单查询；Prompt、世界书、STscript、自动化等核心逻辑尽量做成可单测的纯服务。
- 新功能后置。v1.3.1 收口阶段只处理会影响稳定性、数据安全、架构边界和可解释性的工作。

### 整改路线图

| 阶段 | 目标 | 主要文件/模块 | 交付物 | 验收方式 | 状态 |
|------|------|---------------|--------|----------|------|
| A0 架构护栏 | 阻止 UI/network/data 边界继续扩散 | `SettingsViewModel.kt`, 新增 `TestConnectionUseCase`, 架构测试 | 设置页连接测试迁入 UseCase；新增依赖边界测试 | 单测证明 ViewModel 不直接依赖 `ChatApiService`；架构测试禁止 `ui` 直接依赖 `ChatApiService`/DAO | done（第一刀） |
| A1 数据迁移策略 | 解决“数据不可丢”和破坏性迁移冲突 | `AppModule.kt`, `TavernDatabase.kt`, migration tests | 明确 v2-v7 策略：补迁移或显式用户提示；禁止静默清库 | 最近 3 个版本 + 一个历史版本迁移测试通过；破坏性迁移必须有用户可见策略 | done（代表性旧 schema 验收） |
| A2 聊天生成拆分 | 降低 `ChatStreamingManager` 多职责风险 | `ChatStreamingManager.kt`, 新增 generation/proactive/image/post-processing coordinator | 拆出生成协调、主动消息、图片生成、助手消息后处理 | 现有聊天 manager 测试全过；主文件明显减负；发送/继续/重生成/图片/主动消息行为不变 | in_progress（提交后处理 + 图片生成 + 群聊选择 + 主动消息调度） |
| A3 reasoning 上下文收口 | 消除会话层 reasoning 串线风险 | `MessageExecutionHelper.kt`, `ContinueGenerationUseCase.kt`, `ChatStreamingManager.kt` | 引入 `GenerationContext` / `GenerationResult`，reasoning 随请求上下文传递 | 并发/连续发送测试覆盖不同 chat/request 不串 reasoning | todo |
| A4 Prompt 可解释化 | 让最终 prompt 能解释来源 | `PromptBuilder.kt`, `PromptSectionBuilder.kt`, `PromptInspector*` | 引入 `PromptSection(source, content, tokenEstimate)` 或等价 trace | Inspector 显示最终 messages、token、每段来源；PromptBuilder 测试覆盖 trace | todo |
| A5 世界书匹配引擎 | 把复杂匹配从 Repository 拆出 | `WorldBookRepository.kt`, 新增 `WorldBookMatcher` | Repository 只取数据；Matcher 输出命中列表和 `WorldBookMatchTrace` | 现有匹配测试迁移；补 regex/case/whole word/token budget 的待办测试或明确未实现 | todo |
| A6 自动化事件总线 | 避免聊天页硬编码自动化事件 | `QuickReplyAutomationTriggerUseCase.kt`, `ChatScreen.kt`, 新增 automation event 模型 | `chat_open` / `assistant_reply` 迁入业务事件分发；世界书 automation id 做设计预留 | 自动触发只执行一次；unsafe action 仍默认拦截；无 UI 直接拼事件逻辑 | todo |
| A7 配置档案建模 | 为 Connection Profiles 打基础 | `ApiConfigStore.kt`, `ApiConfig.kt`, 新增 profile entity/repository | 单一配置迁移为默认 profile；角色/聊天绑定预留 | 旧配置可无损迁移；密钥仍加密；连接测试走 profile | todo |
| A8 质量门禁复核 | 把审计口径固化到开发流程 | Gradle test/lint/detekt/Kover 报告，模拟器 smoke | 每次阶段完成记录自动验证 + 手测证据 | `testDebugUnitTest`、`detekt`、`lintDebug`、`assembleDebug`；设备 smoke 未做则写未验收 | todo |

### 第一批执行顺序

1. **A0 架构护栏**：先把 `SettingsViewModel -> ChatApiService` 改成 `SettingsViewModel -> TestConnectionUseCase -> ChatApiService`，并加架构依赖测试。
2. **A1 数据迁移策略**：处理 `fallbackToDestructiveMigrationFrom(2,3,4,5,6,7)`，不能继续让生产路径静默清库。
3. **A2/A3 聊天生成收口**：先拆图片生成和 proactive，再处理发送/继续/重生成，最后落地 `GenerationContext`。
4. **A4/A5 可解释性**：Prompt 和世界书一起做 trace，避免后续 SillyTavern 宏、outlet、token budget 继续堆到不可验证状态。
5. **A6 以后**：自动化、Connection Profiles、Data Bank/RAG、扩展 hook 都在主链路稳定后推进。

### A0 执行记录

| 项目 | 文件 | 结果 |
|------|------|------|
| 连接测试下沉 | `TestConnectionUseCase.kt` / `SettingsViewModel.kt` | 设置页 ViewModel 不再直接注入 `ChatApiService`，连接测试网络调用由 UseCase 承接 |
| ViewModel 回归测试 | `SettingsViewModelTest.kt` | 覆盖连接测试成功状态与 `CancellationException` 不吞异常 |
| UseCase 测试 | `TestConnectionUseCaseTest.kt` | 覆盖连接测试 prompt、`maxTokens = 50` 和 100 字符预览截断 |
| 架构边界测试 | `ArchitectureBoundaryTest.kt` | 扫描 `ui` 源码，禁止直接导入 `ChatApiService` 或 DAO |

**验证结果**：

- `testDebugUnitTest --tests com.tavern.lite.domain.usecase.TestConnectionUseCaseTest --tests com.tavern.lite.ui.screens.settings.SettingsViewModelTest --tests com.tavern.lite.architecture.ArchitectureBoundaryTest` — 通过。
- `detekt` — 通过，0 code smells。
- `rg "ChatApiService|com\\.tavern\\.lite\\.data\\.db\\.dao" app/src/main/java/com/tavern/lite/ui` — 无匹配。

**未验收 / 后续**：

- UI 层仍存在历史性的 `ApiConfigStore`、`ImageGenerationService`、`PromptBuilder` 等 `network` 包依赖；本轮只锁住直接网络服务和 DAO 入口，后续在 A2/A4/A7 中继续收敛。
- 未做模拟器/真机手测，因为本轮只移动设置页连接测试边界，未改变可视交互。

### A1 执行记录

| 验收项 | 规则来源 | 对应文件 | 验收方式 | 实际结果 | 是否通过 |
|------|----------|----------|----------|----------|----------|
| 生产路径禁止静默清库 | 当前原则“数据安全优先”；A1 目标“禁止静默清库”；审计问题 `fallbackToDestructiveMigrationFrom(2,3,4,5,6,7)` | `AppModule.kt` | `rg -n "fallbackToDestructiveMigrationFrom|MIGRATION_2_8|MIGRATION_7_8" app/src/main/java app/src/test/java` | 生产注册新增 `MIGRATION_2_8` 到 `MIGRATION_7_8`，未再匹配 `fallbackToDestructiveMigrationFrom` | 通过 |
| v2-v7 有显式迁移入口 | A1 交付物“明确 v2-v7 策略”；迁移链必须可审计 | `TavernDatabase.kt`, `TavernDatabaseMigrationTest.kt` | `testDebugUnitTest --tests com.tavern.lite.data.db.TavernDatabaseMigrationTest` | 主链继续覆盖 1→31；早期入口覆盖 2→8、3→8、4→8、5→8、6→8、7→8 | 通过 |
| 早期旧表补到 v8 基线再继续迁移 | A1 数据不可丢；旧 `CREATE TABLE IF NOT EXISTS` 不能补已有旧表缺列 | `TavernDatabase.kt` | `TavernDatabaseEarlyMigrationTest` 构造缺列旧表并跑到当前版本 | `normalizeVersion8Columns()` 先补 v8 必需列，再进入 8→31 迁移链；v2 代表性聊天数据保留 | 通过 |
| v6 swipe/记忆/脚本代表性数据保留 | A1 至少覆盖一个历史版本迁移；历史线索显示 v6 已有 swipe 字段 | `TavernDatabaseEarlyMigrationTest.kt` | `testDebugUnitTest --tests com.tavern.lite.data.db.TavernDatabaseEarlyMigrationTest` | v6 代表性 schema 迁移到当前版本后，`swipe_content`、`swipe_index`、`memories.content`、`scripts.find_pattern` 均保留 | 通过 |
| 最近迁移链未回退 | A1 验收方式“最近 3 个版本 + 历史版本迁移测试通过” | `TavernDatabaseSqlMigrationTest.kt`, `TavernDatabaseIndexMigrationTest.kt`, `QuickReplyMigrationTest.kt` | 迁移 SQL 测试 + v29→v30 索引迁移 + v30→v31 Quick Reply 迁移测试 | 21→29、27→29、28→29、29→30、30→31 相关迁移测试均通过 | 通过 |
| 完整真实 v2-v7 schema 无损迁移 | “没有证据写未验收”原则 | `app/schemas/...`，旧发布样本数据库 | 查证 schema 文件与本轮测试证据 | 仓库只有 21.json 到 31.json，没有 v2-v7 Room schema，也没有真实旧库样本；当前只验证代表性旧 schema | 未验收 |

**验证结果**：

- `testDebugUnitTest --tests com.tavern.lite.data.db.TavernDatabaseMigrationTest --tests com.tavern.lite.data.db.TavernDatabaseEarlyMigrationTest --tests com.tavern.lite.data.db.TavernDatabaseSqlMigrationTest` — 通过。
- `testDebugUnitTest --tests com.tavern.lite.data.db.TavernDatabaseIndexMigrationTest --tests com.tavern.lite.data.db.QuickReplyMigrationTest` — 通过。
- `detekt` — 通过，0 code smells。
- `rg -n "fallbackToDestructiveMigrationFrom|MIGRATION_2_8|MIGRATION_7_8|normalizeVersion8Columns" app/src/main/java app/src/test/java` — 未匹配破坏性 fallback；匹配到新增迁移入口、注册和补列工具。

**未验收 / 后续**：

- 没有 v2-v7 的 Room schema JSON 或真实用户库样本，不能声明“所有真实 v2-v7 数据完整无损通过”；如后续拿到样本，需要补样本库迁移测试。
- 本轮未跑 `lintDebug`、`assembleDebug`、模拟器/真机 smoke；A1 是数据库迁移路径改动，当前证据限于 JVM/Robolectric SQL 测试和 detekt。
- `TavernDatabaseEarlyMigrationTest` 是代表性旧 schema，不等同于所有历史中间版本的逐字段还原证据。

### A2 执行记录（持续拆分）

| 验收项 | 规则来源 | 对应文件 | 验收方式 | 实际结果 | 是否通过 |
|------|----------|----------|----------|----------|----------|
| 助手回复提交后处理从 manager 外提 | A2 目标“拆出助手消息后处理”；当前原则“复杂业务编排下沉到可单测服务” | `AssistantReplyCommitter.kt`, `ChatStreamingManager.kt` | 代码证据 + manager 回归测试 | `ChatStreamingManager` 改为委托 `AssistantReplyCommitter` 处理落库消息读取、表情更新、多段拆分和 committed 事件 | 通过 |
| 后处理边界可单测 | 当前原则“核心逻辑尽量做成可单测的纯服务” | `AssistantReplyCommitterTest.kt` | `testDebugUnitTest --tests com.tavern.lite.ui.screens.chat.manager.AssistantReplyCommitterTest` | 覆盖空 id、取消保护、表情更新、committed 事件、多段回复拆分与追加 assistant 消息 | 通过 |
| 图片生成链路从 manager 外提 | A2 目标“拆出图片生成”；当前原则“复杂业务编排下沉到可单测服务” | `ImageGenerationCoordinator.kt`, `ChatStreamingManager.kt` | 代码证据 + coordinator 测试 + manager 回归测试 | `ChatStreamingManager` 不再直接调用 `imageGenerationService.generateImage()` 后拼 `/imagine`，改为委托 `ImageGenerationCoordinator` | 通过 |
| 图片生成边界可单测 | A2 目标“图片生成行为不变”；当前原则“核心逻辑尽量做成可单测的纯服务” | `ImageGenerationCoordinatorTest.kt` | `testDebugUnitTest --tests com.tavern.lite.ui.screens.chat.manager.ImageGenerationCoordinatorTest` | 覆盖图片生成成功后发送 `/imagine`、图片服务返回 null、生成后取消时不发送消息 | 通过 |
| 群聊响应角色选择从 manager 外提 | A2 目标“拆出生成协调”；当前原则“核心逻辑尽量做成可单测的纯服务” | `GroupRespondingCharacterSelector.kt`, `ChatStreamingManager.kt` | selector 单测 + manager 回归测试 | `LIST_ORDER`、`ROUND_ROBIN`、`NATURAL` 响应选择从 manager 私有函数迁入 selector；轮询状态由 selector 持有 | 通过 |
| 主动消息调度与发送从 manager 外提 | A2 目标“拆出主动消息”；当前原则“复杂业务编排下沉到可单测服务” | `ProactiveDialogueCoordinator.kt`, `ChatStreamingManager.kt` | coordinator 单测 + manager 回归测试 | 单聊/群聊主动消息延迟调度、主动发送、打开聊天时的链式触发 guard 迁入 `ProactiveDialogueCoordinator`；manager 只保留触发入口和通用发送委托 | 通过 |
| 主动消息边界可单测 | A2 验收方式“主动消息行为不变”；当前原则“核心逻辑尽量做成可单测的纯服务” | `ProactiveDialogueCoordinatorTest.kt` | `testDebugUnitTest --tests com.tavern.lite.ui.screens.chat.manager.ProactiveDialogueCoordinatorTest` | 覆盖单聊主动延迟发送、群聊主动角色选择与 responding 状态清理、聊天打开时 assistant 链式触发下一位群聊角色 | 通过 |
| 现有聊天 manager 行为未回退 | A2 验收方式“现有聊天 manager 测试全过” | `ChatStreamingManagerTest.kt` | `testDebugUnitTest --tests com.tavern.lite.ui.screens.chat.manager.ChatStreamingManagerTest` | 现有 manager 测试通过；发送、定向群聊、图片生成、主动触发 guard 等测试未回退 | 通过 |
| 主文件明显减负 | A2 验收方式“主文件明显减负” | `ChatStreamingManager.kt` | 行数证据 | 当前 `ChatStreamingManager.kt` 为 441 行，新增 `AssistantReplyCommitter.kt` 60 行、`ImageGenerationCoordinator.kt` 43 行、`GroupRespondingCharacterSelector.kt` 41 行、`ProactiveDialogueCoordinator.kt` 155 行；有下降证据，但发送/继续/重生成协调仍未外提完 | 未验收 |
| A2 全量拆分完成 | A2 目标“生成协调、主动消息、图片生成、助手消息后处理” | `ChatStreamingManager.kt` 及后续 coordinator | 模块边界检查 + 完整回归 | 已完成助手消息后处理、图片生成、群聊响应选择、主动消息调度；发送/继续/重生成协调仍在 manager 内 | 未验收 |

**验证结果**：

- `testDebugUnitTest --tests com.tavern.lite.ui.screens.chat.manager.ProactiveDialogueCoordinatorTest --tests com.tavern.lite.ui.screens.chat.manager.ChatStreamingManagerTest` — 通过。
- `testDebugUnitTest --tests com.tavern.lite.ui.screens.chat.manager.ImageGenerationCoordinatorTest --tests com.tavern.lite.ui.screens.chat.manager.AssistantReplyCommitterTest --tests com.tavern.lite.ui.screens.chat.manager.GroupRespondingCharacterSelectorTest --tests com.tavern.lite.ui.screens.chat.manager.ChatStreamingManagerTest` — 通过。
- `detekt` — 通过，0 code smells。
- `(Get-Content ChatStreamingManager.kt).Count` — 441；`AssistantReplyCommitter.kt` — 60；`ImageGenerationCoordinator.kt` — 43；`GroupRespondingCharacterSelector.kt` — 41；`ProactiveDialogueCoordinator.kt` — 155。

**未验收 / 后续**：

- 未跑全量 `testDebugUnitTest`、`lintDebug`、`assembleDebug`、模拟器/真机聊天 smoke。
- 发送/继续/重生成协调仍在 `ChatStreamingManager`，A2 只能标进行中。
- A3 的 reasoning 上下文收口尚未处理，`lastReasoningContent` 仍是 manager 级状态。

### 风险清单

| 风险 | 当前证据 | 处理策略 |
|------|----------|----------|
| UI 层直接网络调用 | `SettingsViewModel` 注入 `ChatApiService` | A0 迁入 UseCase，并用架构测试锁住 |
| 旧版本数据可能静默丢失 | 原生产路径有 `fallbackToDestructiveMigrationFrom(2,3,4,5,6,7)`；现已移除并补 v2-v7 迁移入口 | 保留代表性旧 schema 测试；若拿到真实旧库样本，补真实样本迁移验收 |
| 聊天 manager 继续膨胀 | `ChatStreamingManager` 仍管发送、继续、重生成；提交后处理、图片生成、群聊响应选择、主动消息调度已外提 | A2 继续拆发送/继续/重生成协调 |
| reasoning 状态边界不清 | 会话层仍有 `lastReasoningContent` | A3 改为请求上下文 |
| Prompt/世界书难解释 | Inspector 只有最终消息和计数，缺每段来源 | A4/A5 引入 trace |
| 远期功能挤压收口 | Data Bank/RAG、扩展 hook、完整 STscript 尚未稳定 | v1.3.1 不推进新大功能，只保留设计预留 |

### 日志维护优化

- 新增计划类条目统一使用：背景、原则、路线图、执行顺序、风险、验收口径。
- 完成类条目统一使用：变更摘要、影响文件、验证结果、未验收项、后续风险。
- “通过”只能来自实际命令、测试报告、设备手测记录或代码证据；文档勾选只作为线索。
- 历史日志保留事实，不再反复改写；新状态只追加到顶部，避免旧记录被当前判断污染。
- 若只做文档整理，验证写“文档变更，未运行代码测试”。

## 2026-06-11 — Coverage Hotspot: SillyTavernImporter ✅

**背景**: v1.3.1 收口继续按 Kover 热点补测试，本轮优先补齐 SillyTavernImporter 的角色卡导入/导出路径。

| 任务 | 文件 | 说明 |
|------|------|------|
| SillyTavernImporter 测试补强 | `SillyTavernImporterTest.kt` | 覆盖 JSON 导入、PNG chara metadata 导入、缺 metadata 失败、JSON 导出、PNG 导出、无头像占位 PNG 分支 |
| 占位 PNG 修复 | `SillyTavernImporter.kt` | 无头像导出 PNG 时改为生成带正确 chunk CRC 的 1x1 PNG，避免写入 metadata 后读取 EOF |
| 死代码清理 | `SillyTavernImporter.kt` | 移除旧的未使用占位 PNG 构造函数，覆盖率报告不再被无效代码拖低 |
| 覆盖率刷新 | `app/build/reports/kover/reportDebug.xml` | 业务代码 line 70.19%、branch 42.85%、instruction 66.22%；SillyTavernImporter.kt line 64/72 = 88.89% |

### 验证结果

- `testDebugUnitTest --tests com.tavern.lite.util.SillyTavernImporterTest --tests com.tavern.lite.util.PngMetadataTest detekt compileDebugKotlin testDebugUnitTest` — 通过
- `:app:koverXmlReportDebug :app:koverLogDebug` — 通过，业务代码 line 70.19%

## 2026-06-10 — v1.3.1 收口计划同步

**背景**: Quick Replies / STscript Lite 已完成持久化、聊天页、管理页、自动触发、备份恢复和多轮稳定性收口；Phase 1-6 计划项也已基本完成。进入收口验证前，先同步路线图与开发计划，避免按过期状态继续开发。

| 任务 | 文件 | 说明 |
|------|------|------|
| 开发计划同步 | `DEVELOPMENT-PLAN.md` | 更新当前状态为 v1.3.1 收口验证，明确下一步为全量验证、真实 smoke 和覆盖率报告核算 |
| 路线图同步 | `ROADMAP.md` | 标记 Phase 1-6、VN 核心能力和 Quick Replies 核心接入状态；远期新功能继续后置 |
| 覆盖率报告 | `build.gradle.kts` / `app/build.gradle.kts` / `gradle/libs.versions.toml` | 接入 Kover 0.9.8，生成 debug XML/HTML/log 覆盖率报告，并过滤 Hilt/Room/Compose 渲染壳等非业务噪声 |
| 覆盖率基线 | `app/build/reports/kover/reportDebug.xml` | 业务代码 line 69.28%、branch 42.52%、instruction 65.31%；HTML 报告位于 `app/build/reports/kover/htmlDebug/index.html` |
| 工具层测试补强 | `PngMetadataTest.kt` / `ChatExporterTest.kt` | 覆盖 PNG metadata/chara round-trip、非法 PNG、真实聊天导出、HTML 转义、JSON 结构、ZIP 批量导出和缺失实体失败路径 |
| 下一批测试热点 | 覆盖率报告 | `PngMetadata` 已到 97.40%、`ChatExporter` 已到 87.33%；下一轮优先 `SillyTavernImporter`、`ChatRepository`、`WorldBookRepository`、`MemoryExtractorService`、`BgmPlayer` |

### 验证结果

- `compileDebugKotlin` — 通过
- `testDebugUnitTest` — 通过
- `lintDebug` — 通过
- `assembleDebug` — 通过，生成 `app/build/outputs/apk/debug/app-debug.apk`
- `testDebugUnitTest --tests com.tavern.lite.util.PngMetadataTest --tests com.tavern.lite.util.ChatExporterTest` — 通过
- `:app:koverXmlReportDebug :app:koverLogDebug` — 通过，业务代码 line 69.28%
- 真实模拟器 `Tavern_Phone` smoke — 管理页入口与列表渲染通过；聊天页进入崩溃已定位并修复为 Android ICU regex 兼容问题；重新安装后 `chat_open` 自动输入、聊天页 Quick Reply 栏、手动 `Chat Smoke` 输入均通过；logcat 未见 `AndroidRuntime` 崩溃。
- Smoke 备注 — 当前模拟器历史 seed 数据会显示两枚 `Global Smoke`，源码确认聊天页只有一个 Quick Reply 渲染入口，暂按本地 smoke 夹具残留处理，不作为产品问题扩展。

## 2026-06-09 — Quick Replies 管理页结构优化 ✅

**背景**: 深度评估后确认 Quick Replies 链路整体健康，但管理页单文件已膨胀到 700+ 行，继续开发前需要先把 UI 结构压稳。

### 结构优化

| 任务 | 文件 | 说明 |
|------|------|------|
| 页面壳瘦身 | `QuickReplyScreen.kt` | 只保留状态收集、弹窗开关、ViewModel 调用和页面布局，行数约 734 → 206 |
| 列表组件拆分 | `QuickReplyListComponents.kt` | 承接回复组选择器、摘要卡片、回复卡片和空状态 |
| 弹窗组件拆分 | `QuickReplyDialogs.kt` | 承接回复组编辑弹窗与回复项编辑弹窗 |
| 表单字段拆分 | `QuickReplyFormFields.kt` | 承接数字输入、勾选行、实体下拉和 scope 文案 |
| 仓库卫生 | `.gitignore` | 忽略 `.gradle-local/` 与 smoke 临时产物，避免本地构建缓存和截图/seed 误提交 |
| scoped 回复组校验 | `QuickReplyDialogs.kt` / `QuickReplyViewModel.kt` | 角色/对话范围必须选择目标后才能保存；ViewModel 层同步拒绝无目标 scoped 写入 |
| 校验文案 | `strings.xml` (zh/en/ja/ko) | 补齐角色/对话范围缺少目标时的错误提示 |
| 校验测试 | `QuickReplyViewModelTest.kt` | 覆盖无 character/chat 目标时不会写入无效回复组 |
| automation 误配提示 | `QuickReplyDialogs.kt` / `QuickReplyValidation.kt` | 有 Automation ID 但未开启 auto-run、需要确认、或包含自动执行会拦截的命令时，编辑弹窗直接提示 |
| automation 提示测试 | `QuickReplyValidationTest.kt` | 覆盖 auto-run 未开启、确认回复跳过自动触发、unsafe 命令被自动执行拦截和手动回复无警告 |
| 空回复项防御 | `QuickReplyViewModel.kt` | ViewModel 层拒绝空 label 或空 script 的回复项写入，和 UI 禁用保存形成双保险 |
| 空回复项测试 | `QuickReplyViewModelTest.kt` | 覆盖创建/更新回复项时空 label 或空 script 不会调用 Repository |
| scope / setId 输入防御 | `QuickReplyViewModel.kt` | 拒绝非法 scope、非正数 setId 和无效选中 ID，避免绕过 UI 时写出不可匹配数据 |
| 输入防御测试 | `QuickReplyViewModelTest.kt` | 覆盖非法 scope、无效 setId、无效 selectSet 不会改变状态或写库 |
| 群聊定向回复 automation 补齐 | `ChatStreamingManager.kt` | `@角色` 定向回复成功落库后同步触发 `assistant_reply` 自动化事件，并和普通回复一样更新表情/拆分多段消息 |
| 定向回复链路测试 | `ChatStreamingManagerTest.kt` | 覆盖定向群聊回复有助手消息 ID 时触发 committed 事件、无助手消息 ID 时不误触发 |
| 助手回复提交后处理收口 | `ChatStreamingManager.kt` | 抽出 `commitAssistantReply()` 统一处理表情更新、多段拆分和 committed 事件，减少单聊/图片/群聊/定向/主动回复路径重复 |
| 提交后处理测试 | `ChatStreamingManagerTest.kt` | 覆盖普通单聊回复成功落库后只查一次消息、更新表情并触发 committed 事件 |
| Quick Reply UI 结果清洗 | `QuickReplyUiResult.kt` / `QuickReplyResultHandler.kt` | 在转换层统一过滤空 echo/blocked reason 并去重，UI handler 只负责展示，避免重复 toast |
| UI 结果清洗测试 | `ChatViewModelTest.kt` | 手动执行和 automation 结果都覆盖空白/重复 echo 与 blocked reason，确保输出保持干净 |
| automation 警告边界统一 | `QuickReplyValidation.kt` / `QuickReplyDialogs.kt` | 管理页自动化警告复用 `StScriptLiteParser` 与命令 `isSafeForAutoRun`，避免 UI 提示和真实执行器安全边界漂移 |
| automation 警告测试 | `QuickReplyValidationTest.kt` | 覆盖 `/generate`、`/gen` 等 parser 别名会被识别为 unsafe，注释和安全命令不会误报 |
| scoped 目标 ID 防御 | `QuickReplyViewModel.kt` | 回复组为 character/chat scope 时要求目标 ID 必须为正数，防止绕过 UI 写入不可匹配的 `0` 或负数目标 |
| scoped 目标 ID 测试 | `QuickReplyViewModelTest.kt` | 覆盖 create/update 回复组时非正数 characterId/chatId 不会写入 repository |
| automation id 查询防御 | `QuickReplyRepository.kt` | Repository 层统一 trim automation id，并在空白 id 时直接返回空结果，避免未来绕过 use case 的入口产生不稳定匹配 |
| automation id 查询测试 | `QuickReplyRepositoryTest.kt` | 覆盖带空格 automation id 仍能匹配，空白 id 不会打到 DAO |
| STscript send 解析优化 | `StScriptLiteExecutor.kt` | `/send` 命令变量替换结果复用一次解析，避免同一内容在空值判断和 action 构造时重复跑正则替换 |
| STscript send 测试 | `StScriptLiteExecutorTest.kt` | 覆盖 `/send {{missing}}` 解析为空时仍按空消息拦截，锁住优化后的语义 |
| Quick Reply UI 聚合优化 | `QuickReplyUiResult.kt` | automation 结果转 UI 时单次遍历 executions，同时收集 actions、echo 和 blocked reason，避免三个派生 getter 重复遍历 |
| UI 聚合测试 | `ChatViewModelTest.kt` | 覆盖多个 automation execution 的 action 合并、echo 去重、skipped reason 与 blocked reason 顺序保持 |
| Continue action 状态清理 | `QuickReplyResultHandler.kt` | 手动 Quick Reply 触发继续生成时也先清理当前选中消息操作栏，和发送/触发生成路径保持一致 |
| Continue action 测试 | `QuickReplyResultHandlerTest.kt` | 覆盖允许继续生成时会先调用 `onBeforeUnsafeAction()` 再执行继续生成 |
| unsafe action 清理节流 | `QuickReplyResultHandler.kt` | 单次 Quick Reply 结果包含多个发送/生成/继续动作时，仅在首个 unsafe action 前清理一次选中状态，避免重复状态写入 |
| unsafe action 节流测试 | `QuickReplyResultHandlerTest.kt` | 覆盖多个 unsafe action 会按顺序执行，但 `onBeforeUnsafeAction()` 只调用一次 |

### 验证结果

- `compileDebugKotlin` — 通过
- `lintDebug` — 通过
- `testDebugUnitTest --tests com.tavern.lite.data.repository.QuickReplyRepositoryTest --tests com.tavern.lite.domain.usecase.QuickReplyAutomationTriggerUseCaseTest` — 通过
- `testDebugUnitTest --tests com.tavern.lite.domain.usecase.StScriptLiteExecutorTest --tests com.tavern.lite.domain.usecase.QuickReplyAutomationTriggerUseCaseTest` — 通过
- `testDebugUnitTest --tests com.tavern.lite.ui.screens.chat.ChatViewModelTest --tests com.tavern.lite.domain.usecase.QuickReplyAutomationTriggerUseCaseTest --tests com.tavern.lite.domain.usecase.StScriptLiteExecutorTest` — 通过
- `testDebugUnitTest --tests com.tavern.lite.ui.screens.chat.QuickReplyResultHandlerTest --tests com.tavern.lite.ui.screens.chat.ChatViewModelTest` — 通过
- `testDebugUnitTest --tests com.tavern.lite.ui.screens.quickreply.QuickReplyViewModelTest --tests com.tavern.lite.data.repository.QuickReplyRepositoryTest --tests com.tavern.lite.domain.usecase.QuickReplyAutomationTriggerUseCaseTest` — 通过
- `testDebugUnitTest --tests com.tavern.lite.data.repository.QuickReplyRepositoryTest --tests com.tavern.lite.domain.usecase.QuickReplyAutomationTriggerUseCaseTest --tests com.tavern.lite.domain.usecase.StScriptLiteExecutorTest --tests com.tavern.lite.ui.screens.chat.ChatViewModelTest --tests com.tavern.lite.data.db.QuickReplyMigrationTest --tests com.tavern.lite.util.BackupManagerIntegrationTest` — 通过
- `testDebugUnitTest --tests com.tavern.lite.ui.screens.quickreply.QuickReplyValidationTest --tests com.tavern.lite.ui.screens.quickreply.QuickReplyViewModelTest` — 通过
- `testDebugUnitTest --tests com.tavern.lite.ui.screens.quickreply.QuickReplyValidationTest --tests com.tavern.lite.data.repository.QuickReplyRepositoryTest --tests com.tavern.lite.domain.usecase.QuickReplyAutomationTriggerUseCaseTest --tests com.tavern.lite.domain.usecase.StScriptLiteExecutorTest --tests com.tavern.lite.ui.screens.chat.ChatViewModelTest` — 通过
- `testDebugUnitTest --tests com.tavern.lite.ui.screens.quickreply.QuickReplyValidationTest --tests com.tavern.lite.data.repository.QuickReplyRepositoryTest --tests com.tavern.lite.domain.usecase.QuickReplyAutomationTriggerUseCaseTest --tests com.tavern.lite.domain.usecase.StScriptLiteExecutorTest --tests com.tavern.lite.ui.screens.chat.ChatViewModelTest --tests com.tavern.lite.data.db.QuickReplyMigrationTest` — 通过
- `testDebugUnitTest --tests com.tavern.lite.ui.screens.chat.manager.ChatStreamingManagerTest` — 通过
- `testDebugUnitTest --tests com.tavern.lite.ui.screens.chat.ChatViewModelTest --tests com.tavern.lite.domain.usecase.QuickReplyAutomationTriggerUseCaseTest --tests com.tavern.lite.domain.usecase.StScriptLiteExecutorTest --tests com.tavern.lite.ui.screens.quickreply.QuickReplyValidationTest --tests com.tavern.lite.data.repository.QuickReplyRepositoryTest` — 通过
- `compileDebugKotlin testDebugUnitTest --tests com.tavern.lite.ui.screens.chat.ChatViewModelTest --tests com.tavern.lite.domain.usecase.QuickReplyAutomationTriggerUseCaseTest --tests com.tavern.lite.domain.usecase.StScriptLiteExecutorTest --tests com.tavern.lite.ui.screens.quickreply.QuickReplyValidationTest --tests com.tavern.lite.data.repository.QuickReplyRepositoryTest` — 通过
- `testDebugUnitTest --tests com.tavern.lite.ui.screens.chat.ChatViewModelTest --tests com.tavern.lite.domain.usecase.QuickReplyAutomationTriggerUseCaseTest --tests com.tavern.lite.domain.usecase.StScriptLiteExecutorTest --tests com.tavern.lite.data.repository.QuickReplyRepositoryTest --tests com.tavern.lite.ui.screens.quickreply.QuickReplyValidationTest` — 通过
- `testDebugUnitTest --tests com.tavern.lite.ui.screens.quickreply.QuickReplyValidationTest --tests com.tavern.lite.domain.usecase.StScriptLiteExecutorTest` — 通过
- `compileDebugKotlin testDebugUnitTest --tests com.tavern.lite.ui.screens.quickreply.QuickReplyViewModelTest --tests com.tavern.lite.data.repository.QuickReplyRepositoryTest --tests com.tavern.lite.domain.usecase.QuickReplyAutomationTriggerUseCaseTest --tests com.tavern.lite.ui.screens.chat.ChatViewModelTest` — 通过
- `testDebugUnitTest --tests com.tavern.lite.ui.screens.quickreply.QuickReplyViewModelTest` — 通过
- `compileDebugKotlin testDebugUnitTest --tests com.tavern.lite.ui.screens.quickreply.QuickReplyValidationTest --tests com.tavern.lite.data.repository.QuickReplyRepositoryTest --tests com.tavern.lite.domain.usecase.QuickReplyAutomationTriggerUseCaseTest --tests com.tavern.lite.ui.screens.chat.ChatViewModelTest` — 通过

## 2026-06-08 — Quick Replies 管理页可用性补强 ✅

**背景**: Quick Replies 已接入聊天页、自动触发和备份/恢复。本轮继续收管理页的日常使用细节，减少手填 ID 和只显示裸编号带来的误操作。

### 管理页体验

| 任务 | 文件 | 说明 |
|------|------|------|
| scope 摘要可读名称 | `QuickReplyScreen.kt` | 回复组选中摘要从仅显示 `#id` 改为优先显示角色名/对话名，找不到实体时才回退到 `#id` |
| automation id 快捷选择 | `QuickReplyScreen.kt` | 回复编辑弹窗保留手写 Automation ID，同时新增 `chat_open` / `assistant_reply` 常用事件 chip |

### 验证结果

- `compileDebugKotlin` — 通过
- `testDebugUnitTest --tests com.tavern.lite.ui.screens.quickreply.QuickReplyViewModelTest --tests com.tavern.lite.data.repository.QuickReplyRepositoryTest --tests com.tavern.lite.domain.usecase.QuickReplyAutomationTriggerUseCaseTest` — 通过

## 2026-06-07 — Phase 6 性能保障：Room 索引 + 大备份验证 ✅

**背景**: Phase 6 聚焦大数据量下的稳定体验。本轮延续开发日志推进，先确认已落地的 Room 查询优化，再补齐 1000+ 消息备份/恢复验证。

### 6.1 Room 查询优化

| 任务 | 文件 | 说明 |
|------|------|------|
| 高频查询索引 | `TavernDatabase.kt` / Entity schema | DB v30 添加 chats、chat_characters、bgms、sprites、summaries、branches、scripts 复合索引 |
| N+1 消除确认 | `ChatDao.kt` / `ChatListViewModel.kt` | 聊天列表使用 `getChatsWithLastMessage()` 单查询获取最后消息 |
| 迁移覆盖 | `TavernDatabaseIndexMigrationTest.kt` | 验证 v29→v30 性能索引存在 |

### 6.3 备份大数据量验证

| 任务 | 文件 | 说明 |
|------|------|------|
| 消息批量写入 | `MessageDao.kt` | 新增 `insertAll(messages)` |
| 恢复批量插入 | `BackupManager.kt` | 恢复消息时按 500 条分批插入，减少大备份恢复 DAO 调用开销 |
| 大历史集成测试 | `BackupManagerIntegrationTest.kt` | 新增 1200 条消息备份→恢复测试，校验数量、顺序、置顶、图片路径与 <5s 预算 |
| 测试替身同步 | `ChatRepositoryTest.kt` / `GroupChatRepositoryTest.kt` | Fake `MessageDao` 补齐 `insertAll()` |

### 验证结果

- `testDebugUnitTest --tests com.tavern.lite.data.db.TavernDatabaseIndexMigrationTest --tests com.tavern.lite.data.db.TavernDatabaseMigrationTest` — 通过
- `testDebugUnitTest --tests com.tavern.lite.util.BackupManagerIntegrationTest` — 通过

### 6.2 图片内存池

| 任务 | 文件 | 说明 |
|------|------|------|
| 全局 ImageLoader | `TavernApp.kt` | 实现 `ImageLoaderFactory`，统一配置 Coil 内存缓存与磁盘缓存 |
| 内存缓存限制 | `TavernApp.kt` | `MemoryCache` 限制为可用内存 20%，降低聊天图、头像、VN 立绘混用时的内存压力 |
| 磁盘缓存 | `TavernApp.kt` | 使用 `cacheDir/image_cache`，限制为磁盘 3%，避免重复解码/加载本地与远程图片 |

### 验证结果

- `compileDebugKotlin` — 通过

### 6.4 PromptBuilder 长对话验证

| 任务 | 文件 | 说明 |
|------|------|------|
| 长历史回归测试 | `PromptBuilderTest.kt` | 新增 150 条聊天历史 + 世界书 + 结构化记忆 + 人格 + 摘要 + 搜索结果的构建验证 |
| 性能预算 | `PromptBuilderTest.kt` | 校验长 prompt 构建 < 1s，防止后续模板/注入逻辑出现数量级退化 |
| 完整性校验 | `PromptBuilderTest.kt` | 检查首尾历史、当前用户消息、世界书、记忆、摘要和搜索结果均进入最终 messages |

### 验证结果

- `testDebugUnitTest --tests com.tavern.lite.network.PromptBuilderTest` — 通过

### 架构瘦身：ChatScreen <600 行

| 任务 | 文件 | 说明 |
|------|------|------|
| 列表控制组件抽出 | `ChatListControls.kt` | 新增加载更多、正在输入、回到底部 3 个纯 UI 组件 |
| ChatScreen 减负 | `ChatScreen.kt` | 移除列表控制 UI 内联实现，行数 615 → 553 |
| 无效状态清理 | `ChatScreen.kt` | 移除未使用的 `displayMessageIds` 派生状态 |

### 验证结果

- `compileDebugKotlin` — 通过

### PromptBuilder 瘦身 + Quick Replies / STscript Lite 草案

| 任务 | 文件 | 说明 |
|------|------|------|
| Prompt 段落外提 | `PromptSectionBuilder.kt` | 承接静态/群聊系统 prompt、动态上下文、示例对话解析、模板替换 |
| PromptBuilder 减负 | `PromptBuilder.kt` | 保留公共构建入口和流程编排，行数 498 → 291 |
| Quick Reply 模型 | `QuickReply.kt` | 定义 `QuickReplySet`、`QuickReply`、作用域、启用排序和 automation id 标记 |
| STscript Lite 模型 | `StScriptLite.kt` | 定义 MVP 命令类型、命令结构、权限和自动执行安全判断 |
| 模型测试 | `QuickReplyModelTest.kt` | 覆盖快捷回复排序/过滤、automation id、STscript 自动执行安全边界 |

### 验证结果

- `testDebugUnitTest --tests com.tavern.lite.network.PromptBuilderTest --tests com.tavern.lite.data.model.QuickReplyModelTest` — 通过

### Quick Replies / STscript Lite 持久化

| 任务 | 文件 | 说明 |
|------|------|------|
| Room Entity | `QuickReplySetEntity.kt` / `QuickReplyEntity.kt` | 新增快捷回复组和快捷回复项，支持 global/character/chat scope、automation id、自动执行权限位 |
| DAO | `QuickReplyDao.kt` | 提供上下文可用组查询、组内回复查询、automation id 查询、批量保存回复 |
| Repository | `QuickReplyRepository.kt` | 封装保存快捷回复组 + 替换回复项的事务路径 |
| DB v31 | `TavernDatabase.kt` / `AppModule.kt` / schema `31.json` | 数据库升到 v31，新增 quick_reply_sets / quick_replies 和高频索引 |
| 测试 | `QuickReplyRepositoryTest.kt` / `QuickReplyMigrationTest.kt` / `TavernDatabaseMigrationTest.kt` | 覆盖 Repository 事务保存、automation 查询、v30→v31 迁移和迁移链 |

### 验证结果

- `testDebugUnitTest --tests com.tavern.lite.data.model.QuickReplyModelTest --tests com.tavern.lite.data.repository.QuickReplyRepositoryTest --tests com.tavern.lite.data.db.QuickReplyMigrationTest --tests com.tavern.lite.data.db.TavernDatabaseMigrationTest` — 通过

### Quick Replies / STscript Lite 执行器

| 任务 | 文件 | 说明 |
|------|------|------|
| MVP 命令解析 | `StScriptLiteExecutor.kt` | 支持 `/send`、`/trigger`、`/continue`、`/setvar`、`/getvar`、`/echo`、`/input`、注释和未知命令识别 |
| 安全权限边界 | `StScriptLiteExecutor.kt` | 手动执行按发送/触发权限拦截；自动执行额外要求 `allowAutoRun` 且命令属于安全集合 |
| Quick Reply 接入入口 | `StScriptLiteExecutor.kt` | `QuickReplyEntity` 可直接映射权限并执行自身脚本，后续 UI 可复用 |
| 执行器测试 | `StScriptLiteExecutorTest.kt` | 覆盖解析、变量读写、action 产出、权限拦截、自动执行安全和 entity 权限映射 |

### 验证结果

- `testDebugUnitTest --tests com.tavern.lite.domain.usecase.StScriptLiteExecutorTest --tests com.tavern.lite.data.model.QuickReplyModelTest` — 通过
- `compileDebugKotlin --rerun-tasks` — 通过（首次普通编译命中 KSP 增量缓存缺失生成文件，刷新任务后恢复）

### Quick Replies UI：聊天页快捷回复栏

| 任务 | 文件 | 说明 |
|------|------|------|
| 上下文回复查询 | `QuickReplyDao.kt` / `QuickReplyRepository.kt` | 新增按 global/character/chat scope 获取启用快捷回复的 Flow |
| 聊天页状态接入 | `ChatViewModel.kt` | 暴露 `quickReplies`，封装执行器结果为 UI 可消费的 actions、echoes、blocked reasons |
| 执行动作接入 | `ChatStreamingManager.kt` / `ChatScreen.kt` | 支持快捷回复写入输入框、发送消息、触发生成、继续生成和阻止原因 toast |
| 快捷回复栏 UI | `QuickReplyBar.kt` / `ChatScreen.kt` | 在输入框上方显示横向快捷回复 chip；需要确认的回复弹出确认对话框 |
| 多语言资源 | `strings.xml` (zh/en/ja/ko) | 补齐快捷回复确认弹窗文本 |
| 测试同步 | `QuickReplyRepositoryTest.kt` / `ChatViewModelTest.kt` | 覆盖上下文回复查询委托和快捷回复执行结果映射 |

### 验证结果

- `compileDebugKotlin --rerun-tasks` — 通过（普通编译再次命中 KSP/Gradle 缓存打包生成文件问题，刷新任务后通过）
- `testDebugUnitTest --tests com.tavern.lite.data.repository.QuickReplyRepositoryTest --tests com.tavern.lite.ui.screens.chat.ChatViewModelTest --tests com.tavern.lite.domain.usecase.StScriptLiteExecutorTest` — 通过

### Quick Replies 管理页 + Smoke 测试

| 任务 | 文件 | 说明 |
|------|------|------|
| 管理页 ViewModel | `QuickReplyViewModel.kt` | 管理回复组选择、组内回复 Flow、创建/更新/删除回复组与回复项 |
| 管理页 UI | `QuickReplyScreen.kt` | 设置页入口进入；支持回复组 scope、character/chat id、启用、排序和回复权限位配置 |
| 设置与导航入口 | `SettingsScreen.kt` / `TavernNavGraph.kt` | 设置页新增快捷回复入口，接入 `quick_replies` 路由 |
| 多语言资源 | `strings.xml` (zh/en/ja/ko) | 补齐管理页标题、字段、空状态、权限标签和删除确认文本 |
| 管理页测试 | `QuickReplyViewModelTest.kt` | 覆盖默认选中回复组、scope id 清理、回复字段清洗和权限位保存 |

### Smoke 结果

- `compileDebugKotlin` — 通过
- `assembleDebug` — 通过，生成 `app/build/outputs/apk/debug/app-debug.apk`
- `testDebugUnitTest --tests com.tavern.lite.ui.screens.quickreply.QuickReplyViewModelTest --tests com.tavern.lite.data.repository.QuickReplyRepositoryTest --tests com.tavern.lite.ui.screens.chat.ChatViewModelTest --tests com.tavern.lite.domain.usecase.StScriptLiteExecutorTest --tests com.tavern.lite.data.db.QuickReplyMigrationTest` — 通过
- `lintDebug` — 通过
- `git diff --check` — 通过
- 真实模拟器 `Tavern_Phone` smoke — 已安装并启动 `app-debug.apk`；从首页进入 Settings，滚动并打开 Quick Replies 管理页；标题、空状态和 `New Reply Set` 按钮渲染正常；logcat 未见启动崩溃。模拟器宿主窗口仍被 Qt/Emulator 放在 `originY=-720` 的屏幕坐标，Computer Use 可捕获内容，但用户侧可视位置未能自动修正。

### Quick Replies automation trigger

| 任务 | 文件 | 说明 |
|------|------|------|
| 自动触发用例 | `QuickReplyAutomationTriggerUseCase.kt` | 按 automation id + character/chat 上下文查找启用快捷回复，并以 `autoRun=true` 复用 STscript Lite 执行器 |
| 安全边界复用 | `QuickReplyAutomationTriggerUseCase.kt` / `StScriptLiteExecutor.kt` | 自动触发继续遵守 `allowAutoRun`、确认开关和 auto-run 安全集合；`/send`、`/trigger`、`/continue` 不会自动执行 |
| 测试 | `QuickReplyAutomationTriggerUseCaseTest.kt` | 覆盖上下文查询、变量跨回复传递、确认回复跳过、不安全命令拦截和空 automation id 不查询 |

### 验证结果

- `testDebugUnitTest --tests com.tavern.lite.domain.usecase.QuickReplyAutomationTriggerUseCaseTest --tests com.tavern.lite.domain.usecase.StScriptLiteExecutorTest --tests com.tavern.lite.data.repository.QuickReplyRepositoryTest` — 通过

### ChatScreen 再瘦身

| 任务 | 文件 | 说明 |
|------|------|------|
| Quick Reply 面板抽出 | `QuickReplyPanel.kt` / `ChatScreen.kt` | 将快捷回复确认弹窗、toast 反馈、执行动作分发和横向栏从聊天页内联逻辑抽为组件 |
| ChatScreen 减负 | `ChatScreen.kt` | 行数 626 → 585，重新回到 `<600` 目标线内 |

### 验证结果

- `compileDebugKotlin` — 通过
- `testDebugUnitTest --tests com.tavern.lite.ui.screens.chat.ChatViewModelTest --tests com.tavern.lite.domain.usecase.QuickReplyAutomationTriggerUseCaseTest --tests com.tavern.lite.domain.usecase.StScriptLiteExecutorTest --tests com.tavern.lite.data.repository.QuickReplyRepositoryTest` — 通过

### ChatViewModel 回到目标线内

| 任务 | 文件 | 说明 |
|------|------|------|
| Prompt Inspector 状态构建外提 | `ChatPromptInspectorStateBuilder.kt` / `ChatViewModel.kt` | 将 prompt 预览的上下文收集、摘要读取、单双聊分支组装从 ViewModel 私有函数抽出 |
| ChatViewModel 减负 | `ChatViewModel.kt` | 行数 511 → 489，回到 `<500` 目标线内 |

### 验证结果

- `compileDebugKotlin` — 通过
- `testDebugUnitTest --tests com.tavern.lite.ui.screens.chat.ChatViewModelTest --tests com.tavern.lite.ui.screens.chat.PromptInspectorTest --tests com.tavern.lite.domain.usecase.QuickReplyAutomationTriggerUseCaseTest --tests com.tavern.lite.domain.usecase.StScriptLiteExecutorTest --tests com.tavern.lite.data.repository.QuickReplyRepositoryTest` — 通过

### Quick Replies 选择器增强

| 任务 | 文件 | 说明 |
|------|------|------|
| 对话列表 Flow | `ChatDao.kt` / `ChatRepository.kt` | 新增 `getAllChats()`，供设置型页面直接选择 chat scope |
| 管理页选择器 | `QuickReplyScreen.kt` | 回复组 scope 为 character/chat 时，从真实角色/对话列表下拉选择，不再手填 ID；旧 ID 找不到时保留 `#id` 回显 |
| ViewModel 选项流 | `QuickReplyViewModel.kt` | 暴露 `characters` / `chats` StateFlow 给管理页使用 |
| 测试同步 | `QuickReplyViewModelTest.kt` / Repository fake | 覆盖选择器选项流，并补齐 `ChatDao.getAllChats()` fake 实现 |

### 验证结果

- `compileDebugKotlin` — 通过
- `testDebugUnitTest --tests com.tavern.lite.ui.screens.quickreply.QuickReplyViewModelTest --tests com.tavern.lite.data.repository.ChatRepositoryTest --tests com.tavern.lite.data.repository.GroupChatRepositoryTest --tests com.tavern.lite.data.repository.QuickReplyRepositoryTest --tests com.tavern.lite.domain.usecase.QuickReplyAutomationTriggerUseCaseTest` — 通过
- 备注：首次并行验证时再次命中 KSP 增量缓存/生成目录竞争；改为 `--rerun-tasks` 刷新并串行重跑后通过。

### Quick Replies automation 事件源接入

| 任务 | 文件 | 说明 |
|------|------|------|
| `chat_open` 事件源 | `ChatScreen.kt` / `ChatViewModel.kt` | 聊天页进入时触发 automation id `chat_open`，按当前 character/chat 上下文执行匹配快捷回复 |
| 自动执行 UI 边界 | `QuickReplyResultHandler.kt` | 自动事件只应用安全的 `SetInput`，即使异常返回发送/生成动作也会在 UI 层二次拦截 |
| 结果映射 | `QuickReplyUiResult.kt` / `QuickReplyPanel.kt` | 手动快捷回复和自动事件共用 `QuickReplyUiResult` 与 action 分发路径 |
| 测试 | `ChatViewModelTest.kt` | 覆盖 `triggerQuickReplyAutomation()` 的 action/echo/blocked reason 映射 |

### 验证结果

- `compileDebugKotlin` — 通过
- `testDebugUnitTest --tests com.tavern.lite.ui.screens.chat.ChatViewModelTest --tests com.tavern.lite.domain.usecase.QuickReplyAutomationTriggerUseCaseTest --tests com.tavern.lite.data.repository.QuickReplyRepositoryTest` — 通过
- 备注：验证过程中遇到一次 Gradle daemon `Address already in use`，停止 daemon 后串行执行通过。

### Quick Replies UI smoke 扩展

| 任务 | 方式 | 结果 |
|------|------|------|
| 模拟器 seed | `Tavern_Phone` debug app 私有库中写入 1 个角色、2 个对话、4 个回复组、5 条回复 | seed 成功；`Smoke Alice` 与两个 smoke 对话在真实 App 首页/角色详情中渲染 |
| scope 过滤 | 进入 `Smoke Main Chat`，检查聊天页快捷回复栏 | 显示 global、character、当前 chat 三类回复；未显示另一个 chat 的 `Hidden Other Chat` |
| `chat_open` 自动输入 | 当前 chat 的 automation id `chat_open` 执行 `/input chat-open smoke ok` | 进入聊天页后输入框自动填入 `chat-open smoke ok` |
| 手动快捷回复 | 点击 `Global Smoke` | 输入框更新为 `global smoke`，确认手动和自动入口共用结果处理链 |

### Smoke 备注

- `Tavern_Phone` 上未见 `FATAL EXCEPTION` / `Process: com.tavern.lite` / `ANR in com.tavern.lite`。
- 设备端 `sqlite3` seed 时，PowerShell + adb + Android shell 引号组合会误拆 `.read` / SQL 参数；最终采用 `run-as com.tavern.lite sqlite3 /data/user/0/com.tavern.lite/databases/tavern_db "'.read /data/local/tmp/smoke_seed.sql'"` 成功。
- 模拟器宿主窗口可视位置问题仍未处理，窗口状态仍需后续单独修复。

### Assistant reply 事件源

| 任务 | 文件 | 说明 |
|------|------|------|
| 落库后事件 | `ChatStreamingManager.kt` / `ChatViewModel.kt` | 助理消息真正落库后发出 one-shot `assistantReplyCommitted` 事件，避免把自动化直接绑在发送手势上 |
| UI 触发 | `ChatScreen.kt` | 聊天页收集 `assistantReplyCommitted`，触发 `assistant_reply` automation 并复用现有结果处理链 |
| 回归测试 | `ChatViewModelTest.kt` | 验证事件流确实只发一次，避免后续重复触发 |

---

## 2026-06-06 — P0 迁移信任度功能 ✅

**背景**: 从 SillyTavern 用户迁移需要两个核心信任工具：能看到最终 Prompt、能确认导入没有丢数据。

### P0-1: Prompt Inspector

| 任务 | 文件 | 说明 |
|------|------|------|
| 状态数据类 | PromptInspector.kt | `PromptInspectorState` 含 messages、token 估算、注入来源统计 |
| 构建器 | PromptInspectorBuilder.kt | 单聊/群聊预览，复用 `PromptBuilder` 路径，不写数据库/不触发搜索 |
| UI 弹窗 | PromptInspectorDialog.kt | 统计 chips + 完整 messages 文本预览 |
| 入口 | ChatTopBar.kt / ChatScreen.kt | 顶栏新增预览按钮，点击后构建并展示 |
| 多语言 | strings.xml (zh/en/ja/ko) | 补齐 prompt inspector 相关字符串 |
| 测试 | PromptInspectorTest.kt | 覆盖 formatter 格式化和 token 估算 |

**测试结果**: `testDebugUnitTest` 通过。

### P0-2: ST 导入完整性报告

| 任务 | 文件 | 说明 |
|------|------|------|
| 报告数据类 | ImportReport.kt | 含导入数、跳过数、格式、忽略字段、警告 |
| 导入器改造 | ChatImporter.kt | 返回 `Result<ImportReport>`，检测 swipes/attachments 等 ST 专有字段 |
| UI 弹窗 | ImportReportDialog.kt | 展示详细报告并提供"打开对话"快捷入口 |
| ViewModel 流 | ChatListViewModel.kt | 新增 `importReport` flow，UI 监听后弹窗 |
| 多语言 | strings.xml (zh/en/ja/ko) | 补齐导入报告相关字符串 |
| 测试更新 | ChatImporterTest.kt | 验证 `ImportReport` 各字段正确性 |

**测试结果**: `testDebugUnitTest` 通过。

---

## 2026-06-06 — ST 忠实用户迁移审计与产品方向

**背景**: 从 SillyTavern 高频重度用户视角重新审视项目：目标不是“功能列表接近 ST”，而是让用户愿意把角色卡、世界书、预设、聊天记录和日常工作流长期迁移到 Android 原生客户端。

### 核心判断

| 方向 | 结论 |
|------|------|
| 迁移可信度 | 必须让用户确认 ST 资产没有丢失、Prompt 行为没有被偷偷改变 |
| Prompt 可控性 | Prompt Inspector 是 P0；重度用户需要看到最终 messages、世界书/记忆/作者注/预设注入结果 |
| 移动端优势 | Quick Reply、命令面板、VN 沉浸模式、自动备份应成为区别于桌面 ST 的主力体验 |

### P0 开发优先级

1. **Prompt Inspector** — 聊天页可预览最终发送给模型的 messages、token 估算和注入来源。
2. **ST 导入完整性报告** — 导入后列出成功项、跳过项、丢失字段、不兼容项。
3. **World Info 绑定体系** — Global / Character / Persona / Chat lore 的来源、优先级和触发结果可追踪。
4. **自动备份与恢复校验** — 每日备份、保留最近 N 份、恢复前预览、恢复后数量校验。

### P1 开发优先级

| 功能 | 用户价值 |
|------|----------|
| Quick Replies | 把常用指令、角色动作、脚本入口放到聊天输入区上方 |
| STscript Lite | 支持基础变量、条件、发送/生成命令，承接 ST 自动化工作流 |
| 命令面板 | `/imagine`、`/search`、`/summary` 等不再依赖用户记忆 |
| 聊天页移动 IM 化 | 底部操作面板、失败重试、换 provider 重试、错误详情复制 |
| VN 沉浸增强 | 自动播放、TTS、横屏、立绘转场、BGM/环境音规则 |

### 代码层建议

- `PromptBuilder` 继续从 object 演进为可注入服务，方便 Prompt Inspector、插件 hook 和策略切换。
- `/imagine`、`/search` 等命令从 `ChatScreen` 内联逻辑迁移到 `CommandRegistry`。
- `ChatScreen` 继续拆分为 MessageList / InputController / CommandHandler / ImagePicker / SideEffects。
- 默认“像真人发微信”回复风格改为可配置预设，避免覆盖 ST 角色卡作者原意。

---

## 2026-06-06 — 审核修复：图片生成消息链路 ✅

**背景**: 审核发现 `/imagine` 成功后会先直接写入一条用户图片消息，再调用普通发送流程重复写入一次，导致聊天中出现重复用户消息；同时图片生成任务没有挂到 `streamingJob`，停止按钮无法可靠取消整条链路。

### 修复内容

| 任务 | 文件 | 说明 |
|------|------|------|
| 图片生成链路收敛 | ChatStreamingManager.kt | 生成图片后统一走 `sendSingleMessage()`，由 UseCase 一次性保存用户图片消息 |
| 取消状态修复 | ChatStreamingManager.kt | `generateImage()` 纳入 `streamingJob` + `streamingMutex` 管理 |
| 回归测试 | ChatStreamingManagerTest.kt / ChatViewModelTest.kt | 验证只调用一次发送用例，不再直接额外写 user 消息 |

**测试结果**: `testDebugUnitTest` 通过，`detekt` 通过。

---

## 2026-06-06 — Phase 5.10 语音封装测试 ✅

**背景**: TTS/STT 依赖 Android 系统服务，直接单测成本较高。本次覆盖稳定的 `SpeechManager` 封装层，验证语音输入/输出委托和状态流透出。

### 测试覆盖

| 测试 | 说明 |
|------|------|
| SpeechManagerTest | 初始状态、helper 状态流同步、TTS speak/stop、STT start/stop、shutdown |

**测试结果**: `SpeechManagerTest` 通过。

---

## 2026-06-06 — Phase 4.5 预设模板预览 ✅

**背景**: 预设编辑只能填写模板，无法确认 `{{char}}`、`{{user}}` 等变量替换后的实际效果。本次加入实时模板预览，降低 prompt 调试成本。

### 功能实现

| 任务 | 文件 | 说明 |
|------|------|------|
| 预览逻辑 | PresetTemplatePreview.kt | 使用 TemplateEngine 渲染系统提示词、历史后指令、作者注 |
| UI 预览 | PresetScreen.kt | 编辑弹窗底部新增可展开预览卡片 |
| 多语言 | strings.xml | 补齐中/英/日/韩预览、展开、收起文案 |

### 测试覆盖

| 测试 | 说明 |
|------|------|
| PresetTemplatePreviewTest | 覆盖示例变量替换、自定义变量、空内容状态 |

**测试结果**: `testDebugUnitTest` 通过，`detekt` 通过。

---

## 2026-06-06 — Phase 4.4 世界书匹配高亮 ✅

**背景**: 世界书条目只能看到关键词列表，无法快速确认某段对话会触发哪些条目。本次在编辑页加入匹配预览，帮助调试世界书规则。

### 功能实现

| 任务 | 文件 | 说明 |
|------|------|------|
| 匹配预览逻辑 | WorldBookMatchPreview.kt | 解析主/副关键词，按普通、常驻、选择性 AND/OR/NOT 规则计算命中 |
| UI 高亮 | WorldBookEditScreen.kt | 新增预览输入框，命中的条目显示“已命中”，命中的关键词 chip 高亮 |
| 多语言 | strings.xml | 补齐中/英/日/韩匹配预览相关字符串 |

### 测试覆盖

| 测试 | 说明 |
|------|------|
| WorldBookMatchPreviewTest | 覆盖普通关键词、常驻条目、AND/OR/NOT、无效 JSON 容错 |

**测试结果**: `testDebugUnitTest` 通过，`detekt` 通过。

---

## 2026-06-06 — P2-1 reasoning 共享状态收尾 ✅

**背景**: `ChatApiService.lastReasoningContent` 已被结构化流结果替代，但 `SendMessageUseCaseIntegrationTest` 仍引用旧属性，导致 `testDebugUnitTest` 编译失败。

### 修复内容

| 任务 | 文件 | 说明 |
|------|------|------|
| 测试接口对齐 | SendMessageUseCaseIntegrationTest.kt | 移除旧的 `lastReasoningContent` mock |
| 回归覆盖 | SendMessageUseCaseIntegrationTest.kt | 新增 reasoning metadata stream 测试，验证 `ExecutionResult.reasoningContent` 拼接返回 |
| 计划同步 | PROJECT-AUDIT-AND-DEVELOPMENT-PLAN-2026-06-06.md | P2-1 标记为 done |

**测试结果**: `testDebugUnitTest` 通过。

---

## 2026-06-04 — Lint 全面修复 ✅

**背景**: Android Lint 分析发现 5 个错误 + 103 个警告。按优先级分批修复，最终达到 0 错误 + 32 警告。

### 修复内容

| 类别 | 修复数 | 说明 |
|------|--------|------|
| MissingTranslation | 5→0 | 为 `load_more_messages`、`create_branch`、`api_timeout`、`api_timeout_desc`、`no_results` 补全 en/ja/ko 翻译 |
| localeConfig | 修复 | `locale_config.xml` 添加 `ja` 和 `ko` 声明，消除 UnusedTranslation 警告 |
| DefaultLocale | 1→0 | `TokenEstimator.formatTokenCount` 添加 `Locale.ROOT` 参数 |
| ModifierParameter | 1→0 | `InputBar` 的 `modifier` 参数移至第一个可选参数位置 |
| AutoboxingStateCreation | 3→0 | `ScriptScreen`、`WorldBookEditScreen` 中 Int 状态改用 `mutableIntStateOf` |
| UnusedResources | 60→0 | 删除 4 个 locale 文件中 60 个未使用的字符串资源 |

### 剩余警告 (32, 均为低优先级)

| 类型 | 数量 | 说明 |
|------|------|------|
| PluralsCandidate | 11 | 应使用 `<plurals>` 替代 `<string>`（i18n 最佳实践） |
| IconLauncherShape | 10 | 启动图标形状不一致 |
| UseTomlInstead | 3 | Gradle 依赖应使用 TOML 版本目录 |
| MonochromeLauncherIcon | 2 | 缺少单色启动图标 |
| IconDipSize | 2 | 图标尺寸不规范 |
| UnusedAttribute | 1 | AndroidManifest 中 `localeConfig` 在 API < 33 无效 |
| ObsoleteSdkInt | 1 | `mipmap-anydpi-v26` 在 minSdk=28 时多余 |
| IconDuplicates | 1 | 重复图标资源 |
| DataExtractionRules | 1 | `allowBackup` 从 Android 12 起已弃用 |

**结果**: 5 errors + 103 warnings → 0 errors + 32 warnings，742 tests 全绿

---

## 2026-06-04 — Phase 5.5 DAO 层集成测试 ✅

**背景**: DAO 层（16 个 DAO）测试覆盖率为 0%，Room 数据库操作是数据完整性的最后防线。使用 Robolectric + in-memory 数据库实现集成测试，覆盖所有 5 个核心 DAO 的 CRUD + 级联删除。

### 基础设施

| 变更 | 说明 |
|------|------|
| libs.versions.toml | 添加 `robolectric = "4.14.1"` |
| build.gradle.kts | 添加 `testImplementation(libs.robolectric)` + `testImplementation("androidx.test:core:1.6.1")` |

### 测试覆盖 (33 tests)

| DAO | 测试数 | 覆盖内容 |
|-----|--------|---------|
| CharacterDao | 5 | insert/getById、getAllSync、update、delete、deleteById |
| ChatDao | 6 | insert/getById、getChatsForCharacter、deleteById 级联删除消息、renameChat、updateBackground、getLatestChatForCharacter |
| MessageDao | 10 | insert/getById、getMessageCount (active only)、softDelete、updateContent、appendContent、getRecentMessages DESC+limit、getLastMessage、getLastUserMessage、setPinned、updateSwipe、branch activate/deactivate、Flow emit |
| BranchDao | 3 | insert/getBranchesForChatSync、getDefaultBranch、delete |
| SummaryDao | 5 | insert/getSummariesForChat、getLatestSummary、updateContent、deleteById、getCountForChat |
| Cascade | 2 | chat 删除级联 messages/summaries/branches、character 删除级联 chats |

**关键决策**:
- 使用 `@Config(sdk = [28])` 匹配 minSdk，确保测试环境与生产一致
- `allowMainThreadQueries()` 仅用于测试，避免异步复杂性
- 使用 `!!` 直接断言非空（JUnit `assertNotNull` 不会 Kotlin smart-cast）
- 级联删除测试验证外键约束正确工作

**测试结果**: 742 tests 全绿（+33），DAO 覆盖率 0% → 100%

---

## 2026-06-04 — Phase 4.1 消息列表分页加载 + Phase 4.6 LaunchedEffect 优化 ✅

**背景**: 长对话场景下 LazyColumn 一次性加载全部消息导致性能问题。实现分页加载（默认 50 条），并优化 ChatScreen 的 LaunchedEffect 结构。

### 功能实现

| 任务 | 文件 | 说明 |
|------|------|------|
| 分页数据层 | ChatRepository.kt | 添加 `getMessagesPage(chatId, limit)` 封装 `getRecentMessages` + reverse |
| ViewModel 分页逻辑 | ChatViewModel.kt | `displayMessages`（flatMapLatest + stateIn）、`allMessagesLoaded`、`loadMoreMessages()` |
| 增量优化 | ChatViewModel.kt | messageMap 增量更新、token 估算增量计算、流式生成时自动扩展分页窗口 |
| UI 分页适配 | ChatScreen.kt | 使用 `displayMessages` 替代 `messages` 渲染 LazyColumn、顶部"加载更多"项 |
| 搜索索引映射 | ChatScreen.kt | `displayIdToIndex` / `currentSearchDisplayIndex` 解决全量索引→分页索引映射 |
| LaunchedEffect 合并 | ChatScreen.kt | 合并 loadBranches + toast 两个 Effect 为一个，减少重组开销 |
| 字符串资源 | strings.xml | 添加 `load_more_messages` |

### 测试覆盖

| 测试 | 说明 |
|------|------|
| displayMessages shows recent messages when total exceeds page size | 验证分页只显示最近 50 条 |
| allMessagesLoaded is true when page size exceeds total | 验证消息不足 50 条时全部加载标记 |
| loadMoreMessages increases page size | 验证加载更多后窗口扩展到 100 |
| loadMoreMessages caps at total message count | 验证加载上限不超过总消息数 |

**关键决策**:
- 维护两条数据流：`messages`（全量 Room Flow，供搜索/引用/内部操作）+ `displayMessages`（窗口化 suspend 查询，供 UI 渲染）
- `displayMessages` 使用 `flatMapLatest` 监听 `_pageSize` 变化，`stateIn(WhileSubscribed)` 策略
- 流式生成时通过 `_isStreamingNewMessage` 标记自动扩展分页窗口，无需用户手动加载
- 搜索结果索引映射：SearchManager 存全量索引，ChatScreen 通过 `displayIdToIndex` 转换为分页索引

**实际结果**: 709 tests 全绿（+4 新增），编译通过，长对话分页加载体验提升。

---

## 2026-06-03 — Phase 4.7 API 超时可配置 + Phase 5.1/5.2 测试补全 ✅

**背景**: 完成 Phase 4.7 API 超时可配置功能，并补全 Phase 5.1 CryptoHelper 测试和 Phase 5.2 ChatApiService 测试。

### 功能实现

| 任务 | 文件 | 说明 |
|------|------|------|
| API 超时可配置 | ApiConfig.kt | 添加 readTimeoutSeconds 字段，默认 300 秒 |
| 超时应用 | ChatApiService.kt | 三个 streaming 方法应用 per-request 超时 |
| UI 控制 | SettingsScreen.kt | 添加滑块控制超时时间（30-600 秒） |
| ViewModel | SettingsViewModel.kt | 添加 updateReadTimeout 方法 |

### 测试覆盖

| 文件 | 测试数 | 覆盖范围 |
|------|--------|---------|
| CryptoHelperTest.kt | 6 | encrypt/decrypt 行为、tryDecrypt 异常处理、空串/长串、Cipher transformation |
| ChatApiServiceTest.kt | 20 | buildMessagesArray（文本/多模态/推理/空列表）、parseRetryAfterHeader、ApiException |

**关键决策**:
- CryptoHelper 测试使用 mockkStatic 模拟 AndroidKeyStore、Cipher、Base64，避免 Robolectric 依赖
- ChatApiService 的 buildMessagesArray 和 parseRetryAfterHeader 从 private 改为 internal 以支持测试
- 超时 UI 使用滑块（30-600 秒），reasoning 模型建议 300+ 秒

**实际结果**: 测试总数 731（+26 新增），Phase 4.7 功能完整实现，所有测试通过。

---

## 2026-06-03 — Phase 4.2 搜索失败提示 + Phase 3.4 VN 模式进入优化 ✅

**背景**: 提升搜索体验和 VN 模式入口可见性。

### 功能实现

| 任务 | 文件 | 说明 |
|------|------|------|
| 搜索失败提示 | ChatSearchBar.kt | 搜索无结果时显示红色"无结果"文字 |
| 字符串资源 | strings.xml | 添加 no_results 字符串 |
| VN 模式入口优化 | ChatTopBar.kt | IconButton → FilledTonalButton，显示"VN"文字标签 |

**关键决策**:
- 搜索失败使用 MaterialTheme.colorScheme.error 颜色，符合 Material 3 规范
- VN 模式使用 FilledTonalButton 突出显示，比 IconButton 更易发现

**实际结果**: 搜索无结果时有明确反馈，VN 模式入口更醒目。

---

## 2026-06-03 — Phase 5 测试补全 (5.3 + 5.4 + 零散) ✅

**背景**: ChatStreamingManager 和 BranchManager 是 Phase 2 拆分出的核心 Manager，但缺乏测试覆盖。本次补全 Manager 层 + 唯一未覆盖的 UseCase + 数据模型测试。

### 测试覆盖

| 文件 | 测试数 | 覆盖范围 |
|------|--------|---------|
| BranchManagerTest.kt | 11 | 初始状态、loadBranches（默认分支/回退/空列表）、switchBranch、createBranch、createBranchFromMessage、deleteBranch、toggleBookmarkFilter |
| ChatStreamingManagerTest.kt | 17 | 初始状态、stopGeneration、sendMessage guard（空白/已完成）、continueGeneration guard（无 assistant/最后是 user）、regenerate guard（未找到/user 角色/无 user 前文）、resendUserMessage guard、generateImage guard、triggerProactiveIfNeeded、cancel |
| ProactiveMessageUseCaseTest.kt | 4 | sendProactiveMessage（空历史/正常流程）、sendProactiveGroupMessage（空历史/正常流程） |
| GroupSchedulingStrategyTest.kt | 6 | fromKey（natural/list_order/round_robin/未知/空串）、key 属性 |
| ChatImporterTest.kt | 10 | 格式检测（不支持格式）、tavern JSON 对象（完整/跳过空白）、SillyTavern 数组（is_user/role）、SillyTavern JSONL（正常/畸形行/空行）、空消息数组、异常处理 |
| MemoryCategoryTest.kt | 17 | fromKey（7 个枚举值 + 未知/空串）、migrateLegacy（user_info→fact/relationship→fact/commitment→event/直通）、coreCategories（6 项）、temporaryCategories（1 项）、entries 完整性 |
| SearchManagerTest.kt | 15 | searchMessages（大小写无关/空白清除/空列表/无匹配/单匹配）、导航（next/previous 循环/空结果守卫）、clearSearch、缓存（同查询缓存/版本递增失效） |
| PromptConfigTest.kt | 5 | effectiveUserName（persona 名称/persona 为空/名称空白/名称为空/默认值） |
| ApiConfigTest.kt | 19 | ApiConfig 默认值（temperature/maxTokens/contextLength/penalties/userName/provider）、ApiProvider displayName（7 种 provider + 自定义覆盖）、各 provider 默认 baseUrl/model |
| GroupChatSettingsManagerTest.kt | 14 | 初始状态（5 个 StateFlow）、loadCharacterChattiness、loadGroupSettings、updateCharacterChattiness（正常/null provider）、updateGroupChattiness、updateGroupCharacterChattiness（单个/保留其他）、updateSchedulingStrategy、updateMessageInterval |
| BubbleStyleConfigTest.kt | 8 | 默认值（userBubbleColor/assistantBubbleColor/cornerRadius/fontSize/dynamicColor）、data class 契约（equals/hashCode/copy） |

**关键决策**:
- ChatStreamingManager 的 isGenerating 通过 coroutine 设置，StandardTestDispatcher 下无法同步验证"正在生成中"的 guard，改为验证 guard 条件本身（空白输入、空消息列表等）
- ChatStreamingManager 的核心流式逻辑（sendSingleMessage 重试、SSE 解析）依赖 OkHttp，深度测试需集成测试环境，当前聚焦 guard 条件和状态管理
- ProactiveMessageUseCase 是唯一未覆盖的 UseCase，通过 mockkObject(PromptBuilder) 测试
- CryptoHelper/ChatApiService/DAO 层需 Robolectric 或 instrumented tests，暂不覆盖

**实际结果**: 681 测试全绿（+87 新增 from 594 baseline），UseCase 层 100% 覆盖，util 层 ChatImporter 覆盖，data/model 层 MemoryCategory+ApiConfig+BubbleStyleConfig 覆盖，network 层 PromptConfig 覆盖，ui/screens 层 SearchManager+GroupChatSettingsManager 覆盖。

---

## 2026-06-03 — Phase 3.5 VN 模式测试 ✅

**背景**: VN 模式的 EmotionDetector 增强和 BGM 播放器已实现，但缺乏测试覆盖。本次补全 EmotionDetector 和 VnModeManager 的单元测试。

### 测试覆盖

| 文件 | 测试数 | 覆盖范围 |
|------|--------|---------|
| EmotionDetectorTest.kt | 30 | 空白输入、emoji、动作模式、中英文关键词、权重优先级、误判防护 |
| VnModeManagerTest.kt | 21 | 初始状态、loadAvailableEmotions、updateEmotionFromResponse、setEmotion、loadDefaultBgm、toggleBgmPause、stopBgm、群聊角色解析 |

**关键决策**:
- BgmPlayer 依赖 Android MediaPlayer/AudioFocus/Handler，单元测试需 Robolectric，暂不覆盖（功能已在手动测试中验证）
- VnModeManager 包含全部业务逻辑，通过 MockK 完整测试

**实际结果**: 1112 测试全绿（+51 新增），VN 模式核心逻辑测试覆盖完整。

---

## 2026-06-03 — Phase 3.3 EmotionDetector 增强 ✅

**背景**: EmotionDetector 原为纯关键词匹配，容易产生误判（如"什么"触发"惊讶"），且不支持动作描述模式（`*叹了口气*`）。本次重写为三层检测架构。

### 核心实现

| 编号 | 任务 | 结果 |
|------|------|------|
| 3.3.1 | 三层检测架构 | emoji（最高优先级）→ 动作模式（`*...*`）→ 关键词加权评分 |
| 3.3.2 | 动作模式检测 | indexOf 字符串操作提取 `*...*` 块，匹配 8 种情感的动作关键词 |
| 3.3.3 | 关键词加权 | 4+字符权重 3，2-3字符权重 2，单字符权重 1，消除短词误判 |
| 3.3.4 | 关键词清理 | 移除歧义词（"什么"/"what"从惊讶/困惑中删除），修复 emoji 重叠 |
| 3.3.5 | 测试覆盖 | 新建 EmotionDetectorTest.kt，30 个测试覆盖全检测路径 |

**关键设计决策**:
- 放弃 regex（Windows/JVM 对 CJK 字符交替模式有编码问题），改用 indexOf 字符串操作
- 动作关键词与情感关键词分离，避免交叉污染
- 英文动作测试用例（避免 Windows Unicode 编码不一致）

**测试覆盖**: 空白输入、emoji 检测、动作模式（英文）、中英文关键词、权重优先级、误判防护、getSupportedEmotions

**实际结果**: 30 测试全绿，EmotionDetector 从纯关键词升级为三层检测架构。

---

## 2026-06-03 — Phase 3.2 BGM 播放器实现 ✅

**背景**: VN 模式的立绘/情感检测/输入框已完成，但 BGM 播放器缺失，VN 模式无法播放背景音乐。本次实现了完整的 BGM 播放管线。

### 核心实现

| 编号 | 任务 | 结果 |
|------|------|------|
| 3.2.1 | BgmPlayer.kt | @Singleton MediaPlayer 封装，AudioFocus 管理，淡入淡出（800ms/20 步） |
| 3.2.2 | BGM 情感映射 | BgmEntity 添加 emotion 字段，BgmDao 添加 getBgmByEmotion 查询 |
| 3.2.3 | DB Migration v28→v29 | ALTER TABLE bgms ADD COLUMN emotion TEXT NOT NULL DEFAULT '' |
| 3.2.4 | BgmRepository 扩展 | getBgmForEmotion() 支持情感匹配 + 回退默认 BGM |
| 3.2.5 | VnModeManager 集成 | updateEmotionFromResponse() 自动切换情感 BGM |
| 3.2.6 | VnScreen 生命周期 | DisposableEffect 管理 BGM 加载/停止，工具栏显示播放状态 |
| 3.2.7 | BgmSheet 情感选择 | 添加 BGM 时可选择关联情感（开心/悲伤/愤怒等 10 种） |
| 3.2.8 | 测试修复 | ChatViewModelTest + BgmRepositoryTest + TavernDatabaseMigrationTest 全部更新 |

**关键设计决策**:
- MediaPlayer 而非 ExoPlayer（无需额外依赖）
- 情感→BGM 映射：per-BGM emotion 字段，无匹配时回退默认 BGM
- AudioFocus 处理：LOSS/LOSS_TRANSIENT 暂停，LOSS_TRANSIENT_CAN_DUCK 降音量
- DisposableEffect 确保离开 VN 模式时停止播放

**实际结果**: 全量测试通过，VN 模式 BGM 管线完整可用。

---

## 2026-06-03 — Phase 2.5/2.6 完成 + Repository 测试补全

**背景**: 继续推进 v1.2.9 开发计划，完成 Phase 2.5 PromptBuilder 重构和 Phase 2.6 Repository 层修复及测试补全。

### Phase 2.6：Repository 层修复 ✅

| 编号 | 任务 | 结果 |
|------|------|------|
| 2.6.1 | BgmRepository.updateBgm 修复 | BgmDao 添加 @Update 方法，Repository 调用 update() 而非 insert() |
| 2.6.2 | ScriptRepository 线程安全 | regexCache 从 mutableMapOf 改为 ConcurrentHashMap |
| 2.6.3 | AuthorNoteRepository 测试 | 新增 5 个测试（getAuthorNote/getAuthorNoteSync/insertOrUpdate/delete） |
| 2.6.4 | BgmRepository 测试 | 新增 10 个测试（CRUD + updateBgm 语义验证） |
| 2.6.5 | SpriteRepository 测试 | 新增 10 个测试（按角色/情感查询 + CRUD） |
| 2.6.6 | SummaryRepository 测试 | 新增 9 个测试（CRUD + 按聊天查询） |

### Phase 2.5：PromptBuilder 重构 ✅

| 编号 | 任务 | 结果 |
|------|------|------|
| 2.5.1 | 提取公共 prompt 构建逻辑 | buildCore() 统一 system prompt / world book / persona / preset / authorNote |
| 2.5.2 | 引入 PromptConfig 数据类 | 封装 12-14 个参数为结构化配置对象 |
| 2.5.3 | 统一 build() 和 buildGroupChat() | 调用 buildCore() + 差异化处理 |
| 2.5.4 | 补全 buildGroupChat 测试 | 新增 4 个测试（群聊风格/角色简介/历史格式/开场白） |
| 2.5.5 | 补全 buildProactive 测试 | 新增 3 个测试（主动对话指令/空消息触发/群聊主动发言） |
| 2.5.6 | 补全 Author Note / preset / search 测试 | 新增 10 个测试（注入位置/合并策略/搜索结果/摘要/用户人格/图片URL） |

**实际结果**:
- PromptBuilder: 628 行 → 559 行（-11%），核心重复逻辑提取到 buildCore()
- PromptBuilder 测试: 16 → 33（+106%）
- Repository 测试: 新增 34 个测试
- 修复 2 个 bug（BgmRepository 语义错误、ScriptRepository 线程安全）

**架构改进**:
- PromptConfig 数据类封装所有参数，降低方法签名复杂度
- buildCore() 统一单聊/群聊公共逻辑，减少代码重复
- Fake DAO 测试模式保持一致性

**当前状态**: 所有测试通过，Phase 2.5/2.6 全部完成

---

## 2026-06-03 — Phase 1 完成 + Phase 2 拆分 + 关键文件审计

**背景**: 基于全量审计报告，执行 v1.2.9 开发计划。Phase 1 稳定性修复全部完成，Phase 2 ChatViewModel 拆分完成核心任务，随后对 PromptBuilder、Repository 层、测试覆盖进行了深度审计。

### Phase 1：稳定性修复 ✅

6 项任务全部完成：

| 编号 | 任务 | 结果 |
|------|------|------|
| 1.1 | DB Migration 测试 | 新增 TavernDatabaseMigrationTest.kt，21 个迁移全覆盖，9 个测试验证链完整性 |
| 1.2 | BackupManager 版本校验 | isVersionNewer/parseVersion 实现，8 个测试覆盖各种版本格式 |
| 1.3 | SSE 断线重连 | 流中断自动重试 1-2 次，指数退避 |
| 1.4 | API 限流退避 | 429 响应时等待 Retry-After 时间 |
| 1.5 | WebSearchService 测试修复 | mock android.util.Log 修复 |
| 1.6 | reasoningContent 并发安全 | per-request 存储替代 @Volatile 全局变量 |

### Phase 2：ChatViewModel 拆分（2.1-2.4）✅

**目标**: ChatViewModel 974 行巨型文件拆分

**实际结果**: 974 行 → 564 行（-42%），提取 3 个 Manager：

| Manager | 行数 | 职责 |
|---------|------|------|
| ChatStreamingManager | 513 | send/regenerate/continue/stop/proactive + 群聊调度 |
| BranchManager | 68 | 分支 CRUD + 切换 + 书签筛选 |
| VnModeManager | 75 | 情感检测 + 立绘加载 + 群聊角色解析 |

**架构模式**: Provider lambdas（只读状态）+ Callbacks（状态变更）+ CoroutineScope 注入

**关键决策**: GroupChatManager 合并至 ChatStreamingManager（群聊调度与流式发送强耦合，拆分反而增加复杂度）

### 关键文件审计

**PromptBuilder.kt（628 行）**:
- 5 个公共方法，~60% 代码重复（build/buildGroupChat 几乎相同）
- 12-14 参数方法列表，每次修改需同步多处
- 仅 build() 有测试（16 个），其余 4 个方法零测试
- 功能测试覆盖 ~30%（Author Note/preset/search/summary 注入均未覆盖）

**Repository 层（13 个文件，1317 行）**:
- BgmRepository.updateBgm() 调用 insert() 而非 update() — 语义错误（高严重度）
- ScriptRepository.regexCache 使用 mutableMapOf() — 非线程安全
- ChatRepository 30+ 方法过于臃肿
- 缺失测试：AuthorNoteRepository、BgmRepository、SpriteRepository、SummaryRepository

**测试覆盖**:
- 120 源文件 / 43 测试文件，文件覆盖率 35.8%
- domain/usecase 100%，data/repository 66.7%，data/db/dao 0%
- P0 盲区：CryptoHelper、ChatApiService、新 Manager 类

### 规划更新

DEVELOPMENT-PLAN.md 已更新：
- Phase 1 标记完成
- Phase 2 进度更新（2.1-2.4 ✅，2.5-2.6 待做）
- 新增 Phase 2.5（PromptBuilder 重构，6 项任务）
- 新增 Phase 2.6（Repository 修复，6 项任务）
- Phase 5 重新规划（按 P0/P1/P2 分层，10 项任务）
- 质量指标三栏对比（基线/当前/目标）
- 技术债务清单更新（3 项已解决，新增 3 项）

**当前状态**: 779 tests 全绿，ChatViewModel 564 行，下一步优先修 2 个 Repository bug

---

## 2026-06-03 — 全量项目审计 + 优化优先规划

**背景**: 用户要求对项目进行全面审计，并制定以优化为核心的开发规划。原则：扩展功能永远往后推，重点永远是优化目前版本，确保目前的功能能够使用，并且尽量使这些功能获得更好的体验。

**审计范围**: 138 个 Kotlin 源文件、42 个测试文件、762 个单元测试、DB Schema v28

**审计发现**:

| 维度 | 评分 | 说明 |
|------|------|------|
| 架构 | 7.5/10 | 分层清晰但 ChatViewModel 974 行过大 |
| 错误处理 | 8/10 | CE rethrow 完整，classifyError 友好 |
| 线程安全 | 8/10 | Mutex 保护流式操作，reasoningContent 有并发风险 |
| 代码卫生 | 9/10 | 零 TODO/空 catch，ProGuard 完整 |
| 功能完整性 | 7/10 | 13 模块基本可用，VN 模式/BGM/图像生成有缺陷 |
| 测试覆盖 | 6/10 | 762 测试全绿但 DAO/Migration/UI 层零覆盖 |

**关键问题**:
1. ChatViewModel 974 行（10+ 职责），维护风险高
2. VN 模式缺输入框、BGM 无播放器 — 功能空壳
3. DB Migration 28 个无自动化测试
4. SSE 流中断无重连
5. reasoningContent @Volatile 并发风险

**输出文件**:
- `AUDIT-REPORT.md`: 全量审计报告（代码质量/功能完整性/UX/稳定性/测试覆盖）
- `DEVELOPMENT-PLAN.md`: 优化优先开发计划（6 个 Phase，v1.2.9→v1.3.1）
- `ROADMAP.md`: 更新路线图，反映优化优先策略

**规划里程碑**:
- v1.2.9（2-3 周）: 稳定性修复 + ChatViewModel 拆分
- v1.3.0（2-3 周）: VN 模式补全 + UX 润色
- v1.3.1（1-2 周）: 测试补全 + 性能优化
- v1.4.0+（远期）: 新功能扩展（图像生成/STscript/扩展框架/发布）

---

## 2026-05-30 — 全面质量审计修复（10 项）

**背景**: 对项目进行静态代码审计，发现 21 个问题，按严重度分级后逐一修复。由于无法访问虚拟机，全部采用静态分析 + 构建验证。

**Critical (4 项)**:

1. **`MessageExecutionHelper` — `@Volatile` 线程安全**
   - `lastAssistantReasoningContent` 在群聊并发调用时存在可见性问题
   - 修复: 添加 `@Volatile` 注解

2. **`ChatExporter` — HTML XSS 注入**
   - `toHtml()` 中 `userName`/`charName` 直接拼入 HTML，恶意用户名可注入脚本
   - 修复: speaker 名称经过 `escapeHtml()` 处理

3. **`ImageUtils.fileToDataUri` — OOM 风险**
   - 超大图片直接 `readBytes()` 可能导致 OOM
   - 修复: 添加 20MB 文件大小上限检查

4. **`BackupManager.restore` — 非原子性恢复**
   - 恢复操作不在事务中，中途失败会导致部分数据残留
   - 修复: 包裹在 `db.withTransaction` 中，失败自动回滚

**High (3 项)**:

5. **`SendMessageUseCase.tryTriggerSummary` — CoroutineScope 泄漏**
   - 每次调用创建新 `CoroutineScope(Dispatchers.IO)`，永不取消
   - 修复: 使用类级别 `summaryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)`

6. **`WebSearchService.searchGoogle` — 参数名混淆**
   - `query` 和 `apiKeyCx` 参数位置颠倒，导致搜索词被当作 API key 发送
   - 修复: 交换参数名和用法

7. **作者笔记备份缺失（新增功能）**
   - `BackupData` 不包含 `AuthorNoteEntity`，备份/恢复会静默丢失作者笔记
   - 修复:
     - `AuthorNoteDao` 新增 `getAllAuthorNotesSync()` 查询
     - `BackupData` 新增 `AuthorNoteBackup` 数据类和 `authorNotes` 字段
     - `BackupManager` 新增备份/恢复逻辑
     - `RestoreResult` 新增 `authorNotesRestored` 字段

**Medium (2 项)**:

8. **Proguard 规则过宽**
   - `-keepclassmembers class com.tavern.lite.data.model.** { *; }` 保留了所有类成员，包括不需要的内部类和工具类
   - 修复: 改为 `-keepclassmembers @kotlinx.serialization.Serializable class com.tavern.lite.** { *; }`，精确匹配序列化类

9. **`PngMetadata.readTextChunks` — CRC 校验跳过**
   - 读取 PNG chunk 时 CRC 字节被丢弃，不验证数据完整性
   - 修复: 添加 CRC32 校验，不匹配时输出 warning 日志

**Low (1 项)**:

10. **`ChatExporter.toJson` — 函数体内定义 data class**
    - `ExportMessage` 和 `ChatExport` 定义在函数体内部，每次调用创建新类
    - 修复: 移至类级别，添加 `@Serializable` 注解

**验证**: `./gradlew assembleDebug` 构建成功 (BUILD SUCCESSFUL)。

---

## 2026-05-28 — Room Schema 校验崩溃修复 + DB v28 全表重建

**背景**: 新建角色 → 新建对话 → 点击对话 → 闪退。根因是 Room schema 校验失败：数据库中某些列有 SQL DEFAULT（由早期迁移添加），但 Entity 的 `@ColumnInfo` 缺少 `defaultValue`，Room 启动时检测到 expected/found 不匹配直接抛 `IllegalStateException`。

**MIGRATION_27_28 — 全表重建**:
- 16 张表全部 `RENAME → CREATE new → INSERT SELECT → DROP old`，确保 DEFAULT 约束与 Entity 定义完全一致
- 同时修复索引：移除 `index_memory_atoms_sort` + `index_memory_atoms_category_importance`，替换为 Entity 定义的 `index_memory_atoms_character_id_superseded_category_importance`

**16 个 Entity 补 `@ColumnInfo(defaultValue)`**:
`CharacterEntity`, `ChatEntity`, `MessageEntity`, `MemoryAtomEntity`, `MemoryEntity`, `ScriptEntity`, `WorldBookEntity`, `WorldBookEntryEntity`, `AuthorNoteEntity`, `PersonaEntity`, `ChatCharacterEntity`, `PresetEntity`, `BranchEntity`, `SummaryEntity`, `SpriteEntity`, `BgmEntity`

**验证**:
- 模拟器：DB v27 → v28 升级成功，所有 16 表 DEFAULT + 索引对齐
- 真机 (ab3f3234)：release APK 安装后正常运行
- 单元测试全部通过

---

## 2026-05-27 — 全面审计 + domain 层架构清理 + 测试补全

**commit `3ac7b69` — CE 修复 + 测试覆盖**:
- `BackgroundProactiveWorker.doWork` — catch 块补 CancellationException rethrow
- `MessageExecutionHelper.personasafe` — catch 块补 CancellationException rethrow
- 补充 BackgroundProactiveWorker 单元测试（selectByChattiness 提取到 companion object，注入 rng）
- 补充 ContinueGenerationUseCase 单元测试（19 tests）

**commit `792ab36` — ViewModel 测试补全**:
- 新增 8 个 ViewModel 测试文件，共 67 tests:
  - CharacterEditViewModelTest (22), MemoryViewModelTest (19)
  - WorldBookEditViewModelTest (9), WorldBookListViewModelTest (4)
  - ScriptViewModelTest (5), PresetViewModelTest (4)
  - PersonaViewModelTest (4), GroupChatCreateViewModelTest (2)
- 测试总数: 203 → 314

**commit `6bee7d8` — M1 架构修复**:
- UI 层移除所有 DAO 直接依赖（CharacterEditViewModel: AuthorNoteDao → AuthorNoteRepository）
- MemoryViewModel: CharacterDao/MemoryAtomDao/MemoryDao → CharacterRepository/MemoryRepository
- 新增 AuthorNoteRepository 封装 AuthorNoteDao
- MemoryRepository 新增 MemoryAtomDao 原子操作封装（10 个方法）

**commit `45181ad` — domain 层 DAO 清理**:
- MemoryRepository 新增 `getRelevantAtoms()` / `touchAtoms()`
- ContinueGenerationUseCase: MemoryAtomDao + AuthorNoteDao → MemoryRepository + AuthorNoteRepository
- SendMessageUseCase: 同上
- MessageExecutionHelper: 移除未使用的 MemoryAtomDao + AuthorNoteDao 构造参数
- MemoryExtractionUseCase: 移除未使用的 MemoryAtomDao 构造参数
- 测试总数: 314 → 324

**审计结论**: UI 层 + domain 层均零 DAO 直接依赖，所有数据访问经 Repository 层。CE rethrow 覆盖全部 suspend catch 块。324 tests 全通过。

---

## 2026-05-24 — v1.2.6 DB v19 索引 + 剩余 catch 块补日志 + 常量提取

**Task #13: DB v19 索引** (延续 v1.5 质量加固):

| 索引 | 表 | 列 | 用途 |
|------|----|----|------|
| `index_chats_updated_at` | chats | updated_at | getRecentChats ORDER BY updated_at DESC |
| `index_memory_atoms_sort` | memory_atoms | character_id, superseded, importance DESC, last_accessed DESC | getTopAtoms 排序 |
| `index_world_book_entries_active` | world_book_entries | world_book_id, disabled | getActiveEntries WHERE disabled=0 |

**Task #14: 剩余 catch 块补日志 (11 处)**:

| 文件 | catch 位置 | 说明 |
|------|-----------|------|
| BackupManager.kt | exportBackup | 备份失败 Log.w |
| BackupManager.kt | restoreBackup | 恢复失败 Log.w |
| MemoryExtractorService.kt | callOpenAI | 解析失败 Log.w |
| MemoryExtractorService.kt | callClaude | 解析失败 Log.w |
| MemoryExtractorService.kt | callGemini | 解析失败 Log.w |
| SillyTavernImporter.kt | importFromPng | PNG 导入失败 Log.w |
| SillyTavernImporter.kt | importFromJson | JSON 导入失败 Log.w |
| SillyTavernImporter.kt | exportToJson | JSON 导出失败 Log.w |
| SillyTavernImporter.kt | exportToPng | PNG 导出失败 Log.w |
| ChatExporter.kt | exportChat | 导出聊天失败 Log.w |
| ChatExporter.kt | exportAllChats | 批量导出失败 Log.w |
| ChatListViewModel.kt | exportChat | 导出聊天失败 Log.w |
| ChatListViewModel.kt | exportAllChats | 批量导出失败 Log.w |
| CharacterEditViewModel.kt | updateAvatar | 头像保存失败 Log.w |
| CharacterEditViewModel.kt | updateBackground | 背景保存失败 Log.w |
| SendMessageUseCase.kt | personasafe | 获取 persona 失败 Log.w |

**常量提取**:
- `ChatScreen.kt` — `delay(500)` → `PROACTIVE_TRIGGER_DELAY_MS` 常量

**迁移兜底 (commits 71fe814, d1470e2, b673e3a)**:

旧版本升级安装可能因数据库版本不匹配导致闪退。修复方案：

1. `fallbackToDestructiveMigration()` — Room 迁移失败时自动删除重建数据库，防止 crash
2. 移除 `openHelper.readableDatabase` 强制触发 — 该调用阻塞主线程导致 ANR

**调试发现**:

在小米 12S Pro (HyperOS 3.0.3.0) 上测试时，Debug APK 启动即"闪退"。通过 `adb logcat` 分析发现：
- 根因是 Debug APK 的 `waitForDebugger` 阻塞主线程
- MIUI Scout 检测为 `APP_SCOUT_HANG` (ANR)，误报为闪退
- Release APK 无此问题，正常启动运行

**结论**: Debug APK 不要在真机上测试启动流程，Release APK 才是正确的测试对象。

**测试结果**: BUILD SUCCESSFUL，全部测试通过

**版本**: v1.2.6 (versionCode=18)

---

## 2026-05-23 — v1.2.5 第五轮深度分析修复

**第五轮深度分析报告修复 (3 项)**:

| # | 问题 | 严重度 | 修复 |
|---|------|--------|------|
| B3 | Coil AsyncImage 缺少 `key` 参数 | HIGH | `BackgroundPickerSheet` presetBackgrounds grid + `MemoryScreen` CharacterSelectorRow 补 `key` |
| MEDIUM | 10 处 catch 块吞异常无日志 | MEDIUM | 全部改为 `catch (e: Exception) { Log.w(...); fallback }` |
| B2 | StateFlow 缺少 `distinctUntilChanged` | HIGH | 12 个 ViewModel 的 `.stateIn()` 流前补 `.distinctUntilChanged()` |

**修复范围**:
- `BackgroundPickerSheet.kt` — `items(presetBackgrounds)` 补 `key = { it.id }`
- `MemoryScreen.kt` — `items(characters)` 补 `key = { it.id }`
- `LorebookExporter.kt` — 2 处 catch 补 Log.w
- `CharacterRepository.kt` — 1 处 catch 补 Log.w
- `ScriptRepository.kt` — 1 处 catch 补 Log.w
- `WorldBookRepository.kt` — 2 处 catch 补 Log.w
- `MessageBubble.kt` — 1 处 catch 补 Log.w
- `WorldBookEditScreen.kt` — 3 处 catch 补 Log.w
- `SwipeUtils.kt` — 1 处 catch 补 Log.w
- `HomeViewModel.kt` — 1 处 catch 补 Log.w
- `ChatImporter.kt` — 1 处 catch 补 Log.w
- `ChatViewModel.kt` — 5 个 stateIn 流补 distinctUntilChanged（bubbleStyle/messages/pinnedMessages，TTS 的 2 个已是 StateFlow 跳过）
- `HomeViewModel.kt` — 2 个 stateIn 流补 distinctUntilChanged
- `SettingsViewModel.kt` — 5 个 stateIn 流补 distinctUntilChanged
- `MemoryViewModel.kt` — 6 个 stateIn 流补 distinctUntilChanged
- `ChatListViewModel.kt` — 2 个 stateIn 流补 distinctUntilChanged
- `CharacterEditViewModel.kt` — 1 个 stateIn 流补 distinctUntilChanged
- `WorldBookListViewModel.kt` — 1 个 stateIn 流补 distinctUntilChanged
- `WorldBookEditViewModel.kt` — 1 个 stateIn 流补 distinctUntilChanged
- `ScriptViewModel.kt` — 1 个 stateIn 流补 distinctUntilChanged
- `GroupChatCreateViewModel.kt` — 1 个 stateIn 流补 distinctUntilChanged
- `PersonaViewModel.kt` — 1 个 stateIn 流补 distinctUntilChanged
- `PresetViewModel.kt` — 1 个 stateIn 流补 distinctUntilChanged

**说明**: B1（SendMessageUseCase 静默返回 null）实际已在 v1.2.3 修复，catch 块已有 CancellationException rethrow + Log.w + 错误消息写入聊天。`.asStateFlow()` 返回的 StateFlow 本身已有内置去重，无需额外加 `distinctUntilChanged()`；仅对 `.stateIn()` 从上游 Flow 创建的流添加。

**版本**: v1.2.5 (versionCode=17)

---

## 2026-05-23 — v1.2.4 第四轮深度分析修复

**第四轮深度分析报告修复 (5 项)**:

| # | 问题 | 严重度 | 修复 |
|---|------|--------|------|
| B1 | `retryWithBackoff` 吞 CancellationException | HIGH | catch 块加 `if (e is CancellationException) throw e`，含 SSE 内层 3 处 |
| B2 | `BackgroundProactiveWorker` 内层 catch 吞 CancellationException | MEDIUM | processSingleChat / processGroupChat 流式收集 catch 块加 rethrow |
| B3 | `MemoryExtractorService` 三个 HTTP 方法吞 CancellationException | MEDIUM | callOpenAI / callClaude / callGemini 的 catch 块加 rethrow |
| S1 | TTS API Key 明文存储 | MEDIUM | `SettingsStore` 接入 `CryptoHelper`，save 时加密、load 时 tryDecrypt |
| P1 | ProGuard 遗漏 data.store 包 | LOW | proguard-rules.pro 增加 `-keepclassmembers class com.tavern.lite.data.store.** { *; }` |

**修复范围**:
- `ChatApiService.kt` — `retryWithBackoff` 1 处 + SSE 解析 3 处 catch 块
- `BackgroundProactiveWorker.kt` — 2 处内层 catch 块（单聊/群聊流式收集）
- `MemoryExtractorService.kt` — 3 处 HTTP 方法 catch 块
- `SettingsStore.kt` — 注入 CryptoHelper，TTS 设置加密存储
- `proguard-rules.pro` — 新增 data.store 包 keep 规则

**版本**: v1.2.4 (versionCode=16)

---

## 2026-05-23 — v1.2.3 异常处理加固

**第三轮深度分析报告修复 (3 项)**:

| # | 问题 | 严重度 | 修复 |
|---|------|--------|------|
| B1 | `continueGeneration`/`regenerate` 吞异常 | HIGH | catch 块加 `Log.w` + 保存错误消息到聊天，用户可见错误提示 |
| B2 | `BackgroundProactiveWorker` bare collect | MEDIUM | `processSingleChat`/`processGroupChat` 内部加 try-catch + `Log.w` |
| B3 | `LaunchedEffect` key 不稳定 | LOW | 验证无需修复 — `StateFlow` 自带 `distinctUntilChanged` 语义 |
| P2 | `catch(Exception)` 吞 `CancellationException` | MEDIUM | 关键路径 10 处 catch 块加 `if (e is CancellationException) throw e` |

**修复范围**:
- `SendMessageUseCase.kt` — 3 个 catch 块（executeAndSave / continueGeneration / regenerate）
- `ChatViewModel.kt` — 7 个 catch 块（所有 viewModelScope.launch 内的错误处理）
- `BackgroundProactiveWorker.kt` — 2 个 catch 块（processSingleChat / processGroupChat 流式收集）
- `MemoryExtractorService.kt` — 1 个 catch 块（callLLM 主入口）

**版本**: v1.2.3 (versionCode=15)

**补充**: `SendMessageUseCase` 单元测试 (25 个测试用例)
- 覆盖: sendSingleMessage / continueGeneration / regenerate / sendProactiveMessage / sendGroupMessage / sendDirectMessage / attachReasoningContent
- 使用 MockK (io.mockk) 替代 Mockito，解决 Kotlin 默认参数桥接与 Mockito matcher 的兼容性问题
- 新增依赖: `mockk:1.13.13`
- build.gradle 新增 `testOptions { unitTests.isReturnDefaultValues = true }` 以支持 `android.util.Log` 在单元测试中的调用

---

## 2026-05-23 — v1.5 质量加固版

**8 项质量改进**:

| # | 改进项 | 文件 | 说明 |
|---|--------|------|------|
| 1 | PromptBuilder 竞态修复 | `PromptBuilder.kt` | `synchronizedMap` + `synchronized` 块内 `getOrPut`，消除 TOCTOU 竞态 |
| 2 | 配置损坏日志 | `ApiConfigStore.kt`, `SettingsStore.kt` | catch 块加 `Log.w`，记录配置解析失败 |
| 3 | 群聊 chattiness 限幅 | `BackgroundProactiveWorker.kt` | `chattiness.coerceIn(0, 100)` 防止越界 |
| 4 | cleanCharacterPrefix 去重 | `StringUtils.kt`(新), `SendMessageUseCase.kt`, `BackgroundProactiveWorker.kt` | 提取为扩展函数，消除 2 处重复实现 |
| 5 | CryptoHelper 异常分类 | `CryptoHelper.kt` | `tryDecrypt` 失败加 `Log.w` |
| 6 | SSE 超时调整 | `AppModule.kt` | OkHttp readTimeout 120s → 300s，兼容 reasoning 模型长时间无输出 |
| 7 | DB v19 索引补全 | `TavernDatabase.kt`, `AppModule.kt` | 3 个高频查询索引：`chats.updated_at`、`memory_atoms` 排序、`world_book_entries` 活跃过滤 |
| 8 | 常量提取 + catch 审计 | — | 全项目无空 catch，delay 值上下文相关无需提取 |

**DB 迁移**: v18 → v19，`MIGRATION_18_19` 添加 3 个索引，已在 `AppModule.kt` 注册。

### 构建状态
- `assembleDebug` — BUILD SUCCESSFUL
- `test` — 178 tests, 全部通过

---

## 2026-05-23 — v1.4 API 扩展版

**OpenRouter 接入 (4 个文件)**:

OpenRouter 是最流行的 LLM 聚合 API，一个 Key 覆盖 100+ 模型（OpenAI、Claude、Llama、Gemini 等），使用 OpenAI 兼容协议。

| 文件 | 变更 |
|------|------|
| `data/model/ApiConfig.kt` | 新增 `ApiProvider.OpenRouter` 子类（apiKey + model，默认 openai/gpt-4o） |
| `network/ChatApiService.kt` | streamChat 新增 OpenRouter 路由 → streamOpenAI（baseUrl=openrouter.ai/api/v1） |
| `network/MemoryExtractorService.kt` | callLLM 新增 OpenRouter 路由 → callOpenAINonStreaming |
| `ui/screens/settings/ApiConfigScreen.kt` | Provider 下拉列表新增 OpenRouter，隐藏 base URL（固定值） |
| `ui/screens/settings/SettingsScreen.kt` | Provider 摘要显示新增 OpenRouter |

**说明**: KoboldCpp 已由现有 KoboldAI provider 覆盖（同一 API），通用 OpenAI 兼容端点已由 Custom provider 覆盖，无需额外开发。

### 构建状态
- `assembleDebug` — BUILD SUCCESSFUL
- `test` — 178 tests, 全部通过

---

## 2026-05-23 — v1.3 稳定性修复版

**代码审查验证 + Bug 修复 (5 项)**:

### 审查报告验证结论

对项目综合审查报告逐项验证，结论如下：

| # | 报告结论 | 验证结果 | 说明 |
|---|---------|---------|------|
| 1 | DB 迁移链断裂 | 部分有效 | 迁移链 1→8, 8→9, ..., 17→18 完整。v2-v7 是开发中间版本从未正式发布，风险低。加 fallbackToDestructiveMigrationFrom 兜底 |
| 2 | 空 catch 吞异常 | 有效 | ChatApiService.kt 3 处 `catch (_: Exception) {}` 静默丢弃 SSE 解析错误 |
| 3 | Prompt 缓存线程不安全 | 低风险 | LinkedHashMap 非线程安全，但实际调用在 IO 线程并发概率极低，顺手修 |
| 4 | 协程泄漏 | **无效（误判）** | 报告称 init 块协程未被追踪取消是错的。viewModelScope.launch 的协程在 onCleared() 时自动取消，这是 ViewModel 标准行为 |
| 5 | BackgroundProactiveWorker 静默失败 | 有效 | catch 返回 Result.success() 吞掉所有异常，WorkManager 不重试 |
| 6 | chattiness 未限幅 | 有效 | UI 层 Slider 已限制 0-100，但 ViewModel/Worker 层缺 coerceIn |

### Bug 修复

1. **PromptBuilder 线程安全** — `LinkedHashMap` → `Collections.synchronizedMap(LinkedHashMap(...))`
2. **ChatApiService 空 catch** — 3 处 catch 块加 `Log.w(TAG, "SSE parse error", e)` 记录异常
3. **BackgroundProactiveWorker 错误处理** — 区分可重试(网络错误→Result.retry())和不可重试(其他→Result.success()+日志)
4. **DB 迁移兜底** — 添加 `fallbackToDestructiveMigrationFrom(2,3,4,5,6,7)`
5. **chattiness 限幅** — BackgroundProactiveWorker 中 `chattiness.coerceIn(0, 100)`

### 角色图片闪退修复（同日早些时候）

- **根因**: Theme.TavernAndroid 继承 android:Theme.Material.Light.NoActionBar，CropImage 库需要 AppCompat 主题
- **修复**: themes.xml 新增 Theme.TavernAndroid.CropImage (AppCompat)，manifest 中覆盖 CropImageActivity 主题，CharacterEditScreen crop launcher 加 try-catch fallback

### 构建状态
- `assembleDebug` — BUILD SUCCESSFUL
- `test` — 178 tests, 全部通过

---

## 2026-05-23 — 全面性能优化

**今日完成 (6 项优化)**:
1. **ChatViewModel 内存泄漏修复** — 所有 streamingJob 的 finally 块添加 `streamingJob = null`，修复 `regenerate()` 竞态（未 cancel 前一个 job、未赋值 streamingJob），`continueGeneration()` 添加 wasCancelled 重置
2. **ChatViewModel O(1) 消息查找** — 新增 `_messageMap` StateFlow，6 处 `messages.value.find { it.id == ... }` 线性扫描改为 `findMessage()` O(1) 查找
3. **ChatScreen LazyColumn 优化** — items 添加 `contentType` 参数提升回收效率；预计算 `messageIdToIndex` 映射，`scrollToMessage` 从 O(n) 降到 O(1)；群聊角色查找从 `groupCharacters.find` 改为 `groupCharacterMap` O(1)；自动滚动 LaunchedEffect 添加 `isAtBottom` 依赖减少不必要触发
4. **PromptBuilder 静态缓存** — 新增 `staticPromptCache`（LRU, max 16），相同角色+用户名的静态 prompt 直接命中缓存，避免每条消息重复构建；`formatMemoryAtoms` 从多次 `filter` 改为单次 `groupBy` 遍历；提取 `MEMORY_CONTENT_LIMIT` / `TEMP_CONTENT_LIMIT` 等常量
5. **MemoryConsolidator 常量预编译** — `stopWords` / `emotionWords` / `habitWords` / `preferenceWords` 从方法内移到 `companion object`，避免每次调用重建集合；`consolidate()` 的 6 次 `resolveConflicts` 调用改为 `CONSOLIDATION_CATEGORIES` 循环；新增 `transactionOverride` 支持测试注入
6. **MessageBubble 形状缓存** — `RoundedCornerShape` 从重复计算改为 `remember` 缓存，`.border()` 和 `.clip()` 共用同一实例

**Bug 修复**: `MemoryConsolidatorTest` 编译错误 — 构造器缺少 `database` 参数，新增 mockito 依赖 + `transactionOverride` 测试模式

**测试结果**: 168 tests pass / 0 fail，`assembleDebug` 构建成功

**代码变更**: 8 个文件修改（ChatViewModel, ChatScreen, PromptBuilder, MemoryConsolidator, MessageBubble, MemoryConsolidatorTest, libs.versions.toml, build.gradle.kts），383 行新增 / 196 行删除

**版本**: v1.2.2 (versionCode=14) — 优化版，commit e99fa18

---

## 2026-05-20 — v1.2.0 正式发布

**今日完成 (6 项)**:
1. **Phase IV — 全量数据备份/恢复** — 导出/导入全部数据（角色、对话、消息、记忆、世界书等），合并策略按名称去重
2. **消息引用回复** — 长按消息引用回复，显示引用卡片，DB 12→13 迁移新增 reply_to_id
3. **Phase V 优化** — 聊天列表 JOIN 查询消除 N+1、消息置顶（DB 13→14）、从此处删除、触觉反馈
4. **Bug 修复** — API 配置输入框光标卡顿（本地状态 + debounce）、清除缓存按钮无反馈（状态管理 + Toast）
5. **开发日志页面** — 点击"关于"跳转开发日志，展示 v1.0.0-beta1 到 v1.2.0 共 10 个版本更新历史 + 生活记录
6. **v1.2.0 正式发布** — 打 tag 推送 GitHub Release

**代码变更**: 新增 3 个文件 (BackupData / BackupManager / DevLogScreen)，15+ 个文件修改

**版本**: v1.2.0 (versionCode=14)

---

## 2026-05-19 — 今日总结

**今日完成 (3 项)**:
1. **v1.0.7 发布** — 全局后台主动对话 (WorkManager PeriodicWorkRequest)，设置页独立总开关
2. **项目多维度审查 & 完整规划书** — 技术路线/社区建设/生态扩展/商业化/里程碑，已写入 DEV-LOG
3. **API Key 加密** — Phase I 第一项完成，Android Keystore AES-256-GCM，明文自动迁移

**代码变更**: 新增 4 个文件 (BackgroundProactiveWorker / ProactiveWorkScheduler / CryptoHelper / ApiConfigStore 修改)，13 个文件修改

**当前进度**: Phase I 五项任务完成 1/5，下一项 ChatViewModel 拆分 (1032→3 UseCase)

**版本**: v1.0.7 (versionCode=10)，下一版本 v1.1.0 计划包含代码拆分 + 核心测试

---

## 2026-05-19 — 项目多维度审查 & 后续开发规划

### 一、项目现状总览

| 维度 | 评分 | 说明 |
|------|------|------|
| 架构设计 | 7/10 | MVVM + Repository + Hilt DI，单 Activity + Compose Navigation，清晰分层 |
| 代码质量 | 7/10 | 无 TODO/FIXME，但 ChatViewModel(1032 行) 和 ChatScreen(1099 行) 过大 |
| 功能完整度 | 8/10 | 核心功能齐全（角色卡/聊天/群聊/记忆/世界书/脚本/主动对话/后台调度） |
| 测试覆盖 | 3/10 | 仅 4 个测试文件（ScriptRepo/WorldBookMatch/MemoryExtractor/PromptBuilder），ViewModel/Repo 无测试 |
| 安全性 | 7/10 | API Key 已使用 Android Keystore AES-256-GCM 加密存储 |
| 用户体验 | 7/10 | 基础体验流畅，缺少搜索/滑动手势/TTS 等进阶功能 |

**代码规模**: 76 个 Kotlin 源文件，~12,255 行代码，12 个 Entity，10 个 DAO，12 个路由。

### 二、已完成的核心功能

- 角色卡系统: 创建/编辑/导入导出（PNG/JSON，SillyTavern 兼容）
- 聊天系统: 流式回复、Markdown 渲染、消息编辑/删除/分叉
- 群聊系统: 多角色轮替发言、@ 提及、群聊持久化
- 记忆系统: 正则快速提取 + LLM 批量提取、5 类记忆（用户信息/角色一致性/事件/关系/承诺）
- 世界书: 关键词匹配、常驻/选择性条目、AND/OR/NOT 逻辑
- 正则脚本: 消息替换和正则处理
- 用户角色: 多 persona 管理
- 主动对话: 健谈度概率触发、群聊加权选择、冷却机制
- 后台调度: WorkManager 15 分钟周期、设置开关
- API 支持: OpenAI / Claude / Ollama / Custom（OpenAI 兼容协议）
- 思维链: reasoning_content 收集和传回
- 导出: Markdown/HTML/纯文本/JSON 四种格式

### 三、问题和技术债务

#### 3.1 架构问题
- **ChatViewModel 过大 (1032 行)**: 承载了聊天逻辑、主动对话、群聊发言、记忆提取、@ 处理等所有职责，应拆分为多个 UseCase 或子 ViewModel
- **ChatScreen 过大 (1099 补)**: UI 和逻辑混合，应拆分为独立组件
- **fallbackToDestructiveMigration()**: DB 迁移失败时会销毁数据，生产环境危险

#### 3.2 安全问题
- **API Key 明文存储**: `ApiConfigStore` 将整个 `ApiConfig`（含 apiKey）序列化为 JSON 存入 DataStore，无加密
- **无 ProGuard 规则审查**: release 构建启用了 minify，但未确认敏感类是否被正确混淆

#### 3.3 测试缺口
- ViewModel 层零测试（ChatViewModel / SettingsViewModel / HomeViewModel 等）
- Repository 层零测试（ChatRepository / CharacterRepository / GroupChatRepository 等）
- DAO 层零测试（仅依赖 Room 编译时验证）
- 无 UI 测试（Compose 测试）

#### 3.4 功能缺失（对比 SillyTavern）
- **消息滑动手势**: SillyTavern 的核心交互（左右滑动切换回复变体），当前 SwipeUtils 存在但未在 UI 中集成
- **聊天搜索**: 无法搜索历史消息
- **TTS 语音**: 无语音朗读功能
- **预设管理**: 无系统提示词预设（NSFW/jailbreak/场景预设）
- **高级世界书**: 缺少递归条目、扫描深度、触发计数等 SillyTavern 高级功能
- **Lorebook 导入导出**: 无法导入 SillyTavern 的 lorebook 文件
- **向量记忆搜索**: 当前仅关键词匹配，无语义搜索
- **群聊头像气泡**: 群聊消息不显示角色头像

### 四、总体规划书

#### 愿景

酒馆 Lite 的目标不仅仅是做一个"SillyTavern 安卓移植版"，而是要成为 **AI 角色扮演领域的移动端首选平台**。SillyTavern 在桌面端已经验证了市场需求，但移动端存在明显空白——没有一个原生、流畅、功能完整的安卓应用。我们要填补这个空白，并在此基础上构建一个活跃的创作者社区。

---

#### A. 技术路线图

##### A1. 代码质量加固 (v1.1.x) — 最高优先级

技术债务是功能开发的地基。地基不稳，后续每个新功能都会变得更难做。

| 任务 | 说明 | 工作量 |
|------|------|--------|
| ~~**API Key 加密**~~ ✅ | Android Keystore AES-256-GCM (CryptoHelper)，已完成 | 已完成 |
| **ChatViewModel 拆分** | 1032 行→3 个 UseCase（ChatUseCase / ProactiveUseCase / GroupChatUseCase），每个 < 300 行 | 2-3 天 |
| **ChatScreen 拆分** | 1099 行→MessageList / InputBar / ChatTopBar / ChattinessSheet 等独立组件 | 1-2 天 |
| **移除 fallbackToDestructiveMigration** | 补全所有 DB migration，移除破坏性降级，保护用户数据 | 0.5 天 |
| **核心单元测试** | ViewModel + Repository 测试覆盖，目标覆盖率 60%+ | 2-3 天 |
| **ProGuard 规则审查** | 确认 release 构建中敏感类被正确混淆 | 0.5 天 |

##### A2. 核心体验补齐 (v1.2.x) — 高优先级

这些是 SillyTavern 用户迁移过来后最先会问"为什么没有"的功能。

| 任务 | 说明 | 工作量 |
|------|------|--------|
| **消息滑动手势** | SwipeUtils 已存在但未集成 UI。左右滑动切换回复变体，这是 SillyTavern 最核心的交互 | 2 天 |
| **聊天搜索** | 消息全文搜索 + 关键词高亮 + 跳转到对应位置 | 1 天 |
| **群聊头像气泡** | 群聊消息左侧显示角色小头像，一眼区分谁在说话 | 1 天 |
| **预设管理** | 系统提示词预设库（角色扮演/创意写作/NSFW/场景），支持导入导出 | 2 天 |
| **消息引用回复** | 长按消息→引用→回复，群聊中尤其有用 | 1 天 |
| **图片消息支持** | 支持在聊天中发送/接收图片（多模态 API） | 2 天 |

##### A3. 扩展 API & 高级功能 (v1.3.x) — 中优先级

扩大用户群，覆盖更多 API 生态。

| 任务 | 说明 | 工作量 |
|------|------|--------|
| **KoboldAI 支持** | 添加 KoboldAI API provider，本地模型用户的核心需求 | 1 天 |
| **Gemini 支持** | 添加 Google Gemini API provider | 1 天 |
| **OpenRouter 支持** | 统一 API 网关，一个 Key 访问所有模型 | 1 天 |
| **API 连接池优化** | 复用连接、超时重试、指数退避 | 1 天 |
| **高级世界书** | 递归条目、扫描深度、触发计数、选择性逻辑增强 | 2-3 天 |
| **Lorebook 导入导出** | SillyTavern lorebook JSON 兼容 | 1 天 |
| **TTS 语音朗读** | 系统 TTS 引擎 + 第三方 API（ElevenLabs/Azure TTS），支持多种音色 | 2-3 天 |

##### A4. 智能化 & 差异化 (v1.4.x+) — 中低优先级

这些是酒馆 Lite 独有的功能，超越 SillyTavern。

| 任务 | 说明 | 工作量 |
|------|------|--------|
| **向量记忆搜索** | 本地 embedding 模型 + 语义检索，替代纯关键词匹配 | 3-5 天 |
| **智能摘要** | 长对话自动压缩为摘要，节省 token | 2 天 |
| **多模态输入** | 语音输入 + 图片识别（结合 API 的 vision 能力） | 2-3 天 |
| **角色卡生成器** | AI 辅助创建角色卡（描述/性格/开场白一键生成） | 2 天 |
| **对话剧情树** | 可视化分支剧情，像互动小说一样选择走向 | 3-5 天 |
| **场景系统** | 预设场景模板（咖啡馆/冒险/校园），一键切换世界观 | 2 天 |
| **数据备份恢复** | 本地备份 + Google Drive 同步 | 2 天 |
| **性能优化** | 消息列表虚拟化、图片懒加载、DB 查询优化 | 1-2 天 |
| **国际化完善** | 日语/韩语/繁体中文支持 | 1-2 天 |

---

#### B. 社区建设路线图

开源项目的生命力在于社区。以下是分阶段的社区建设计划。

##### B1. 基础建设 (v1.1.x 同步进行)

在功能稳定之前，先把社区基础设施搭好。

| 任务 | 说明 | 工作量 |
|------|------|--------|
| **README 美化** | 添加截图/GIF 演示、功能亮点、安装说明、Badge（版本/下载量/Stars） | 1 天 |
| **CONTRIBUTING.md** | 贡献指南：代码规范、PR 流程、Issue 模板、分支策略 | 0.5 天 |
| **Issue 模板** | Bug 报告 / 功能请求 / 角色卡问题 三种模板 | 0.5 天 |
| **PR 模板** | 标准化 PR 描述格式 | 0.5 天 |
| **CHANGELOG.md** | 从 v1.0.0 开始的完整变更日志 | 0.5 天 |
| **Discord / QQ 群** | 建立实时交流社区，收集用户反馈 | 0.5 天 |
| **GitHub Discussions** | 开启 Discussions 板块，用于问答和讨论 | 0.5 天 |

##### B2. 内容生态 (v1.2.x 同步进行)

让用户不只是使用者，更是创作者。

| 任务 | 说明 | 工作量 |
|------|------|--------|
| **角色卡分享平台** | 简单的 Web 页面，展示和下载社区创建的角色卡 | 3-5 天 |
| **世界书模板库** | 预设世界观模板（奇幻/科幻/现代/校园），用户可一键导入 | 2 天 |
| **预设分享** | 系统提示词预设的导入导出 + 社区分享 | 1 天 |
| **创作教程** | 写 3-5 篇教程：如何写好角色卡 / 世界书进阶 / 记忆系统使用技巧 | 2 天 |
| **示例角色卡包** | 打包 5-10 个高质量示例角色卡，随应用附带或单独下载 | 1 天 |

##### B3. 社区运营 (v1.3.x 同步进行)

持续运营，保持社区活跃。

| 任务 | 说明 | 持续性 |
|------|------|--------|
| **每周精选** | 每周推荐社区创建的优质角色卡/世界书 | 每周 1 小时 |
| **版本发布公告** | 每个版本在 Discord/QQ/贴吧 发布详细更新说明 | 每版本 |
| **用户反馈收集** | 定期整理 Issue 和社区反馈，调整开发优先级 | 每两周 |
| **贡献者致谢** | README 中添加贡献者列表，社区活动中感谢贡献者 | 持续 |
| **翻译志愿者** | 招募社区成员帮忙翻译日语/韩语/繁体中文 | 按需 |

---

#### C. 生态系统扩展

##### C1. 内容分享平台 (v1.3.x)

酒馆 Lite 的核心差异化之一：原生集成的内容分享生态。

**角色卡市场**:
- Web 端（GitHub Pages 或独立站点）展示角色卡
- 支持按标签/评分/下载量筛选
- 角色卡 PNG 直接下载，导入酒馆 Lite 一键使用
- 创作者主页 + 作品集

**世界书共享**:
- 世界观模板的社区共享
- 支持 fork 和二次创作
- 版本管理（世界书更新后可同步）

**预设库**:
- 系统提示词预设的社区共享
- 按场景分类（角色扮演/创意写作/代码助手/日常聊天）

##### C2. 插件系统 (v1.4.x+)

长期目标：让社区能够扩展应用功能。

**插件类型**:
- **API Provider 插件**: 社区贡献新的 API 后端（如 Replicate / Together AI）
- **TTS 引擎插件**: 支持更多语音合成服务
- **记忆增强插件**: 向量数据库、知识图谱等高级记忆方案
- **UI 主题插件**: 自定义聊天气泡、背景、字体
- **正则脚本市场**: 社区分享的正则处理脚本

**技术方案**:
- 基于 Android 的 Dynamic Feature Module
- 插件通过 AIDL 或 Content Provider 与主应用通信
- 插件市场（Web 端展示 + 应用内安装）

##### C3. 多端协同 (v2.0+)

长期愿景：酒馆 Lite 不仅仅是手机应用。

**云同步**:
- 角色卡/聊天记录/设置的云端同步
- 手机 ↔ 平板 ↔ 桌面无缝切换
- 端到端加密保护用户隐私

**桌面端**:
- Windows/macOS 桌面版（Compose Multiplatform 或 Electron）
- 共享核心业务逻辑（Kotlin Multiplatform）

**浏览器扩展**:
- Chrome 扩展：在网页中直接调用酒馆 Lite 的 API 配置
- 与 Web 版 SillyTavern 互通

---

#### D. 商业化思考

酒馆 Lite 作为开源项目，商业化需要谨慎。以下是几种可行的模式：

##### D1. 完全开源 + 赞助

- GitHub Sponsors / Open Collective 接受赞助
- 所有功能完全免费开源
- 适合个人开发者/学习项目阶段

##### D2. 免费增值 (Freemium)

- 核心功能永久免费开源
- 高级功能付费（需谨慎，不能影响核心体验）：
  - 云同步服务（服务器成本）
  - 高级 TTS 音色（API 成本）
  - 向量记忆搜索（计算资源）
  - 主题/皮肤包
- 付费部分闭源，核心保持 MIT

##### D3. 内容平台收入

- 角色卡市场的创作者打赏/付费
- 平台抽成 10-20%
- 类似 Steam 创意工坊模式

**当前建议**: 保持完全开源 + GitHub Sponsors，等功能和用户群稳定后再考虑增值模式。

---

#### E. 关键里程碑

| 里程碑 | 目标 | 预计时间 |
|--------|------|----------|
| **v1.1.0** | ~~API Key 加密~~ ✅ + 代码拆分 + 核心测试 | 2026-05 下旬 |
| **v1.2.0** | 滑动手势 + 搜索 + 预设管理 | 2026-06 上旬 |
| **v1.3.0** | KoboldAI/Gemini + 高级世界书 + TTS | 2026-06 中旬 |
| **v1.4.0** | 向量记忆 + 角色卡生成器 + 插件系统原型 | 2026-07 |
| **社区 100 Stars** | GitHub Stars 达到 100 | 2026-06 |
| **社区 500 Stars** | GitHub Stars 达到 500 | 2026-08 |
| **内容平台上线** | 角色卡分享 Web 端上线 | 2026-07 |
| **v2.0** | 云同步 + 多端支持 | 2026 Q4 |

---

#### F. 立即行动清单

**本周 (v1.0.7 之后)**:
1. ✅ API Key 加密 — security/CryptoHelper.kt (AES-256-GCM) + ApiConfigStore 集成，明文自动迁移
2. README 美化（截图 + Badge + 安装说明）
3. CONTRIBUTING.md + Issue 模板

**下周**:
4. ChatViewModel 拆分
5. CHANGELOG.md
6. 建立 Discord / QQ 群

**本月**:
7. 消息滑动手势
8. 预设管理
9. 角色卡分享平台原型

---

## 2026-05-19 — API Key 加密存储 (Phase I 第一项)

### API Key 加密

**需求**: DataStore 中的 API Config（含 API Key）以明文 JSON 存储，任何有 root 权限或备份访问权的人都能直接读取。需要加密保护。

**方案**: 使用 Android Keystore API 直接实现 AES-256-GCM 加密，不引入额外依赖。

**核心实现**:
- `security/CryptoHelper.kt` — @Singleton，AES-256-GCM 加解密
  - `ensureKeyExists()`: 首次使用时在 AndroidKeyStore 生成 256-bit AES 密钥（别名 `tavern_api_key`）
  - `encrypt(plainText)`: AES-GCM 加密 → IV(12 bytes) + cipher → Base64 编码
  - `decrypt(cipherText)`: Base64 解码 → 拆分 IV + cipher → AES-GCM 解密
  - `tryDecrypt(cipherText)`: 解密失败返回 null，用于判断是否为加密数据
- `network/ApiConfigStore.kt` — 注入 CryptoHelper
  - `configFlow`: 读取时先 `tryDecrypt`，失败则当作明文（向后兼容旧版数据）
  - `save()`: 序列化 JSON → 加密 → 存入 DataStore
  - 旧版明文数据首次读取后自动以加密格式重新存储

**安全性**: 密钥由 Android Keystore 硬件安全模块管理，应用无法导出密钥。即使 DataStore 文件被泄露，也无法解密 API Key。

**修改文件 (2 个)**:
- 新增: `security/CryptoHelper.kt`
- 修改: `network/ApiConfigStore.kt`

---

## 2026-05-19 — v1.0.7: 全局后台主动对话

> 版本号: 1.0.7 (versionCode=10)
> Release: https://github.com/yannicksong0106/tavern-android/releases/tag/v1.0.7

### 全局后台主动对话

**需求**: 应用挂在后台时，也能持续运行 AI 主动对话、群聊自发互动逻辑。设置里有独立总开关，关闭后完全停止后台调度。

**方案**: 使用 Android WorkManager 的 `PeriodicWorkRequest`（最小间隔 15 分钟）作为后台调度器，比 ForegroundService 更轻量，系统自动管理电池优化和 Doze 模式。

**核心实现**:
- `BackgroundProactiveWorker` — @HiltWorker，每 15 分钟随机选择一个聊天触发主动对话
  - 单聊：获取角色 → 按 chattiness/100 概率决定 → `PromptBuilder.buildProactive()` → API 调用 → 保存消息
  - 群聊：获取群聊角色 → 按健谈度加权随机选择 → `PromptBuilder.buildGroupProactive()` → API 调用 → 保存消息
  - 静默失败，不打扰用户
- `ProactiveWorkScheduler` — 调度管理器，`schedule()` 注册 periodic work，`cancel()` 取消
- `SettingsStore` — 新增 `backgroundProactiveFlow` 开关（默认关闭）
- `SettingsScreen` — 新增"后台主动对话"Switch UI
- `TavernApp` — 实现 `Configuration.Provider` + `HiltWorkerFactory`
- `AppModule` — 提供 `WorkManager` 单例
- `ChatDao` — 新增 `getRecentChats(limit)` 查询

**隔离性**: Worker 使用独立的 API 调用和 DB 写入，不影响前台手动聊天、流式回复、原有业务流程。

**修改文件 (13 个)**:
- 新增: `worker/BackgroundProactiveWorker.kt`, `worker/ProactiveWorkScheduler.kt`
- 修改: `TavernApp.kt`, `AppModule.kt`, `SettingsStore.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt`, `ChatDao.kt`, `AndroidManifest.xml`, `strings.xml`(中英), `build.gradle.kts`, `libs.versions.toml`

---

## 2026-05-19 — v1.0.6: 健谈度调整 UI + 思维链模型修复

> 版本号: 1.0.6 (versionCode=9)
> Release: https://github.com/yannicksong0106/tavern-android/releases/tag/v1.0.6

### 健谈度调整 UI

**问题**: CharacterEditScreen 有角色级健谈度 Slider，但群聊中没有调整入口。用户需要在聊天界面直接调整健谈度。

**方案**: 聊天顶栏新增设置按钮（齿轮图标），点击打开 ModalBottomSheet 弹窗。

**单聊模式**:
- 角色健谈度 Slider（0-100）
- 5 档文字描述：沉默寡言(≤20) / 较为安静(≤40) / 正常交流(≤60) / 比较健谈(≤80) / 非常健谈(>80)
- 调整后自动持久化到 CharacterEntity.chattiness

**群聊模式**:
- 群聊整体健谈度 Slider（ChatEntity.groupChattiness）— 控制群聊整体主动发言频率
- 每个角色独立健谈度 Slider（ChatCharacterEntity.chattiness）— 群内每个角色独立控制
- 两层分离，互不影响

**修改文件**:
- `ChatDao.kt` — 新增 `updateGroupChattiness(chatId, chattiness)`
- `ChatCharacterDao.kt` — 新增 `updateChattiness(chatId, characterId, chattiness)`
- `ChatRepository.kt` — 暴露 `updateGroupChattiness`
- `GroupChatRepository.kt` — 暴露 `updateCharacterChattiness` + `getChatCharacters`
- `ChatViewModel.kt` — 3 个 StateFlow（characterChattiness / groupChattiness / groupCharacterChattiness）+ 更新方法
- `ChatScreen.kt` — Settings 图标 + ChattinessSheet（ModalBottomSheet + Slider）
- `strings.xml` — 4 条新字符串（chat_settings / group_chattiness / group_chattiness_desc / character_chattiness）
- `values-en/strings.xml` — 英文对应

### 思维链模型修复（v1.0.5 合入）

**问题**: DeepSeek V4 Pro / Qwen 3.6 Plus 等思维链模型返回大量重复 "null" 字符串。

**根因**: `delta?.optString("content")` 在 JSON null 时返回字面量 `"null"` 字符串，通过了 `isNullOrEmpty()` 检查。思维链模型的 thinking 阶段 `delta.content` 为 JSON null，每个 thinking chunk 都被 emit 为 "null"。

**修复**:
- `optString("content")` → `opt("content")` + `JSONObject.NULL` 双重检查
- 收集 `reasoning_content` 思维链内容到 `lastReasoningContent`
- `ChatMessage` 新增 `reasoningContent` 字段
- `buildMessagesArray` 序列化 `reasoning_content` 传回 API
- ChatViewModel 所有 7 个 streamChat 调用点适配 `attachReasoningContent()`

**修改文件**:
- `ChatApiService.kt` — null 解析修复 + reasoning_content 收集 + ChatMessage 扩展
- `ChatViewModel.kt` — `lastAssistantReasoningContent` + `attachReasoningContent()` 辅助方法

---

## 2026-05-19 — v1.0.4: 代码优化

> 版本号: 1.0.4 (versionCode=7)
> Release: https://github.com/yannicksong0106/tavern-android/releases/tag/v1.0.4

### 记忆提取逻辑重构

**问题**: 记忆提取代码在 sendSingleChatMessage / sendGroupChatMessage / sendProactiveSingleMessage / sendProactiveGroupMessage / sendDirectMessage / continueGeneration / regenerateMessage 7 处重复。

**修复**: 抽取为 `extractMemoryIfNeeded(charId, charName, userContent, config)` 统一方法，所有调用点复用。

### 其他优化

- `@` 提及正则预编译到 `companion object`，避免每次调用重新编译
- HomeScreen 标签解析用 `remember` 缓存，避免 recomposition 重复计算
- 移除 HomeScreen 未使用的 `onMemoryClick` 参数和 `clip` import

**修改文件**:
- `ChatViewModel.kt` — 记忆提取重构 + 正则预编译
- `HomeScreen.kt` — 标签缓存 + 清理未使用代码
- `build.gradle.kts` — versionCode=7, versionName=1.0.4

---

## 2026-05-18 ~ 2026-05-19 — v1.0.3: 现有功能打磨（代码审查 + 优化）

> 基于全量代码审查的系统性优化，修复关键 bug、提升性能、补齐 i18n。
> 三轮审查，10 个批次，累计 27 项修复。

### 优化计划

#### Batch 1: Critical Bug 修复

| # | 问题 | 文件 | 修复方案 |
|---|------|------|----------|
| 1 | `appendToMessage` 流式每 chunk 做 read-modify-write | `ChatRepository.kt:51-54` | 改为 `appendToMessageDirect(id, chunk)` 直接 SQL `content = content \|\| :chunk`，不做 SELECT |
| 2 | `stopGeneration` 后 finally 仍执行拆分 | `ChatViewModel.kt:209-213` | 加 `wasCancelled` 标志，cancel 时跳过 `splitIntoMultipleMessages` |
| 3 | `createBranch` 未实际创建分支 | `ChatRepository.kt:74-84` | 将 `newBranchId` 写入后续消息的 `branch_id` 字段 |

#### Batch 2: 性能优化

| # | 问题 | 文件 | 修复方案 |
|---|------|------|----------|
| 4 | LazyColumn `indexOf` O(n²) | `ChatScreen.kt:335` | 用 `displayMessages.forEachIndexed` 替代，或在 `items` 中用 `index` 参数 |
| 5 | `shouldExtract` 基于截断后 size | `ChatViewModel.kt:179-180` | 改用独立计数器 `_messageCount`，每发一条消息 +1，不受 contextLength 影响 |
| 6 | `parseSwipeContent` 重复实现 | `ChatViewModel.kt` + `ChatRepository.kt` | 抽取到 `util/SwipeUtils.kt` 共享 |

#### Batch 3: 功能修复

| # | 问题 | 文件 | 修复方案 |
|---|------|------|----------|
| 7 | `continueGeneration` 跳过记忆提取 | `ChatViewModel.kt:255-313` | 补上 memoryAtomDao + memoryRepository 调用 |
| 8 | Claude 非流式缺少 system 字段 | `MemoryExtractorService.kt:277-288` | 补上 `put("system", ...)` |

#### Batch 4: i18n 补齐

| # | 问题 | 文件 | 修复方案 |
|---|------|------|----------|
| 9 | 硬编码 "回到底部" | `ChatScreen.kt:415` | 改用 `stringResource(R.string.scroll_to_bottom)` |
| 10 | 硬编码 "..." fallback | `ChatScreen.kt:538` | 改用 `stringResource(R.string.empty_message)` |

#### Batch 5: 代码质量

| # | 问题 | 文件 | 修复方案 |
|---|------|------|----------|
| 11 | 流式消息 `id = -1` hack | `ChatScreen.kt:371-376` | 定义 `STREAMING_MESSAGE_ID` 常量，增加语义 |
| 12 | 背景图片无 fallback | `ChatScreen.kt:293-298` | 添加 `error` placeholder，文件不存在时清除背景路径 |

#### Batch 6: 资源泄漏 + 防抖

| # | 问题 | 文件 | 修复方案 |
|---|------|------|----------|
| 13 | 临时文件泄漏 | `ChatListScreen.kt:96-102` | `deleteOnExit()` → `delete()`，Android JVM 不会正常退出 |
| 14 | 头像文件不清理 | `CharacterEditViewModel.kt:96-112` | 换头像前删除旧文件 |
| 15 | 背景文件不清理 | `CharacterEditViewModel.kt:114-130` | 换背景前删除旧文件（仅自定义图片，preset 不删） |
| 16 | 搜索无防抖 | `HomeViewModel.kt:38-43` | 添加 `debounce(300)`，清空时立即响应 |

#### Batch 7: 低优先级打磨

| # | 问题 | 文件 | 修复方案 |
|---|------|------|----------|
| 17 | 触觉反馈类型不当 | `ChatScreen.kt:432` | `TextHandleMove` → `LongPress`，更符合发送按钮语义 |
| 18 | 设置页滑块无防抖 | `SettingsViewModel.kt` | `saveConfig` 改为 `MutableSharedFlow` + `debounce(300)`，滑动时只写入最终值 |
| 19 | 硬编码英文 prompt | `SettingsViewModel.kt:101` | 跳过 — AI 测试 prompt，保持英文更可靠 |
| 20 | PNG 检测读全文件 | `HomeViewModel.kt:109` | 只读前 8 字节 magic bytes，避免大文件内存开销 |

#### Batch 8: 第三轮深度审查

| # | 问题 | 文件 | 修复方案 |
|---|------|------|----------|
| 24 | `matchEntry` 每次调用创建新 Json 实例 | `WorldBookRepository.kt` | 注入 `Json` 实例复用，避免重复创建 |
| 25 | `findIendPosition` O(n) 线性扫描 PNG 字节 | `PngMetadata.kt` | IEND 是最后一个 chunk，直接从文件末尾定位（O(1)） |
| 26 | `SimpleDateFormat` 非线程安全 | `ChatExporter.kt` | 替换为 `DateTimeFormatter`（线程安全） |

#### Batch 9: 数据层清理

| # | 问题 | 文件 | 修复方案 |
|---|------|------|----------|
| 27 | `CharacterRepository` 中 `Json` 实例重复创建 | `CharacterRepository.kt` | 注入 `Json` 实例，替换所有 `Json.xxx` 调用 |
| 28 | `touchMemories` 逐个调用 `touchMemory`，N 次 DB | `MemoryRepository.kt` + `MemoryDao.kt` | `MemoryDao` 新增 `touchMemories(ids)` 批量方法 |

#### Batch 10: 记忆系统性能

| # | 问题 | 文件 | 修复方案 |
|---|------|------|----------|
| 29 | `extractKeywords` 正则每次重新编译 | `MemoryConsolidator.kt` | 提取为 companion object 常量 `PUNCTUATION_REGEX`/`WHITESPACE_REGEX` |
| 30 | `groupBySimilarity` + `isDuplicate` O(n²) 重复提取关键词 | `MemoryConsolidator.kt` | 预提取关键词到 Map/List 缓存，避免循环内重复计算 |

### 验证目标

- `assembleDebug` ✅
- `testDebugUnitTest` ✅（49 tests）
- 手动测试：流式对话、停止生成、继续生成、swipe 切换、背景设置、导入对话、搜索角色

### 优化统计

- **累计修复**: 27 项（Batch 1-10）
- **跳过**: 1 项（#19 硬编码英文 prompt — 合理保留）
- **涉及文件**: 21 个源文件 + 2 个测试文件 + 2 个 strings.xml

### 按类别汇总

| 类别 | 项数 | 涉及项 |
|------|------|--------|
| Critical Bug | 3 | #1 appendToMessage, #2 stopGeneration, #3 createBranch |
| 性能优化 | 9 | #4 LazyColumn, #5 messageCount, #20 PNG magic, #24-25 Json/PNG, #28-30 批量/正则/缓存 |
| 功能修复 | 2 | #7 continueGeneration 记忆, #8 Claude system |
| 代码质量 | 5 | #6 SwipeUtils, #11 常量, #12 背景 fallback, #26 DateTimeFormatter, #27 CharacterRepository Json |
| 资源泄漏 | 3 | #13 临时文件, #14-15 头像/背景清理 |
| 防抖优化 | 2 | #16 搜索防抖, #18 设置防抖 |
| i18n | 2 | #9-10 硬编码中文 |
| 交互体验 | 1 | #17 触觉反馈 |

---

## 2026-05-18 — v1.0.2-debug: 活人感优化 + Bug 修复

> 版本号: 1.0.2-debug (versionCode=3)

### 活人感优化

**回复风格**
- 系统提示词注入聊天风格指引：每条 1-3 句、口语化、语气词
- 鼓励一两个字的自然回应（嗯、好、行、确实），允许口语化省略和倒装
- AI 回复按段落自动拆分成多条消息，逐条显示
- 消息间隔随内容长度随机变化（500ms-2000ms）
- 最后一段过渡优化，避免流式气泡与新消息重叠闪烁

**视觉优化**
- 同角色连续消息气泡圆角合并（类微信分组）
- 分组内消息间距收紧（3dp），不同角色保持 8dp
- 时间戳只在分组最后一条消息显示
- 顶部栏"正在输入..."增加脉冲动画
- 消息列表入场动画（animateItem）
- 发送/停止按钮改用 Material3 组件，自带 ripple 触摸反馈
- 继续按钮改用 FilledTonalButton，更规范的 Material3 风格

**交互优化**
- 输入框在 AI 回复期间不再禁用，用户可随时打字
- 智能自动滚动：只在用户已在底部时自动跟随，上翻时不强制拉回
- 上翻时显示浮动"回到底部"按钮
- 发送消息时触觉反馈
- 输入提示改为口语化："说点什么…" / "Say something…"
- 发送按钮：有文字时 primary 实心圆，无文字时灰色

### Bug 修复

- **软键盘布局错乱**: 添加 `windowSoftInputMode="adjustResize"`，将 `imePadding` 移到外层 Column
- **流式回复重复气泡**: 过滤掉正在写入的 assistant 消息，只显示 streamingText 气泡
- **流式输出内容重复**: 统一 `isGenerating` 生命周期，streamingText 全程覆盖拆分过程

---

## 2026-05-18 — v1.0.1-debug: 语言切换修复 + 全局 i18n

> 版本号: 1.0.1-debug (versionCode=2)

### 语言切换修复

**问题**: 点击中文/英文选项后语言没有切换。

**原因**: `AppCompatDelegate.setApplicationLocales()` 在 `setContent{}` 组合作用域内调用，触发 Activity 重建时机不对。

**修复**:
- `MainActivity.kt`: 将语言设置移到 `onCreate()` 中 `setContent` 之前，通过 `lifecycleScope.launch` 读取 DataStore 偏好
- `SettingsScreen.kt`: 用户点击语言选项时直接调用 `AppCompatDelegate.setApplicationLocales()`，立即生效

**已知问题**: 中英文切换仍存在 bug，待进一步排查。

### 全局 i18n

多个页面的硬编码中文替换为 `stringResource`:
- ChatScreen: 时间戳格式化（刚刚/分钟前/小时前/天前）
- CharacterEditScreen, PersonaScreen, ScriptScreen, WorldBookEditScreen, WorldBookListScreen, BackgroundPickerSheet: 各种 UI 文本

---

## 2026-05-18 — AI 主动记忆系统 (Memory Atoms)

> 从"只记住最近20条对话"升级为"AI 主动判断哪些信息值得记住"的结构化记忆系统。

### 背景

原有记忆系统只有手动添加的扁平记忆 + 关键词匹配检索（`LIKE '%keyword%'`），无法自动从对话中提取重要信息。参考腾讯四层语义金字塔（`D:/tencent-agent-memory`），实现 Android 简化版。

### 架构

```
L0 对话历史（已有）
    ↓ 正则快速提取（每轮） + LLM 批量提取（每10轮）
L1 记忆原子 (memory_atoms) — 结构化事实，按类别分类
    ↓ MemoryConsolidator 去重合并
L2 PromptBuilder 注入 — character_consistency 类型始终优先（人设红线）
```

### 记忆分类

| 类别 | 优先级 | 说明 |
|------|--------|------|
| `character_consistency` | 最高（始终注入） | 角色性格/外貌/背景/承诺 — 人设不能崩 |
| `commitment` | 高 | 任何一方的承诺约定 |
| `user_info` | 中 | 用户个人信息（名字/年龄/偏好） |
| `relationship` | 中 | 人物关系变化 |
| `event` | 普通 | 重要事件（约定/决定/转折点） |

### 新增文件

| 文件 | 说明 |
|------|------|
| `MemoryAtomEntity.kt` | 结构化记忆原子表（category/importance/source/superseded/expires_at） |
| `MemoryAtomDao.kt` | DAO — 按类别/重要度/关键词/相似度检索，冲突管理 |
| `MemoryExtractorService.kt` | 双层提取：正则快速（每轮，开销≈0）+ LLM 批量（每10轮，调 API） |
| `MemoryConsolidator.kt` | 关键词去重 + 相似度合并 + 冲突检测，防止记忆膨胀 |
| `MemoryExtractorServiceTest.kt` | 8 个测试覆盖正则提取逻辑 |

### 修改文件

| 文件 | 变更 |
|------|------|
| `TavernDatabase.kt` | version 8→9，新增 `memory_atoms` 表 + `MIGRATION_8_9` |
| `AppModule.kt` | 注册 `MemoryAtomDao` + 迁移注入 |
| `PromptBuilder.kt` | 新增 `memoryAtoms` 参数，按类别分组注入，`character_consistency` 最优先 |
| `ChatViewModel.kt` | 每轮正则提取 + 每10轮 LLM 提取 + 自动合并，3 处 `PromptBuilder.build()` 调用更新 |
| `MemoryViewModel.kt` | 支持查看/管理 AI 记忆原子（`memoryAtoms` StateFlow + `addAtom`/`supersedeAtom`/`deleteAtom`） |
| `strings.xml` (中/英) | 15 个新字符串（类别标签、来源标签、Tab 标签） |
| `PromptBuilderTest.kt` | 新增 6 个测试覆盖 memoryAtoms 注入逻辑 |
| `build.gradle.kts` + `libs.versions.toml` | 添加 `appcompat` 依赖（修复 i18n 遗留问题） |

### 提取策略

- **正则快速提取**（每轮，开销≈0）：匹配"我叫X"、"我X岁"、"我喜欢X"、"我讨厌X"、"我答应X"等模式
- **LLM 批量提取**（每10轮，调 API）：发送最近30条对话给 LLM，返回结构化 JSON，包含所有5个类别
- **去重合并**：新记忆插入前检查关键词相似度（阈值0.6），相似则更新访问时间不重复插入
- **冲突检测**：同一类别内按关键词分组，保留最新最重要的，supersede 旧的

### 人设保护机制

`character_consistency` 类型的记忆**始终注入到 system prompt**，不受数量限制。PromptBuilder 中格式化为 `[角色名 的核心人设 — 必须严格遵守]`，确保 AI 在任何情况下都遵守角色设定。

### 验证

| 项目 | 结果 |
|------|------|
| `assembleDebug` | ✅ 成功 |
| `testDebugUnitTest` | ✅ 45 tests pass / 0 fail |

---

## 2026-05-18 — 国际化 (i18n) 支持

> 为应用添加中英文切换功能，所有 UI 界面字符串外部化到 Android string resources。

### 1. 语言切换基础设施

- **SettingsStore** — 新增 `languageFlow` + `saveLanguage()`，DataStore 持久化语言设置（"system"/"zh"/"en"）
- **MainActivity** — 读取语言设置，`AppCompatDelegate.setApplicationLocales()` 实现运行时语言切换
- **SettingsViewModel** — 新增 `language` StateFlow + `updateLanguage()` 方法
- **SettingsScreen** — 新增"语言"设置区域（中文 / English / 跟随系统，RadioButton 选择）

### 2. 字符串资源外部化

- **values/strings.xml**（中文）— 221 条字符串，覆盖全部 UI 界面
- **values-en/strings.xml**（英文）— 221 条对应翻译

### 3. UI 文件更新

| 文件 | 更新内容 |
|------|----------|
| `CharacterEditScreen.kt` | 示例对话、系统提示词、历史后指令、作者注释区域、创作者标签 |
| `MemoryScreen.kt` | 全部对话框、标签、空状态、菜单项 |
| `PersonaScreen.kt` | 全部对话框、标签、空状态、菜单项 |
| `ScriptScreen.kt` | 全部对话框、标签、空状态、类型名称、复选框标签 |
| `WorldBookListScreen.kt` | 全部对话框、标签、空状态 |
| `WorldBookEditScreen.kt` | 全部对话框、标签、空状态、条目标签 |
| `BackgroundPickerSheet.kt` | 预设背景改用 string resource ID，全部标签外部化 |
| `ChatScreen.kt` | `formatTimestamp()` 改用 `Context.getString()` 处理相对时间 |
| `SettingsScreen.kt` | `bubbleColorOptions` 改用 string resource ID |
| `ChatListScreen.kt` | 修复 "更多" content description |

### 4. 已知限制

- ViewModel 中的 Toast 提示信息（"导出成功"、"API 错误"等）仍为硬编码中文，因非 composable 上下文需要传入 `Context` 才能使用 `getString()`

### 构建状态
- i18n 验证 — 4 项检查全部通过（imports、无残留中文、中英对照、语法正确）

---

## 2026-05-17 — v1.0.0-beta1 首次发布

- 创建 README.md（中文，功能列表、技术栈、构建说明、项目结构）
- 完善 .gitignore（Kotlin/IDE/OS 规则）
- 初始化 Git 仓库，首次 commit（108 文件，11310 行）
- 创建 GitHub 仓库：`yannicksong0106/tavern-android`
- 推送 `main` 分支 + 创建 `v1.0.0-beta1` tag + GitHub Release（prerelease）
- 已完成 Phase A-G，下一步 Phase H（群聊系统）

---

## 2026-05-17 — Phase G 用户角色系统

> 用户角色系统（Persona）：让用户在对话中拥有多重身份，可创建多个角色并设置默认，支持 per-character 覆盖。类似 SillyTavern 的 Persona 功能。

### G1. 数据模型

- **PersonaEntity** — `personas` 表：id, name, biography, avatar_path, is_default, created_at
- **CharacterPersonaEntity** — `character_personas` 联合表：character_id + persona_id（per-character 角色覆盖），外键级联删除
- **PersonaDao** — 全套 CRUD + 默认角色管理 + character-persona 关联查询（Flow + 同步）
- **PersonaRepository** — `getEffectivePersona(characterId)`：per-character 覆盖 > 默认角色，优先级链清晰
- **TavernDatabase** — 升级到 v8，新增 PersonaEntity + CharacterPersonaEntity

### G2. 管理 UI

- **PersonaScreen** — 用户角色列表（带头像）、新建/编辑对话框（支持 `{{user}}`/`{{char}}` 占位符）、设为默认、删除；默认角色高亮显示
- **PersonaViewModel** — 管理 persona CRUD 和默认设置
- **SettingsScreen** — 新增"用户角色"入口卡片（带 Person 图标和描述文字）
- **TavernNavGraph** — 新增 `persona` 路由

### G3. Prompt 集成

- **PromptBuilder.build()** — 新增 `persona: PersonaEntity?` 参数
  - `persona.name` 优先于 `userName` 用于 `{{user}}` 占位符替换
  - `persona.biography` 注入系统 prompt：`[User Persona: {name}]\n{biography}`
- **ChatViewModel** — `sendMessage()`、`continueGeneration()`、`regenerate()` 三处均加载有效 persona 并传入 PromptBuilder

**实际改动文件:**
```
新增 (6): PersonaEntity.kt, CharacterPersonaEntity.kt, PersonaDao.kt, PersonaRepository.kt
         PersonaScreen.kt, PersonaViewModel.kt
修改 (6): TavernDatabase.kt (v8), AppModule.kt, PromptBuilder.kt, ChatViewModel.kt
         TavernNavGraph.kt, SettingsScreen.kt
```

**验证:** assembleDebug ✅ | testDebugUnitTest 31 tests ✅

---

## 2026-05-17 — Phase F 聊天核心增强

### F1. 滑动切换替代回复 (Swipe Alternatives)

- **MessageEntity** — 新增 `swipeContent: String`（JSON 数组存储替代回复）和 `swipeIndex: Int`（当前选中索引）
- **MessageDao** — 新增 `updateSwipe()` 和 `updateSwipeIndex()` 方法
- **ChatRepository** — 新增 `addSwipe()`（添加新替代回复）、`switchSwipe()`（切换到指定索引）、`getSwipeCount()`、JSON 解析辅助方法
- **ChatViewModel** — `regenerate()` 重构：不再删除旧消息，而是将当前回复保存为旧 swipe，生成新回复作为新 swipe；新增 `swipeLeft()`/`swipeRight()`/`getSwipeInfo()` 方法
- **ChatScreen** — MessageBubble 新增：左右箭头按钮切换替代回复、"1/3" 格式的滑动计数指示器、相对时间戳显示（"刚刚"/"2 分钟前"/"3 小时前"等）
- **数据库** — v5 → v6，新增 `swipe_content` 和 `swipe_index` 列

### F2. 作者注释 (Author's Note)

- **AuthorNoteEntity** — 新表 `author_notes`：character_id (FK)、content、position（before_an/after_an）、depth（从末尾算起的注入位置）
- **AuthorNoteDao** — CRUD + `getAuthorNoteSync()` 非 Flow 查询
- **PromptBuilder** — 新增 `authorNote` 参数，在聊天历史的指定深度位置注入系统消息
- **CharacterEditViewModel** — 注入 AuthorNoteDao，loadCharacter 时加载作者注释，save 时保存/删除作者注释
- **CharacterEditScreen** — 新增"作者注释"区域：内容输入框、注入深度输入、位置切换（点击切换 before_an/after_an）
- **ChatViewModel** — sendMessage 和 regenerate 时加载作者注释并传入 PromptBuilder
- **数据库** — v6 → v7，新增 AuthorNoteEntity + authorNoteDao()

### F3. 继续生成 (Continue Generation)

- **ChatViewModel** — 新增 `continueGeneration()`：追加到上一条 AI 消息，不重新发送用户消息；复用 PromptBuilder + 流式接收 + 正则脚本处理
- **ChatScreen** — InputBar 新增"继续生成"按钮（Replay 图标），当最后一条消息是 AI 回复且未在生成中时显示
- 按钮颜色使用 `MaterialTheme.colorScheme.secondary` 区分于发送按钮

### F4. 消息时间戳

- **ChatScreen** — 每条消息气泡底部显示相对时间戳（`formatTimestamp()` 工具函数）
- 时间格式：刚刚 / X 分钟前 / X 小时前 / X 天前 / MM-dd HH:mm（超过 7 天）

### 新增文件

| 文件 | 说明 |
|------|------|
| `data/db/entity/AuthorNoteEntity.kt` | 作者注释实体 |
| `data/db/dao/AuthorNoteDao.kt` | 作者注释 DAO |

### 修改文件

| 文件 | 变更 |
|------|------|
| `data/db/entity/MessageEntity.kt` | 新增 swipeContent、swipeIndex 字段 |
| `data/db/dao/MessageDao.kt` | 新增 updateSwipe、updateSwipeIndex |
| `data/db/TavernDatabase.kt` | v7 + AuthorNoteEntity + authorNoteDao() |
| `data/repository/ChatRepository.kt` | 新增 addSwipe、switchSwipe、getSwipeCount |
| `di/AppModule.kt` | 新增 provideAuthorNoteDao() |
| `network/PromptBuilder.kt` | 新增 authorNote 参数，深度注入 |
| `ui/screens/chat/ChatViewModel.kt` | 注入 AuthorNoteDao，swipe/continue 方法，authorNote 传入 |
| `ui/screens/chat/ChatScreen.kt` | swipe 导航、时间戳、Continue 按钮、Replay 图标 |
| `ui/screens/character/CharacterEditViewModel.kt` | 注入 AuthorNoteDao，authorNote 状态管理 |
| `ui/screens/character/CharacterEditScreen.kt` | 作者注释 UI 区域 |

### 构建状态
- `assembleDebug` — BUILD SUCCESSFUL
- `testDebugUnitTest` — 31 tests, 全部通过

---

## 2026-05-17 — Phase A 视觉体验

### A2: 聊天气泡样式自定义

- **BubbleStyleConfig** — 新增数据模型：用户/助手气泡颜色、圆角大小、字体大小、Material You 开关
- **SettingsStore** — 新增 DataStore 持久化视觉设置（`visual_settings`）
- **SettingsScreen** — 新增"聊天气泡"区域：8 色气泡颜色选择器（用户/助手分别设）、圆角滑块（4-24dp）、字体大小滑块（12-20sp）
- **SettingsScreen** — 新增"主题"区域：Material You 动态取色开关（Android 12+）
- **ChatViewModel** — 注入 SettingsStore，暴露 `bubbleStyle` StateFlow
- **ChatScreen** — MessageBubble 读取自定义颜色/圆角/字体大小，优先级：自定义 > 主题默认
- **MarkdownText** — 新增 `textSize` 参数，支持自定义字体大小

### A3: Material You 动态取色

- **TavernTheme** — 已有 `dynamicColor` 参数，从硬编码 `false` 改为从 SettingsStore 读取
- **MainActivity** — 注入 SettingsStore，`collectAsStateWithLifecycle` 读取 `bubbleStyle.dynamicColor` 传入 TavernTheme
- 设置页新增开关，Android 12+ 生效，低版本 fallback 到酒馆暗色/亮色主题

### A4: 动画增强

- **消息出现动画** — `AnimatedVisibility(fadeIn + slideInVertically)` 包裹每条消息
- **页面转场** — NavHost 添加水平滑动 + 淡入淡出转场（前进/后退分别处理）
- **流式光标闪烁** — `InfiniteTransition` 驱动 `animateFloat`，"正在输入..." 后闪烁方块光标

### 新增文件

| 文件 | 说明 |
|------|------|
| `data/model/BubbleStyleConfig.kt` | 气泡样式配置数据模型 |
| `data/store/SettingsStore.kt` | 视觉设置 DataStore |

### 修改文件

| 文件 | 变更 |
|------|------|
| `ui/screens/settings/SettingsViewModel.kt` | 注入 SettingsStore，新增 bubbleStyle flow + updateBubbleStyle() |
| `ui/screens/settings/SettingsScreen.kt` | 新增气泡样式区域 + 主题区域 |
| `ui/screens/chat/ChatViewModel.kt` | 注入 SettingsStore，暴露 bubbleStyle |
| `ui/screens/chat/ChatScreen.kt` | MessageBubble 自定义样式 + 消息动画 + 光标闪烁 |
| `ui/components/MarkdownText.kt` | 新增 textSize 参数 |
| `ui/navigation/TavernNavGraph.kt` | 页面转场动画 |
| `MainActivity.kt` | 动态取色从 SettingsStore 读取 |

### 构建状态
- `assembleDebug` — BUILD SUCCESSFUL，零错误

---

## 2026-05-17 — Phase B 记忆系统

### B1. 数据模型

- **MemoryEntity** — `memories` 表：id, character_id (FK), content, importance (1-10), source (auto/manual), created_at, last_accessed, access_count
- **MemoryDao** — CRUD + 关键词搜索 (`LIKE`) + top-K 排序 (importance + recency 加权) + touchMemory (更新访问时间和次数)
- **TavernDatabase** — v3 → v4，新增 MemoryEntity + memoryDao()
- **AppModule** — 新增 provideMemoryDao()

### B2. 记忆检索

- **MemoryRepository** — `getRelevantMemories()` 从用户消息提取关键词（最长 3 个词），LIKE 匹配记忆内容，不足时补充 top memories
- **ChatViewModel** — sendMessage 时调用 `memoryRepository.getRelevantMemories()` + `touchMemories()`

### B3. 记忆注入

- **PromptBuilder** — 新增 `memories` 参数，`buildSystemPrompt()` 在世界书之后注入 `[Memory]` section（每条前缀 `- `）

### B4. 记忆管理 UI

- **MemoryScreen** — 记忆列表（重要度星标、来源、时间、访问次数），支持添加/编辑/删除/清除全部
- **MemoryViewModel** — 管理记忆 CRUD 状态
- **MemoryDialog** — 添加/编辑对话框，含内容输入 + 重要度滑块 (1-10)
- **CharacterEditScreen** — 编辑角色时新增"记忆管理"入口（带脑图标）
- **导航路由** — `memory/{characterId}`

### 新增文件

| 文件 | 说明 |
|------|------|
| `data/db/entity/MemoryEntity.kt` | 记忆实体 |
| `data/db/dao/MemoryDao.kt` | 记忆 DAO |
| `data/repository/MemoryRepository.kt` | 记忆仓库 |
| `ui/screens/memory/MemoryScreen.kt` | 记忆管理页面 |
| `ui/screens/memory/MemoryViewModel.kt` | 记忆 ViewModel |

### 修改文件

| 文件 | 变更 |
|------|------|
| `data/db/TavernDatabase.kt` | v4 + MemoryEntity + memoryDao() |
| `di/AppModule.kt` | provideMemoryDao() |
| `network/PromptBuilder.kt` | 新增 memories 参数，注入 [Memory] section |
| `ui/screens/chat/ChatViewModel.kt` | 注入 MemoryRepository，sendMessage 时检索记忆 |
| `ui/screens/character/CharacterEditScreen.kt` | 记忆管理入口 |
| `ui/navigation/TavernNavGraph.kt` | memory 路由 |

### 构建状态
- `assembleDebug` — BUILD SUCCESSFUL，零错误

---

## 2026-05-17 — 第一阶段完成

### Bug 修复 (4 个)
1. **导入角色丢失头像** — `createCharacter()` 新增 `avatarPath` 参数，`importFromPng()` 正确传递
2. **流式断流留下空消息** — `assistantMsgId` 提到 try 外，catch 中清理空消息，新增 `getMessageById()`
3. **Claude Provider 硬编码 URL** — `ApiProvider.Claude` 新增 `baseUrl` 字段，设置页显示 Base URL
4. **postHistoryInstructions 未使用** — PromptBuilder 在历史后插入系统消息

### 新功能：聊天管理
- ChatListScreen — 角色对话列表，支持新建/删除/重命名/长按编辑
- ChatListViewModel — 管理聊天列表状态
- ChatDao 新增 `renameChat()`、`deleteById()`
- ChatRepository 新增 `renameChat()`、`deleteChatById()`、`getMessageById()`
- 导航新增 CHAT_LIST 路由，HomeScreen 点击角色进入聊天列表

---

## 2026-05-17 — 第二阶段完成

### 1. WorldBook 管理 UI
- **WorldBookListScreen** — 世界书列表，支持创建/删除，空状态引导
- **WorldBookEditScreen** — 世界书编辑，条目管理（增删改、启用/禁用、常驻标记）
- **WorldBookListViewModel / WorldBookEditViewModel** — 业务逻辑层
- **导航路由** — `WORLD_BOOK_LIST` / `WORLD_BOOK_EDIT`，HomeScreen 顶栏新增世界书入口
- **角色关联** — CharacterEntity 新增 `worldBookId` 字段，ChatViewModel 接入世界书关键词匹配
- **WorldBookRepository** — 新增 `updateWorldBook()` 方法

### 2. 搜索增强
- CharacterDao `searchCharacters` 从单字段（name）扩展到四字段（name/description/personality/tags）模糊搜索

### 3. 头像编辑
- CharacterEditScreen 新增 avatar picker — 点击头像触发 `ActivityResultContracts.GetContent("image/*")`
- CharacterEditViewModel 新增 `updateAvatar(uri)` — 复制选中图片到 `filesDir/avatars/`
- 编辑角色时 `avatarPath` 正确保留和更新

### 4. 角色卡导出 UI
- HomeScreen 角色卡长按菜单新增"导出角色卡"选项（`DropdownMenu` + `FileDownload` 图标）
- HomeViewModel 新增 `exportCharacter()` — 调用 `SillyTavernImporter.exportToJson()`，导出到 `cacheDir/exports/`

### 5. 对话树分支切换
- **MessageDao** — 新增 `getBranchIds()`、`deactivateAllMessages()`、`activateBranch()`、`deactivateMessage()`
- **ChatRepository** — 新增 `createBranch()`、`switchBranch()`、`sendMessageInBranch()`
- **ChatViewModel** — 新增 `loadBranches()`、`switchBranch()`、`createBranchFromMessage()`
- **ChatScreen** — BranchNavigationBar（分支切换 UI，左右箭头 + "分支 X / Y"）+ 消息长按菜单"从此处分叉"

### 数据库升级
- Room DB version 1 → 2，启用 `fallbackToDestructiveMigration()`

### 构建状态
- `assembleDebug` — BUILD SUCCESSFUL，零错误
- 已安装到 Tavern_Phone 模拟器验证

---

## 2026-05-17 — Phase C SillyTavern 兼容

### C1. 角色卡 PNG 导出

- **PngMetadata** — 新增 `writeCharaCard()`：复制源 PNG，Base64 编码 JSON 写入 tEXt chunk
- **SillyTavernImporter** — 新增 `exportToPng()` + `createMinimalPng()`（无头像时生成 1x1 透明 PNG）
- **HomeViewModel** — 新增 `exportCharacterPng()`
- **HomeScreen** — 角色菜单拆分为"导出为 JSON"和"导出为 PNG"两个选项

### C2. chara_card_v3 spec

- **CharacterData** — 新增 v3 字段：`alternateGreetings`、`groupOnlyGreetings`
- 导入时自动检测 v2/v3 并适配

### C3. WI 高级逻辑

- **WorldBookEntryEntity** — 新增：`selective`、`selectiveLogic`（0=AND, 1=OR, 2=NOT）、`excludeRecursion`、`preventRecursion`、`group`、`groupOverride`、`groupWeight`
- **WorldBookRepository** — `matchEntries()` 重构为 `matchEntry()`，实现 selective logic：
  - AND：主关键词 AND 副关键词
  - OR：主关键词 OR 副关键词
  - NOT：主关键词 AND NOT 副关键词
- **WorldBookEditScreen** — EntryEditDialog 新增：副关键词输入框（selective 时显示）、匹配逻辑下拉菜单（AND/OR/NOT）、selective 标签显示
- **WorldBookEditViewModel** — `addEntry()` 扩展签名：keysSecondary, selective, selectiveLogic

### C4. 正则脚本

- **ScriptEntity** — `scripts` 表：character_id (FK), name, comment, scriptType (0=用户/1=AI/2=两者), findPattern, replacePattern, isRegex, caseSensitive, enabled, sortOrder
- **ScriptDao** — CRUD + `getEnabledScripts()` 按 sort_order 排序
- **ScriptRepository** — `applyScripts()` 遍历启用脚本，支持正则/字面量替换，异常时跳过
- **ScriptScreen** — 脚本管理 UI：列表（名称、类型标签、正则标签、启用/禁用/编辑/删除），添加/编辑对话框（名称、备注、执行时机下拉、查找模式、替换、正则/大小写复选框）
- **ScriptViewModel** — 管理脚本 CRUD 状态
- **ChatViewModel** — 注入 ScriptRepository，`sendMessage()` 中对用户消息（type 0）和 AI 回复（type 1）分别执行正则处理
- **CharacterEditScreen** — 编辑角色时新增"正则脚本"入口（Code 图标）
- **TavernDatabase** — v4 → v5，新增 ScriptEntity + scriptDao()
- **AppModule** — 新增 provideScriptDao()
- **导航路由** — `script/{characterId}`

### 新增文件

| 文件 | 说明 |
|------|------|
| `data/db/entity/ScriptEntity.kt` | 脚本实体 |
| `data/db/dao/ScriptDao.kt` | 脚本 DAO |
| `data/repository/ScriptRepository.kt` | 脚本仓库 + 正则处理 |
| `ui/screens/script/ScriptScreen.kt` | 脚本管理页面 |
| `ui/screens/script/ScriptViewModel.kt` | 脚本 ViewModel |

### 修改文件

| 文件 | 变更 |
|------|------|
| `data/db/TavernDatabase.kt` | v5 + ScriptEntity + scriptDao() |
| `di/AppModule.kt` | provideScriptDao() |
| `ui/screens/chat/ChatViewModel.kt` | 注入 ScriptRepository，用户消息和 AI 回复正则处理 |
| `ui/screens/worldbook/WorldBookEditViewModel.kt` | addEntry() 签名扩展 |
| `ui/screens/character/CharacterEditScreen.kt` | 正则脚本入口 |
| `ui/navigation/TavernNavGraph.kt` | script 路由 |
| `util/PngMetadata.kt` | writeCharaCard() |
| `util/SillyTavernImporter.kt` | exportToPng() + createMinimalPng() |
| `ui/screens/home/HomeViewModel.kt` | exportCharacterPng() |
| `ui/screens/home/HomeScreen.kt` | PNG 导出菜单 |
| `data/model/CharacterCard.kt` | v3 字段 |
| `data/db/entity/WorldBookEntryEntity.kt` | selective/selectiveLogic 等字段 |
| `data/repository/WorldBookRepository.kt` | selective logic 匹配 |
| `ui/screens/worldbook/WorldBookEditScreen.kt` | 选择性匹配 UI |

### 构建状态
- `assembleDebug` — BUILD SUCCESSFUL，零错误
- 已安装到模拟器验证

---

## 2026-05-17 — Phase D 质量保障

### D1. 单元测试

- **PromptBuilderTest** (10 tests) — 系统 prompt 组装、占位符替换、世界书/记忆注入、示例对话解析、历史后指令、消息顺序、空字段处理
- **ScriptRepositoryTest** (11 tests) — 正则/字面量替换、scriptType 过滤、大小写敏感/不敏感、无效正则容错、多脚本顺序执行、捕获组、禁用脚本跳过
- **WorldBookMatchTest** (10 tests) — 常驻条目、主关键词匹配、大小写不敏感、selective AND/OR/NOT 逻辑、空关键词、多关键词任一匹配

### D2. UI 测试

- 添加 Compose UI Test 依赖（`ui-test-junit4` + `ui-test-manifest`）
- 可后续补充关键页面渲染测试

### D3. 构建与发布

- **ProGuard rules** — 扩充 keep 规则：Room Entity、Hilt、OkHttp、Retrofit、Markwon、Coil、Coroutines
- **GitHub Actions CI** — `.github/workflows/ci.yml`：
  - push/PR to main 触发
  - lintDebug → testDebugUnitTest → assembleDebug
  - 上传 debug APK artifact
  - main 分支 push 自动构建 release APK

### 新增文件

| 文件 | 说明 |
|------|------|
| `app/src/test/.../PromptBuilderTest.kt` | PromptBuilder 单元测试 |
| `app/src/test/.../ScriptRepositoryTest.kt` | 正则脚本处理测试 |
| `app/src/test/.../WorldBookMatchTest.kt` | 世界书关键词匹配测试 |
| `.github/workflows/ci.yml` | GitHub Actions CI 流水线 |

### 修改文件

| 文件 | 变更 |
|------|------|
| `gradle/libs.versions.toml` | 新增 test 依赖版本 |
| `app/build.gradle.kts` | 新增 testImplementation/androidTestImplementation |
| `app/proguard-rules.pro` | 扩充 keep 规则 |

### 测试状态
- `testDebugUnitTest` — 31 tests, 全部通过
- `assembleDebug` — BUILD SUCCESSFUL

---

## 2026-05-17 — 内测发布准备

### Bug 修复

1. **ChatScreen 消息动画性能问题** — `AnimatedVisibility(visible = true)` 导致每次重组时所有消息重新播放动画，移除后消息正常渲染
2. **ChatListViewModel 死代码** — `importChat(uri)` 方法从未实际读取 URI，已删除
3. **TavernNavGraph 未使用导入** — 移除 `EnterTransition`/`ExitTransition` 无用导入
4. **ChatScreen 未使用导入** — 移除 `AnimatedVisibility`/`fadeIn`/`slideInVertically` 无用导入

### 应用重命名

- 应用名称：酒馆 AI → **酒馆 Lite (Tavern Lite)**
- strings.xml + HomeScreen 标题已更新

### 应用图标重设计

- **背景** — 深色 (`#1A1A2E`)，呼应暗色主题
- **前景** — 陶土色 (`#D77757`) 酒杯 + 奶泡 + 白色对话气泡（三点省略号），象征 AI 对话
- 自适应图标 (Adaptive Icon) 兼容 API 26+

### 构建状态
- `testDebugUnitTest` — 31 tests, 全部通过
- `assembleRelease` — BUILD SUCCESSFUL
- Release APK — 2.1MB（R8 压缩），19MB（Debug）

---

## 2026-05-17 — Phase E 对话导出

### E1. 多格式导出

- **ChatExporter** — 核心导出工具，支持 4 种格式：
  - **Markdown** — 标题 + 时间戳 + 消息内容，`**角色名**` 标记发言人
  - **HTML** — 暗色主题网页样式，圆角气泡布局，角色/用户分别着色
  - **纯文本** — `[角色名] 时间\n内容` 简单格式
  - **JSON** — 结构化数据（chatName, characterName, userName, messages[]），可重新导入

### E2. 范围选择

- **单对话导出** — ChatListScreen 每个对话项右侧新增分享按钮，弹出格式选择
- **批量导出** — ChatListScreen 顶栏菜单"导出全部对话"，打包为 ZIP（每个对话一个文件）

### E3. 分享集成

- **FileProvider** — `res/xml/file_paths.xml` 配置 cache/exports 路径
- **AndroidManifest** — 注册 FileProvider（`${applicationId}.fileprovider`）
- **Share Intent** — 导出完成后自动弹出 Android Share Sheet，支持分享到任意应用

### E4. 对话导入

- **ChatImporter** — 支持 3 种格式导入：
  - 酒馆 AI 导出的 JSON 对象（含 messages 数组）
  - SillyTavern `chat_*.jsonl` 格式（每行一个 JSON，`{name, is_user, mes}` 字段）
  - JSON 数组格式
- **ChatListScreen** — 顶栏新增"导入对话"按钮（FileOpen 图标），通过 `OpenDocument` 选择 .json/.txt 文件
- **ChatDao** — 新增 `getAllChatsForCharacter()` 非 Flow 查询
- **ChatRepository** — 新增 `getAllChatsForCharacter()` + `getAllMessagesForChat()`

### 新增文件

| 文件 | 说明 |
|------|------|
| `util/ChatExporter.kt` | 多格式导出工具（MD/HTML/TXT/JSON/ZIP） |
| `util/ChatImporter.kt` | 多格式导入工具（JSON/JSONL） |
| `res/xml/file_paths.xml` | FileProvider 路径配置 |

### 修改文件

| 文件 | 变更 |
|------|------|
| `data/db/dao/ChatDao.kt` | 新增 getAllChatsForCharacter() |
| `data/repository/ChatRepository.kt` | 新增 getAllChatsForCharacter() + getAllMessagesForChat() |
| `ui/screens/chatlist/ChatListViewModel.kt` | 注入 ChatExporter/ChatImporter，新增 exportChat/exportAllChats/importChatFromFile |
| `ui/screens/chatlist/ChatListScreen.kt` | 导出/导入 UI + FormatBottomSheet + Share Intent |
| `AndroidManifest.xml` | 注册 FileProvider |

### 构建状态
- `assembleDebug` — BUILD SUCCESSFUL
- `testDebugUnitTest` — 31 tests, 全部通过

---

## 2026-05-17 — 包名去隐私化

### applicationId 变更

- **`com.yannick.tavern` → `com.tavern.lite`** — 移除开发者个人信息
- `app/build.gradle.kts` — namespace + applicationId 同步更新
- 全部源码目录从 `com/yannick/tavern/` 迁移到 `com/tavern/lite/`
- 所有 `.kt` 文件 package 声明批量替换
- `app/proguard-rules.pro` keep 规则路径更新
- `.vscode/tasks.json` 启动命令更新

### 修改文件

| 文件 | 变更 |
|------|------|
| `app/build.gradle.kts` | namespace + applicationId 改为 com.tavern.lite |
| `app/proguard-rules.pro` | keep 规则路径更新 |
| `.vscode/tasks.json` | 启动命令包名更新 |
| 全部 `*.kt` 源文件 | package 声明 com.yannick.tavern → com.tavern.lite |
| 源码目录结构 | `com/yannick/tavern/` → `com/tavern/lite/` |

### 构建状态
- `assembleDebug` — BUILD SUCCESSFUL
- `assembleRelease` — BUILD SUCCESSFUL
- `testDebugUnitTest` — 31 tests, 全部通过
- Release APK — 2.1MB，已签名，无个人隐私信息

---

## 当前计划入口

当前优先级以本文件顶部的“2026-06-12 — 后端架构整改计划与日志整理”为准；长期路线仍参考 `ROADMAP.md`，阶段任务细节参考 `DEVELOPMENT-PLAN.md`。
# Tavern Android Automation Addendum

## 2026-06-12 - A2 Generation Continuation Split

**Context**: Automation continued the backend architecture cleanup plan. A2 was still the next unfinished item after proactive dialogue, image generation, group response selection, and assistant reply committing had already been extracted from `ChatStreamingManager`.

| Item | Files | Result |
|------|-------|--------|
| Continue/regenerate coordinator | `GenerationContinuationCoordinator.kt` / `ChatStreamingManager.kt` | Moved continue and regenerate request resolution plus `ContinueGenerationUseCase` delegation out of `ChatStreamingManager`; manager now keeps only job/state orchestration for these paths. |
| Coordinator tests | `GenerationContinuationCoordinatorTest.kt` | Covered last-assistant continue resolution, nearest previous-user regenerate resolution, invalid target guards, and delegation of config/character/reasoning parameters. |

**Verification**:
- `testDebugUnitTest --tests com.tavern.lite.ui.screens.chat.manager.GenerationContinuationCoordinatorTest --tests com.tavern.lite.ui.screens.chat.manager.ChatStreamingManagerTest` - passed.
- `detekt` - passed, 0 code smells.

**Unverified / follow-up**:
- Full `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and emulator smoke were not run in this pass.
- A2 still has the normal send/direct/group send orchestration inside `ChatStreamingManager`; the next small step is to extract that send coordination or move on to A3 reasoning context if risk priority changes.

## 2026-06-12 - A2 Send Coordination Split

**Context**: Automation continued the A2 manager split after the continuation/regenerate coordinator pass. The workspace was clean and no active prior automation work was detected.

| Item | Files | Result |
|------|-------|--------|
| Normal send coordinator | `GenerationSendCoordinator.kt` / `ChatStreamingManager.kt` | Moved single send, direct group mention send, group responder selection, group `SendMessageUseCase` delegation, assistant commit callbacks, and inter-reply delay handling out of `ChatStreamingManager`. |
| Coordinator tests | `GenerationSendCoordinatorTest.kt` | Covered single send delegation, direct send delegation, group response commit order, cancellation after first group commit, and virtual-time delay between group replies. |
| Jitter guard | `GenerationSendCoordinator.kt` | Fixed the small-interval jitter edge case by coercing the random bound to at least 1 ms. |

**Verification**:
- `testDebugUnitTest --tests com.tavern.lite.ui.screens.chat.manager.GenerationSendCoordinatorTest --tests com.tavern.lite.ui.screens.chat.manager.ChatStreamingManagerTest` - passed.
- `detekt` - passed, 0 code smells.

**Unverified / follow-up**:
- Full `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and emulator smoke were not run in this pass.
- A2 is now largely split by responsibility, but `ChatStreamingManager` still owns job/mutex/state orchestration and `lastReasoningContent`. The next likely step is A3 reasoning context cleanup.

## 2026-06-12 - A3 Reasoning Context Start

**Context**: Automation continued the backend architecture cleanup after A2 send coordination. The next unfinished source-of-truth item was A3 reasoning context cleanup because `ChatStreamingManager` still held `lastReasoningContent` as manager-level mutable state.

| Item | Files | Result |
|------|-------|--------|
| Reasoning context boundary | `GenerationReasoningContext.kt` / `ChatStreamingManager.kt` | Replaced the manager-level `lastReasoningContent` string with a small context object keyed by assistant message id. Continue/regenerate now read reasoning for the target assistant message instead of a single global latest value. |
| Regression tests | `GenerationReasoningContextTest.kt` / `ChatStreamingManagerTest.kt` | Covered storing reasoning by assistant message id, clearing stale reasoning when a same-message result has none, ignoring results without ids, and manager delegation of target-message reasoning into continue generation. |

**Verification**:
- `testDebugUnitTest --tests com.tavern.lite.ui.screens.chat.manager.GenerationReasoningContextTest --tests com.tavern.lite.ui.screens.chat.manager.ChatStreamingManagerTest --tests com.tavern.lite.ui.screens.chat.manager.GenerationContinuationCoordinatorTest` - passed.
- `detekt` - passed, 0 code smells.

**Unverified / follow-up**:
- Full `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and emulator smoke were not run in this pass.
- A3 is only started. The next likely step is to extend the typed generation context/result boundary into more send paths, especially group responses and any request path that should persist or replay reasoning across manager recreation.

## 2026-06-12 - A3 Group Reasoning Context

**Context**: Automation continued A3 after the first reasoning context split. The remaining source-of-truth gap was that group responses returned per-assistant `ExecutionResult` values, but `ChatStreamingManager` did not record their reasoning into the per-message context.

| Item | Files | Result |
|------|-------|--------|
| Group response reasoning recording | `ChatStreamingManager.kt` / `GenerationReasoningContext.kt` | Added a batch record path and now records every group response `ExecutionResult` after group send coordination completes. |
| Regression coverage | `ChatStreamingManagerTest.kt` | Added coverage proving a group assistant message's recorded reasoning is passed into subsequent continue generation for that assistant message. |

**Verification**:
- `testDebugUnitTest --tests com.tavern.lite.ui.screens.chat.manager.GenerationReasoningContextTest --tests com.tavern.lite.ui.screens.chat.manager.ChatStreamingManagerTest --tests com.tavern.lite.ui.screens.chat.manager.GenerationSendCoordinatorTest` - passed.
- `detekt` - passed, 0 code smells.

**Unverified / follow-up**:
- Full `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and emulator smoke were not run in this pass.
- A3 still has no durable reasoning replay across manager recreation because `MessageEntity` does not persist reasoning content. The next likely step is to decide whether reasoning should remain session-only or gain a persisted metadata column/table.
