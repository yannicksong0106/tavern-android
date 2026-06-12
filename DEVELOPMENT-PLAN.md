# 酒馆 AI (TavernAndroid) 开发计划

> 更新于 2026-06-10 | 基于当前工作区深度评估 + Quick Replies 收口优化
> 当前状态：Quick Replies / STscript Lite 已完成核心接入与多轮收口；DB Schema v31；核心定向测试、`compileDebugKotlin`、`lintDebug` 通过
> **核心原则：扩展功能永远往后推，重点永远是优化目前版本，确保目前的功能能够使用，并且尽量使这些功能获得更好的体验。**

---

## 当前进度总览

### 已完成 (Phases A-M, J, K, L, P, Q, R, S, O3, T, U)
- A 视觉体验 | B 记忆系统 | C ST 兼容 | D 质量保障 | E 对话导出
- F 聊天核心 | G 用户角色 | H 群聊 | I 扩展 API | M 数据管理
- J TTS/STT/多模态 | K 高级 WI+Prompt | L UI 手势
- O1 性能优化 | O2 无障碍 | O4 测试补充 | O5 质量验证
- P 聊天分支与书签 | Q 三级预设与模板引擎 | R 自动摘要
- S 安全收敛 | O3 多语言 | T Web Search | U 群聊调度增强
- **Phase 1 稳定性修复 ✅** | **Phase 2 ChatViewModel 拆分 (2.1-2.4) ✅**
- **Phase 2.5 PromptBuilder 重构 ✅** | **Phase 2.6 Repository 修复 ✅**
- **Phase 3.4 VN 模式进入优化 ✅** | **Phase 5.1 CryptoHelper 测试 ✅**
- **Phase 5.2 ChatApiService 测试 ✅** | **Phase 4.2 搜索失败提示 ✅**
- **Phase 4.7 API 超时可配置 ✅**
- **Phase 4.1 消息列表分页加载 ✅** | **Phase 4.6 ChatScreen LaunchedEffect 优化 ✅**
- **Phase 4.3 图像生成多 provider ✅** (OpenAI + Gemini；Claude 官方 API 暂无图片生成端点)
- **Lint 全面修复 ✅** (5 errors + 103 warnings → 0 errors + 32 warnings)

### 进行中
- V VN 模式 (立绘 ✅ + 情感检测 ✅ + 输入框 ✅ + BGM 播放器 ✅ + EmotionDetector 增强 ✅ + 测试 ✅ + 3.4 进入优化 ✅)
- v1.3.1 收口验证（全量测试 / lint / assembleDebug / 真实 smoke）
- 覆盖率报告核算 ✅（Kover debug：业务代码 line 70.19%，branch 42.85%）
- Quick Replies / STscript Lite（持久化 ✅ + 聊天页 ✅ + 管理页 ✅ + 自动触发 ✅ + 备份恢复 ✅ + 多轮稳定性收口 ✅）

### 待优化（本计划重点）
- ~~PromptBuilder 628 行 DRY 违例 + 测试盲区~~ ✅ 已完成（559 行，33 测试）
- ~~Repository 层线程安全 + 语义错误~~ ✅ 已完成（2 bug 修复 + 34 测试）
- 测试覆盖率目标已用 Kover 重算：业务代码 line 70.19% / branch 42.85%；下一步按热点补分支测试
- ChatViewModel / ChatScreen 后续只做小步拆分，优先等覆盖率报告指出风险区域后再动

### 待完成（远期）
- W 图像生成增强 | X STscript | Y 扩展框架 | Z 发布准备

---

## Phase 1：稳定性修复 (v1.2.9) — ✅ 已完成

> 确保现有功能不崩溃、不丢数据

| 编号 | 任务 | 文件 | 说明 | 状态 |
|------|------|------|------|------|
| 1.1 | DB Migration 测试 | TavernDatabaseMigrationTest.kt | v1→v28 迁移链完整性验证（21 个迁移全覆盖） | ✅ |
| 1.2 | BackupManager 版本校验 | BackupManager.kt | isVersionNewer/parseVersion 已实现，8 个测试覆盖 | ✅ |
| 1.3 | SSE 断线重连 | ChatStreamingManager.kt | 流中断自动重试 1-2 次，指数退避 | ✅ |
| 1.4 | API 限流退避 | ChatStreamingManager.kt | 429 响应时等待 Retry-After 时间 | ✅ |
| 1.5 | WebSearchService 测试修复 | WebSearchServiceTest.kt | mock android.util.Log 修复 | ✅ |
| 1.6 | reasoningContent 并发安全 | MessageExecutionHelper.kt | per-request 存储替代 @Volatile 全局变量 | ✅ |

**实际结果**：779 tests 全绿，新增 17 个测试用例

---

## Phase 2：ChatViewModel 拆分 (v1.2.9) — ✅ 已完成

> 降低巨型 ViewModel 的维护风险，为后续优化打基础
> ChatViewModel: 974 行 → <500 行；ChatScreen 也已回到目标线内

| 编号 | 任务 | 说明 | 状态 |
|------|------|------|------|
| 2.1 | 提取 ChatStreamingManager | 流式对话逻辑（send/regenerate/continue/stop/proactive）独立，513 行 | ✅ |
| 2.2 | 提取 GroupChatManager | 群聊调度逻辑已整合进 ChatStreamingManager（selectRespondingCharacters + 策略） | ✅ 合并至 2.1 |
| 2.3 | 提取 BranchManager | 分支 CRUD + 切换 + 书签筛选，68 行 | ✅ |
| 2.4 | 提取 VnModeManager | 情感检测 + 立绘加载 + 群聊角色解析，75 行 | ✅ |
| 2.5 | ChatViewModel 继续瘦身 | 提取 SearchManager/SpeechManager/GroupChatSettingsManager，去除委托包装 | ✅ |
| 2.6 | ChatScreen 拆分 | 提取 ChatTopBar/ChatSearchBar/ChatBackground 子组件 | ✅ |

**架构模式**：
- Provider lambdas 传递只读状态
- Callbacks 处理状态变更
- CoroutineScope 注入管理生命周期

**验证**：拆分后所有现有功能行为不变，779 tests 全绿

---

## Phase 2.5：PromptBuilder 重构 — 优先级 🔴 — ✅ 已完成

> 628 行的 object 单例，~60% 代码重复，12-14 参数方法，测试覆盖 ~30%

### 问题诊断

| 问题 | 严重度 | 详情 |
|------|--------|------|
| build() 与 buildGroupChat() 重复 | 高 | ~60% 代码几乎相同，仅角色解析和对话历史不同 |
| 参数列表过长 | 高 | 12-14 个参数，每次修改都需同步多处 |
| buildProactive/buildGroupProactive 未测试 | 高 | 4 个公共方法中只有 build() 有测试 |
| Author Note 注入未测试 | 中 | authorNote 位置计算（depth%length）无验证 |
| 预设合并逻辑未测试 | 中 | mergePresetMessages 的 6 种策略未覆盖 |
| searchResults/summary 注入未测试 | 中 | web 搜索结果和自动摘要注入路径未覆盖 |
| 图片 URL 多模态未测试 | 中 | imageUrls 拼接到 content 数组的逻辑未验证 |

### 重构计划

| 编号 | 任务 | 说明 | 状态 |
|------|------|------|------|
| 2.5.1 | 提取公共 prompt 构建逻辑 | buildCore() 统一 system prompt / world book / persona / preset / authorNote | ✅ |
| 2.5.2 | 引入 PromptConfig 数据类 | 封装 12-14 个参数为结构化配置对象 | ✅ |
| 2.5.3 | 统一 build() 和 buildGroupChat() | 调用 buildCore() + 差异化处理 | ✅ |
| 2.5.4 | 补全 buildGroupChat 测试 | 群聊角色解析、对话历史格式 | ✅ |
| 2.5.5 | 补全 buildProactive 测试 | 主动对话触发场景 | ✅ |
| 2.5.6 | 补全 Author Note / preset / search 测试 | 注入位置、合并策略、搜索结果格式 | ✅ |

**实际结果**：628 行 → 559 行（-11%），测试 16 → 33（+106%）

---

## Phase 2.6：Repository 层修复 — 优先级 🔴 — ✅ 已完成

> 13 个 Repository，发现 2 个 bug + 1 个线程安全问题

### 问题诊断

| 文件 | 问题 | 严重度 | 修复方案 |
|------|------|--------|---------|
| BgmRepository.kt | updateBgm() 调用 insert() 而非 update()，语义错误 | 高 | 调用 dao.updateBgm(entity) |
| ScriptRepository.kt | regexCache 使用 mutableMapOf()，非线程安全 | 中 | 改用 ConcurrentHashMap |
| ChatRepository.kt | 30+ 方法，过于臃肿 | 中 | 考虑拆分为 MessageRepository / BranchRepository |

### 修复计划

| 编号 | 任务 | 说明 | 状态 |
|------|------|------|------|
| 2.6.1 | BgmRepository.updateBgm 修复 | BgmDao 添加 @Update 方法，Repository 调用 update() | ✅ |
| 2.6.2 | ScriptRepository 线程安全 | mutableMapOf → ConcurrentHashMap | ✅ |
| 2.6.3 | 补全 AuthorNoteRepository 测试 | 5 个测试覆盖 CRUD | ✅ |
| 2.6.4 | 补全 BgmRepository 测试 | 10 个测试覆盖 CRUD + updateBgm 语义验证 | ✅ |
| 2.6.5 | 补全 SpriteRepository 测试 | 10 个测试覆盖按角色/情感查询 | ✅ |
| 2.6.6 | 补全 SummaryRepository 测试 | 9 个测试覆盖 CRUD + 按聊天查询 | ✅ |

---

## Phase 3：VN 模式补全 (v1.3.0) — 优先级 🟡

> 让已实现的 VN 模式真正可用，这是移动端差异化卖点

| 编号 | 任务 | 说明 | 状态 |
|------|------|------|------|
| 3.1 | VnScreen 添加输入框 | 底部输入栏 + 发送功能，复用 InputBar 组件 | ✅ |
| 3.2 | BGM 播放器实现 | MediaPlayer 封装 + AudioFocus + 淡入淡出 + 情感→BGM 映射 + DB v29 | ✅ |
| 3.3 | EmotionDetector 增强 | 三层检测（emoji→动作模式→关键词加权）+ 30 测试 | ✅ |
| 3.4 | VN 模式进入优化 | VN 按钮改为 FilledTonalButton + "VN" 文字标签，视觉突出 | ✅ |
| 3.5 | VN 模式测试 | EmotionDetector 30 测试 + VnModeManager 21 测试（BgmPlayer 需 Robolectric，暂缓） | ✅ |

**验证**：VN 模式可发送消息 → AI 回复 → 立绘切换 → BGM 播放 → 返回普通模式

---

## Phase 4：UX 润色 (v1.3.0) — 优先级 🟡

> 提升日常使用体验

| 编号 | 任务 | 说明 | 状态 |
|------|------|------|------|
| 4.1 | 消息列表分页加载 | LazyColumn 分页，减少初始加载时间 | ✅ |
| 4.2 | 搜索失败用户提示 | 搜索无结果时显示红色"无结果"文字提示 | ✅ |
| 4.3 | 图像生成多 provider | Gemini 原生图片生成 / Imagen 已接入；Claude 官方 API 暂无图片生成端点，保持明确不支持 | ✅ |
| 4.4 | 世界书匹配高亮 | 匹配预览文本驱动条目触发状态与关键词高亮 | ✅ |
| 4.5 | 预设模板预览 | 编辑预设时可预览变量替换效果 | ✅ |
| 4.6 | ChatScreen LaunchedEffect 优化 | 合并相关 Effect，减少重组开销 | ✅ |
| 4.7 | API 超时可配置 | Settings 中添加超时时间设置 | ✅ |

**验证**：长对话流畅滚动 + 搜索失败有提示 + 预设预览正确 + Gemini 图片生成单测覆盖

---

## Phase 5：测试补全 (v1.3.1) — 优先级 🟡

> 消除测试盲区，提升质量信心

### 当前测试分布

| 层 | 源文件 | 测试文件 | 覆盖率 | 优先级 |
|----|--------|---------|--------|--------|
| domain/usecase | 12 | 13 | 100% | - |
| ui/screens/*ViewModel | 16 | 13 | 81.3% | - |
| network | 8 | 6 | 75.0% | P1 |
| data/repository | 13 | 9 | 69.2% | P1 |
| data/model | 7 | 5 | 71.4% | P2 |
| data/db/dao | 16 | 1 | 100% | - |
| util | 24 | 5 | 20.8% | P2 |
| ui/screens | 17 | 2 | 11.8% | P3 |
| **总计** | **127** | **49** | **38.6%** | - |

### P0 — 核心安全/可靠性

| 编号 | 任务 | 说明 | 状态 |
|------|------|------|------|
| 5.1 | CryptoHelper 测试 | 6 测试：encrypt/decrypt 行为、tryDecrypt 异常处理、空串/长串 | ✅ |
| 5.2 | ChatApiService 测试 | 20 测试：buildMessagesArray（文本/多模态/推理/空列表）、parseRetryAfterHeader、ApiException | ✅ |
| 5.3 | ChatStreamingManager 测试 | send/regenerate/stop/continue 核心流程 + guard 条件，17 测试 | ✅ |
| 5.4 | BranchManager/VnModeManager 测试 | 分支切换 11 测试 + 情感检测 21 测试 | ✅ |

### P1 — 数据层完整性

| 编号 | 任务 | 说明 | 状态 |
|------|------|------|------|
| 5.5 | DAO 层测试 | 33 测试：CharacterDao 5 + ChatDao 6 + MessageDao 10 + BranchDao 3 + SummaryDao 5 + Cascade 2，Robolectric 集成测试 | ✅ |
| 5.6 | AuthorNoteRepository 测试 | CRUD + 排序查询 | ✅ |
| 5.7 | BgmRepository/SpriteRepository 测试 | 立绘/BGM 数据层 | ✅ |
| 5.8 | SummaryRepository 测试 | 自动摘要数据层 | ✅ |

### P2 — 工具层

| 编号 | 任务 | 说明 | 状态 |
|------|------|------|------|
| 5.9 | ChatImporter/SillyTavernImporter 测试 | 基础导入格式、PNG metadata、导出、异常边界和无头像 PNG 占位分支已覆盖 | ✅ |
| 5.10 | TtsHelper/SttHelper 测试 | SpeechManager 封装层覆盖 TTS/STT 委托与状态流 | ✅ |

**当前 Kover 基线**：业务代码 line 70.19% / branch 42.85%。下一步不再按测试文件数量估算，改按覆盖率热点补分支测试。

---

## Phase 6：性能优化 (v1.3.1) — 优先级 🟢

> 大数据量下的性能保障

| 编号 | 任务 | 说明 | 状态 |
|------|------|------|------|
| 6.1 | Room 查询优化 | v30 高频复合索引 + 聊天列表最后消息单查询，迁移测试覆盖 | ✅ |
| 6.2 | 图片内存池 | Application 全局 Coil ImageLoader，20% 内存缓存 + 3% 磁盘缓存 | ✅ |
| 6.3 | 备份大数据量验证 | 1200 条消息备份/恢复完整性 + <5s 预算测试，恢复消息批量插入 | ✅ |
| 6.4 | PromptBuilder 长对话验证 | 150 条历史 + 动态上下文构建完整性与 <1s 预算测试 | ✅ |

**验证**：1000+ 消息对话流畅加载，备份/恢复 < 5s

---

## 里程碑规划

### v1.2.9 — 稳定性 + 架构优化 ✅
```
Phase 1: 稳定性修复（6 项）✅
Phase 2: ChatViewModel 拆分（2.1-2.6）✅
Phase 2.5: PromptBuilder 重构（6 项）✅
Phase 2.6: Repository 修复（6 项）✅
```

### v1.3.0（2-3 周）— 功能补全 + UX 润色
```
Phase 3: VN 模式补全（5 项）
Phase 4: UX 润色（7 项）
```

### v1.3.1（收口中）— 质量保障
```
Phase 5: 测试补全（10 项）✅；Kover 基线已完成，下一步按热点补分支测试
Phase 6: 性能优化（4 项）✅
Quick Replies / STscript Lite：核心接入 + 收口验证
```

### v1.4.0+（远期）— 新功能扩展
```
Phase W: 图像生成增强（SD WebUI/ComfyUI）
Phase X: STscript 命令引擎
Phase Y: 扩展框架
Phase Z: Play Store 发布
```

---

## 技术债务清单

| 债务 | 严重度 | 归属 Phase | 状态 |
|------|--------|-----------|------|
| ~~ChatViewModel 974 行~~ | ~~高~~ | Phase 2 | ✅ 已减至 379 行 |
| ~~ChatScreen 718 行~~ | ~~中~~ | Phase 2.6 | ✅ 已减至 553 行 |
| ~~PromptBuilder 628 行 DRY 违例~~ | ~~高~~ | Phase 2.5 | ✅ 已减至 291 行 |
| ~~BgmRepository.updateBgm 语义错误~~ | ~~高~~ | Phase 2.6 | ✅ 已修复 |
| ~~ScriptRepository regexCache 线程不安全~~ | ~~中~~ | Phase 2.6 | ✅ 已修复 |
| ~~BGM 无播放器~~ | ~~中~~ | Phase 3 | ✅ 已实现 BgmPlayer |
| ~~EmotionDetector 纯关键词~~ | ~~低~~ | Phase 3 | ✅ 已增强并补测试 |
| ~~DAO 零测试~~ | ~~中~~ | Phase 5 | ✅ 33 测试全覆盖 |
| ~~DB Migration 零测试~~ | ~~高~~ | Phase 1 | ✅ 21 迁移全覆盖 |
| ~~SSE 无重连~~ | ~~中~~ | Phase 1 | ✅ 指数退避重试 |
| ~~reasoningContent 并发风险~~ | ~~中~~ | Phase 1 | ✅ per-request 存储 |
| ~~CryptoHelper 零测试~~ | ~~高~~ | Phase 5 | ✅ 已补测试 |
| ~~ChatApiService 零测试~~ | ~~高~~ | Phase 5 | ✅ 已补测试 |
| ~~图像生成仅 DALL-E~~ | ~~低~~ | Phase 4 | ✅ 已支持 Gemini |
| ~~搜索失败无提示~~ | ~~低~~ | Phase 4 | ✅ 已补提示 |

---

## 质量指标目标

| 指标 | v1.2.8 基线 | 当前 | v1.2.9 目标 | v1.3.1 目标 |
|------|------------|------|-----------|-----------|
| 测试数量 | 762 | 约 812+ | 850+ | 900+ |
| ChatViewModel 行数 | 974 | <500 | <500 ✅ | <500 |
| ChatScreen 行数 | 718 | <600 | <600 ✅ | <500 |
| QuickReplyScreen 行数 | - | 206 | <300 ✅ | <300 |
| QuickReply 管理页组件最大行数 | - | 271 | <300 ✅ | <300 |
| PromptBuilder 行数 | 628 | 291 | <400 ✅ | <400 ✅ |
| 测试覆盖率（Kover 业务代码） | 35.8% 估算 | line 70.19% / branch 42.85% | 45%+ ✅ | 60%+ ✅ |
| DAO 测试覆盖 | 0% | 100% | 100% ✅ | 100% |
| DB Migration 测试 | 0 | v31 迁移链 ✅ | 全部 | 全部 |
| VN 模式可用性 | 部分 | 可用（输入框 + BGM + 情感检测） | 部分 | 完整 |
| Lint 错误 | 5 | 0 ✅ | 0 | 0 |
| Lint 警告 | 103 | 32 | <50 | <20 |

---

## 下一步行动（优先级排序）

1. **按覆盖率热点补测试** — `PngMetadata` / `ChatExporter` / `SillyTavernImporter` 已补；下一轮优先 `ChatRepository`、`WorldBookRepository`、`MemoryExtractorService`
2. **BGM 播放器边界测试** — `BgmPlayer` 当前可行动覆盖为 0%，需覆盖空路径、切歌、音量、释放和失败回退
3. **assistant_reply 扩展 smoke** — 真实发送链路确认 automation 只触发一次，并继续遵守安全边界
4. **真实模拟器可视位置修复** — Tavern_Phone 宿主窗口仍落在 `originY=-720`，需后续单独处理 Emulator/Qt 窗口状态

---

*本计划随开发进度实时更新。每完成一个 Phase，在此文档中标记状态并记录实际结果。*
