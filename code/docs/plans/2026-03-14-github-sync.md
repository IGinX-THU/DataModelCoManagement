# GitHub Repo Sync Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将本地项目与远端 GitHub 仓库建立同步，并按用户意图替换远端内容。

**Architecture:** 以 Git 作为版本控制核心。先确认替换范围（仓库根目录或 `code/` 子目录），再按对应流程初始化、提交并推送。

**Tech Stack:** Git, GitHub, PowerShell

---

### Task 1: 明确替换范围与远端信息

**Files:**
- Modify: 无

**Step 1: 确认当前目录是否为 Git 仓库**

```bash
git rev-parse --is-inside-work-tree
```

Expected: `fatal: not a git repository`

**Step 2: 确认远端仓库默认分支**

```bash
git ls-remote --symref https://github.com/IGinX-THU/DataModelCoManagement.git HEAD
```

Expected: 输出 `ref: refs/heads/main HEAD` 或 `ref: refs/heads/master HEAD`

**Step 3: 明确替换范围**

- 覆盖仓库根目录：本地项目作为仓库根直接推送
- 仅覆盖 `code/` 子目录：需要在克隆仓库后替换 `code/` 目录内容

---

### Task 2A: 覆盖仓库根目录（路径 A）

**Files:**
- Create: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code/.git`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code/.gitignore`（如需）

**Step 1: 初始化 Git 仓库**

```bash
git init
```

Expected: 初始化成功提示

**Step 2: 配置默认分支为 main（如需要）**

```bash
git branch -M main
```

Expected: 无输出

**Step 3: 设置远端仓库**

```bash
git remote add origin https://github.com/IGinX-THU/DataModelCoManagement.git
```

Expected: 无输出

**Step 4: 添加并提交当前项目**

```bash
git add -A
```

Expected: 无输出

```bash
git commit -m "chore: init project"
```

Expected: 生成首个提交

**Step 5: 覆盖推送到远端**

```bash
git push -u origin main --force
```

Expected: 远端 main 被覆盖为本地内容

---

### Task 2B: 仅覆盖 `code/` 子目录（路径 B）

**Files:**
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code`（作为源内容）
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code-sync/.git`（新克隆目录）
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code-sync/code`（替换内容）

**Step 1: 在新目录克隆远端仓库**

```bash
git clone https://github.com/IGinX-THU/DataModelCoManagement.git "E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code-sync"
```

Expected: 克隆完成

**Step 2: 用本地项目替换远端的 `code/` 目录内容**

```bash
Remove-Item -Recurse -Force "E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code-sync/code"
Copy-Item -Recurse -Force "E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code" "E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code-sync/code"
```

Expected: `code-sync/code` 内容与本地项目一致

**Step 3: 提交并推送**

```bash
cd "E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code-sync"
git add -A
git commit -m "chore: sync code directory"
git push
```

Expected: 仅 `code/` 目录被更新

---

### Task 3: 验证远端结果

**Files:**
- Modify: 无

**Step 1: 查看远端最新提交**

```bash
git ls-remote https://github.com/IGinX-THU/DataModelCoManagement.git HEAD
```

Expected: 输出最新提交哈希
