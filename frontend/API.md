# 智能招聘系统 · 前后端接口文档

> 本文档从当前前端 `src/data/` 的所有 mock 数据中提取接口定义。
> **每次前后端动接口时，必须同步更新本文档。**

生成时间：2026-07-18
项目：`D:\WorkProject\HrProject\hr-web\frontend`

---

## 0. 鉴权 & 会话

当前使用 `localStorage` 存储，无后端鉴权接口：

| 键名 | 类型 | 说明 |
|---|---|---|
| `hr_role` | `string` | 角色：`admin`/`hr`/`interviewer`/`dept_head`/`employee`/`no_recruit` |
| `hr_user` | `string` | 用户名，用作显示名 |
| `hr_temp_interviewer` | `string` | sessionStorage 临时标记，登录页「临时面试官」演示入口 |

**后续需替换为**：JWT token (cookie/Authorization header) + `GET /api/auth/me` 回包结构见下文。

### GET /api/auth/me

```json
Response 200:
{
  "user": {
    "id": "U001",
    "name": "张HR",
    "role": "admin",
    "avatar": null
  },
  "menus": ["recruit-dashboard","recruit-demand","recruit-talent","recruit-interview","recruit-ai","recruit-config"]
}
```

**角色说明：**

| role | 标签 | 菜单范围 |
|---|---|---|
| `admin` | 管理员 | 全部 6 项 |
| `hr` | HR 专员 | 看板/需求/人才库/面试 |
| `interviewer` | 面试官 | 看板/面试计划 |
| `temp_interviewer` | 临时面试官 | 看板/面试计划 |
| `dept_head` | 部门负责人 | 看板/需求管理 |
| `employee` | 基层员工 | 看板/需求管理 |
| `no_recruit` | 无权限员工 | 侧边栏隐藏 |

---

## 1. 招聘看板 — `/recruit-dashboard`

> 数据源：`src/data/dashboard.js`

### 1.1 GET /api/dashboard/kpi

```json
Response 200:
{
  "data": [
    { "val": 8,  "label": "全公司待面试",   "trend": "+2 昨日" },
    { "val": 12, "label": "待评价",         "trend": "+3 昨日" },
    { "val": 8,  "label": "在招岗位",       "trend": "+1 昨日" },
    { "val": 5,  "label": "本月入职总量",    "trend": "持平" }
  ]
}
```

**说明**：KPI 值按角色返回不同数据（admin/hr/interviewer 三套），字段 `val`(整数/浮点数)、`label`、`trend`。

### 1.2 GET /api/dashboard/funnel

```json
Response 200:
{
  "period": "2026-07",
  "overallRate": "1.4%",
  "stages": [
    {
      "label": "收简历",   "count": 346, "pct": "100%", "link": "/recruit-talent",
      "conv": null,        "wow": "+8.4%", "wowUp": true,
      "dwell": "1.2d",     "health": "good", "bottleneck": false,
      "owner": "HR 团队",
      "spark": [38,42,51,47,55,60,53],
      "note": "本月入口流量稳定，邮箱采集与内推贡献最高，简历池充足。"
    },
    {
      "label": "筛选通过", "count": 89,  "pct": "25.7%", "link": "/recruit-demand",
      "conv": "25.7%",     "wow": "+2.1%", "wowUp": true,
      "dwell": "2.4d",     "health": "good", "bottleneck": false,
      "owner": "用人部门 · HR",
      "spark": [12,10,14,11,13,15,14],
      "note": "筛选通过率 25.7%，可继续优化 JD 精准度，减少无效投递。"
    },
    {
      "label": "面试",     "count": 42,  "pct": "12.1%", "link": "/recruit-interview",
      "conv": "47.2%",     "wow": "-6.3%", "wowUp": false,
      "dwell": "3.6d",     "health": "watch", "bottleneck": false,
      "owner": "面试官团队",
      "spark": [7,6,5,6,5,7,6],
      "note": "面试排期充足，重点关注面试到 Offer 的转化质量与评估一致性。"
    },
    {
      "label": "Offer",    "count": 8,   "pct": "2.3%",  "link": "/recruit-interview",
      "conv": "19.0%",     "wow": "-4.5%", "wowUp": false,
      "dwell": "2.1d",     "health": "risk", "bottleneck": true,
      "owner": "HR · 用人经理",
      "spark": [1,2,1,1,2,1,0],
      "note": "面试→Offer 转化仅 19.0%，为当前最大瓶颈，建议复盘评估口径与决策时效。"
    },
    {
      "label": "入职",     "count": 5,   "pct": "1.4%",  "link": "/recruit-demand",
      "conv": "62.5%",     "wow": "+0.4%", "wowUp": true,
      "dwell": "5.0d",     "health": "good", "bottleneck": false,
      "owner": "HR 团队",
      "spark": [1,0,1,1,1,0,1],
      "note": "Offer 到入职转化健康，保持 offer 后跟进与入职关怀节奏。"
    }
  ]
}
```

**字段说明：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `label` | `string` | 阶段名称 |
| `count` | `number` | 当前阶段人数 |
| `pct` | `string` | 占总简历百分比 |
| `link` | `string` | 前端跳转路径 |
| `conv` | `string|null` | 从上一阶段进入本阶段的转化率（入口阶段为 null） |
| `wow` | `string` | 环比变化（如 `+8.4%` / `-6.3%`） |
| `wowUp` | `boolean` | 环比方向（true=上升，false=下降） |
| `dwell` | `string` | 平均停留（如 `1.2d`、`5.0d`） |
| `health` | `enum: good\|watch\|risk` | 健康度 |
| `bottleneck` | `boolean` | 是否为瓶颈阶段 |
| `owner` | `string` | 负责人 |
| `spark` | `number[7]` | 近 7 天日报数字 |
| `note` | `string` | 经营洞察文本 |

### 1.3 GET /api/dashboard/dept-progress

```json
Response 200:
{
  "data": [
    { "dept": "技术部", "hired": 3, "total": 5, "pct": 60 },
    { "dept": "产品部", "hired": 1, "total": 3, "pct": 33 },
    { "dept": "运营部", "hired": 0, "total": 2, "pct": 0  },
    { "dept": "数据部", "hired": 2, "total": 2, "pct": 100 }
  ]
}
```

### 1.4 GET /api/dashboard/channel

```json
Response 200:
{
  "data": [
    { "channel": "邮箱采集",   "resume": 120, "pass": 35, "interview": 18, "hire": 2, "cost": "¥0"   },
    { "channel": "Boss 直聘",  "resume": 98,  "pass": 28, "interview": 12, "hire": 1, "cost": "¥8K"  },
    { "channel": "猎聘",      "resume": 65,  "pass": 15, "interview": 7,  "hire": 1, "cost": "¥12K" },
    { "channel": "内推",      "resume": 42,  "pass": 8,  "interview": 4,  "hire": 1, "cost": "¥3K"  }
  ]
}
```

**字段说明**：`resume`=简历量，`pass`=筛选通过，`interview`=面试，`hire`=录用，`cost`=人均成本。

### 1.5 GET /api/dashboard/risk-alerts

```json
Response 200:
{
  "data": [
    { "text": "运营部·运营总监 — 发布20天零简历",       "type": "reject", "link": "/recruit-demand-detail", "action": "查看" },
    { "text": "技术部·前端工程师 — HC仅剩1个",          "type": "warn",   "link": "/recruit-demand-detail", "action": "查看" },
    { "text": "3名候选人超7天未安排面试",               "type": "warn",   "link": "/recruit-interview",      "action": "安排" },
    { "text": "数据部·数据分析师 — 昨日已招满",          "type": "done",   "link": "/recruit-demand-detail", "action": "查看" }
  ]
}
```

**字段说明**：`type` 枚举 `reject`(红)/`warn`(黄)/`done`(绿)，`link` 前端跳转，`action` 操作按钮文案。

---

## 2. 需求管理 — `/recruit-demand`

> 数据源：`src/data/demand.js`

### 2.1 GET /api/demand/list

```json
Response 200:
{
  "data": [
    {
      "id": "DM2026070006",
      "position": "运营总监",
      "dept": "运营部",
      "hc": 1,
      "urgency": "very",
      "urgencyLabel": "非常紧急",
      "urgencyType": "reject",
      "submitter": "陈总",
      "status": "approval",
      "statusLabel": "审批中",
      "statusType": "warn",
      "approvalNodes": [
        { "label": "部门负责人", "state": "current" },
        { "label": "HR",        "state": "pending" },
        { "label": "高管",       "state": "pending" }
      ],
      "linkedCount": 0
    }
  ]
}
```

**字段说明：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `string` | 需求编号（`DM` 前缀） |
| `position` | `string` | 岗位名称 |
| `dept` | `string` | 所属部门 |
| `hc` | `number` | 招聘名额 |
| `urgency` | `enum: very\|high\|normal` | 紧急度原始值 |
| `urgencyLabel` | `string` | 紧急度显示文本 |
| `urgencyType` | `enum: reject\|warn\|draft` | 紧急度样式 |
| `submitter` | `string` | 提交人 |
| `status` | `enum: approval\|open\|closed\|draft` | 需求状态原始值 |
| `statusLabel` | `string` | 状态显示文本 |
| `statusType` | `enum: warn\|progress\|draft` | 状态样式 |
| `approvalNodes[].state` | `enum: done\|current\|pending` | 审批节点状态 |
| `linkedCount` | `number` | 已关联候选人数量 |

**补充字段**（状态为 `open` 时出现）：
- `directApply`: 直接投递数
- `systemRecommend`: 系统推荐数
- `internalMatch`: 内部匹配数
- `internalNames`: 内部匹配人名列表
- `interviewing`: 面试中人数

**筛选参数**（query string）：
| 参数 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `search` | `string` | `""` | 搜索关键词（岗位/部门） |
| `status` | `string` | `"all"` | 状态筛选 |
| `urgency` | `string` | `"all"` | 紧急度筛选 |

### 2.2 POST /api/demand/create

```json
Request:
{
  "position": "前端工程师",
  "dept": "技术部",
  "hc": 2,
  "urgency": "high",
  "submitter": "刘博",
  "description": "...",
  "requiredSkills": [...],
  "plusSkills": [...]
}

Response 200:
{
  "id": "DM2026070007",
  "created": true
}
```

### 2.3 PATCH /api/demand/{demandId}

更新需求信息（同上 Request Body，所有字段可选）。

### 2.4 POST /api/demand/{demandId}/close

```json
Response 200:
{ "closed": true }
```

---

## 3. 需求详情 — `/recruit-demand-detail`

> 数据源：`src/data/demand-detail.js`

### 3.1 GET /api/demand/{demandId}

```json
Response 200:
{
  "id": "DM2026070005",
  "position": "高级Java工程师",
  "dept": "技术部",
  "hc": 2,
  "urgency": "紧急",
  "salary": "¥15K - ¥25K / 月",
  "date": "2026-08-01",
  "submitter": "刘博",
  "submitDate": "2026-07-12",
  "channels": ["Boss直聘", "猎聘", "邮箱采集"],
  "progress": { "hired": 1, "total": 2, "pct": 50 },
  "description": "负责公司电商中台核心服务的架构设计...",
  "requiredSkills": ["Java · 5年以上", "Spring Boot/Cloud", "MySQL 调优", "Kubernetes", "微服务架构设计", "分布式系统"],
  "plusSkills": ["团队管理经验", "DevOps/CI-CD", "多语言（Go/Python）", "技术博客/开源贡献"],
  "approvalNodes": [
    { "actor": "刘博",  "role": "部门负责人", "status": "已通过", "date": "2026-07-12 14:30" },
    { "actor": "张HR",  "role": "HR",        "status": "已通过", "date": "2026-07-13 09:15" },
    { "actor": "高管",  "role": "高管",       "status": "已通过", "date": "2026-07-13 16:00" }
  ]
}
```

### 3.2 GET /api/demand/{demandId}/candidates

```json
Response 200:
{
  "data": [
    {
      "name": "张三",
      "profileScore": 88,
      "profileGrade": "A",
      "matchScore": 92,
      "ageDays": 4,
      "source": "direct",
      "sourceLabel": "直接投递",
      "status": "interviewing",
      "statusLabel": "面试中",
      "edu": "本科",
      "years": "5+",
      "notRecReason": null
    }
  ]
}
```

**字段说明：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `profileScore` | `number` | 简历画像评分 (0-100) |
| `profileGrade` | `enum: A\|A-\|B+\|B\|B-\|C+\|C` | 简历等级 |
| `matchScore` | `number|null` | 人岗匹配分 (0-100，null 表示未匹配) |
| `ageDays` | `number` | 简历停留天数 |
| `source` | `enum: direct\|external\|internal` | 来源类型 |
| `sourceLabel` | `string` | 来源显示文本 |
| `status` | `enum: interviewing\|available` | 候选人状态 |
| `notRecReason` | `string|null` | 不推荐原因 |
| `isEmployee` | `boolean` | 是否内部员工 |

**筛选参数**（query string）：
| 参数 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `source` | `string` | `"all"` | 来源筛选 |
| `status` | `string` | `"all"` | 状态筛选 |
| `grade` | `string` | `"all"` | 简历等级筛选 |
| `ageDays` | `string` | `"all"` | 停留天数筛选 |
| `match` | `string` | `"all"` | 匹配状态筛选 |
| `edu` | `string` | `"all"` | 学历筛选 |
| `years` | `string` | `"all"` | 年限筛选 |
| `isEmployee` | `string` | `"all"` | 是否内部 |

### 3.3 POST /api/demand/{demandId}/candidates/{name}/link

关联候选人到需求（当前存储为 `localStorage demand_{demandId}_linked`）。

```json
Request:
{ "link": true }

Response 200:
{ "linked": true, "linkedCount": 3 }
```

---

## 4. 人才库 — `/recruit-talent`

> 数据源：`src/data/talent.js`

### 4.1 GET /api/talent/list?tab={tab}

```json
Response 200:
{
  "tab": "external",
  "data": [
    {
      "id": "C2026070012",
      "name": "张三",
      "portraitClass": "score-high",
      "portrait": "A · 88",
      "edu": "本科",
      "years": "5年",
      "skills": ["Java", "K8s", "微服务"],
      "company": "阿里巴巴",
      "source": "邮箱",
      "inDate": "07-12",
      "status": "available",
      "statusLabel": "可联系",
      "note": "",
      "locked": false
    }
  ]
}
```

**分页/筛选参数**（query string）：
| 参数 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `tab` | `enum: external\|internal\|blacklist` | `external` | 标签页 |
| `source` | `string` | `"all"` | 来源筛选（external tab） |
| `status` | `string` | `"all"` | 状态筛选 |
| `dept` | `string` | `"all"` | 部门筛选（internal tab） |
| `search` | `string` | `""` | 搜索关键词 |

**字段说明：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `string` | 候选人编号（`C` 前缀） |
| `portraitClass` | `enum: score-high\|score-mid\|score-low` | 画像等级样式 |
| `portrait` | `string` | 画像等级 + 分数（如 `A · 88`、`—`） |
| `status` | `enum: available\|locked\|reserve\|archived` | 候选人状态 |
| `locked` | `boolean` | 是否节点锁定 |

**内部员工专属字段**（tab=internal）：
- `dept`：所在部门
- `perf`：绩效评级
- `matchHtml`：匹配方向 + 分数
- `transfer`：是否为可调岗员工
- `pos`：现职岗位

**黑名单专属字段**（tab=blacklist）：
- `phone`：手机号（脱敏）
- `date`：录入日期
- `reason`：原因
- `operator`：操作人
- `expiry`：封禁期限

### 4.2 PATCH /api/talent/{candidateId}/note

```json
Request:
{ "note": "联系反馈：可约面，期望薪资 18K" }

Response 200:
{ "updated": true }
```

### 4.3 GET /api/talent/match?demandId={demandId}

```json
Response 200:
{
  "demandId": "DM2026070005",
  "results": [
    { "id": "EMP001", "name": "王工", "dept": "技术部", "curPos": "高级Java", "perf": "A", "score": 92, "transferable": true },
    { "id": "EMP015", "name": "赵工", "dept": "数据部", "curPos": "数据分析师", "perf": "B+", "score": 42, "transferable": false }
  ]
}
```

---

## 5. 面试计划 — `/recruit-interview`

> 数据源：`src/data/interview.js`

### 5.1 GET /api/interview/list?tab={tab}&status={status}

```json
Response 200:
{
  "tab": "all",
  "data": [
    {
      "name": "张三",
      "position": "高级Java工程师",
      "round": "初试(1/3轮)",
      "interviewer": "李面试官",
      "date": "07-16",
      "time": "14:00",
      "method": "飞书视频",
      "status": "scheduled",
      "statusLabel": "待面试",
      "createdBy": "张HR",
      "isMine": false
    }
  ]
}
```

**字段说明：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `round` | `string` | 面试轮次描述 |
| `method` | `enum: 飞书视频\|现场\|电话\|飞书消息\|待定` | 面试方式 |
| `status` | `enum: pending\|scheduled\|evaluating\|offer\|onboard\|done` | 面试状态 |
| `statusLabel` | `string` | 状态显示文本 |
| `isMine` | `boolean` | 是否当前用户的面试 |
| `createdBy` | `string` | 创建人 |

**状态映射**：
| status | statusLabel | 说明 |
|---|---|---|
| `pending` | 待安排 | 面试尚未安排 |
| `scheduled` | 待面试 | 已安排面试时间 |
| `evaluating` | 待评价 | 面试已完成、待填写评价 |
| `offer` | 待录用 | 评价通过、待发 Offer |
| `onboard` | 待入职 | Offer 已接受 |
| `done` | 已入职 | 已入职 |

### 5.2 GET /api/interview/alerts

```json
Response 200:
{
  "data": [
    { "text": "孙九 · 复试超3天未评价", "type": "reject", "action": "去评价", "actionMsg": "填写对孙九的评价" },
    { "text": "张三 · 07-16 14:00 初试",  "type": "warn",   "action": "查看",   "actionMsg": "" }
  ]
}
```

### 5.3 POST /api/interview/create

```json
Request:
{
  "name": "张三",
  "position": "高级Java工程师",
  "round": "初试",
  "interviewer": "李面试官",
  "date": "2026-07-20",
  "time": "14:00",
  "method": "飞书视频"
}

Response 200:
{ "created": true }
```

### 5.4 POST /api/interview/schedule

安排面试（同 create，支持批量）。

### 5.5 POST /api/interview/{interviewId}/evaluate

```json
Request:
{
  "score": 85,
  "result": "pass",
  "comment": "技术基础扎实，微服务经验丰富..."
}

Response 200:
{ "evaluated": true }
```

**result 枚举**：`pass`（通过）/ `fail`（淘汰）/ `hold`（待定）

---

## 6. 招聘辅助中心 — `/recruit-ai`

> 数据源：`src/data/ai.js`

本页面为 AI 能力/工作流展示页，当前无 CRUD 接口。后续对应每项能力单独接入。

### 6.1 GET /api/ai/capabilities

```json
Response 200:
{
  "data": [
    {
      "ability": "简历 AI 解析 + 画像生成 + 标签打标",
      "page": "邮件管理 / 人才库上传",
      "trigger": "邮箱定时同步 or 手动上传 PDF/DOCX",
      "workflow": "① 简历画像解析",
      "status": "done"
    },
    {
      "ability": "审批通过自动匹配（内外并行）",
      "page": "需求管理",
      "trigger": "三步审批全部通过后系统自动触发",
      "workflow": "② 人岗匹配打分",
      "status": "done"
    }
  ]
}
```

**`status` 枚举**：`done`(已接入) / `warn`(待接入) / `draft`(规划中)

### 6.2 POST /api/ai/run/{workflow}

触发 AI 工作流（如 JD 草稿生成、简历解析等），具体 Request/Response 按工作流单据接口定义。

---

## 7. 招聘基础配置 — `/recruit-config`

> 数据源：`src/data/config.js`

### 7.1 GET /api/config/email-accounts

```json
Response 200:
{
  "data": [
    {
      "address": "hr-recruit@company.com",
      "type": "企业邮箱",
      "freq": "每 30 分钟",
      "status": "正常",
      "statusColor": "done",
      "lastSync": "07-14 14:30"
    }
  ]
}
```

### 7.2 POST /api/config/email-accounts

新增邮箱账户。

```json
Request:
{
  "address": "new-hr@company.com",
  "type": "企业邮箱",
  "freq": "每 30 分钟"
}

Response 200:
{ "created": true }
```

### 7.3 GET /api/config/channels

```json
Response 200:
{
  "data": [
    { "code": "BOSS",   "name": "Boss直聘", "type": "招聘平台", "cost": "¥8,000",  "status": "启用" },
    { "code": "LIEPIN", "name": "猎聘",     "type": "猎头平台", "cost": "¥12,000", "status": "启用" },
    { "code": "EMAIL",  "name": "邮箱采集",  "type": "自动管道", "cost": "¥0",      "status": "启用" },
    { "code": "NEITUI", "name": "内部推荐",  "type": "内部渠道", "cost": "¥3,000",  "status": "启用" }
  ]
}
```

### 7.4 PUT /api/config/channels/{code}

更新渠道配置（字段：`cost`, `status`）。

### 7.5 GET /api/config/score-rules

```json
Response 200:
{
  "profileWeight": 0.10,
  "matchWeight": 0.90,
  "decay30": 1.0,
  "decay90": 0.85,
  "decayOver90": 0.70,
  "passLine": 60,
  "topCount": 5,
  "searchRange": "近 3 个月"
}
```

**字段说明**：
| 字段 | 类型 | 说明 |
|---|---|---|
| `profileWeight` | `number` | 简历画像权重 |
| `matchWeight` | `number` | 人岗匹配权重 |
| `decay30` | `number` | 30 天内时效权重 |
| `decay90` | `number` | 90 天内时效权重 |
| `decayOver90` | `number` | 超过 90 天时效权重 |
| `passLine` | `number` | 匹配合格线 (0-100) |
| `topCount` | `number` | Top N 推荐数量 |
| `searchRange` | `string` | 搜索范围 |

### 7.6 PUT /api/config/score-rules

更新打分规则（同上所有字段）。

### 7.7 GET /api/config/notify-templates

```json
Response 200:
{
  "data": [
    { "name": "面试邀请通知",       "type": "面试", "method": "飞书 + 短信", "updated": "07-10" },
    { "name": "Offer 发送模板",     "type": "Offer", "method": "邮件",        "updated": "07-08" },
    { "name": "未通过通知",         "type": "淘汰", "method": "短信",        "updated": "07-01" },
    { "name": "面试提醒（前一天）",  "type": "提醒", "method": "飞书 + 短信", "updated": "06-28" }
  ]
}
```

### 7.8 GET /api/config/role-permissions

```json
Response 200:
{
  "data": [
    { "role": "管理员",     "menus": "全部 6 项",     "dataScope": "全量无隔离",   "ops": "全部" },
    { "role": "HR 专员",    "menus": "看板/需求/人才库/面试", "dataScope": "全公司",  "ops": "CRUD + 发Offer" },
    { "role": "面试官",     "menus": "看板/面试计划",  "dataScope": "仅自己场次",   "ops": "填评价" },
    { "role": "部门负责人", "menus": "看板/需求管理",  "dataScope": "本部门",      "ops": "审批需求" },
    { "role": "基层员工",   "menus": "看板/需求管理",  "dataScope": "仅自己的需求", "ops": "提交需求" },
    { "role": "无权限员工", "menus": "侧边栏隐藏",     "dataScope": "—",           "ops": "—" }
  ]
}
```

### 7.9 GET /api/config/audit-logs

```json
Response 200:
{
  "data": [
    {
      "time": "07-14 14:30",
      "user": "张HR",
      "module": "面试",
      "action": "发起面试",
      "detail": "张三 → 高级Java工程师初试，面试官李面试官"
    }
  ]
}
```

---

## 8. 通用约定

### 8.1 枚举值汇总

| 枚举 | 可选值 | 使用页面 |
|---|---|---|
| `health` | `good` / `watch` / `risk` | 看板漏斗 |
| `status (demand)` | `approval` / `open` / `closed` / `draft` | 需求管理 |
| `urgency` | `very` / `high` / `normal` | 需求管理 |
| `status (interview)` | `pending` / `scheduled` / `evaluating` / `offer` / `onboard` / `done` | 面试计划 |
| `status (candidate)` | `available` / `locked` / `reserve` / `archived` | 人才库 |
| `status (talent)` | `available` / `interviewing` | 需求详情 |
| `source` | `direct` / `external` / `internal` | 需求详情候选人 |
| `type (alert)` | `reject`(红) / `warn`(黄) / `done`(绿) | 风险预警 |
| `type (talent alert)` | `reject` / `warn` / `done` | 面试提醒 |
| `ai status` | `done` / `warn` / `draft` | 辅助中心能力 |

### 8.2 通用请求参数

| 参数 | 类型 | 说明 |
|---|---|---|
| `search` | `string` | 搜索关键词（通常搜索岗位/部门/姓名） |
| `status` | `string` | 状态筛选，`"all"` 表示全部 |
| `sort` | `string` | 排序字段 |
| `order` | `enum: asc\|desc` | 排序方向 |
| `page` | `number` | 分页页码（默认 1） |
| `pageSize` | `number` | 分页大小（默认 20） |

### 8.3 通用响应格式

**成功**：
```json
{ "data": {...} }
```

**列表**：
```json
{ "data": [...], "total": 100, "page": 1, "pageSize": 20 }
```

**错误**：
```json
{ "error": { "code": "NOT_FOUND", "message": "需求不存在" } }
```

**HTTP 状态码约定**：200 成功 / 400 参数错误 / 401 未登录 / 403 无权限 / 404 不存在 / 500 服务端异常。

### 8.4 日期/时间格式

- 日期：`YYYY-MM-DD`（如 `2026-07-16`）
- 日期时间：`YYYY-MM-DD HH:mm`（如 `2026-07-12 14:30`）
- 相对时间：`Xd` = X 天（如 `1.2d`、`5.0d`）
- 百分比：带 `%` 的字符串（如 `25.7%`）
- 环比：带符号的百分比字符串（如 `+8.4%`、`-6.3%`）

### 8.5 当前 localStorage 键名（待迁移到 API）

| 键名 | 用途 | 对应 API |
|---|---|---|
| `hr_role` | 用户角色 | `GET /api/auth/me` |
| `hr_user` | 用户名 | `GET /api/auth/me` |
| `hr_temp_interviewer` | 临时面试官标记 | 改为 Query 参数 `?demo=interviewer` |
| `demand_{id}_linked` | 需求关联候选人数 | `GET/MODIFY /api/demand/{id}/candidates` |

---

## 9. 文档维护约定

- 新增 mock 数据字段 → **必须在本文档对应接口处追加字段说明**
- 修改字段名/类型 → **必须同步更新本文档，并标注 Breaking Change**
- 新增页面/数据源 → **必须在本文档新增接口章节**
- 每次接口变更，在文件尾部的 Changelog 记录：

**格式**：
```
### YYYY-MM-DD
- [接口] 改动内容
```

---

## Changelog

### 2026-07-18
- 初次建立：从 7 个 `src/data/*.js` 提取全部 mock 数据结构，定义 24 个 API 端点。
- 提取 localStorage 会话键名，规划 `GET /api/auth/me` 鉴权接口。
- 定义通用枚举、请求参数、响应格式和日期时间规范。
