# 酒馆 AI (TavernAndroid) 开发日志

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

## 待做方向

详见 `ROADMAP.md`。
