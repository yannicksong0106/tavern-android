# 酒馆 AI (TavernAndroid) 路线图

> 更新于 2026-07-19 | HEAD `73436cc` | versionCode 24 / 1.4.0 | DB Room v34 | tag `v1.4.0` 仍在 `62a4caf`（其后已有优化与修复提交）
> **核心原则：优化优先于扩展，确保现有功能稳定可用、体验更好**
> **验收原则：没有证据只能写未验收；文档勾选不算结果**

---

## 已完成 Phases

### Phase A：视觉体验 ✅
- A1 自定义背景 | A2 气泡样式 | A3 Material You 动态取色 | A4 动画增强

### Phase B：记忆系统 ✅
- B1 数据模型 | B2 记忆提取 | B3 记忆检索与注入 | B4 记忆管理 UI

### Phase C：SillyTavern 兼容 ✅
- C1 角色卡 PNG 导入导出 | C2 chara_card_v3 spec | C3 WI 高级逻辑 | C4 正则脚本

### Phase D：质量保障 ✅
- D1 单元测试 | D2 UI 测试依赖 | D3 ProGuard/R8 + CI

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

### 架构整改 A0–A7（摘要）
- A0 护栏 | A1 迁移 | A2 生成拆分（实质 done）| A3 reasoning | A4 Prompt trace | A5 世界书 Matcher
- A6：**QR automation 已接线**；世界书 `automation_id` **仅字段预留，不接线命中触发**（2026-07-19 决策）
- A7 配置档案

---

## 进行中 / 收口中（v1.4.x）

### Phase V：Visual Novel 模式 — 核心完成，体验继续打磨
- [x] V1 立绘系统（SpriteEntity + SpriteRepository）
- [x] V2 表情映射（EmotionDetector 关键词匹配）
- [x] V3 VN 游戏界面补全（输入框 + 发送链路）
- [x] V5 BGM 系统补全（BgmPlayer + AudioFocus + 情感映射）
- [x] V6 VN 设置优化（入口与基础体验优化）
- [x] V7 DB Migration v26/v27
- [ ] V4 转场动画优化（低优先级打磨）

### A8 质量门禁与设备 smoke — partial
- [x] 本地门禁流程 + CI workflow 定义（assemble / test / lint / detekt / kover）
- [x] 历史本地全绿记录（含 07-16：1197 tests）
- [x] 旧库启动 / 迁移 / 首页 / 核心流式对话 smoke（历史证据）
- [ ] 剩余主交互 smoke：停止 / 继续 / 重生 / VN·BGM / profile / 预设预览
- [ ] X4 编辑器面板 + X5 脚本包导入导出真机手测
- [ ] push/PR 后真实 CI run 证据
- [ ] 本轮全量门禁复跑（随当前 HEAD）

### 稳健性 / 数据卫生（优先于新功能）
- [x] 世界书导入解析失败回滚孤儿书（`73436cc`，相关 17 tests 绿）
- [ ] 世界书导入真机畸形 JSON 手测
- [ ] 文档基线与 tag/version 漂移评估（DB 34 vs tag@62a4caf）

---

## 当前计划：优化优先 (v1.4.x 收口)

> 详见 DEVELOPMENT-PLAN.md 与 DEV-LOG.md。原则：扩展永远后置。

### 已完成基线（不再重开）
- Phase 1–6 稳定性/拆分/VN/UX/测试/性能 ✅
- Quick Replies / STscript Lite 核心接入 ✅
- Phase X1–X4 主体 + 参数级补全代码 ✅
- X5 本地脚本包分享（codec + UI + 输入加固）✅ 代码落地；真机未验收
- v1.4.0 后多轮健壮性与性能审计（至 DB v34）✅ 代码落地

---

## 远期计划（仅主链路稳定后）

### Phase W：图像生成增强
- W1 SD WebUI API | W2 ComfyUI API | W3 设置 | W4 /draw 命令 | W5 画廊

### Phase X：STscript — 代码进度纠正
- [x] X1 解析器 | [x] X2 内置命令（`/delay` `/cancel` `/clearvar` `/if`）
- [x] X3 宏系统（`/macro` `/call`，深度上限 16，权限边界）
- [x] X4 编辑 UI（命令面板、参考弹窗、高亮、行号诊断、变量辅助、未知命令预警）
- [x] X4 参数级补全（`/if` 操作符、`/delay` 单位 chip；`appendStScriptParam`）— **代码已落地，真机未验收**
- [x] X5 本地脚本包分享（JSON codec + 导入导出 UI + 权限重置 + 输入上限）— **非在线市场**
- [ ] X5 真机手测 / 分享体验打磨
- [ ] X5 在线市场（需另定分发方案；当前无服务端）— 远期

### Phase Y：扩展框架
- Y1 ExtensionApi | Y2 Hook 系统 | Y3 内置扩展重构 | Y4-Y6 存储/UI/manifest

### Phase Z：发布准备
- Z1 Release 签名(✅) | Z2 App Icon | Z3 截图 | Z4 Store Listing | Z5-Z7 版本/R8/验证

---

## 里程碑

| 版本 | 内容 | 状态 |
|------|------|------|
| v1.2.9 | Phase 1 + 2（稳定性 + 架构优化） | ✅ |
| v1.3.0 | Phase 3 + 4（VN 补全 + UX 润色） | ✅ |
| v1.3.1 | Phase 5 + 6 + Quick Replies + 收口加固 | ✅ |
| v1.4.0 | Phase X1–X4 主体，tag @ `62a4caf` | ✅ |
| v1.4.0+ / v1.4.x | 参数补全 + X5 本地分享 + 多轮优化 + 世界书导入回滚；A8/A6 决策收口 | 🔄 进行中 |
| 更远 | W / Y / Z / X5 在线市场 | 远期 |

---

*每个可提交单元完成后：相关单测 + 按风险决定是否跑全量门禁；设备 smoke 单独记证据。*
