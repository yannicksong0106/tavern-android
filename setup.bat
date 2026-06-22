@echo off
chcp 65001 >nul
title Git Setup Script

echo ============================================================
echo        Git Setup Script
echo        Double-click this file to run
echo ============================================================
echo.

cd /d D:\tavern-android

echo Step 1: Backup current code...
echo.

echo Creating backup branch...
git checkout -b backup/backend-completion-2026-06-22

echo.
echo Adding all files...
git add -A

echo.
echo Committing changes...
git commit -m "backup: Pre-backend-completion code backup

- Current version: v1.3.1 in development
- Completed: A0-A3, A5, A8, CE1-CE7
- Pending: A4, A6

Related to backend completion plan"

echo.
echo Switching back to main...
git checkout main

echo.
echo Step 1 completed! Code backed up.
echo.

echo ============================================================
echo        Step 2: Create A4 feature branch
echo ============================================================
echo.

echo Creating A4 branch...
git checkout -b feature/A4-prompt-explainability

echo.
echo Step 2 completed! A4 branch created.
echo.

echo ============================================================
echo        Current Status
echo ============================================================
echo.

echo Current branch:
git branch

echo.
echo Recent commits:
git log --oneline -5

echo.
echo ============================================================
echo        Setup Complete!
echo ============================================================
echo.
echo You are now ready to develop A4!
echo.
echo Next steps:
echo   1. Open your code editor
echo   2. Read QUICK-REFERENCE.md for task list
echo   3. Start with Task 1: Create PromptSection.kt
echo   4. After each task, commit your changes
echo.
echo Press any key to exit...
pause >nul
