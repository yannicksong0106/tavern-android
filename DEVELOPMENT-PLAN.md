# 酒馆 AI (TavernAndroid) 开发计划

> 更新于 2026-06-03 | 基于全量审计报告
> 当前状态：138 .kt 文件，762 tests（全绿），DB Schema v28
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

### 进行中
- V VN 模式 (立绘+情感检测 ✅，缺输入框/BGM 播放器)

### 待优化（本计划重点）
- 稳定性：DB Migration 测试、SSE 重连、API 限流退避
- 架构：ChatViewModel 974 行拆分
- UX：VN 模式补全、搜索失败提示、消息分页

### 待完成（远期）
- W 图像生成增强 | X STscript | Y 扩展框架 | Z 发布准备

---

## Phase 1：稳定性修复 (v1.2.9) — 优先级 🔴

> 确保现有功能不崩溃、不丢数据

| 编号 | 任务 | 文件 | 说明 | 状态 |
|------|------|------|------|------|
| 1.1 | DB Migration 测试 | 新增 TavernDatabaseMigrationTest.kt | 为 v1→v28 关键迁移编写自动化测试 | ⬜ |
| 1.2 | BackupManager 版本校验 | BackupManager.kt | 恢复时校验备份版本号，低版本备份警告用户 | ⬜ |
| 1.3 | SSE 断线重连 | ChatApiService.kt | 流中断时自动重试 1-2 次，带指数退避 | ⬜ |
| 1.4 | API 限流退避 | ChatApiService.kt | 429 响应时自动等待 Retry-After 时间 | ⬜ |
| 1.5 | WebSearchService 测试修复 | WebSearchServiceTest.kt | mock android.util.Log 或迁移到 Robolectric | ⬜ |
| 1.6 | reasoningContent 并发安全 | MessageExecutionHelper.kt | 改用 per-request 存储替代 @Volatile 全局变量 | ⬜ |

**验证**：`testDebugUnitTest` 全部通过 + 手动测试网络中断/限流场景

---

## Phase 2：ChatViewModel 拆分 (v1.2.9) — 优先级 🔴

> 降低 974 行巨型 ViewModel 的维护风险，为后续优化打基础

| 编号 | 任务 | 说明 | 状态 |
|------|------|------|------|
| 2.1 | 提取 ChatStreamingManager | 流式对话逻辑（send/regenerate/continue/stop）独立 | ⬜ |
| 2.2 | 提取 GroupChatManager | 群聊调度逻辑独立 | ⬜ |
| 2.3 | 提取 BranchManager | 分支/书签/导航逻辑独立 | ⬜ |
| 2.4 | 提取 VnModeManager | VN 模式状态管理（emotion/sprite）独立 | ⬜ |
| 2.5 | ChatViewModel 瘦身 | 只保留协调逻辑，委托给各 Manager | ⬜ |
| 2.6 | ChatScreen 拆分 | 提取 ChatTopBar/ChatBottomSheet/ChatDialogs 等子组件 | ⬜ |

**验证**：拆分后所有现有功能行为不变，测试全部通过

---

## Phase 3：VN 模式补全 (v1.3.0) — 优先级 🟡

> 让已实现的 VN 模式真正可用，这是移动端差异化卖点

| 编号 | 任务 | 说明 | 状态 |
|------|------|------|------|
| 3.1 | VnScreen 添加输入框 | 底部输入栏 + 发送功能 + 历史消息浏览 | ⬜ |
| 3.2 | BGM 播放器实现 | MediaPlayer 封装，播放/暂停/音量控制/循环 | ⬜ |
| 3.3 | EmotionDetector 增强 | 增加上下文感知，支持自定义关键词映射 | ⬜ |
| 3.4 | VN 模式进入优化 | ChatScreen 中 VN 入口更明显，支持快速切换 | ⬜ |
| 3.5 | VN 模式测试 | EmotionDetector + BgmPlayer + VnScreen 基础测试 | ⬜ |

**验证**：VN 模式可发送消息 → AI 回复 → 立绘切换 → BGM 播放 → 返回普通模式

---

## Phase 4：UX 润色 (v1.3.0) — 优先级 🟡

> 提升日常使用体验

| 编号 | 任务 | 说明 | 状态 |
|------|------|------|------|
| 4.1 | 消息列表分页加载 | LazyColumn 分页，减少初始加载时间 | ⬜ |
| 4.2 | 搜索失败用户提示 | performSearchIfNeeded 失败时显示 Toast | ⬜ |
| 4.3 | 图像生成多 provider | 支持 Claude/Gemini 图像生成 API | ⬜ |
| 4.4 | 世界书匹配高亮 | 匹配到的关键词在 UI 中高亮显示 | ⬜ |
| 4.5 | 预设模板预览 | 编辑预设时可预览变量替换效果 | ⬜ |
| 4.6 | ChatScreen LaunchedEffect 优化 | 合并相关 Effect，减少重组开销 | ⬜ |
| 4.7 | API 超时可配置 | Settings 中添加超时时间设置 | ⬜ |

**验证**：长对话流畅滚动 + 搜索失败有提示 + 预设预览正确

---

## Phase 5：测试补全 (v1.3.1) — 优先级 🟡

> 消除测试盲区，提升质量信心

| 编号 | 任务 | 说明 | 状态 |
|------|------|------|------|
| 5.1 | DAO 层测试 | 为关键 DAO（MessageDao/ChatDao/CharacterDao）编写测试 | ⬜ |
| 5.2 | DB Migration 测试补全 | 覆盖 v18→v28 所有关键迁移 | ⬜ |
| 5.3 | VN 模式测试 | VnScreen 基础渲染测试 + EmotionDetector 边界测试 | ⬜ |
| 5.4 | Integration 测试补全 | SendMessageUseCase 端到端测试 | ⬜ |
| 5.5 | BackupManager 测试 | 备份/恢复全流程测试 | ⬜ |

**验证**：测试总数达到 850+，DAO 测试覆盖核心 CRUD

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

### v1.2.9（2-3 周）— 稳定性 + 架构优化
```
Phase 1: 稳定性修复（6 项）
Phase 2: ChatViewModel 拆分（6 项）
```

### v1.3.0（2-3 周）— 功能补全 + UX 润色
```
Phase 3: VN 模式补全（5 项）
Phase 4: UX 润色（7 项）
```

### v1.3.1（1-2 周）— 质量保障
```
Phase 5: 测试补全（5 项）
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
| 图像生成仅 DALL-E | 低 | Phase 4 |
| 搜索失败无提示 | 低 | Phase 4 |

---

## 质量指标目标

| 指标 | 当前 | v1.2.9 目标 | v1.3.1 目标 |
|------|------|-----------|-----------|
| 测试数量 | 762 | 800+ | 850+ |
| ChatViewModel 行数 | 974 | <500 | <500 |
| DAO 测试覆盖 | 0% | 50% | 80% |
| DB Migration 测试 | 0 | 关键迁移 | 全部迁移 |
| VN 模式可用性 | 部分 | 部分 | 完整 |

---

*本计划随开发进度实时更新。每完成一个 Phase，在此文档中标记状态并记录实际工时。*
