# IGinX DMA System (Frontend Demo)
# 数据模型协同管理系统 - 前端演示

本项目是 **Data Model Co-Management System (IGinX DMA System)** 的前端演示原型。该系统旨在提供一个现代化的界面，用于管理数据模型、可视化关联关系、编辑数据以及监控系统任务。

这是一个基于 **Vue 3** 和 **Vite** 构建的单页应用 (SPA)，展示了系统的核心功能交互和 UI 设计。

## 🚀 功能特性 (Features)

本 Demo 展示了以下核心功能模块：

- **📊 仪表盘 (Dashboard)**
  - 系统状态概览
  - 关键指标 (KPI) 展示 (模型总数、时序点数、任务成功率等)
  - 任务调度趋势图表
  - 最新执行记录监控

- **🗂️ 模型资产管理 (Model Assets)**
  - 数据模型资产的浏览与管理
  - 支持查看模型详情和属性

- **📝 数据编辑器 (Data Editor)**
  - 提供类似 Excel 的数据编辑体验
  - 支持直接对数据进行增删改查操作

- **🔗 关联管理 (Association)**
  - 可视化展示数据模型之间的关联关系
  - 支持定义和管理模型间的映射规则

- **📈 数据分析 (Analysis)**
  - 提供数据分析工具和视图
  - 帮助用户从数据中获取洞察

- **⚡ 任务监控 (Task Monitor)**
  - 实时跟踪后台任务的执行状态
  - 查看任务日志和执行结果

- **⚙️ 系统设置 (Settings)**
  - 系统参数配置和个性化设置

## 🛠️ 技术栈 (Tech Stack)

本项目采用现代前端技术栈构建：

- **核心框架**: [Vue 3](https://vuejs.org/) (Composition API)
- **构建工具**: [Vite](https://vitejs.dev/)
- **状态管理**: [Pinia](https://pinia.vuejs.org/)
- **路由管理**: [Vue Router](https://router.vuejs.org/)
- **样式框架**: [Tailwind CSS](https://tailwindcss.com/)
- **图表库**: [ECharts](https://echarts.apache.org/)
- **图标库**: [RemixIcon](https://remixicon.com/), [Heroicons](https://heroicons.com/)
- **本地存储**: [LocalForage](https://github.com/localForage/localForage)

## 📦 安装与使用 (Usage)

### 前置要求

- Node.js (建议使用 LTS 版本)
- npm 或 yarn 包管理器

### 1. 克隆项目

```bash
git clone <repository-url>
cd DataModelCoManagement/frontend
```

### 2. 安装依赖

```bash
npm install
# 或者
yarn install
```

### 3. 启动开发服务器

```bash
npm run dev
# 或者
yarn dev
```

启动后，访问浏览器 `http://localhost:5173` 即可查看演示效果。

### 4. 构建生产版本

```bash
npm run build
# 或者
yarn build
```

构建产物将输出到 `dist` 目录。

## 📂 目录结构

```
frontend/
├── src/
│   ├── components/      # 公共组件 (弹窗、侧边栏、工具栏等)
│   ├── layouts/         # 页面布局组件 (AppLayout)
│   ├── router/          # 路由配置
│   ├── stores/          # Pinia 状态管理
│   ├── views/           # 页面视图 (Dashboard, ModelAssets 等)
│   ├── App.vue          # 根组件
│   └── main.js          # 入口文件
├── public/              # 静态资源
└── index.html           # HTML 入口
```

## 📝 说明

本项目的当前版本为演示 Demo，部分数据 (如仪表盘图表数据) 为静态模拟数据 (Mock Data)，旨在展示系统设计理念和交互流程。实际后端对接逻辑需根据具体 API 进行配置。
