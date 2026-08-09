# API 契约文档

> 对齐现有 Flask 后端接口，**对前端零影响**。URL 路径、方法、请求/响应格式完全一致。

## 一、统一响应格式

### 成功
```json
{ "data": ..., "message": "ok" }
```

### 失败
```json
{ "error": { "code": "AUTH_FAILED", "message": "用户名或密码错误" } }
```

### 分页
```json
{ "data": [...], "total": 100, "page": 1, "pageSize": 20 }
```

## 二、错误码

| 状态码 | code | 说明 |
|--------|------|------|
| 400 | INVALID_INPUT | 参数错误 |
| 400 | AUTH_FAILED | 用户名或密码错误 |
| 401 | UNAUTHORIZED | 未登录 |
| 401 | TOKEN_EXPIRED | 登录已过期 |
| 401 | INVALID_TOKEN | 无效令牌 |
| 403 | FORBIDDEN | 无权限访问 |
| 404 | NOT_FOUND | 资源不存在 |
| 429 | ACCOUNT_LOCKED | 账户已锁定 |
| 500 | INTERNAL_ERROR | 服务器内部错误 |

## 三、认证接口 /api/auth/*

### POST /api/auth/login
请求：
```json
{ "username": "20260001", "password": "123456", "rememberMe": false }
```
响应：
```json
{
  "data": {
    "token": "eyJ...",
    "user": { "id": "1", "name": "李潇潇", "role": "admin", "mustChangePassword": false },
    "menus": ["recruit-dashboard", "recruit-demand", "recruit-talent", "recruit-interview", "recruit-ai", "recruit-config"]
  },
  "message": "ok"
}
```
响应头：`Set-Cookie: hr_token=...; HttpOnly; Path=/; SameSite=Lax`

### GET /api/auth/me
请求头：`Authorization: Bearer <token>` 或 Cookie `hr_token`
响应：
```json
{
  "data": { "user": { "id": "1", "name": "李潇潇", "role": "admin", "avatar": null }, "menus": [...] },
  "message": "ok"
}
```

### POST /api/auth/logout
清空 httpOnly cookie。

## 四、业务接口清单

| 模块 | 方法 | 路径 | 说明 |
|------|------|------|------|
| dashboard | GET | /api/dashboard/kpi | 角色感知 KPI |
| dashboard | GET | /api/dashboard/funnel | 招聘漏斗 |
| dashboard | GET | /api/dashboard/dept-progress | 部门进度 |
| dashboard | GET | /api/dashboard/channel | 渠道统计 |
| dashboard | GET | /api/dashboard/risk-alerts | 风险预警 |
| dashboard | GET | /api/dashboard/monthly | 月度统计 |
| demand | GET | /api/demand/list | 需求列表（分页） |
| demand | POST | /api/demand/create | 创建需求 |
| demand | PATCH | /api/demand/{id} | 更新需求 |
| demand | GET | /api/demand/{id} | 需求详情 |
| demand | POST | /api/demand/{id}/approve | 审批通过 |
| demand | POST | /api/demand/{id}/reject | 审批驳回 |
| demand | POST | /api/demand/{id}/submit | 提交审批 |
| demand | POST | /api/demand/{id}/close | 关闭需求 |
| demand | DELETE | /api/demand/{id} | 删除需求 |
| demand | GET | /api/demand/{id}/candidates | 需求候选人 |
| demand | POST | /api/demand/{id}/match | 批量匹配 |
| talent | GET | /api/talent/list | 人才库列表 |
| talent | GET | /api/talent/candidate/{id} | 候选人详情 |
| talent | PATCH | /api/talent/{id}/note | 更新备注 |
| talent | POST | /api/talent/upload-resume | 上传简历 |
| talent | GET | /api/talent/candidate/{id}/contact-info | 联系方式（HR） |
| talent | GET | /api/talent/candidate/{id}/export | 数据导出 |
| interview | GET | /api/interview/list | 面试列表 |
| interview | POST | /api/interview/create | 发起面试 |
| interview | POST | /api/interview/{id}/evaluate | 面试评价 |
| interview | POST | /api/interview/{id}/complete | 完成面试 |
| interview | POST | /api/interview/{id}/offer | 发 Offer |
| interview | POST | /api/interview/{id}/onboard | 确认入职 |
| interview | DELETE | /api/interview/{id} | 取消面试 |
| hire | POST | /api/hire/offer/create | 创建 Offer |
| hire | POST | /api/hire/offer/{id}/send | 发送 Offer |
| hire | POST | /api/hire/offer/{id}/accept | 接受 Offer |
| hire | POST | /api/hire/offer/{id}/reject | 拒绝 Offer |
| hire | GET | /api/hire/offers | Offer 列表 |
| config | GET | /api/config/channels | 渠道列表 |
| config | GET | /api/config/email-accounts | 邮箱账号 |
| config | GET | /api/config/notify-templates | 通知模板 |
| config | GET | /api/config/score-rules | 评分规则 |
| config | GET | /api/config/knowledge-base | 知识库 |
| config | GET | /api/config/audit-logs | 审计日志 |
| config | GET | /api/config/api-keys | API 密钥 |
| health | GET | /api/health | 健康检查 |
| ai | GET | /api/ai/capabilities | AI 能力清单 |
| ai | POST | /api/ai/run/{workflow} | 执行工作流 |
| ai | POST | /api/ai/stream/{workflow} | SSE 流式 |

## 五、AI 工作流

| workflow | 请求体 | 说明 |
|----------|--------|------|
| jd-generate | `{ role, dept }` | 生成 JD |
| resume-parse | `{ text }` | 简历解析 |
| match-score | `{ jd, candidate }` | 人岗匹配 |
| interview-qa | `{ jd, resume }` | 面试题生成 |
| talent-search | `{ query }` | 人才搜索 |
| offer-email-generate | `{ candidate, offer }` | Offer 邮件 |

AI 响应统一包含：
```json
{ "data": { ..., "disclaimer": "此内容由AI生成，请人工审核确认后使用" }, "message": "ok" }
```
