# 酒馆 AI (TavernAndroid) 开发计划

> 创建于 2026-05-28 | 基于 v1.2.75 全量审计报告
> 当前状态：167 .kt 文件，438 tests，功能覆盖率 ~55% (vs SillyTavern)

---

## 当前进度总览

### 已完成 (Phases A-M, J, K, L, P, Q, R)
- A 视觉体验 | B 记忆系统 | C ST 兼容 | D 质量保障 | E 对话导出
- F 聊天核心 | G 用户角色 | H 群聊 | I 扩展 API | M 数据管理
- J TTS/STT/多模态 | K 高级 WI+Prompt | L UI 手势
- O1 性能优化 | O2 无障碍 | O4 测试补充 | O5 质量验证
- P 聊天分支与书签 | Q 三级预设与模板引擎 | R 自动摘要

### 进行中
- T Web Search ✅ (代码完成，6 个测试需修复 android.util.Log mock)
- U 群聊调度增强 ✅ (三种调度策略 + 可配置间隔 + UI)
- V VN 模式 (立绘系统 ✅，待实现 UI)

### 待完成
- W 图像生成 | X STscript | Y 扩展框架 | Z 发布准备

---

## Phase S：安全收敛 (v1.2.8) — 优先级 🔴

> 解决审计报告中的高优先级安全问题

| 编号 | 任务 | 文件 | 说明 | 状态 |
|------|------|------|------|------|
| S1 | Release 签名配置 | `app/build.gradle.kts` | 替换 debug keystore 为正式签名配置 | ✅ |
| S2 | TLS 证书固定 | `res/xml/network_security_config.xml` | 添加公钥证书固定 (pin-set) | ✅ |
| S3 | Coil AsyncImage key 补全 | `CharacterAvatar.kt`, `ChatScreen.kt`, `InputBar.kt`, `MessageBubble.kt` | 补 `key` 参数防缓存错乱 | ✅ |
| S4 | catch 块 CE rethrow 全量审计 | 74 处 catch 块 | 逐文件确认所有 suspend catch 有 CE rethrow | ✅ |

**验证**：`assembleRelease` + `testDebugUnitTest` + 手动测试 API 连接

---

## Phase O3：多语言 (v1.3) — 优先级 🟡

> i18n: 英文/日文/韩文字符串资源

| 编号 | 任务 | 说明 | 状态 |
|------|------|------|------|
| O3-1 | 英文 strings.xml | `res/values-en/strings.xml`，翻译全部 236 条字符串 | ✅ |
| O3-2 | 日文 strings.xml | `res/values-ja/strings.xml`，236 条字符串 | ✅ |
| O3-3 | 韩文 strings.xml | `res/values-ko/strings.xml`，236 条字符串 | ✅ |
| O3-4 | 语言切换 UI | SettingsScreen 添加语言选择（跟随系统/中文/English/日本語/한국어） | ✅ |
| O3-5 | Locale 持久化 | AppLocale 存储到 DataStore，App 启动时 apply | ✅ |

**验证**：切换语言后所有 UI 文本正确显示，无截断

---

## Phase P：聊天分支与书签 (v1.3) — 优先级 🔴

> 审计报告标注为"完全缺失"的核心功能，SillyTavern 树状分支

| 编号 | 任务 | 说明 | 状态 |
|------|------|------|------|
| P1 | 数据模型扩展 | `MessageEntity` 已有 `parentId`, `branchId`, `isActive` 字段 | ✅ |
| P2 | 分支创建逻辑 | `ChatRepository.createBranchFromMessage()`: 从任意消息创建新分支 | ✅ |
| P3 | 分支导航 UI | `BranchNavigationBar` composable: 显示分支切换，前后导航 | ✅ |
| P4 | 书签系统 | 使用 `isPinned` 字段实现书签功能，`togglePinMessage` 操作 | ✅ |
| P5 | 书签列表 UI | `BookmarkSheet`: 显示所有书签消息，点击跳转到消息位置 | ✅ |
| P6 | 分支导出 | 导出时支持选择分支路径 | ⬜ |
| P7 | DB Migration v23 | `branches` 表 + `messages` 表 `parent_id`, `branch_id` 列已存在 | ✅ |

**已实现**：
- `BranchEntity` + `BranchDao`: 分支 CRUD 操作
- `ChatRepository`: `switchBranch`, `createBranch`, `createBranchFromMessage`, `deleteBranch`
- `ChatViewModel`: `loadBranches`, `switchBranch`, `createBranch`, `deleteBranch`
- `BranchNavigationBar`: 分支前后切换导航
- `BookmarkSheet`: 书签消息列表，点击跳转
- `MessageBubble`: 长按菜单包含"创建分支"和"置顶消息"选项

**验证**：创建分支 → 切换分支 → 置顶消息 → 书签列表跳转

---

## Phase Q：三级预设与模板引擎 (v1.3) — 优先级 🔴

> SillyTavern Global→Char→Chat 三级预设 + Handlebars 模板

| 编号 | 任务 | 说明 | 状态 |
|------|------|------|------|
| Q1 | 预设层级重构 | `PresetEntity` 新增 `scope: PresetScope` (GLOBAL/CHARACTER/CHAT) | ✅ |
| Q2 | 优先级解析 | `PresetResolver.resolve()`: Chat > Character > Global 合并 | ✅ |
| Q3 | Handlebars 模板引擎 | 引入 `handlebars` 库，支持 `{{user}}`, `{{char}}`, `{{persona}}` 等变量 | ✅ |
| Q4 | 条件块支持 | `{{#if ...}}`, `{{#each ...}}` 模板语法 | ✅ |
| Q5 | 预设管理 UI 重构 | PresetScreen: 按 scope 分组，拖拽排序 | ✅ |
| Q6 | PromptBuilder 重构 | 使用模板引擎替代硬编码字符串拼接 | ✅ |

**已实现**：
- `TemplateEngine`: Handlebars 模板引擎封装，支持 `{{var}}`, `{{#if}}`, `{{#each}}`, `{{#unless}}` 语法
- `PromptBuilder.replacePlaceholders()`: 支持 `{{user}}`, `{{char}}`, `{{persona}}`, `{{description}}`, `{{personality}}`, `{{firstMessage}}`, `{{mesExamples}}`, `{{personaDescription}}` 变量
- 模板编译缓存 + 解析失败 fallback 到简单替换

**依赖**：`com.github.jknack:handlebars:4.4.0` (JitPack)

**验证**：创建 Global/Character/Chat 预设 → 模板变量正确替换 → 优先级生效

---

## Phase R：自动摘要 (v1.4) — 优先级 🟡

> 长对话自动压缩为摘要，节省 token

| 编号 | 任务 | 说明 | 状态 |
|------|------|------|------|
| R1 | 摘要模型 | `SummaryEntity` + `SummaryRepository`，存储对话摘要 | ✅ |
| R2 | 摘要生成 UseCase | `SummaryUseCase`: 当上下文超过阈值时，调用 LLM 生成摘要 | ✅ |
| R3 | 摘要注入 Prompt | `PromptBuilder`: 将摘要作为上下文注入，替代旧消息 | ✅ |
| R4 | 摘要触发策略 | 可配置：消息数阈值 / token 数阈值 / 手动触发 | ✅ |
| R5 | 摘要查看 UI | `SummarySheet`: 查看/编辑/删除摘要 | ✅ |
| R6 | DB Migration v24 | 新增 `summaries` 表 | ✅ |

**数据模型**：
```sql
CREATE TABLE summaries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    chat_id INTEGER NOT NULL REFERENCES chats(id),
    content TEXT NOT NULL,
    message_range_start INTEGER NOT NULL,
    message_range_end INTEGER NOT NULL,
    token_count INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL
)
```

**验证**：长对话 → 自动触发摘要 → 摘要注入 prompt → 对话质量不下降

---

## Phase R.5：质量验证检查点 — 优先级 🔴

> Phase P/Q/R 完成后的质量门禁，确保新功能无回归

| 编号 | 任务 | 说明 | 状态 |
|------|------|------|------|
| QA1 | Coil key 全量检查 | 所有 6 处 AsyncImage 已有 key 参数 | ✅ |
| QA2 | CE rethrow 全量检查 | 全部 64 个 catch 块正确处理（~40 suspend+CE, ~24 非suspend） | ✅ |
| QA3 | 测试覆盖率 | 新增 SummaryUseCaseTest (13 tests)，总测试 426 | ✅ |
| QA4 | DB Migration 验证 | v24 migration summaries 表 schema 正确 | ✅ |

**验证**：`testDebugUnitTest` 全部通过 + 手动功能测试

---

## Phase T：Web Search (v1.4) — 优先级 🔴

> 角色可通过搜索引擎获取实时信息

| 编号 | 任务 | 说明 | 状态 |
|------|------|------|------|
| T1 | 搜索引擎接口 | `WebSearchService`，支持 DuckDuckGo(免费)/Bing/Google | ✅ |
| T2 | 搜索结果解析 | 提取标题、摘要、URL，格式化为上下文 | ✅ |
| T3 | 搜索触发 | `/search`/`/搜索` 命令 + autoSearch 自动检测 | ✅ |
| T4 | 结果注入 | PromptBuilder.build() + buildGroupChat() 注入 `[Web Search Results]` | ✅ |
| T5 | 搜索设置 | SettingsScreen WebSearchSection: 引擎选择、API Key、maxResults、autoSearch | ✅ |
| T6 | 搜索结果缓存 | ConcurrentHashMap LRU 缓存，30min TTL，100 条上限 | ✅ |

**已实现**：
- `WebSearchService`: 3 搜索引擎 (DuckDuckGo 免费/Bing/Google)，ConcurrentHashMap LRU 缓存
- `SendMessageUseCase.performSearchIfNeeded()`: `/search`/`/搜索` 命令 + autoSearch 自动检测
- `PromptBuilder`: build() 和 buildGroupChat() 均支持 `searchResults` 参数注入
- `SettingsScreen`: WebSearchSection (引擎选择/API Key/maxResults slider/autoSearch 开关)
- `SettingsViewModel`: webSearchConfig StateFlow + updateWebSearchConfig()
- i18n: 4 语言 (values/values-en/values-ja/values-ko) 各 11 条搜索相关字符串
- `WebSearchServiceTest`: 12 个单元测试覆盖各引擎 + 缓存

**已知问题**：6 个 WebSearchService 测试因 `android.util.Log` 在纯 JVM 单测中不可用而失败 (IllegalStateException)，需 mock Log 或改用 Robolectric

**验证**：`/search 今天天气` → 搜索结果注入 → 角色回答包含实时信息

---

## Phase U：群聊调度增强 (v1.4) — 优先级 🟢

> SillyTavern 自然/列表顺序调度算法

| 编号 | 任务 | 说明 | 状态 |
|------|------|------|------|
| U1 | 调度策略枚举 | `GroupSchedulingStrategy`: NATURAL, LIST_ORDER, ROUND_ROBIN | ✅ |
| U2 | 自然调度 | 基于健谈度 + 上下文相关性 + 随机因子选择下一个发言者 | ✅ |
| U3 | 列表顺序 | 按角色列表顺序轮流发言 | ✅ |
| U4 | 调度 UI | ChattinessSheet: 调度策略选择 + 发言间隔滑块 | ✅ |
| U5 | 发言间隔控制 | 可配置消息间隔时间 (500ms-5000ms)，避免刷屏 | ✅ |

**验证**：切换调度策略 → 群聊发言顺序符合预期

---

## Phase V：Visual Novel Mode (v1.5) — 优先级 🔴

> 移动端差异化壁垒：立绘 + 背景 + 对话框

| 编号 | 任务 | 说明 | 状态 |
|------|------|------|------|
| V1 | 立绘系统 | `SpriteEntity` + `SpriteRepository`，存储角色表情立绘 | ✅ |
| V2 | 表情映射 | 根据 AI 回复情感分析自动切换立绘表情 | ⬜ |
| V3 | VN 游戏界面 | 全屏立绘 + 底部对话框 + 背景层 | ⬜ |
| V4 | 转场动画 | 淡入淡出、滑动等场景切换效果 | ⬜ |
| V5 | BGM 系统 | `BgmEntity`，角色可配置背景音乐 | ⬜ |
| V6 | VN 设置 | 角色编辑页: 上传立绘、配置表情映射、设置 BGM | ⬜ |
| V7 | DB Migration v26 | 新增 `sprites` 表 | ✅ |

**差异化价值**：这是 SillyTavern 没有的移动端独有功能，可成为核心卖点

**验证**：进入 VN 模式 → 立绘随情感切换 → 背景/转场/BGM 正常

---

## Phase W：图像生成增强 (v1.5) — 优先级 🟡

> SD WebUI/ComfyUI API 集成

| 编号 | 任务 | 说明 | 状态 |
|------|------|------|------|
| W1 | SD WebUI API | `StableDiffusionService`: txt2img, img2img | ⬜ |
| W2 | ComfyUI API | `ComfyUiService`: workflow JSON 执行 | ⬜ |
| W3 | 图像生成设置 | Settings: SD WebUI/ComfyUI 地址、模型选择 | ⬜ |
| W4 | 角色立绘生成 | `/draw` 命令: 基于角色描述生成立绘 | ⬜ |
| W5 | 图像画廊 | `ImageGalleryScreen`: 查看所有生成的图片 | ⬜ |

**验证**：`/draw 角色名 开心表情` → 生成图片 → 保存到画廊

---

## Phase X：STscript 命令引擎 (v1.6) — 优先级 🟢

> SillyTavern DSL 脚本 + 宏系统

| 编号 | 任务 | 说明 | 状态 |
|------|------|------|------|
| X1 | 脚本解析器 | `ScriptEngine`: 解析 `/command arg1 arg2` 语法 | ⬜ |
| X2 | 内置命令 | `/send`, `/roll`, `/setvar`, `/getvar`, `/if`, `/loop` | ⬜ |
| X3 | 宏系统 | `{{roll 2d6}}`, `{{getvar name}}`, `{{random a\|b\|c}}` | ⬜ |
| X4 | 脚本编辑 UI | 脚本编辑器，语法高亮 | ⬜ |
| X5 | 脚本市场 | 导入/导出脚本 | ⬜ |

**验证**：输入 `/roll 2d6` → 返回骰子结果 → 变量可跨消息使用

---

## Phase Y：扩展框架 (v1.6) — 优先级 🟡

> 原 ROADMAP Phase N 的重构版本

| 编号 | 任务 | 说明 | 状态 |
|------|------|------|------|
| Y1 | ExtensionApi 接口 | 定义插件生命周期: init/enable/disable/configure | ⬜ |
| Y2 | Hook 系统 | `HookManager`: onMessageSend, onMessageReceive, onPromptBuild 等钩子 | ⬜ |
| Y3 | 内置扩展重构 | TTS/记忆/正则/图像生成重构为扩展 | ⬜ |
| Y4 | 扩展存储 | `ExtensionConfigEntity` + `ExtensionRepository` | ⬜ |
| Y5 | 扩展 UI | ExtensionScreen: 启用/禁用/配置扩展 | ⬜ |
| Y6 | 扩展 manifest | JSON manifest 定义扩展元数据 | ⬜ |

**验证**：禁用 TTS 扩展 → TTS 功能消失 → 重新启用 → 功能恢复

---

## Phase Z：发布准备 (O5) — 优先级 🟡

> Play Store 上架

| 编号 | 任务 | 说明 | 状态 |
|------|------|------|------|
| Z1 | Release 签名 | 生成正式 keystore，配置 signingConfigs | ✅ (已由 S1 完成) |
| Z2 | App Icon | 设计正式 app icon (adaptive icon) | ⬜ |
| Z3 | 截图准备 | 手机 + 平板截图，各 4-8 张 | ⬜ |
| Z4 | Store Listing | 标题、描述、分类、隐私政策 | ⬜ |
| Z5 | 版本号管理 | 自动化 versionCode/versionName | ⬜ |
| Z6 | R8 全量优化 | `minifyEnabled true` + `shrinkResources true` | ⬜ |
| Z7 | 签名验证 | 确保 release APK 可正常安装运行 | ⬜ |

**验证**：`assembleRelease` → 安装到真机 → 全流程测试

---

## 执行计划

### 里程碑 v1.2.8 (1 周) — 安全收敛
```
Phase S: S1 + S2 + S3 + S4
```

### 里程碑 v1.3 (4 周) — 核心体验对齐
```
Week 1: Phase O3 (多语言)
Week 2-3: Phase P (聊天分支/书签)
Week 4: Phase Q (三级预设/模板)
```

### 里程碑 v1.4 (4 周) — 智能能力扩展
```
Week 1: Phase R (自动摘要) + Phase R.5 (质量验证)
Week 2-3: Phase T (Web Search) 🔴
Week 4: Phase U (群聊调度增强)
```

### 里程碑 v1.5 (6 周) — 移动端差异化
```
Week 1-4: Phase V (Visual Novel Mode)
Week 5-6: Phase W (图像生成增强)
```

### 里程碑 v1.6 (6 周) — 生态建设
```
Week 1-3: Phase X (STscript 命令引擎)
Week 3-5: Phase Y (扩展框架)
Week 6: Phase Z (发布准备)
```

---

## 技术债务 & 质量指标

| 指标 | 当前 | v1.3 目标 | v1.6 目标 |
|------|------|----------|----------|
| 测试数量 | 438 | 500+ | 700+ |
| 功能覆盖率 (vs ST) | ~52% | 65% | 75% |
| 安全评分 | B+ | A- | A |
| 多语言支持 | 中/英/日/韩 | 中/英/日/韩 | 中/英/日/韩 |
| DB Schema 版本 | v26 | v25 | v26 |

---

## 依赖引入计划

| Phase | 新增依赖 | 用途 |
|-------|---------|------|
| Q | `com.github.jknack:handlebars:4.4.x` | 模板引擎 |
| R | 无新增 | 复用现有 Retrofit |
| T | 无新增 | 复用现有 Retrofit |
| V | 无新增 | 复用现有 Coil |
| W | 无新增 | 复用现有 Retrofit |
| X | 无新增 | 自研解析器 |
| Y | 无新增 | 自研框架 |

---

*本计划随开发进度实时更新。每完成一个 Phase，在此文档中标记状态并记录实际工时。*
