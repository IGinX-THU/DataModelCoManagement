# Repository Guidelines

## 项目结构与模块组织
- iginx-assoc-backend/：Spring Boot 服务，业务源码在 src/main/java，配置与资源位于 src/main/resources/，数据库建表脚本集中在 src/main/resources/sql/。
- iginx-assoc-ui/：Vue 3 + Vite 前端，入口 src/main.js，页面放在 src/views/，通用组件位于 src/components/，状态管理在 src/stores/。
- IGinX/：外部引擎与 UDF 示例，改动需注明来源与同步计划。
- iginx-assoc-ui/dist/、*.log、*.err.log 为运行或构建产物，禁止直接提交。

## 构建、测试与开发命令
- mvn -f iginx-assoc-backend/pom.xml spring-boot:run：在 Java 17 环境下启动后端（默认 8080 端口）。
- mvn -f iginx-assoc-backend/pom.xml test：运行后端单测，务必保持 60s 内完成。
- cd iginx-assoc-ui && npm install：安装前端依赖。
- 
pm run dev / 
pm run build / 
pm run preview：依次用于本地调试、生成产物、离线验证构建结果。

## 编码风格与命名约定
- Java：包名全小写（如 com.xmu.iginx.assoc），类名 PascalCase，方法与字段 camelCase，缩进 4 空格，必要处添加中文注释说明业务意图。
- Vue/JS：组件文件使用 PascalCase（如 AboutModal.vue），视图以 *View.vue 结尾，脚本与样式统一 2 空格缩进。
- 禁止遗留 TODO、无效代码或英文注释；同类配置集中管理，避免硬编码重复。

## 测试指南
- 后端采用 Spring Boot Test（JUnit 5），测试类放于 iginx-assoc-backend/src/test/java，命名遵循 *Test。
- 优先覆盖服务、仓储与关键工具类，兼顾异常与边界路径；需要隔离配置时使用 @ActiveProfiles("test")。
- 暂无前端测试框架，如需补充请先更新 package.json 并在 PR 中解释策略。

## 提交与 PR 规范
- Commit 建议遵循 Conventional Commits，例如 eat: support tag mapping、ix: handle empty measurement。
- PR 描述需包含：变更摘要、影响模块、关联 Issue（若有），前端改动附关键截图或录屏；涉及 IGinX 或数据库结构的修改需说明同步方案与回滚步骤。

## 安全与配置提示
- pplication.yml 含默认 IGinX 账号与地址，严禁在日志、截图或示例中泄露，建议通过环境变量覆盖敏感信息。
- 任何数据库结构或初始化数据调整都必须同步更新 schema.sql，并在 PR 中标注依赖的迁移脚本与验证方式。
