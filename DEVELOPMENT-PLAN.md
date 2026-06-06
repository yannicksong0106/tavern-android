# 酒馆 AI (TavernAndroid) 开发计划

> 更新于 2026-06-06 | 基于当前工作区核对 + Phase 4.3 实施结果
> 当前状态：220 .kt 文件（main 151 / test 69），773 testcases（全绿），DB Schema v29
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
- Phase 6 性能优化（Room 查询 / 图片内存 / 大数据备份恢复 / 长 prompt 构建验证）

### 待优化（本计划重点）
- ~~PromptBuilder 628 行 DRY 违例 + 测试盲区~~ ✅ 已完成（559 行，33 测试）
- ~~Repository 层线程安全 + 语义错误~~ ✅ 已完成（2 bug 修复 + 34 测试）
- 测试覆盖率 35.8% → 目标 60%+（需引入覆盖率报告重新核算）
- ChatViewModel 继续瘦身 + ChatScreen 拆分

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

## Phase 2：ChatViewModel 拆分 (v1.2.9) — 进行中

> 降低巨型 ViewModel 的维护风险，为后续优化打基础
> ChatViewModel: 974 行 → 379 行（-61%）

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
| 5.9 | ChatImporter/SillyTavernImporter 测试 | 导入格式兼容性，10 测试覆盖 3 种格式 + 边界 | ✅ |
| 5.10 | TtsHelper/SttHelper 测试 | SpeechManager 封装层覆盖 TTS/STT 委托与状态流 | ✅ |

**目标**：测试覆盖率 35.8% → 60%+，测试总数 779 → 900+

---

## Phase 6：性能优化 (v1.3.1) — 优先级 🟢

> 大数据量下的性能保障

| 编号 | 任务 | 说明 | 状态 |
|------|------|------|------|
| 6.1 | Room 查询优化 | 检查 N+1 查询，添加必要索引 | ⬜ |
| 6.2 | 图片内存池 | Coil 内存缓存配置优化 | ⬜ |
| 6.3 | 备份大数据量验证 | 1000+ 消息对话的备份/恢复性能 | ⬜ |
| 6.4 | PromptBuilder 长对话验证 | 100+ 消息的 prompt 构建性能 | ⬜ |

**验证**：1000+ 消息对话流畅加载，备份/恢复 < 5s

---

## 里程碑规划

### v1.2.9（进行中）— 稳定性 + 架构优化
```
Phase 1: 稳定性修复（6 项）✅
Phase 2: ChatViewModel 拆分（2.1-2.4 ✅，2.5-2.6 待做）
Phase 2.5: PromptBuilder 重构（6 项）
Phase 2.6: Repository 修复（6 项）
```

### v1.3.0（2-3 周）— 功能补全 + UX 润色
```
Phase 3: VN 模式补全（5 项）
Phase 4: UX 润色（7 项）
```

### v1.3.1（1-2 周）— 质量保障
```
Phase 5: 测试补全（10 项）
Phase 6: 性能优化（4 项）
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
| ~~ChatScreen 718 行~~ | ~~中~~ | Phase 2.6 | ✅ 已减至 560 行 |
| ~~PromptBuilder 628 行 DRY 违例~~ | ~~高~~ | Phase 2.5 | ✅ 已减至 559 行 |
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
| 测试数量 | 762 | 773 | 850+ | 900+ |
| ChatViewModel 行数 | 974 | 429 | <500 | <500 |
| ChatScreen 行数 | 718 | 616 | <600 | <500 |
| PromptBuilder 行数 | 628 | 532 | <400 | <400 |
| 测试文件覆盖率 | 35.8% | 待重算 | 45%+ | 60%+ |
| DAO 测试覆盖 | 0% | 100% | 100% ✅ | 100% |
| DB Migration 测试 | 0 | 21/21 ✅ | 全部 | 全部 |
| VN 模式可用性 | 部分 | 部分 | 部分 | 完整 |
| Lint 错误 | 5 | 0 ✅ | 0 | 0 |
| Lint 警告 | 103 | 32 | <50 | <20 |

---

## 下一步行动（优先级排序）

1. **Phase 6.1** — Room 查询优化审计（检查 N+1 查询，补必要索引）
2. **Phase 6.3** — 备份大数据量验证（1000+ 消息备份/恢复性能与完整性）
3. **架构瘦身** — ChatScreen 继续拆分至 <600 行，PromptBuilder 继续向 <400 行推进

---

*本计划随开发进度实时更新。每完成一个 Phase，在此文档中标记状态并记录实际结果。*
