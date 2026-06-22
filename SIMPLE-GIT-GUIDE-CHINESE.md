# 简单 Git 操作指南（中文版）

你只需要按顺序复制粘贴这些命令到 PowerShell 中执行即可。

---

## 第一步：备份当前代码

打开 PowerShell，复制粘贴以下命令：

```
cd D:\tavern-android
```

然后按回车。

接着复制粘贴：

```
git checkout -b backup/backend-completion-2026-06-22
```

然后按回车。

接着复制粘贴：

```
git add -A
```

然后按回车。

接着复制粘贴：

```
git commit -m "backup: 代码备份"
```

然后按回车。

接着复制粘贴：

```
git checkout main
```

然后按回车。

**第一步完成！你的代码现在已经备份了。**

---

## 第二步：创建 A4 开发分支

继续在 PowerShell 中复制粘贴以下命令：

```
git checkout -b feature/A4-prompt-explainability
```

然后按回车。

**第二步完成！你现在在 A4 开发分支上。**

---

## 第三步：开始开发

现在你可以开始开发了！

每完成一个任务，复制粘贴以下命令：

**任务 1 完成后：**
```
git add app/src/main/java/com/tavern/lite/network/PromptSection.kt
git commit -m "feat(A4): 新增 PromptSection 数据类"
```

**任务 2 完成后：**
```
git add app/src/main/java/com/tavern/lite/network/PromptSectionBuilder.kt
git commit -m "feat(A4): 修改 PromptSectionBuilder 返回 List<PromptSection>"
```

**任务 3 完成后：**
```
git add app/src/main/java/com/tavern/lite/network/PromptBuilder.kt
git commit -m "feat(A4): 修改 PromptBuilder 使用新的 section 接口"
```

---

## 查看当前状态

随时可以查看当前状态：

```
git status
```

查看提交历史：

```
git log --oneline -5
```

---

## 重要提示

1. **每完成一个任务就提交一次**：这样如果出问题，可以回到之前的状态
2. **提交信息要有意义**：说明你做了什么修改
3. **不要害怕出错**：Git 可以随时回到之前的状态

---

## 需要帮助？

如果有问题，随时问我！
