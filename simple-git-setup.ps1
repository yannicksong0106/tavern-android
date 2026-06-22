# ============================================================
# Simple Git Setup Script (English version to avoid encoding issues)
# You just need to press Enter to execute each step
# ============================================================

$Host.UI.RawUI.WindowTitle = "Git Setup"

Write-Host ""
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "        Git Setup Script" -ForegroundColor Cyan
Write-Host "        Press Enter to execute each step" -ForegroundColor Cyan
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Backup
Write-Host "Step 1: Backup current code" -ForegroundColor Yellow
Write-Host ""
Write-Host "This creates a safety copy of your code." -ForegroundColor Gray
Write-Host ""

Write-Host "Press Enter to continue..." -ForegroundColor Green
Read-Host

Set-Location D:\tavern-android
git checkout -b backup/backend-completion-2026-06-22
git add -A
git commit -m "backup: Pre-backend-completion code backup

- Current version: v1.3.1 in development
- Completed: A0-A3, A5, A8, CE1-CE7
- Pending: A4, A6

Related to backend completion plan"

git checkout main

Write-Host ""
Write-Host "Backup completed!" -ForegroundColor Green
Write-Host ""

# Step 2: Create A4 branch
Write-Host "Step 2: Create A4 feature branch" -ForegroundColor Yellow
Write-Host ""
Write-Host "This creates a new branch for A4 development." -ForegroundColor Gray
Write-Host ""

Write-Host "Press Enter to continue..." -ForegroundColor Green
Read-Host

git checkout -b feature/A4-prompt-explainability

Write-Host ""
Write-Host "A4 branch created!" -ForegroundColor Green
Write-Host ""

# Step 3: Show instructions
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "        Next Steps" -ForegroundColor Cyan
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Now you can start developing A4!" -ForegroundColor Gray
Write-Host ""
Write-Host "After completing each task, run:" -ForegroundColor Yellow
Write-Host ""
Write-Host "Task 1: Create PromptSection.kt" -ForegroundColor Gray
Write-Host "  git add app/src/main/java/com/tavern/lite/network/PromptSection.kt" -ForegroundColor White
Write-Host '  git commit -m "feat(A4): Add PromptSection data class"' -ForegroundColor White
Write-Host ""
Write-Host "Task 2: Update PromptSectionBuilder.kt" -ForegroundColor Gray
Write-Host "  git add app/src/main/java/com/tavern/lite/network/PromptSectionBuilder.kt" -ForegroundColor White
Write-Host '  git commit -m "feat(A4): Update PromptSectionBuilder"' -ForegroundColor White
Write-Host ""
Write-Host "Task 3: Update PromptBuilder.kt" -ForegroundColor Gray
Write-Host "  git add app/src/main/java/com/tavern/lite/network/PromptBuilder.kt" -ForegroundColor White
Write-Host '  git commit -m "feat(A4): Update PromptBuilder"' -ForegroundColor White
Write-Host ""

# Show current status
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "        Current Status" -ForegroundColor Cyan
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Current branch:" -ForegroundColor Yellow
git branch

Write-Host ""
Write-Host "Recent commits:" -ForegroundColor Yellow
git log --oneline -5

Write-Host ""
Write-Host "===========================================================" -ForegroundColor Green
Write-Host "        Setup Complete!" -ForegroundColor Green
Write-Host "===========================================================" -ForegroundColor Green
Write-Host ""
Write-Host "You are now ready to develop A4!" -ForegroundColor Gray
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "  1. Open your code editor" -ForegroundColor Gray
Write-Host "  2. Read QUICK-REFERENCE.md for task list" -ForegroundColor Gray
Write-Host "  3. Start with Task 1: Create PromptSection.kt" -ForegroundColor Gray
Write-Host "  4. After each task, commit your changes" -ForegroundColor Gray
Write-Host ""
Write-Host "Good luck!" -ForegroundColor Cyan
Write-Host ""
