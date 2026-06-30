# 酒馆 AI (TavernAndroid) 路线图

> 更新于 2026-06-10，反映实际代码状态
> **核心原则：优化优先于扩展，确保现有功能稳定可用、体验更好**

---

## 已完成 Phases

### Phase A：视觉体验 ✅
- A1 自定义背景 | A2 气泡样式 | A3 Material You 动态取色 | A4 动画增强

### Phase B：记忆系统 ✅
- B1 数据模型 | B2 记忆提取 | B3 记忆检索与注入 | B4 记忆管理 UI

### Phase C：SillyTavern 兼容 ✅
- C1 角色卡 PNG 导入导出 | C2 chara_card_v3 spec | C3 WI 高级逻辑 | C4 正则脚本

### Phase D：质量保障 ✅
- D1 单元测试（762 tests, 42 test files）| D2 UI 测试依赖 | D3 ProGuard/R8 + CI

### Phase E：对话导出 ✅
- E1 多格式导出 | E2 批量导出 | E3 Share Sheet | E4 对话导入

### Phase F：聊天核心增强 ✅
- F1 滑动切换 | F2 作者注释 | F3 继续生成 | F4 消息时间戳

### Phase G：用户角色系统 ✅
- G1 数据模型 | G2 管理 UI | G3 Prompt 集成

### Phase H：群聊 ✅
- H1 数据模型 | H2 消息逻辑 | H3 群聊 UI

### Phase I：扩展 API ✅
- I1 KoboldAI | I2 Gemini | I3 OpenRouter + Custom | I4 预设配置
- 7 个 provider: OpenAI, Claude, Ollama, KoboldAI, OpenRouter, Gemini, Custom

### Phase M：数据管理 ✅
- M1 全量备份/恢复 | M2 批量操作 | M3 ST 完整导入 | M4 存储管理

### Phase J：TTS/STT/多模态 ✅
- J1 TTS 朗读 | J2 STT 语音输入 | J3 图片发送 | J4 图片生成

### Phase K：高级 WI + Prompt ✅
- K1 高级 WI 字段 | K2 Prompt 模板 | K3 注入深度控制 | K4 Token 计数器

### Phase L：UI 手势 ✅
- L1 滑动操作 | L2 消息搜索 | L3 上下文菜单 | L4 标签筛选 | L5 收藏置顶

### Phase P：聊天分支与书签 ✅
- P1-P7 全部完成：BranchEntity、分支创建/切换/导航、书签系统

### Phase Q：三级预设与模板引擎 ✅
- Q1-Q6 全部完成：Global→Char→Chat 三级预设、Handlebars 模板引擎

### Phase R：自动摘要 ✅
- R1-R6 全部完成：SummaryEntity、SummaryUseCase、PromptBuilder 注入、触发策略

### Phase S：安全收敛 ✅
- S1 Release 签名 | S2 TLS 证书固定 | S3 Coil key 补全 | S4 CE rethrow 审计

### Phase O3：多语言 ✅
- O3-1 ~ O3-5 全部完成：英/日/韩 strings.xml + 语言切换 UI + Locale 持久化

### Phase T：Web Search ✅
- T1-T6 全部完成：3 搜索引擎、缓存、/search 命令、autoSearch、设置 UI

### Phase U：群聊调度增强 ✅
- U1-U5 全部完成：NATURAL/LIST_ORDER/ROUND_ROBIN 策略 + 间隔控制

---

## 进行中

### Phase V：Visual Novel 模式 — 核心完成，体验继续打磨
- [x] V1 立绘系统（SpriteEntity + SpriteRepository）
- [x] V2 表情映射（EmotionDetector 关键词匹配）
- [x] V3 VN 游戏界面补全（输入框 + 发送链路）
- [x] V5 BGM 系统补全（BgmPlayer + AudioFocus + 情感映射）
- [x] V6 VN 设置优化（入口与基础体验优化）
- [x] V7 DB Migration v26/v27
- [ ] V4 转场动画优化（低优先级打磨）

### v1.3.1 收口验证
- [x] 全量 `testDebugUnitTest`
- [x] `lintDebug`
- [x] `assembleDebug`
- [x] Quick Replies 管理页 + 聊天页真实 smoke
- [x] 覆盖率报告核算（Kover debug：业务代码 line 70.19%，branch 42.85%）

---

## 当前计划：优化优先 (v1.3.1 收口)

> 详见 AUDIT-REPORT.md 和 DEVELOPMENT-PLAN.md

### Phase 1：稳定性修复 (v1.2.9) — ✅
1.1 DB Migration 测试 | 1.2 Backup 版本校验 | 1.3 SSE 断线重连
1.4 API 限流退避 | 1.5 WebSearch 测试修复 | 1.6 reasoningContent 并发安全

### Phase 2：ChatViewModel 拆分 (v1.2.9) — ✅
2.1 ChatStreamingManager | 2.2 GroupChatManager | 2.3 BranchManager
2.4 VnModeManager | 2.5 ViewModel 瘦身 | 2.6 ChatScreen 子组件拆分

### Phase 3：VN 模式补全 (v1.3.0) — ✅
3.1 VnScreen 输入框 | 3.2 BGM 播放器 | 3.3 EmotionDetector 增强
3.4 VN 入口优化 | 3.5 VN 测试

### Phase 4：UX 润色 (v1.3.0) — ✅
4.1 消息分页 | 4.2 搜索失败提示 | 4.3 图像生成多 provider
4.4 世界书匹配高亮 | 4.5 预设模板预览 | 4.6 LaunchedEffect 优化
4.7 API 超时可配置

### Phase 5：测试补全 (v1.3.1) — ✅，按覆盖率热点继续补分支
5.1 DAO 测试 | 5.2 Migration 测试 | 5.3 VN 测试
5.4 Integration 测试 | 5.5 Backup 测试

### Phase 6：性能优化 (v1.3.1) — ✅
6.1 Room 查询优化 | 6.2 图片内存池 | 6.3 大数据量验证 | 6.4 长 prompt 验证

### Quick Replies / STscript Lite — ✅ 核心接入，收口验证中
持久化 | 聊天页快捷回复栏 | 管理页 | 自动触发 | 备份恢复 | 结果清洗与安全边界

---

## 远期计划（v1.4.0+）

> 仅在上述优化全部完成后才推进

### Phase W：图像生成增强
- W1 SD WebUI API | W2 ComfyUI API | W3 设置 | W4 /draw 命令 | W5 画廊

### Phase X：STscript 命令引擎
- X1 解析器 | X2 内置命令 | X3 宏系统 | X4 编辑 UI | X5 脚本市场

### Phase Y：扩展框架
- Y1 ExtensionApi | Y2 Hook 系统 | Y3 内置扩展重构 | Y4-Y6 存储/UI/manifest

### Phase Z：发布准备
- Z1 Release 签名(✅) | Z2 App Icon | Z3 截图 | Z4 Store Listing | Z5-Z7 版本/R8/验证

---

## 里程碑

| 版本 | 内容 | 预估周期 |
|------|------|----------|
| v1.2.9 | Phase 1 + 2（稳定性 + 架构优化） | ✅ |
| v1.3.0 | Phase 3 + 4（VN 补全 + UX 润色） | ✅ |
| v1.3.1 | Phase 5 + 6 + Quick Replies 收口 | 进行中 |
| v1.4.0+ | Phase W/X/Y/Z（新功能） | 远期 |

---

*每个 Phase 完成后 `assembleDebug` 验证 + `testDebugUnitTest` + 安装到模拟器测试。*
