# 酒馆 AI (TavernAndroid) 路线图

> 更新于 2026-05-27，反映实际代码状态

---

## 已完成 Phases

### Phase A：视觉体验 ✅

- A1 自定义背景（CharacterEntity/ChatEntity background_path, BackgroundPickerSheet）
- A2 气泡样式自定义（DataStore preferences, MessageBubble 读取配置）
- A3 Material You 动态取色（dynamicColorScheme, Android 12+）
- A4 动画增强（AnimatedVisibility, 页面转场, 光标闪烁）

### Phase B：记忆系统 ✅

- B1 数据模型（MemoryEntity + MemoryAtomEntity）
- B2 记忆提取（MemoryExtractionUseCase, 自动/手动）
- B3 记忆检索与注入（MemoryRepository, single OR-query, PromptBuilder [Memory] section）
- B4 记忆管理 UI（MemoryScreen, MemoryViewModel, 手动添加/编辑/删除）

### Phase C：SillyTavern 兼容 ✅

- C1 角色卡 PNG 导出/导入（PngMetadata readCharaCard/writeCharaCard）
- C2 chara_card_v3 spec（v2/v3 自动检测适配）
- C3 WI 高级逻辑（selective, selectiveLogic, excludeRecursion, preventRecursion, group 等）
- C4 正则脚本（ScriptEntity, ScriptRepository, find/replace with regex/literal）

### Phase D：质量保障 ✅

- D1 单元测试（336 tests, 29 test files）
- D2 UI 测试依赖已添加
- D3 ProGuard/R8 + GitHub Actions CI

### Phase E：对话导出 ✅

- E1 多格式导出（Markdown / HTML / 纯文本 / JSON）
- E2 单对话 + 批量导出（ZIP）
- E3 FileProvider + Android Share Sheet
- E4 对话导入（酒馆 AI JSON, SillyTavern jsonl, JSON 数组）

### Phase F：聊天核心增强 ✅

- F1 滑动切换替代回复（SwipeUtils, addSwipe/switchSwipe）
- F2 作者注释（AuthorNoteRepository, AuthorNoteEntity）
- F3 继续生成（ContinueGenerationUseCase）
- F4 消息时间戳（MessageEntity.createdAt）

### Phase G：用户角色系统 ✅

- G1 数据模型（PersonaEntity, PersonaRepository）
- G2 管理 UI（PersonaScreen, PersonaViewModel）
- G3 Prompt 集成（PromptBuilder 注入 persona）

### Phase H：群聊 ✅

- H1 数据模型（ChatCharacterEntity, isGroup flag, groupChattiness）
- H2 消息逻辑（SendMessageUseCase: sendGroupMessage/sendDirectMessage, PromptBuilder.buildGroupChat）
- H3 群聊 UI（GroupChatCreateScreen/ViewModel, ChattinessSheet, InputBar @mention）

### Phase I：扩展 API ✅

- I1 KoboldAI（ApiProvider.KoboldAI）
- I2 Gemini（ApiProvider.Gemini）
- I3 OpenRouter + 自定义（ApiProvider.OpenRouter, ApiProvider.Custom）
- I4 预设配置（PresetEntity, PresetRepository, resolveEffectivePreset）
- 所有 7 个 provider: OpenAI, Claude, Ollama, KoboldAI, OpenRouter, Gemini, Custom

### Phase M：数据管理 ✅

- M1 全量备份/恢复（BackupManager: backup/restore, 10 表并行查询）
- M2 批量操作（ChatListViewModel: exportAllChats）
- M3 ST 完整导入（SillyTavernImporter: importFromPng/importFromJson/exportToJson/exportToPng）
- M4 存储管理（DataManagementScreen）

---

## 待完成 Phases

### Phase J：TTS/STT/多模态  ← 部分完成

- [x] J1 TTS 朗读（TtsHelper, auto-detect locale, speed/pitch 控制）
- [ ] J2 STT 语音输入（SpeechRecognizer 集成）
- [ ] J3 图片发送（multimodal API: base64 image in message）
- [ ] J4 图片生成（DALL-E / Stable Diffusion API）

### Phase K：高级 WI + Prompt  ← 部分完成

- [x] K1 高级 WI 字段（selective logic, group, depth, probability — 已在 C3 完成）
- [x] K2 Prompt 模板（PresetEntity 已支持 systemPrompt/postHistoryInstructions/authorNote）
- [ ] K3 注入深度/位置控制 UI（目前 depth 字段存在但无 UI 调整入口）
- [x] K4 Token 计数器（TokenEstimator 字符类别估算，InputBar 实时显示上下文 token 数）

### Phase L：UI 手势  ← 部分完成

- [x] L2 消息搜索（ChatViewModel: searchQuery, searchResults, searchIndex）
- [x] L5 收藏置顶（MessageEntity.isPinned, togglePinMessage, getPinnedMessages）
- [x] L1 滑动操作（左滑回复/编辑，右滑删除 — MessageBubble detectHorizontalDragGestures）
- [x] L3 上下文菜单（长按消息：复制/编辑/删除/重发/分支）
- [ ] L4 标签筛选（CharacterEntity.tags 存在但 HomeScreen 无标签筛选 UI）

### Phase N：扩展系统  ← 待做

- [ ] N1 插件接口（定义 ExtensionApi 接口）
- [ ] N2 内置扩展重构（将 TTS/记忆/正则等重构为扩展）
- [ ] N3 扩展存储（ExtensionConfigEntity）
- [ ] N4 扩展 UI（扩展商店/启用/禁用）

### Phase O：最终打磨  ← 待做

- [ ] O1 性能优化（已优化：Gradle caching, LRU caches, O(1) lookups, dedicated DB queries）
- [ ] O2 无障碍（TalkBack, contentDescription, semantics）
- [ ] O3 多语言（i18n: 英文/日文/韩文字符串资源）
- [ ] O4 测试补充（当前 336 tests, 目标 400+）
- [ ] O5 发布（Play Store preparation, screenshots, listing）

---

## 执行顺序

```
Phase A (视觉体验)       ← ✅ 已完成
Phase B (记忆系统)       ← ✅ 已完成
Phase C (SillyTavern 兼容) ← ✅ 已完成
Phase D (质量保障)       ← ✅ 已完成
Phase E (对话导出)       ← ✅ 已完成
Phase F (聊天核心增强)   ← ✅ 已完成
Phase G (用户角色系统)   ← ✅ 已完成
Phase H (群聊)           ← ✅ 已完成
Phase I (扩展 API)       ← ✅ 已完成
Phase M (数据管理)       ← ✅ 已完成

Phase J (TTS/STT/多模态) ← 部分完成 (J1 ✅, J2-J4 待做)
Phase K (高级 WI+Prompt) ← 部分完成 (K1-K2 ✅, K3-K4 待做)
Phase L (UI 手势)        ← 部分完成 (L2/L5 ✅, L1/L3/L4 待做)
Phase N (扩展系统)       ← 待做
Phase O (最终打磨)       ← 待做
```

**下一步优先级:**
1. **K4 Token 计数器** — 用户高频需求，实时显示上下文占用
2. **L1 滑动操作 + L3 上下文菜单** — 移动端核心交互体验
3. **J2 STT 语音输入** — 移动端差异化功能
4. **L4 标签筛选** — 已有数据层，只需加 UI

每个 Phase 完成后 `assembleDebug` 验证 + `testDebugUnitTest` + 安装到模拟器测试。
