# 酒馆 AI (TavernAndroid) 开发日志

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
