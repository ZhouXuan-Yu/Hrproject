# 开发者指南

## 一、环境要求

| 工具 | 版本 | 说明 |
|------|------|------|
| JDK | 17+ | 推荐 Temurin |
| Maven | 3.9+ | 构建 |
| Docker | 20+ | 容器化开发/部署 |
| Python | 3.12+ | AI 服务 |
| MySQL | 8.0 | 数据库（可复用现有 RDS） |
| Redis | 7.x | 缓存 |

## 二、常用命令

### Java 后端

```bash
# 编译
mvn compile

# 打包（跳过测试）
mvn package -DskipTests

# 运行测试
mvn test

# 本地启动
java -jar hr-bootstrap/target/hr-backend.jar

# 指定环境
SPRING_PROFILES_ACTIVE=dev java -jar hr-bootstrap/target/hr-backend.jar
```

### Python AI 服务

```bash
cd hr-ai-service

# 安装依赖
pip install -r requirements.txt

# 开发模式（热重载）
uvicorn app.main:app --reload --port 8100

# 生产模式
uvicorn app.main:app --host 0.0.0.0 --port 8100 --workers 2
```

### Docker

```bash
# 一键启动全部服务
docker compose up --build

# 只启动 Redis
docker run -d -p 6379:6379 redis:7-alpine
```

## 三、新增接口流程

1. **建实体**：在对应模块 `entity/` 下创建 JPA 实体，映射 MySQL 表
2. **建 Repository**：`repository/` 下继承 `JpaRepository`
3. **建 Service**：`service/` 下写业务逻辑，加 `@Transactional`
4. **建 Controller**：`controller/` 下写 REST 接口，返回 `ApiResponse.success(...)`
5. **加权限**：Controller 方法上加 `@RequireRole({"admin","hr"})`
6. **加缓存**：GET 接口加 `@Cacheable(cacheNames="xxx:list", key="#xxx")`
7. **测试**：运行 `mvn test` + 前端 Playwright

## 四、代码规范

- 包名：`com.hr.{module}.{layer}`
- 响应统一用 `ApiResponse.success(data)` / `ApiResponse.error(code, msg)`
- 业务异常抛 `BusinessException`
- 分页返回 `PageResult`
- 禁止在 Controller 里写业务逻辑
- 禁止在 Service 里直接 new Entity 返回给前端（应转 DTO）

## 五、数据库连接

开发默认连接本地 MySQL `hr_recruit`，生产通过环境变量切换：

```bash
DATABASE_URL=jdbc:mysql://<host>:<port>/<db>?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
DATABASE_USER=<user>
DATABASE_PASSWORD=<password>
```

**注意**：数据库表结构已存在，不要修改任何表。新增字段通过 Flyway `V{n}__xxx.sql` 管理。

## 六、常见问题

| 问题 | 解决 |
|------|------|
| Redis 连接失败 | 先 `docker run -d -p 6379:6379 redis:7-alpine` |
| 登录报错「用户名或密码错误」 | 检查 PASSWORD_SALT 与旧库一致 |
| Flyway 校验失败 | 数据库有未记录的结构变更，执行 `flyway repair` 或 baseline |
| 端口 8080 被占用 | 修改 `application.yml` server.port |
