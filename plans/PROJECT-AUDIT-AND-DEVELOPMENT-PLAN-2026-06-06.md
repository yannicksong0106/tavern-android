# Tavern Android 项目审查与开发计划

> 日期：2026-06-06  
> 范围：以当前工作区代码为准，不以过时文档为准  
> 原则：先保证现有功能稳定可用，再逐步对齐 SillyTavern 核心体验

## 1. 当前状态

这是一个功能密度较高的原生 Android SillyTavern 客户端。当前架构主线是：

```text
Compose UI / ViewModel
  -> manager / usecase
  -> repository
  -> Room / DataStore / OkHttp
```

已具备的核心能力：

- 角色卡：创建、编辑、PNG/JSON 导入导出，兼容 SillyTavern 角色卡方向。
- 聊天：单聊、群聊、流式输出、继续生成、重新生成、swipe 变体、消息编辑/删除/置顶、图片附件。
- Prompt：角色定义、示例对话、世界书、记忆、作者注、用户人格、预设、摘要、Web 搜索结果注入。
- 数据：Room 本地数据库，当前 schema version 29；DataStore 保存设置；API/TTS 配置使用 Android Keystore 加密。
- 增强功能：记忆原子、自动摘要、VN 模式、Sprite/BGM、TTS/STT、后台主动消息、备份恢复、聊天导入导出。
- 测试：当前 `testDebugUnitTest` 通过。

当前明显约束：

- 工作区有大量未提交修改和新增文件，后续开发必须小步提交、每步验证。
- 已有 `AUDIT-REPORT.md`、`DEVELOPMENT-PLAN.md` 部分内容已过时，且早期编码在部分终端显示异常。本计划作为新的执行基线。
- `detekt` 当前不通过，只有 3 个 `SwallowedException` 问题，但会阻断质量门禁。

## 2. 审查结论

### 2.1 架构

项目分层方向正确：UI 不直接操作网络和数据库，复杂聊天链路集中在 UseCase/Manager。近期已经把 `ChatViewModel` 拆出 `ChatStreamingManager`、`BranchManager`、`SearchManager`、`SpeechManager`、`VnModeManager`、`GroupChatSettingsManager`，这是正确方向。

仍需治理：

- `ChatViewModel` 仍是聊天页总协调器，状态和 manager provider/callback 较多，后续应继续收敛状态边界。
- `ChatStreamingManager` 承担发送、重发、继续、群聊调度、主动消息、图片生成、拆分消息等多职责，可继续拆出 generation coordinator 和 post-processing。
- `PromptBuilder` 虽已引入 `PromptConfig`，但仍是高度核心的全局 object。后续任何 SillyTavern 对齐工作都应优先补测试再改逻辑。

### 2.2 数据安全

最高优先级问题：

- `MessageEntity.imagePaths` 已存在，但 `MessageBackup` 未包含图片路径。备份恢复会丢失聊天图片附件。
- `BgmEntity.emotion` 已存在，`BackupManager` 代码里已经尝试读写 `emotion`，但 `BgmBackup` 模型未包含该字段。当前代码存在编译/模型同步风险，且备份恢复会丢失情绪 BGM 映射。
- Room migration 链已经到 v29，但迁移测试主要验证链条存在，不是用真实旧 schema 执行迁移。上线前必须补真实 migration test。

### 2.3 网络与并发

主要风险：

- `ChatApiService.lastReasoningContent` singleton 共享状态已移除，reasoning 现在随每次流式请求的 `ChatStreamChunk` 返回。剩余需要关注的是界面会话层 `ChatStreamingManager` 中上一轮 reasoning 的生命周期边界。
- SSE 解析逻辑已经覆盖 OpenAI 兼容、Claude、Gemini，但不同 provider 的多模态、错误体、finish reason、tool/event 差异仍较弱。
- 错误提示以 toast/log 为主，缺少统一错误模型。后续对齐更多 API/扩展时需要标准化。

### 2.4 功能对齐 SillyTavern

当前已经覆盖 SillyTavern 的部分核心概念：角色卡、世界书、作者注、预设、人格、群聊、swipe、正则脚本、TTS、UI 样式、导入导出。

仍需重点补齐：

- 世界书高级能力：全局/角色/人格/聊天 lorebook 组合策略、regex key、预算、扫描深度、触发类型、character filter、timed effects、outlet。
- STscript/Quick Replies：脚本命令、快捷回复预设、自动执行、与世界书 automation id 联动。
- Data Bank/RAG：文件资料库、聊天附件索引、检索注入。
- Connection Profiles：多 API 配置档案、快速切换、按角色/聊天绑定。
- Prompt Manager：更可视化的 prompt 顺序、启用/禁用、宏展开预览。
- 扩展体系：先不做完整插件市场，但要预留 hook/event 边界。

参考资料：

- SillyTavern World Info 文档：https://docs.sillytavern.app/usage/core-concepts/worldinfo/
- SillyTavern STscript 文档：https://docs.sillytavern.app/usage/st-script/
- SillyTavern Macros 文档：https://docs.sillytavern.app/usage/core-concepts/macros/
- SillyTavern Connection Profiles 文档：https://docs.sillytavern.app/usage/core-concepts/connection-profiles/
- SillyTavern Data Bank 文档：https://docs.sillytavern.app/usage/core-concepts/data-bank/

## 3. 开发原则

1. 稳定性优先。任何新功能前，先保证发送消息、继续生成、重生成、swipe、导入导出、备份恢复、设置保存可用。
2. 数据不可丢。涉及 schema、备份、导入导出、文件路径的变更必须有回归测试。
3. 小步合并。每个任务尽量控制在 1 到 3 个核心文件，避免大面积重构和功能开发混在一起。
4. 先测后扩。Prompt、世界书、记忆、备份、API 解析这类核心逻辑，先补测试再改。
5. 移动端优先。对齐 SillyTavern 时不照搬桌面 UI，保留 Android 原生、触控优先、低维护成本的实现。

## 4. 质量门槛

每个阶段完成时必须满足：

- `./gradlew.bat testDebugUnitTest` 通过。
- `./gradlew.bat detekt` 通过，或阶段文档中明确记录剩余问题和原因。
- 聊天主链路手测通过：发送、停止、继续、重生成、swipe、图片消息。
- 数据链路手测通过：创建角色、创建聊天、重启 app 后数据仍在。
- 涉及备份/导入导出时，必须做一次备份恢复回归。

发布候选额外要求：

- `assembleDebug` 通过。
- 至少一台模拟器或真机冒烟测试。
- 数据库迁移测试覆盖最近 3 个 schema 版本和一个历史线上版本。

## 5. 分阶段计划

### Phase 0：建立稳定基线

目标：不做新功能，先把当前工作区变成可信基线。

任务：

- 修复 `BackupData` / `BackupManager` 字段不同步：
  - `MessageBackup` 增加 `imagePaths`。
  - `BgmBackup` 增加 `emotion`。
  - backup/restore 双向写入，并补测试。
- 修复 `detekt` 3 个 `SwallowedException`：
  - `TemplateEngine.kt`
  - `HomeViewModel.kt`
  - `BgmPlayer.kt`
- 梳理当前未提交文件，按主题拆分提交：VN/BGM、Prompt、Chat manager、测试、i18n、文档。
- 补一个 `CURRENT-STATUS.md` 或更新本计划的状态区，记录当前版本真实测试数、schema、已知风险。

验收：

- 单元测试通过。
- detekt 通过。
- 备份恢复图片消息和 BGM emotion 不丢字段。

### Phase 1：数据与迁移可靠性

目标：任何后续功能开发都不能牺牲用户数据。

任务：

- 增加真实 Room migration 测试：
  - 28 -> 29
  - 27 -> 29
  - 21 -> 29
  - 一个当前用户最可能持有的历史版本 -> 29
- 梳理 schema 与 backup model 的字段映射，建立测试防止新增字段忘记备份。
- 给 `BackupManager.restore` 增加更明确的冲突策略：
  - 全量覆盖
  - 合并导入
  - 只预检不写入
- 文件资源策略：
  - 头像、背景、图片附件、BGM、Sprite 的引用路径是否进入备份。
  - 明确“只备份元数据”还是“连文件一起打包”。

验收：

- migration 测试可真实执行 SQL。
- 备份恢复不会破坏已有聊天。
- 数据恢复失败时事务回滚。

### Phase 2：聊天主链路收敛

目标：让最核心的聊天体验稳定、可维护、可继续扩展。

任务：

- 消除 `ChatApiService.lastReasoningContent` 共享状态：
  - 让流式请求返回 `contentDelta` 和 `reasoningDelta` 或 `ChatStreamResult`。
  - `MessageExecutionHelper` 不再从 singleton 字段读取 reasoning。
- 拆分 `ChatStreamingManager`：
  - `GenerationCoordinator`：发送/继续/重生成。
  - `GroupReplyScheduler`：群聊调度。
  - `MessagePostProcessor`：回复清理、脚本、拆分多消息、情绪更新。
- 统一错误模型：
  - 网络错误、鉴权错误、限流、模型不支持、上下文过长、解析失败。
  - UI 只展示用户可理解的信息，日志保留技术细节。
- 补齐聊天链路集成测试：
  - 单聊发送。
  - 群聊发送。
  - 重生成 swipe。
  - 图片消息。
  - 中断/取消。

验收：

- 主聊天操作无行为回退。
- reasoning 串线风险消除。
- 群聊和后台 proactive 不影响前台聊天状态。

### Phase 3：SillyTavern 核心对齐 I - 世界书

目标：优先对齐 SillyTavern 用户最依赖的 World Info / Lorebook 行为。

任务：

- 世界书来源：
  - Global Lore
  - Character Lore
  - Persona Lore
  - Chat Lore
- 插入策略：
  - Sorted Evenly
  - Character Lore First
  - Global Lore First
- entry 匹配增强：
  - regex key
  - case-sensitive
  - whole word
  - scan depth
  - include names
  - token budget/context budget
- entry 过滤：
  - character filter
  - trigger type：normal、continue、swipe、regenerate、quiet
  - probability
  - inclusion group / group weight
- 高级注入：
  - top/bottom of Author's Note
  - depth 插入
  - outlet 与 `{{outlet::name}}`
- 导入导出：
  - SillyTavern lorebook JSON 导入导出。

验收：

- 当前已有世界书行为保持兼容。
- 常见 SillyTavern lorebook 可导入，并能触发基础 entry。
- 世界书匹配结果可在 UI 中预览，便于调试。

### Phase 4：SillyTavern 核心对齐 II - Prompt 与宏

目标：让角色卡、预设、作者注、世界书、摘要、人格和搜索结果的最终 prompt 可控、可解释。

任务：

- 扩展宏系统：
  - `{{user}}`
  - `{{char}}`
  - `{{persona}}`
  - `{{summary}}`
  - `{{input}}`
  - `{{lastMessage}}`
  - `{{outlet::name}}`
- Prompt 预览：
  - 显示最终 messages 列表。
  - 显示每段来源：角色、预设、世界书、记忆、作者注、摘要、搜索。
  - 显示估算 token。
- Prompt Manager 移动端简化版：
  - 启用/禁用 prompt 段。
  - 调整部分插入顺序。
  - 保存为预设。
- 预设体系增强：
  - Connection/Profile 绑定。
  - 按角色/聊天覆盖。

验收：

- 用户可以解释“为什么这个内容进入了 prompt”。
- PromptBuilder 相关测试覆盖所有新增宏。

### Phase 5：SillyTavern 核心对齐 III - Quick Replies 与 STscript 子集

目标：先实现高价值、低风险的 STscript 子集，不一口气复刻完整脚本语言。

任务：

- Quick Replies：
  - 快捷按钮栏。
  - 多组 preset。
  - 手动触发。
  - 可绑定角色/聊天/全局。
- STscript MVP：
  - `/send`
  - `/trigger`
  - `/continue`
  - `/setvar`、`/getvar`
  - `/echo`
  - `/comment`
  - `/setinput`
  - `{{input}}` 宏联动。
- 自动执行边界：
  - 默认关闭。
  - 明确权限提示。
  - 禁止危险文件/网络操作。
- 与世界书联动：
  - automation id 触发 Quick Reply。

验收：

- 常见 Quick Reply 工作流可用。
- 脚本执行失败不会影响聊天数据库一致性。
- 所有自动执行都有明确开关。

### Phase 6：Data Bank / RAG

目标：对齐 SillyTavern Data Bank 的移动端版本，补足资料检索能力。

任务：

- 文件导入：
  - txt、md、json、pdf 可作为后续扩展。
- 文本切块与索引：
  - 先用本地关键词/BM25 简化实现。
  - 预留 embedding provider 接口。
- 绑定范围：
  - 全局资料库。
  - 角色资料库。
  - 聊天附件资料库。
- Prompt 注入：
  - 检索结果来源标注。
  - token 预算控制。

验收：

- 小型资料库可稳定检索。
- 检索失败不影响普通聊天。
- 大文件导入有进度和取消能力。

### Phase 7：API 与配置档案

目标：对齐 SillyTavern Connection Profiles，同时降低 API 配置复杂度。

任务：

- 多连接档案：
  - provider、baseUrl、model、apiKey、参数、超时。
  - 默认档案。
  - 角色/聊天绑定档案。
- 模型能力声明：
  - 是否支持图片输入。
  - 是否支持流式。
  - 是否支持 reasoning。
  - 是否支持图片生成。
- 连接测试：
  - 保存前测试。
  - 模型列表拉取。
  - 错误提示标准化。

验收：

- 切换 API 不破坏已有配置。
- 密钥仍加密保存。
- 无密钥日志泄露。

### Phase 8：移动端体验与性能

目标：不是简单复刻桌面端，而是做移动端好用的酒馆。

任务：

- 聊天长列表性能：
  - 分页策略稳定。
  - 图片缩略图和缓存策略。
  - 搜索定位稳定。
- VN 模式体验：
  - 输入、BGM、Sprite、转场。
  - 群聊 VN 的角色切换。
- 无障碍与多语言：
  - TalkBack 基础标签。
  - 中/英/日/韩字符串同步。
- 视觉一致性：
  - 设置页、角色页、世界书页表单密度统一。

验收：

- 1000 条消息聊天可流畅打开和滚动。
- 图片附件不会造成明显内存压力。
- 关键按钮有 contentDescription。

### Phase 9：扩展体系预留

目标：先做内部 hook，不急于开放完整插件市场。

任务：

- 定义内部事件：
  - beforePromptBuild
  - afterPromptBuild
  - beforeSend
  - afterResponse
  - onWorldInfoActivated
  - onMessageSaved
- 将 Web Search、Summary、Memory、Quick Replies 逐步改成内部 extension 风格。
- 建立权限模型草案：
  - 读聊天
  - 写聊天
  - 读文件
  - 网络请求

验收：

- 内置功能通过 hook 接入后行为不变。
- 不开放不安全的动态代码执行。

## 6. SillyTavern 对齐矩阵

| 能力 | 当前状态 | 后续策略 |
|---|---|---|
| Character Card | 已有 | 补 v3 字段完整性、导入导出回归 |
| Chat / Swipe | 已有 | 稳定主链路，补集成测试 |
| Group Chat | 已有 | 对齐 `/trigger`、允许/禁止 self response 等设置 |
| World Info / Lorebook | 部分已有 | Phase 3 重点补齐 |
| Author's Note | 已有 | 接入 Prompt 预览和世界书 AN 位置 |
| Persona | 已有 | 增加 persona lorebook |
| Presets | 已有 | 增强 Prompt Manager 和 Connection Profile 绑定 |
| Macros | 部分已有 | Phase 4 扩展 |
| Quick Replies | 缺失 | Phase 5 MVP |
| STscript | 缺失 | Phase 5 子集实现 |
| Data Bank / RAG | 缺失 | Phase 6 |
| Extensions | 缺失 | Phase 9 先做内部 hook |
| UI Customization | 部分已有 | 移动端优先，不照搬桌面 |
| TTS/STT | 已有 | 稳定和配置完善 |
| Image Generation | 部分已有 | 放在稳定性之后扩展 provider |

## 7. 近期执行顺序

接下来建议按这个顺序推进：

1. Phase 0：修备份字段、detekt、当前工作区拆分提交。
2. Phase 1：补真实 migration 与备份恢复测试。
3. Phase 2：消除 reasoning 共享状态，稳住聊天主链路。
4. Phase 3：世界书高级对齐。
5. Phase 4：Prompt 预览与宏系统。

暂缓：

- 完整扩展市场。
- 完整 STscript 语言。
- 大规模 UI 重做。
- 新 provider 大扩展。

这些都应等主链路、数据安全和世界书/Prompt 稳定后再做。

## 8. 工作方式

每个任务采用固定节奏：

1. 先写或补测试。
2. 小范围实现。
3. 跑单元测试和 detekt。
4. 手测关键路径。
5. 更新本计划状态。
6. 单独提交。

状态标记：

- `todo`：未开始。
- `doing`：正在做。
- `blocked`：需要决策或外部依赖。
- `done`：实现、测试、文档都完成。

## 9. 当前第一批任务清单

| ID | 任务 | 状态 | 验收 |
|---|---|---|---|
| P0-1 | 修复 `MessageBackup.imagePaths` 缺失 | done | 图片消息备份恢复后仍显示 |
| P0-2 | 修复 `BgmBackup.emotion` 缺失/不同步 | done | 情绪 BGM 备份恢复后仍可匹配 |
| P0-3 | 修复 3 个 detekt swallowed exception | done | `detekt` 通过 |
| P0-4 | 当前未提交改动分组审查 | todo | 能按主题拆分提交 |
| P1-1 | 增加 28->29 migration 实测 | done | migration test 通过 |
| P1-2 | 增加 backup schema 映射测试 | done | 新字段遗漏会失败 |
| P2-1 | 移除 `ChatApiService.lastReasoningContent` 全局状态 | done | reasoning 随请求结果返回，测试覆盖 metadata stream |
