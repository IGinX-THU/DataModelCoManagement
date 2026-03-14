# 本地项目接入远端仓库 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将当前本地目录接入远端仓库 main 分支，后续可在本地直接 git pull/push。

**Architecture:** 以父目录作为仓库根，`code/` 作为项目子目录；先提交本地 `code/`，再合并远端历史建立追踪。必要时使用根目录 `.gitignore` 仅跟踪 `code/`，避免误收集其他文件。

**Tech Stack:** Git, PowerShell

---

### Task 1: 检查父目录结构与忽略策略

**Files:**
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/.gitignore`（如需）

**Step 1: 查看父目录内容**

```bash
Get-ChildItem -Force "E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现"
```

Expected: 仅包含 `code/` 或少量其他文件/目录

**Step 2: 如父目录有非项目内容，创建根目录 .gitignore 仅跟踪 code/**

```text
*
!code/
!code/**
!.gitignore
```

---

### Task 2: 准备 code/.gitignore 以忽略生成物

**Files:**
- Create/Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code/.gitignore`

**Step 1: 写入忽略规则**

```text
# IDE/编辑器
.idea/
.vscode/
*.iml

# Node 生成物
**/node_modules/
**/dist/
**/.vite/
**/.cache/

# Java/构建产物
**/target/
**/*.class
**/out/

# Python 生成物
**/__pycache__/
**/*.py[cod]
**/.pytest_cache/

# 日志与临时
**/*.log
**/*.log.gz
**/logs/
**/tmp/
**/.tmp_iginx/
**/gc.log

# 运行期数据/导出
**/storage/data/

# 文档中间产物
.docx_text/

# 本工具生成的计划
docs/plans/
```

---

### Task 3: 初始化父目录为 Git 仓库并绑定远端

**Files:**
- Create: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/.git`

**Step 1: 初始化仓库**

```bash
git -C "E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现" init
```

Expected: 初始化成功提示

**Step 2: 绑定远端**

```bash
git -C "E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现" remote add origin https://github.com/IGinX-THU/DataModelCoManagement.git
```

Expected: 无输出

**Step 3: 设置默认分支**

```bash
git -C "E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现" branch -M main
```

Expected: 无输出

---

### Task 4: 提交本地 code/ 内容

**Files:**
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code/*`
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/.gitignore`（如有）

**Step 1: 添加文件**

```bash
git -C "E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现" add "code" ".gitignore"
```

Expected: 无输出

**Step 2: 提交**

```bash
git -C "E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现" commit -m "chore: 初始化本地 code 目录"
```

Expected: 生成首个提交

---

### Task 5: 合并远端历史并建立追踪（推荐方案）

**Files:**
- Modify: `E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/.git`

**Step 1: 拉取远端**

```bash
git -C "E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现" fetch origin main
```

Expected: 获取远端 main

**Step 2: 合并远端历史**

```bash
git -C "E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现" merge --allow-unrelated-histories origin/main
```

Expected: 合并完成（若有冲突需手动解决）

**Step 3: 设置追踪**

```bash
git -C "E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现" branch -u origin/main
```

Expected: main 追踪 origin/main

---

### Task 6: 验证

**Files:**
- Modify: 无

**Step 1: 查看状态**

```bash
git -C "E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现" status -sb
```

Expected: 工作区干净，或仅显示 ahead/behind 信息
