# 酒馆 AI (TavernAndroid) 路线图

> 更新于 2026-05-17，基于用户优先级重新排序

---

## Phase A：视觉体验（优先）

### A1. 自定义背景 ✅

**数据层:**
- `CharacterEntity` 新增 `background_path: String?`（角色默认背景）
- `ChatEntity` 新增 `background_path: String?`（对话级覆盖，优先级更高）
- DB version 3 + migration

**UI 层:**
- ChatScreen: `Box` 包裹，底层 `AsyncImage`（Coil），上层 `LazyColumn`
- 半透明遮罩（`Color.Black.copy(alpha=0.4f)`）保证文字可读
- 大图降采样：`BitmapFactory.Options.inSampleSize` 或 Coil 的 `size()` 限制

**入口:**
- 角色编辑页 → "设置背景"按钮
- 聊天页顶栏菜单 → "更换背景"
- 预设背景包（6-8 张暗色/场景主题图，打包在 `res/raw/` 或 `assets/backgrounds/`）

**存储:** `filesDir/backgrounds/`

### A2. 气泡样式自定义 ✅

- 设置页新增"聊天气泡"选项：颜色（用户/助手分别设）、圆角大小、字体大小
- 存入 DataStore preferences
- ChatScreen 的 `MessageBubble` 读取配置渲染

### A3. Material You 动态取色 ✅

- `Theme.kt` 启用 `dynamicColorScheme`（Android 12+）
- 低版本 fallback 到现有暗色/亮色主题
- 设置页新增"跟随系统主题"开关

### A4. 动画增强 ✅

- 消息出现：`AnimatedVisibility(fadeIn + slideInVertically)`
- 页面转场：Navigation Compose 的 `enterTransition` / `exitTransition`
- 流式输出：光标闪烁动画（`InfiniteTransition`）

---

## Phase B：记忆系统

### B1. 数据模型 ✅

```kotlin
@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "character_id") val characterId: Long,
    val content: String,              // 记忆内容
    val importance: Int = 5,          // 1-10
    val source: String = "auto",      // "auto" / "manual"
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "last_accessed") val lastAccessed: Long,
    @ColumnInfo(name = "access_count") val accessCount: Int = 0
)
```

### B2. 记忆提取 ✅

- **自动提取:** 对话结束后（切出聊天/新建对话时），调用 LLM 总结本轮关键信息
- **手动添加:** 角色详情页 → 记忆管理 → 手动写入
- 提取 prompt: `"从以下对话中提取关于角色和用户的关键事实，每条一行：\n{recent_messages}"`

### B3. 记忆检索与注入 ✅

- 用户发消息时，用 `LIKE` 匹配消息关键词 vs 记忆内容
- 取 top-K（按 importance + last_accessed 加权排序）注入 PromptBuilder
- PromptBuilder 新增 `[Memory]\n` section

### B4. 记忆管理 UI ✅

- 角色编辑页新增"记忆"tab / 独立 MemoryScreen
- 列表展示：内容预览、重要度、访问次数、创建时间
- 支持手动添加/编辑/删除
- 可选：一键"遗忘"清除所有记忆

**预计改动文件:**
```
新增: MemoryEntity.kt, MemoryDao.kt, MemoryRepository.kt
修改: TavernDatabase.kt (v4), PromptBuilder.kt
新增: MemoryScreen.kt, MemoryViewModel.kt
修改: CharacterEditScreen.kt (加记忆入口), ChatViewModel.kt (记忆注入)
```

---

## Phase C：SillyTavern 兼容

### C1. 角色卡 PNG 导出 ✅

- `PngMetadata` 已有 `readCharaCard()`，补 `writeCharaCard()`
- 将 JSON 角色数据写入 PNG tEXt chunk（key = `chara`）
- 导出入口：角色编辑页 → "导出为 PNG"

### C2. chara_card_v3 spec ✅

- `CharacterData` 扩展 v3 字段：`alternate_greetings`、`group_only_greetings`、`post_history_instructions`、`tags` 增强
- 导入时自动检测 v2/v3 并适配

### C3. WI 高级逻辑 ✅

- WorldBookEntryEntity 新增：`selective`、`selectiveLogic`、`excludeRecursion`、`preventRecursion`、`group`、`groupOverride`、`groupWeight`
- WorldBookRepository.matchEntries 实现 selective logic（AND/OR/NOT）
- 支持递归深度控制

### C4. 正则脚本 ✅

- 新增 `ScriptEntity` 表存储用户定义的正则规则
- 每条规则：`pattern`、`replacement`、`placement`（user/assistant/both）、`enabled`
- ChatViewModel 在消息显示前应用正则处理

---

## Phase D：质量保障

### D1. 单元测试 ✅

- PromptBuilderTest (10 tests) — 纯函数测试 prompt 组装逻辑
- ScriptRepositoryTest (11 tests) — 正则/字面量替换、过滤、容错
- WorldBookMatchTest (10 tests) — 关键词匹配 + selective logic

### D2. UI 测试 ✅

- Compose UI Test 依赖已添加
- 可后续补充关键页面渲染测试

### D3. 构建与发布 ✅

- ProGuard/R8 rules 扩充（Room、Hilt、OkHttp、Retrofit、Markwon、Coil、Coroutines）
- GitHub Actions CI：lint + test + build + artifact upload

---

## Phase E：对话导出（后期）

### E1. 导出格式 ✅

- ChatExporter 支持 Markdown / HTML / 纯文本 / JSON 四种格式
- HTML 使用暗色主题、圆角气泡布局

### E2. 导出范围 ✅

- 单对话导出（ChatListScreen 每个对话项分享按钮）
- 批量导出（顶栏菜单"导出全部对话"→ ZIP）

### E3. 分享集成 ✅

- FileProvider + Android Share Sheet
- 导出后自动弹出分享面板

### E4. 对话导入 ✅

- ChatImporter 支持酒馆 AI JSON、SillyTavern jsonl、JSON 数组格式
- ChatListScreen 顶栏导入按钮

---

## 执行顺序

```
Phase A (视觉体验)  ← ✅ 已完成
  ├─ A1 自定义背景 ✅
  ├─ A2 气泡样式 ✅
  ├─ A3 Material You ✅
  └─ A4 动画增强 ✅

Phase B (记忆系统)  ← ✅ 已完成
  ├─ B1 数据模型 ✅
  ├─ B2 记忆提取 ✅
  ├─ B3 检索注入 ✅
  └─ B4 管理 UI ✅

Phase C (SillyTavern 兼容)  ← ✅ 已完成
  ├─ C1 PNG 导出 ✅
  ├─ C2 v3 spec ✅
  ├─ C3 WI 高级逻辑 ✅
  └─ C4 正则脚本 ✅

Phase D (质量保障)  ← ✅ 已完成
  ├─ D1 单元测试 ✅
  ├─ D2 UI 测试 ✅
  └─ D3 CI/CD ✅

Phase E (对话导出)  ← ✅ 已完成
  ├─ E1 多格式导出 ✅
  ├─ E2 范围选择 ✅
  ├─ E3 分享集成 ✅
  └─ E4 对话导入 ✅

Phase F (聊天核心增强)  ← ✅ 已完成
  ├─ F1 滑动切换替代回复 ✅
  ├─ F2 作者注释 ✅
  ├─ F3 继续生成 ✅
  └─ F4 消息时间戳 ✅

Phase G (用户角色系统)  ← ✅ 已完成
  ├─ G1 数据模型 ✅
  ├─ G2 管理 UI ✅
  └─ G3 Prompt 集成 ✅

Phase H (群聊)  ← 待做
  ├─ H1 数据模型
  ├─ H2 轮替逻辑
  └─ H3 群聊 UI

Phase I (扩展 API)  ← 待做
  ├─ I1 KoboldAI
  ├─ I2 Gemini
  ├─ I3 预设配置
  └─ I4 连接测试

Phase J (TTS/STT/多模态)  ← 待做
  ├─ J1 TTS 朗读
  ├─ J2 STT 语音输入
  ├─ J3 图片发送
  └─ J4 图片生成

Phase K (高级 WI + Prompt)  ← 待做
  ├─ K1 高级 WI 字段
  ├─ K2 Prompt 模板
  ├─ K3 注入深度/位置
  └─ K4 Token 计数器

Phase L (UI 手势)  ← 待做
  ├─ L1 滑动操作
  ├─ L2 消息搜索
  ├─ L3 上下文菜单
  ├─ L4 标签筛选
  └─ L5 收藏置顶

Phase M (数据管理)  ← 待做
  ├─ M1 全量备份/恢复
  ├─ M2 批量操作
  ├─ M3 ST 完整导入
  └─ M4 存储管理

Phase N (扩展系统)  ← 待做
  ├─ N1 插件接口
  ├─ N2 内置扩展重构
  ├─ N3 扩展存储
  └─ N4 扩展 UI

Phase O (最终打磨)  ← 待做
  ├─ O1 性能优化
  ├─ O2 无障碍
  ├─ O3 多语言
  ├─ O4 测试补充
  └─ O5 发布
```

每个 Phase 完成后 `assembleDebug` 验证 + `testDebugUnitTest` + 安装到模拟器测试。
