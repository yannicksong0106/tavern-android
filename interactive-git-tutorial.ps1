# ============================================================
# Interactive Git Tutorial Script
# You just need to press Enter to execute each step
# I will explain what each command does
# ============================================================

$Host.UI.RawUI.WindowTitle = "Interactive Git Tutorial"

Write-Host ""
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "        Interactive Git Tutorial" -ForegroundColor Cyan
Write-Host "        You just need to press Enter!" -ForegroundColor Cyan
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host ""

# ============================================================
# Step 1: Explain what we're going to do
# ============================================================

Write-Host "What we're going to do:" -ForegroundColor Yellow
Write-Host ""
Write-Host "  1. Backup current code (create a safety copy)" -ForegroundColor Gray
Write-Host "  2. Create a new branch for A4 development" -ForegroundColor Gray
Write-Host "  3. Start developing A4 Prompt Explainability" -ForegroundColor Gray
Write-Host "  4. Commit changes after each task" -ForegroundColor Gray
Write-Host "  5. Merge back to main when done" -ForegroundColor Gray
Write-Host ""

# ============================================================
# Step 2: Explain Git concepts
# ============================================================

Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "        Git Concepts (for your learning)" -ForegroundColor Cyan
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "Git Branch:" -ForegroundColor Yellow
Write-Host "  - A copy of your code that you can work on separately" -ForegroundColor Gray
Write-Host "  - Like creating a new document to make changes" -ForegroundColor Gray
Write-Host "  - If something goes wrong, you can always go back" -ForegroundColor Gray
Write-Host ""

Write-Host "Git Commit:" -ForegroundColor Yellow
Write-Host "  - Saving a snapshot of your current code" -ForegroundColor Gray
Write-Host "  - Like taking a photo of your work" -ForegroundColor Gray
Write-Host "  - You can always go back to this snapshot" -ForegroundColor Gray
Write-Host ""

Write-Host "Git Merge:" -ForegroundColor Yellow
Write-Host "  - Combining changes from different branches" -ForegroundColor Gray
Write-Host "  - Like merging two documents together" -ForegroundColor Gray
Write-Host "  - This puts your changes back into the main code" -ForegroundColor Gray
Write-Host ""

# ============================================================
# Step 3: Backup current code
# ============================================================

Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "        Step 1: Backup Current Code" -ForegroundColor Cyan
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "This creates a safety copy of your current code." -ForegroundColor Gray
Write-Host "If something goes wrong, you can always restore from here." -ForegroundColor Gray
Write-Host ""

Write-Host "Command explanation:" -ForegroundColor Yellow
Write-Host "  git checkout -b backup/backend-completion-2026-06-22" -ForegroundColor White
Write-Host "    - 'checkout' = switch to a different branch" -ForegroundColor Gray
Write-Host "    - '-b' = create a new branch" -ForegroundColor Gray
Write-Host "    - 'backup/backend-completion-2026-06-22' = branch name" -ForegroundColor Gray
Write-Host ""

Write-Host "Press Enter to execute this command..." -ForegroundColor Green
Read-Host

Set-Location D:\tavern-android
git checkout -b backup/backend-completion-2026-06-22

Write-Host ""
Write-Host "Command explanation:" -ForegroundColor Yellow
Write-Host "  git add -A" -ForegroundColor White
Write-Host "    - 'add' = prepare files to be saved" -ForegroundColor Gray
Write-Host "    - '-A' = all files" -ForegroundColor Gray
Write-Host ""

Write-Host "Press Enter to execute this command..." -ForegroundColor Green
Read-Host

git add -A

Write-Host ""
Write-Host "Command explanation:" -ForegroundColor Yellow
Write-Host '  git commit -m "backup: Pre-backend-completion code backup"' -ForegroundColor White
Write-Host "    - 'commit' = save a snapshot" -ForegroundColor Gray
Write-Host "    - '-m' = message (description of what you did)" -ForegroundColor Gray
Write-Host ""

Write-Host "Press Enter to execute this command..." -ForegroundColor Green
Read-Host

git commit -m "backup: Pre-backend-completion code backup

- Current version: v1.3.1 in development
- Completed: A0-A3, A5, A8, CE1-CE7
- Pending: A4, A6

Related to backend completion plan"

Write-Host ""
Write-Host "Command explanation:" -ForegroundColor Yellow
Write-Host "  git checkout main" -ForegroundColor White
Write-Host "    - Switch back to the main branch" -ForegroundColor Gray
Write-Host ""

Write-Host "Press Enter to execute this command..." -ForegroundColor Green
Read-Host

git checkout main

Write-Host ""
Write-Host "Step 1 completed! Your code is now backed up." -ForegroundColor Green
Write-Host ""

# ============================================================
# Step 4: Create A4 feature branch
# ============================================================

Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "        Step 2: Create A4 Feature Branch" -ForegroundColor Cyan
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "This creates a new branch specifically for A4 development." -ForegroundColor Gray
Write-Host "You can work on A4 without affecting the main code." -ForegroundColor Gray
Write-Host ""

Write-Host "Command explanation:" -ForegroundColor Yellow
Write-Host "  git checkout -b feature/A4-prompt-explainability" -ForegroundColor White
Write-Host "    - Create a new branch for A4 development" -ForegroundColor Gray
Write-Host ""

Write-Host "Press Enter to execute this command..." -ForegroundColor Green
Read-Host

git checkout -b feature/A4-prompt-explainability

Write-Host ""
Write-Host "Step 2 completed! You are now on the A4 feature branch." -ForegroundColor Green
Write-Host ""

# ============================================================
# Step 5: Show what to do next
# ============================================================

Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "        Step 3: Start Developing" -ForegroundColor Cyan
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "Now you can start developing A4 Prompt Explainability." -ForegroundColor Gray
Write-Host ""

Write-Host "After completing each task, commit your changes:" -ForegroundColor Yellow
Write-Host ""
Write-Host "Example:" -ForegroundColor Gray
Write-Host "  1. Create/edit a file" -ForegroundColor Gray
Write-Host "  2. Run: git add <filename>" -ForegroundColor Gray
Write-Host "  3. Run: git commit -m 'description of what you did'" -ForegroundColor Gray
Write-Host ""

Write-Host "A4 Day 1 Tasks:" -ForegroundColor Yellow
Write-Host ""
Write-Host "Task 1: Create PromptSection.kt" -ForegroundColor Gray
Write-Host "  git add app/src/main/java/com/tavern/lite/network/PromptSection.kt" -ForegroundColor White
Write-Host '  git commit -m "feat(A4): Add PromptSection data class"' -ForegroundColor White
Write-Host ""

Write-Host "Task 2: Update PromptSectionBuilder.kt" -ForegroundColor Gray
Write-Host "  git add app/src/main/java/com/tavern/lite/network/PromptSectionBuilder.kt" -ForegroundColor White
Write-Host '  git commit -m "feat(A4): Update PromptSectionBuilder to return List<PromptSection>"' -ForegroundColor White
Write-Host ""

Write-Host "Task 3: Update PromptBuilder.kt" -ForegroundColor Gray
Write-Host "  git add app/src/main/java/com/tavern/lite/network/PromptBuilder.kt" -ForegroundColor White
Write-Host '  git commit -m "feat(A4): Update PromptBuilder to use new section interface"' -ForegroundColor White
Write-Host ""

# ============================================================
# Step 6: Final status
# ============================================================

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
Write-Host "You are now ready to start developing A4!" -ForegroundColor Gray
Write-Host ""
Write-Host "What to do next:" -ForegroundColor Yellow
Write-Host "  1. Open your code editor (VS Code, IntelliJ, etc.)" -ForegroundColor Gray
Write-Host "  2. Read QUICK-REFERENCE.md for the task list" -ForegroundColor Gray
Write-Host "  3. Start with Task 1: Create PromptSection.kt" -ForegroundColor Gray
Write-Host "  4. After completing each task, commit your changes" -ForegroundColor Gray
Write-Host ""
Write-Host "If you have questions, just ask me!" -ForegroundColor Cyan
Write-Host ""
