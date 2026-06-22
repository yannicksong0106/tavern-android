# ============================================================
# Git 交互式学习脚本（中文版）
# 你只需要按回车键执行每一步
# 我会解释每个命令在做什么
# ============================================================

$Host.UI.RawUI.WindowTitle = "Git 交互式学习"

Write-Host ""
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "        Git 交互式学习脚本（中文版）" -ForegroundColor Cyan
Write-Host "        你只需要按回车键！" -ForegroundColor Cyan
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host ""

# ============================================================
# 第一步：解释我们要做什么
# ============================================================

Write-Host "我们要做的事情：" -ForegroundColor Yellow
Write-Host ""
Write-Host "  1. 备份当前代码（创建一个安全副本）" -ForegroundColor Gray
Write-Host "  2. 创建一个新的分支用于 A4 开发" -ForegroundColor Gray
Write-Host "  3. 开始开发 A4 Prompt 可解释化" -ForegroundColor Gray
Write-Host "  4. 每完成一个任务就提交一次" -ForegroundColor Gray
Write-Host "  5. 完成后合并回主分支" -ForegroundColor Gray
Write-Host ""

# ============================================================
# 第二步：解释 Git 概念
# ============================================================

Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "        Git 基础知识（帮你学习）" -ForegroundColor Cyan
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "什么是 Git 分支？" -ForegroundColor Yellow
Write-Host "  - 就像创建一个文档的副本" -ForegroundColor Gray
Write-Host "  - 你可以在副本上随便修改" -ForegroundColor Gray
Write-Host "  - 如果出问题，可以随时回到原来的版本" -ForegroundColor Gray
Write-Host ""

Write-Host "什么是 Git 提交？" -ForegroundColor Yellow
Write-Host "  - 就像给当前代码拍一张照片" -ForegroundColor Gray
Write-Host "  - 记录下你做了什么修改" -ForegroundColor Gray
Write-Host "  - 以后可以随时回到这个状态" -ForegroundColor Gray
Write-Host ""

Write-Host "什么是 Git 合并？" -ForegroundColor Yellow
Write-Host "  - 就像把两个文档合并成一个" -ForegroundColor Gray
Write-Host "  - 把你在分支上做的修改放回主代码" -ForegroundColor Gray
Write-Host ""

# ============================================================
# 第三步：备份当前代码
# ============================================================

Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "        第一步：备份当前代码" -ForegroundColor Cyan
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "这一步会创建一个代码的安全副本。" -ForegroundColor Gray
Write-Host "如果以后出问题，可以随时恢复到这里。" -ForegroundColor Gray
Write-Host ""

Write-Host "命令解释：" -ForegroundColor Yellow
Write-Host "  git checkout -b backup/backend-completion-2026-06-22" -ForegroundColor White
Write-Host "    - 'checkout' = 切换到另一个分支" -ForegroundColor Gray
Write-Host "    - '-b' = 创建一个新分支" -ForegroundColor Gray
Write-Host "    - 'backup/backend-completion-2026-06-22' = 分支名称" -ForegroundColor Gray
Write-Host ""

Write-Host "按回车键执行这个命令..." -ForegroundColor Green
Read-Host

Set-Location D:\tavern-android
git checkout -b backup/backend-completion-2026-06-22

Write-Host ""
Write-Host "命令解释：" -ForegroundColor Yellow
Write-Host "  git add -A" -ForegroundColor White
Write-Host "    - 'add' = 准备文件以便保存" -ForegroundColor Gray
Write-Host "    - '-A' = 所有文件" -ForegroundColor Gray
Write-Host ""

Write-Host "按回车键执行这个命令..." -ForegroundColor Green
Read-Host

git add -A

Write-Host ""
Write-Host "命令解释：" -ForegroundColor Yellow
Write-Host '  git commit -m "backup: 代码备份"' -ForegroundColor White
Write-Host "    - 'commit' = 保存一个快照" -ForegroundColor Gray
Write-Host "    - '-m' = 消息（描述你做了什么）" -ForegroundColor Gray
Write-Host ""

Write-Host "按回车键执行这个命令..." -ForegroundColor Green
Read-Host

git commit -m "backup: 代码备份

- 当前版本：v1.3.1 开发中
- 已完成：A0-A3, A5, A8, CE1-CE7
- 待完成：A4, A6

Related to 后端架构补全方案"

Write-Host ""
Write-Host "命令解释：" -ForegroundColor Yellow
Write-Host "  git checkout main" -ForegroundColor White
Write-Host "    - 切换回主分支" -ForegroundColor Gray
Write-Host ""

Write-Host "按回车键执行这个命令..." -ForegroundColor Green
Read-Host

git checkout main

Write-Host ""
Write-Host "第一步完成！你的代码现在已经备份了。" -ForegroundColor Green
Write-Host ""

# ============================================================
# 第四步：创建 A4 feature 分支
# ============================================================

Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "        第二步：创建 A4 开发分支" -ForegroundColor Cyan
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "这一步会创建一个专门用于 A4 开发的分支。" -ForegroundColor Gray
Write-Host "你可以在分支上工作，不影响主代码。" -ForegroundColor Gray
Write-Host ""

Write-Host "命令解释：" -ForegroundColor Yellow
Write-Host "  git checkout -b feature/A4-prompt-explainability" -ForegroundColor White
Write-Host "    - 创建一个新分支用于 A4 开发" -ForegroundColor Gray
Write-Host ""

Write-Host "按回车键执行这个命令..." -ForegroundColor Green
Read-Host

git checkout -b feature/A4-prompt-explainability

Write-Host ""
Write-Host "第二步完成！你现在在 A4 开发分支上。" -ForegroundColor Green
Write-Host ""

# ============================================================
# 第五步：展示接下来要做什么
# ============================================================

Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "        第三步：开始开发" -ForegroundColor Cyan
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "现在你可以开始开发 A4 Prompt 可解释化了。" -ForegroundColor Gray
Write-Host ""

Write-Host "每完成一个任务，提交你的修改：" -ForegroundColor Yellow
Write-Host ""
Write-Host "示例：" -ForegroundColor Gray
Write-Host "  1. 创建或编辑一个文件" -ForegroundColor Gray
Write-Host "  2. 运行：git add <文件名>" -ForegroundColor Gray
Write-Host "  3. 运行：git commit -m '描述你做了什么'" -ForegroundColor Gray
Write-Host ""

Write-Host "A4 第一天任务：" -ForegroundColor Yellow
Write-Host ""
Write-Host "任务 1：创建 PromptSection.kt" -ForegroundColor Gray
Write-Host "  git add app/src/main/java/com/tavern/lite/network/PromptSection.kt" -ForegroundColor White
Write-Host '  git commit -m "feat(A4): 新增 PromptSection 数据类"' -ForegroundColor White
Write-Host ""

Write-Host "任务 2：修改 PromptSectionBuilder.kt" -ForegroundColor Gray
Write-Host "  git add app/src/main/java/com/tavern/lite/network/PromptSectionBuilder.kt" -ForegroundColor White
Write-Host '  git commit -m "feat(A4): 修改 PromptSectionBuilder 返回 List<PromptSection>"' -ForegroundColor White
Write-Host ""

Write-Host "任务 3：修改 PromptBuilder.kt" -ForegroundColor Gray
Write-Host "  git add app/src/main/java/com/tavern/lite/network/PromptBuilder.kt" -ForegroundColor White
Write-Host '  git commit -m "feat(A4): 修改 PromptBuilder 使用新的 section 接口"' -ForegroundColor White
Write-Host ""

# ============================================================
# 第六步：显示当前状态
# ============================================================

Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "        当前状态" -ForegroundColor Cyan
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "当前分支：" -ForegroundColor Yellow
git branch

Write-Host ""
Write-Host "最近提交：" -ForegroundColor Yellow
git log --oneline -5

Write-Host ""
Write-Host "===========================================================" -ForegroundColor Green
Write-Host "        设置完成！" -ForegroundColor Green
Write-Host "===========================================================" -ForegroundColor Green
Write-Host ""
Write-Host "你现在可以开始开发 A4 了！" -ForegroundColor Gray
Write-Host ""
Write-Host "下一步：" -ForegroundColor Yellow
Write-Host "  1. 打开你的代码编辑器（VS Code、IntelliJ 等）" -ForegroundColor Gray
Write-Host "  2. 阅读 QUICK-REFERENCE.md 了解任务清单" -ForegroundColor Gray
Write-Host "  3. 从任务 1 开始：创建 PromptSection.kt" -ForegroundColor Gray
Write-Host "  4. 每完成一个任务，提交你的修改" -ForegroundColor Gray
Write-Host ""
Write-Host "如果有问题，随时问我！" -ForegroundColor Cyan
Write-Host ""
