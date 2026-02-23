# 修复说明（IGinX 与数据源联通性）

## 🎯 目标
- 解决新增 IoTDB 数据源“测试连接成功但保存失败（IGinX 服务不可用）”的问题
- 保证 IGinX 容器能正确访问 IoTDB 容器或宿主机端口

## ✅ 已修复内容
1. **IGinX 存储引擎 host 自动纠正**
   - 文件：`iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/framework/iginx/IginxConfig.java`
   - 行为：当用户在 UI 填写 `127.0.0.1`/`localhost` 时，注册到 IGinX 的 host 自动替换为 `host.docker.internal`
   - 目的：避免 IGinX 容器内部无法访问自身 `127.0.0.1` 的问题

2. **默认 override 值补齐**
   - 直接在配置类中设置 `storageHostOverride=host.docker.internal`
   - 即使未设置 `application.yml`，也能生效

3. **重复注册存储引擎可自动容错**
   - 文件：`iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/framework/iginx/IginxStorageWrapper.java`
   - 当 IGinX 返回 `repeatedly add storage engine` 时，前端不再报错，直接视为已存在并继续保存数据源

4. **新增数据源前检查已存在的存储引擎**
   - 文件：`iginx-assoc-backend/src/main/java/com/xmu/iginx/assoc/modules/data/service/impl/DataSourceServiceImpl.java`
   - 通过 `session.getClusterInfo()` 判断是否已注册同一 host/port/prefix，避免重复注册失败

## 🔍 验证建议（按顺序）
1. **重启后端**
   ```bash
   mvn -f "E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code/iginx-assoc-backend/pom.xml" spring-boot:run
   ```
2. **新增 IoTDB 数据源（前端）**
   - 主机：`127.0.0.1`
   - 端口：`6667`
   - 数据库：`root`
   - 挂载路径：`root.demo`
3. **观察 IGinX 容器日志**
   ```bash
   docker logs iginx1 --tail 60
   ```
   - 正常情况下应出现 `ip='host.docker.internal'` 或无连接拒绝错误

## 📌 结果说明
如果 IGinX 日志中不再出现 `Connection refused`，且前端保存成功，即说明修复生效。

## ✅ 本次验证结果
- 已通过后端接口成功新增数据源（`root.demo`），返回 `code=200`
- 未再出现“IGinX 服务不可用”错误

如仍失败，请提供：
- 前端保存请求的报错详情
- `docker logs iginx1 --tail 120` 输出

## ⚠️ 注意
- 后端需使用 **JDK 17** 启动；若出现 “无效的标志: --release”，请将 `JAVA_HOME` 指向 `C:/Program Files/Java/jdk17`
