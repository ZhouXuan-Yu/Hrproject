# 架构文档

## 一、总体架构

智能招聘系统后端重塑为「Java 业务服务 + Python AI 服务」双服务架构，Redis 作为统一缓存层。

```
┌────────────────────────────────────────────────────────────┐
│                      前端 Vue3 SPA                         │
│                 (frontend/, Vite dev :7100)                │
└──────────────────────────────┬─────────────────────────────┘
                               │  /api/*
                    ┌──────────▼──────────┐
                    │   Vite Dev Proxy    │
                    └──────────┬──────────┘
                               │
              ┌────────────────┼─────────────────┐
              │                                  │
   ┌──────────▼──────────┐          ┌───────────▼───────────┐
   │   hr-business (Java) │          │  hr-ai-service (Py)   │
   │   Spring Boot :8080  │          │  FastAPI :8100        │
   │                      │          │                       │
   │  Auth / RBAC / JWT   │  HTTP    │  DeepSeek 客户端      │
   │  需求/人才/面试/Offer │◄────────►│  6 个工作流编排        │
   │  看板/配置/集成      │  Feign   │  本地引擎 fallback     │
   └──────────┬──────────┘  /Rest   └───────────┬───────────┘
              │                                 │
   ┌──────────▼──────────┐                      │
   │  MySQL RDS          │                      │
   │  (表结构不变)        │                      │
   └──────────┬──────────┘                      │
              │                                 │
   ┌──────────▼──────────┐           ┌──────────▼──────────┐
   │  Redis 7            │◄─────────►│  Redis (限流/缓存)   │
   │  缓存/锁/Session    │           └─────────────────────┘
   └─────────────────────┘
```

## 二、分层设计

### Java 模块分层（每个 hr-module）

```
Controller (REST 接口)
    ↓
Service (业务逻辑 + @Transactional 事务)
    ↓
Repository (Spring Data JPA)
    ↓
Entity (JPA 实体映射 MySQL 表)
```

### 关键设计决策

| 决策 | 说明 |
|------|------|
| **数据库零变更** | JPA 实体严格映射现有表，Flyway baseline 现有 schema |
| **API 契约兼容** | URL/方法/响应格式与 Flask 完全一致 |
| **AI 服务独立** | Python 独立部署，Java 通过 HTTP 调用，可独立扩容 |
| **Redis 全接入** | 接口缓存 + 业务对象缓存 + 分布式锁 + 登录失败锁定 |
| **JWT 双通道** | Bearer header + httpOnly cookie 兼容前端 |
| **RBAC 方法级** | @RequireRole 注解 + AOP 切面，@DataScope 数据权限 |

## 三、认证流程

```
POST /api/auth/login
  → 校验用户名密码（支持 werkzeug scrypt + legacy SHA-256）
  → 检查 Redis 登录失败锁定（15 分钟 5 次）
  → 签发 JWT（HS256，含 user_id/role/tenant_id）
  → Set-Cookie hr_token=httpOnly; SameSite=Lax
  → 返回 { token, user, menus }

请求进入
  → JwtAuthenticationFilter 提取 token（Bearer/cookie）
  → 解析校验 → 写入 SecurityContext（LoginUser）
  → 后续 Filter/Controller 通过 SecurityUtils.getCurrentUser() 获取
```

## 四、RBAC 权限矩阵

| 角色 | 编码 | 可见菜单 | 数据范围 |
|------|------|---------|---------|
| 管理员 | admin | 6 项全开 | 全量 |
| HR 专员 | hr | 看板/需求/人才/面试 | 全量 |
| 部门负责人 | dept_head | 看板/需求 | 本部门 |
| 总监 | director | 看板/需求 | 本部门 |
| 面试官 | interviewer | 看板/面试 | 仅自己的场次 |
| 临时面试官 | temp_interviewer | 看板/面试 | 仅分配的场次 |
| 基层员工 | employee | 看板/需求 | 仅自己提交 |
| 无权限 | no_recruit | 无 | 无 |

## 五、缓存策略

| 层级 | 技术 | TTL | 场景 |
|------|------|-----|------|
| L1 接口缓存 | Spring @Cacheable | 30s~120s | 看板 KPI、列表页 |
| L2 对象缓存 | Redis Hash | 600s~3600s | 用户/部门/渠道 |
| L3 分布式锁 | Redisson RLock | 5~30s | 面试时段抢占、邮箱同步 |
| L4 会话缓存 | Redis String | 15min~30d | 登录失败计数、JWT 黑名单 |

## 六、AI 工作流

| 工作流 | 说明 | fallback |
|--------|------|----------|
| jd-generate | JD 生成 | 规则模板 |
| resume-parse | 简历解析 | 正则提取 |
| match-score | 人岗匹配 | 加权评分 |
| interview-qa | 面试题生成 | 题库选择 |
| talent-search | 人才搜索 | 关键词过滤 |
| offer-email-generate | Offer 邮件 | 模板生成 |
