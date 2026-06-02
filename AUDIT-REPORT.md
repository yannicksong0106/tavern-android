# Tavern Android 全量审计报告

> 审计日期：2026-06-03 | 版本：v1.2.8 | DB Schema v28

---

## 一、项目概况

| 指标 | 数值 |
|------|------|
| Kotlin 源文件 | 138 |
| 测试文件 | 42 |
| 单元测试 | 762（全部通过） |
| DB Schema 版本 | v28 |
| LLM API 提供商 | 7（OpenAI/Claude/Ollama/KoboldAI/OpenRouter/Gemini/Custom） |
| i18n 语言 | 4（中/英/日/韩） |

---

## 二、代码质量审计

### 2.1 架构评估：7.5/10

**优点**：
- MVVM + Repository + UseCase 分层清晰
- Hilt DI 依赖注入完整，无手动构造
- Single Activity + Compose Navigation 架构统一
- Coroutine 作用域管理规范：viewModelScope / CoroutineScope(SupervisorJob + Dispatchers.IO)

**问题**：
- **ChatViewModel 过大**（974 行）：承担了流式对话、群聊、分支、书签、摘要、TTS/STT、图像生成、VN 模式等 10+ 职责
- **ChatScreen 过大**（718 行）：8 个 LaunchedEffect、30+ 状态管理调用，UI 逻辑耦合严重
- **SendMessageUseCase 职责过重**：单聊/群聊/定向消息/搜索/摘要触发全部在一个类中

### 2.2 错误处理：8/10

**优点**：
- 全部 64+ catch 块正确处理 CancellationException rethrow
- `classifyError(e)` 提供用户友好的错误分类
- `MessageExecutionHelper.executeAndSave()` 网络错误保存为系统消息而非崩溃
- Toast/Snackbar 共 30 处用于用户反馈

**问题**：
- `ImageGenerationService` 只支持 OpenAI DALL-E，其他 provider 静默返回 null 无提示
- `performSearchIfNeeded()` 搜索失败仅 Log.w，用户无感知

### 2.3 线程安全：8/10

**优点**：
- `streamingMutex.withLock` 保护所有流式操作
- `@Volatile` 标记跨线程共享变量
- Room 数据库操作在 Dispatchers.IO
- BackupManager 使用 `db.withTransaction` 保证原子性

**问题**：
- `lastAssistantReasoningContent` 使用 `@Volatile` 但无原子性保证（多条消息并发时可能错乱）
- `summaryScope` 是 Singleton 作用域，多个 chatId 并发摘要时无隔离

### 2.4 代码卫生：9/10

- **零 TODO/FIXME/HACK 注释**
- 零空 catch 块
- ProGuard/R8 规则完整，@Serializable 精确匹配
- 所有 AsyncImage 已补 key 参数防缓存错乱

---

## 三、功能完整性审计

### 3.1 核心功能（已实现且可用）

| 功能 | 状态 | 说明 |
|------|------|------|
| 单聊对话 | ✅ 完整 | 流式 SSE、重试、继续生成、滑动切换回复 |
| 群聊对话 | ✅ 完整 | 三种调度策略、@ 指定回复、可配置间隔 |
| 角色卡导入 | ✅ 完整 | PNG tEXt/JSON/TavernAI 格式，CRC32 校验 |
| 世界书 | ✅ 完整 | 关键词匹配、递归注入、depth/position 控制 |
| 记忆系统 | ✅ 完整 | 原子记忆 + 向量记忆双轨，自动提取/整合 |
| 预设系统 | ✅ 完整 | Global→Char→Chat 三级，Handlebars 模板引擎 |
| 聊天分支 | ✅ 完整 | 树状分支、书签、导航栏 |
| 自动摘要 | ✅ 完整 | 可配置阈值、LLM 生成、注入 prompt |
| Web 搜索 | ✅ 完整 | DuckDuckGo/Bing/Google、缓存、autoSearch |
| 正则脚本 | ✅ 完整 | 发送前/接收后处理，支持正则 |
| TTS/STT | ✅ 完整 | 多引擎 TTS、语音输入 |
| 备份恢复 | ✅ 完整 | 全量 JSON 备份，事务性恢复 |
| 多语言 | ✅ 完整 | 中/英/日/韩，DataStore 持久化 |

### 3.2 Visual Novel 模式（部分完成，有缺陷）

| 组件 | 状态 | 说明 |
|------|------|------|
| VnScreen.kt | ⚠️ 基本可用 | 272 行，全屏立绘 + 对话框 + 工具栏 |
| EmotionDetector | ⚠️ 简单实现 | 关键词匹配，非 AI 情感分析，准确率有限 |
| SpriteRepository | ✅ 完整 | CRUD 完整 |
| BgmRepository | ✅ 完整 | CRUD 完整，但**无播放器实现** |
| 角色编辑中的立绘配置 | ✅ 完整 | SpriteSheet + BgmSheet UI |

**关键缺陷**：
1. **BGM 无实际播放**：BgmRepository 只有数据层，没有 MediaPlayer/ExoPlayer 播放逻辑
2. **VnScreen 无输入框**：用户无法在 VN 模式中发送消息，只能查看
3. **情感检测过于简单**：纯关键词匹配，对中文语境理解有限
4. **无 VN 模式入口提示**：ChatScreen 中 VN 入口不够明显

### 3.3 图像生成（基础实现）

| 组件 | 状态 | 说明 |
|------|------|------|
| ImageGenerationService | ⚠️ 仅 DALL-E | 只支持 OpenAI，其他 provider 静默失败 |
| /draw 命令 | ❌ 未实现 | ChatViewModel 中无 /draw 命令处理 |
| 图像画廊 | ❌ 未实现 | 无 ImageGalleryScreen |

---

## 四、UX 问题清单

### 4.1 高优先级 UX 问题

| 编号 | 问题 | 影响 | 文件 |
|------|------|------|------|
| UX-1 | ChatViewModel 974 行，UI 响应延迟风险 | 中 | ChatViewModel.kt |
| UX-2 | VN 模式无输入框，用户无法交互 | 高 | VnScreen.kt |
| UX-3 | BGM 无实际播放，配置形同虚设 | 中 | BgmRepository.kt |
| UX-4 | 图像生成仅 DALL-E，其他 API 静默失败 | 中 | ImageGenerationService.kt |
| UX-5 | 搜索失败无用户提示 | 低 | SendMessageUseCase.kt:122 |
| UX-6 | EmotionDetector 准确率有限 | 低 | EmotionDetector.kt |

### 4.2 中优先级 UX 问题

| 编号 | 问题 | 影响 |
|------|------|------|
| UX-7 | 长消息列表性能未优化（LazyColumn 无分页） | 中 |
| UX-8 | 世界书关键词匹配无高亮反馈 | 低 |
| UX-9 | 记忆系统无可视化管理界面 | 低 |
| UX-10 | 预设模板编辑无预览功能 | 低 |

---

## 五、稳定性问题

### 5.1 数据层稳定性

| 编号 | 问题 | 风险等级 | 说明 |
|------|------|----------|------|
| ST-1 | DB Migration 链路长（v1→v28） | 中 | 28 个迁移，任一失败全量数据丢失 |
| ST-2 | BackupManager 恢复无版本校验 | 中 | 低版本备份恢复到高版本可能缺字段 |
| ST-3 | 并发写入 Room 无额外保护 | 低 | Room 内部有锁，但大量并发可能 OOM |

### 5.2 网络层稳定性

| 编号 | 问题 | 风险等级 | 说明 |
|------|------|----------|------|
| ST-4 | SSE 流中断无自动重连 | 中 | 网络波动时对话中断，需手动重试 |
| ST-5 | API 超时无用户可配置项 | 低 | OkHttp 默认超时可能不适合长回复 |
| ST-6 | 429 限流无退避策略 | 低 | 快速连续请求可能被 API 封禁 |

### 5.3 UI 层稳定性

| 编号 | 问题 | 风险等级 | 说明 |
|------|------|----------|------|
| ST-7 | LaunchedEffect 8 个，重组开销大 | 中 | ChatScreen 重组时所有 Effect 重新执行 |
| ST-8 | 图片加载无内存限制 | 低 | 大量图片可能 OOM（已加 20MB 文件限制但无内存池） |

---

## 六、测试覆盖分析

### 6.1 覆盖现状

| 层级 | 测试文件数 | 测试数 | 覆盖率评估 |
|------|-----------|--------|-----------|
| Repository | 9 | ~200 | 高 |
| UseCase | 6 | ~180 | 高 |
| ViewModel | 8 | ~150 | 中 |
| Network/Service | 5 | ~120 | 中 |
| Util | 5 | ~60 | 高 |
| DAO | 0 | 0 | **缺失** |
| UI Compose | 0 | 0 | **缺失** |
| Integration | 2 | ~50 | 低 |

### 6.2 测试盲区

1. **DAO 层零测试**：所有 Room DAO 直接依赖 Room 测试规则，未单独验证 SQL 逻辑
2. **UI Compose 零测试**：无 `createComposeRule()` 测试
3. **DB Migration 零测试**：28 个迁移无自动化验证
4. **VN 模式零测试**：VnScreen/EmotionDetector/BgmRepository 无测试
5. **图像生成测试不足**：ImageGenerationServiceTest 存在但覆盖有限
6. **WebSearchService 6 个测试失败**：android.util.Log mock 问题未解决

---

## 七、开发规划（优化优先）

> 核心原则：**扩展功能永远往后推，重点永远是优化目前版本，确保目前的功能能够使用，并且尽量使这些功能获得更好的体验。**

### Phase 1：稳定性修复（1-2 周）— 优先级 🔴

> 确保现有功能不崩溃、不丢数据

| 编号 | 任务 | 文件 | 说明 | 预估工时 |
|------|------|------|------|----------|
| 1.1 | DB Migration 测试 | 新增 TavernDatabaseMigrationTest.kt | 为 v1→v28 关键迁移编写自动化测试，确保升级不丢数据 | 2 天 |
| 1.2 | BackupManager 版本校验 | BackupManager.kt | 恢复时校验备份版本号，低版本备份警告用户 | 0.5 天 |
| 1.3 | SSE 断线重连 | ChatApiService.kt | 流中断时自动重试 1-2 次，带指数退避 | 1 天 |
| 1.4 | API 限流退避 | ChatApiService.kt | 429 响应时自动等待 Retry-After 时间 | 0.5 天 |
| 1.5 | WebSearchService 测试修复 | WebSearchServiceTest.kt | mock android.util.Log 或迁移到 Robolectric | 0.5 天 |
| 1.6 | reasoningContent 并发安全 | MessageExecutionHelper.kt | 改用 ConcurrentHashMap 或 per-request 存储 | 0.5 天 |

**验证**：`testDebugUnitTest` 全部通过 + 手动测试网络中断场景

---

### Phase 2：ChatViewModel 拆分（1-2 周）— 优先级 🔴

> 降低 974 行巨型 ViewModel 的维护风险

| 编号 | 任务 | 说明 | 预估工时 |
|------|------|------|----------|
| 2.1 | 提取 ChatStreamingManager | 流式对话逻辑（send/regenerate/continue/stop）独立为 Manager | 2 天 |
| 2.2 | 提取 GroupChatManager | 群聊调度逻辑独立 | 1 天 |
| 2.3 | 提取 BranchManager | 分支/书签/导航逻辑独立 | 1 天 |
| 2.4 | 提取 VnModeManager | VN 模式状态管理（emotion/sprite）独立 | 0.5 天 |
| 2.5 | ChatViewModel 瘦身 | 只保留协调逻辑，委托给各 Manager | 1 天 |

**验证**：拆分后所有现有功能行为不变，测试全部通过

---

### Phase 3：VN 模式补全（1 周）— 优先级 🟡

> 让已实现的 VN 模式真正可用

| 编号 | 任务 | 说明 | 预估工时 |
|------|------|------|----------|
| 3.1 | VnScreen 添加输入框 | 底部输入栏 + 发送功能 | 1 天 |
| 3.2 | BGM 播放器实现 | MediaPlayer 封装，播放/暂停/音量控制 | 1.5 天 |
| 3.3 | EmotionDetector 增强 | 增加上下文感知，支持自定义关键词映射 | 1 天 |
| 3.4 | VN 模式测试 | EmotionDetector + VnScreen 基础测试 | 1 天 |

**验证**：VN 模式可发送消息 → AI 回复 → 立绘切换 → BGM 播放

---

### Phase 4：UX 润色（1-2 周）— 优先级 🟡

> 提升日常使用体验

| 编号 | 任务 | 说明 | 预估工时 |
|------|------|------|----------|
| 4.1 | 消息列表分页加载 | LazyColumn 分页，减少初始加载时间 | 1 天 |
| 4.2 | 搜索失败用户提示 | performSearchIfNeeded 失败时显示 Toast | 0.5 天 |
| 4.3 | 图像生成多 provider | 支持 Claude/Gemini 图像生成 API | 1.5 天 |
| 4.4 | 世界书匹配高亮 | 匹配到的关键词在 UI 中高亮显示 | 1 天 |
| 4.5 | 预设模板预览 | 编辑预设时可预览变量替换效果 | 1 天 |
| 4.6 | ChatScreen LaunchedEffect 优化 | 合并相关 Effect，减少重组开销 | 1 天 |

**验证**：长对话流畅滚动 + 搜索失败有提示 + 预设预览正确

---

### Phase 5：测试补全（1 周）— 优先级 🟡

> 消除测试盲区

| 编号 | 任务 | 说明 | 预估工时 |
|------|------|------|----------|
| 5.1 | DAO 层测试 | 为关键 DAO（MessageDao/ChatDao/CharacterDao）编写测试 | 2 天 |
| 5.2 | DB Migration 测试补全 | 覆盖 v18→v28 所有关键迁移 | 1 天 |
| 5.3 | VN 模式测试 | VnScreen 基础渲染测试 + EmotionDetector 边界测试 | 1 天 |
| 5.4 | Integration 测试补全 | SendMessageUseCase 端到端测试 | 1 天 |

**验证**：测试总数达到 850+，DAO 测试覆盖核心 CRUD

---

### Phase 6：性能优化（1 周）— 优先级 🟢

> 大数据量下的性能保障

| 编号 | 任务 | 说明 | 预估工时 |
|------|------|------|----------|
| 6.1 | Room 查询优化 | 检查 N+1 查询，添加索引 | 1 天 |
| 6.2 | 图片内存池 | Coil 内存缓存配置优化 | 0.5 天 |
| 6.3 | 备份并行化 | BackupManager 已用 async，验证大数据量性能 | 0.5 天 |
| 6.4 | PromptBuilder 性能 | 模板编译缓存已实现，验证长对话场景 | 0.5 天 |

**验证**：1000+ 消息对话流畅加载，备份/恢复 < 5s

---

## 八、里程碑规划

### v1.2.9（2-3 周）— 稳定性 + 架构优化
```
Phase 1: 稳定性修复（ST-1 ~ ST-6）
Phase 2: ChatViewModel 拆分
```

### v1.3.0（2-3 周）— 功能补全 + UX 润色
```
Phase 3: VN 模式补全
Phase 4: UX 润色
```

### v1.3.1（1-2 周）— 质量保障
```
Phase 5: 测试补全
Phase 6: 性能优化
```

### v1.4.0+（未来）— 新功能扩展
```
Phase W: 图像生成增强（SD WebUI/ComfyUI）
Phase X: STscript 命令引擎
Phase Y: 扩展框架
Phase Z: Play Store 发布
```

---

## 九、技术债务清单

| 债务 | 严重度 | 归属 Phase |
|------|--------|-----------|
| ChatViewModel 974 行 | 高 | Phase 2 |
| ChatScreen 718 行 | 中 | Phase 2 |
| BGM 无播放器 | 中 | Phase 3 |
| EmotionDetector 纯关键词 | 低 | Phase 3 |
| DAO 零测试 | 中 | Phase 5 |
| DB Migration 零测试 | 高 | Phase 1 |
| SSE 无重连 | 中 | Phase 1 |
| reasoningContent 并发风险 | 中 | Phase 1 |

---

## 十、总结

**当前状态**：项目功能覆盖面广（13 个主要功能模块），代码质量良好（零 TODO/空 catch），测试基础扎实（762 测试全绿）。主要风险集中在：

1. **架构债务**：ChatViewModel 过大，维护成本高
2. **稳定性盲区**：DB Migration 无测试、SSE 无重连
3. **功能空壳**：VN 模式缺输入框、BGM 无播放器、图像生成仅 DALL-E

**优先级**：稳定性 > 架构优化 > 功能补全 > UX 润色 > 测试补全 > 性能优化 > 新功能

---

*本报告基于 2026-06-03 代码库状态，随开发进度更新。*
