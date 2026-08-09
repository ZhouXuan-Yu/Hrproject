# 智能招聘系统 · Java 后端重塑

> 目标：在**不修改现有 Flask `backend/` 目录**的前提下，新建本工程 `hr-backend-new/`。
> 业务代码迁至 Java Spring Boot，AI 能力保留为独立 Python FastAPI 服务，Redis 全面接入。

---

## 一、架构总览

```
[前端 Vue3] ── Vite Proxy (:7100) ──┐
                                    │
         ┌──────────────────────────┤
         │                          │
┌────────▼─────────┐        ┌───────▼────────┐
│  hr-business      │        │  hr-ai-service  │
│  Java Spring Boot │        │  Python FastAPI │
│  :8080            │        │  :8100          │
│                   │        │                │
│  /api/auth/*      │        │  /api/ai/*     │
│  /api/demand/*    │        │    ├ run/{wf}  │
│  /api/talent/*    │        │    └ stream/{wf}│
│  /api/interview/* │        └────────┬───────┘
│  /api/hire/*      │                 │
│  /api/dashboard/* │          DeepSeek API
│  /api/config/*    │
│  /api/health      │
└───┬─────────┬─────┘
    │         │
┌───▼───┐  ┌──▼────────┐
│ MySQL │  │   Redis   │
│ RDS   │  │  7.x      │
└───────┘  └───────────┘
```

## 二、模块结构

| 模块 | 说明 | 主要端点 |
|------|------|----------|
| `hr-common` | 公共层：统一响应体、异常、枚举、工具类、Redis | — |
| `hr-auth` | 认证授权：登录/JWT/RBAC/用户管理 | `/api/auth/*` |
| `hr-demand` | 需求管理：CRUD + 审批流 + 状态机 | `/api/demand/*` |
| `hr-talent` | 人才库：候选人 + 简历 + 内部员工 | `/api/talent/*` |
| `hr-interview` | 面试管理：时段 + 预约 + 评价 + 日历 | `/api/interview/*` |
| `hr-hire` | 录用：Offer + 入职 + 候选人确认 | `/api/hire/*` |
| `hr-dashboard` | 招聘看板：KPI + 漏斗 + 部门进度 | `/api/dashboard/*` |
| `hr-config` | 基础配置：渠道/邮箱/模板/评分规则/密钥 | `/api/config/*` |
| `hr-integration` | 外部集成：飞书/腾讯会议/邮件/IAM | — |
| `hr-bootstrap` | 启动模块：聚合所有模块 + 配置 | `/api/health` |
| `hr-ai-service` | AI 服务（Python FastAPI，独立部署） | `/api/ai/*` |

## 三、技术栈

- **Java 17 + Spring Boot 3.3**
- **Spring Security 6** + JJWT（JWT HS256/RS256）
- **Spring Data JPA** (Hibernate 6) + Flyway 迁移
- **Redis 7** + Spring Cache + Redisson（分布式锁）
- **MySQL 8.0**（复用现有 RDS，表结构不变）
- **Python 3.12 + FastAPI** + DeepSeek SDK

## 四、快速启动

### 方式一：Docker Compose 一键启动

```bash
docker compose up --build
```

服务：
- Java 后端: http://127.0.0.1:8080
- AI 服务: http://127.0.0.1:8100
- Redis: 127.0.0.1:6379

### 方式二：本地开发

```bash
# 1. 启动 Redis
docker run -d -p 6379:6379 redis:7-alpine

# 2. 启动 Python AI 服务
cd hr-ai-service
pip install -r requirements.txt
uvicorn app.main:app --port 8100

# 3. 编译并启动 Java（需 JDK17 + Maven）
mvn package -DskipTests
java -jar hr-bootstrap/target/hr-backend.jar
```

## 五、环境变量

| 变量 | 默认 | 说明 |
|------|------|------|
| `DATABASE_URL` | `jdbc:mysql://127.0.0.1:3306/hr_recruit` | JDBC 连接串 |
| `DATABASE_USER` | `root` | 数据库用户 |
| `DATABASE_PASSWORD` | — | 数据库密码 |
| `REDIS_HOST` | `127.0.0.1` | Redis 地址 |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `JWT_SECRET_KEY` | — | JWT 密钥 |
| `PASSWORD_SALT` | `default-salt-change-me` | 旧密码哈希 salt |
| `AI_SERVICE_URL` | `http://127.0.0.1:8100` | Python AI 服务地址 |
| `DEEPSEEK_API_KEY` | — | DeepSeek 密钥 |

## 六、文档

- [架构文档](docs/architecture.md)
- [API 契约](docs/api-contract.md)
- [数据库迁移指南](docs/db-migration-guide.md)
- [开发者指南](docs/dev-guide.md)
