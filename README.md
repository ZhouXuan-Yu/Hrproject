# 智能招聘系统（企业级 HR 招聘平台）

> 覆盖「邮箱收简历 → AI 解析画像 → 人岗匹配 → 面试（飞书/腾讯会议）→ Offer → 入职」全链路的一站式招聘管理平台。
>
> 前端 Vue 3 + Vite，后端 Flask + SQLAlchemy + Celery，AI 由 DeepSeek API 驱动并带本地规则引擎降级兜底。

## 界面预览

### 登录页

Three.js 动态背景，支持角色选择（系统管理员 / HR 专员 / 面试官）。

![登录页](docs/screenshots/01-login.png)

### 招聘看板

招聘全漏斗总览（收简历 → 筛选通过 → 面试 → Offer → 入职）、阶段转化率、瓶颈诊断与近 7 天趋势，支持时间范围与组织范围筛选。

![招聘看板](docs/screenshots/02-dashboard.png)

### 需求管理

招聘需求列表：审批进度（部门负责人 → HR → 财务总监）、招聘进展、紧急度、批量操作与新建需求。

![需求管理](docs/screenshots/03-demand.png)

### 需求详情

单个岗位的候选人推进路径（已加入需求 / 面试中 / 待评价 / 不合适）、审计关注、需求信息与重新匹配。

![需求详情](docs/screenshots/04-demand-detail.png)

### 人才库

简历储备库 + 简历处理管道可视化（邮箱同步过程步骤条 + 最近入库记录），支持手动刷新邮箱简历、查重合并、联系候选人（电话 / 邮件，自动提取自简历）。

![人才库](docs/screenshots/05-talent.png)

### 面试计划

面试预约、飞书视频 / 腾讯会议链接自动生成、面试评价、通过后一键发 Offer、入职确认全流程。

![面试计划](docs/screenshots/06-interview.png)

### 招聘辅助中心（AI）

9 项 AI 辅助能力统一入口：JD 草稿生成、语义简历搜索、人岗匹配工作台、面试辅助、招聘深度报表、候选人沟通助手。支持 DeepSeek 流式输出（SSE）与思考过程可视化，所有 AI 内容带「人工审核」声明。

![招聘辅助中心](docs/screenshots/07-ai.png)

### 招聘基础配置

API 密钥管理（保存后自动连通性测试，实时显示可用状态）、邮箱账户（IMAP 收简历）、评分规则、渠道、通知模板等。

![招聘基础配置](docs/screenshots/08-config.png)

## 核心功能链路

| 环节 | 能力 |
|------|------|
| 简历采集 | 邮箱 IMAP 定时 + 手动刷新收取附件简历；Boss 直聘 CLI 渠道导入 |
| AI 解析 | DeepSeek 解析简历画像（技能 / 经验 / 教育），本地规则引擎降级兜底 |
| 人岗匹配 | 匹配评分（画像分 / 综合分 / 等级）、待补足技能分析、流式思考过程 |
| 面试 | 飞书视频 / 腾讯会议链接生成、多轮次评价、面试时间线 |
| Offer | 草稿 → 发送 → 接受 / 拒绝 / 过期全状态机；薪资预算校验；重复发送防护（草稿自动复用）；候选人 H5 确认链接邮件 |
| 入职 | Offer 接受后自动创建入职记录，人才库状态联动 |
| 通知 | 面试邀请 / Offer / 入职指引邮件，沟通记录留痕（含审计） |

## 技术栈

| 层 | 选型 |
|----|------|
| 前端 | Vue 3 + Vite + Vue Router + Three.js（登录 / 漏斗 3D 装饰） |
| 后端 | Flask + SQLAlchemy + Celery（异步任务） |
| 数据库 | SQLite（开发，`backend/hr_recruit.db`）/ MySQL（生产） |
| AI | DeepSeek API + ai_engine 本地规则降级，SSE 流式输出 `/api/ai/stream/*` |
| 集成 | 飞书（视频会议 / 消息）、腾讯会议、Boss 直聘 CLI、IMAP 邮箱 |
| 测试 | Playwright 49 E2E + pytest 37 后端用例 |

## 项目结构

```
hr-web/
├── frontend/               Vue 3 + Vite（9 个页面，10 个 API 模块）
│   ├── src/views/          页面：登录 / 看板 / 需求 / 需求详情 / 人才库 / 面试 / 辅助中心 / 配置
│   ├── src/components/     含 ai/（AiThinking 思考面板、AiConversation 对话组件）等
│   ├── src/api/            后端 API 封装（统一重试 / 缓存 / 错误处理）
│   ├── tests/              Playwright E2E（49 用例）
│   └── scripts/            capture-readme-shots.mjs（README 截图脚本）
├── backend/                Flask 后端
│   ├── app/api/            13 个 Blueprint，96 个 REST 端点
│   ├── app/services/       25 个业务服务（hire / interview / talent / confirm / ai ...）
│   ├── app/models/         31 张表
│   ├── tasks/              Celery 异步任务（邮箱同步等）
│   ├── scripts/            迁移与种子数据脚本
│   └── tests/              pytest（37 用例）
└── docs/                   设计文档与界面截图（docs/screenshots/）
```

## 快速启动

```bash
# 后端（建议使用 backend/.venv）
cd backend/
pip install -r requirements.txt
python scripts/seed.py      # 初始化数据库 + 种子数据（可选）
python app.py               # http://127.0.0.1:5000

# 前端
cd frontend/
npm install
npm run dev                 # http://127.0.0.1:7100（/api 代理到 5000）
```

浏览器打开 `http://127.0.0.1:7100/login`，默认账号 `admin / admin123`。

### 运行测试

```bash
cd backend/ && python -m pytest tests/ -q        # 37 个后端用例
cd frontend/ && npx playwright test --workers=2  # 49 个 E2E 用例
```

### 重新生成 README 截图

```bash
cd frontend/ && npm run dev &                    # 需后端已启动
node scripts/capture-readme-shots.mjs            # 输出到 docs/screenshots/
```

## 环境变量（backend/.env）

| 变量 | 说明 |
|------|------|
| `SECRET_KEY` / `JWT_SECRET_KEY` | Flask 与 JWT 签名密钥 |
| `DEEPSEEK_API_KEY` | DeepSeek API Key（缺失时自动降级本地规则引擎） |
| `MOCK_FALLBACK` | 是否允许 mock 兜底（生产应为 `false`） |
| 邮箱 IMAP / 飞书 / 腾讯会议 | 在「招聘基础配置」页面可视化配置，密钥不明文硬编码 |

## 设计原则

- **AI 辅助而非替代**：所有 AI 生成内容带「请人工审核确认后使用」声明；对外联系动作（电话 / 邮件）均由 HR 人工触发，系统只提供话术草稿与字段补齐。
- **真实数据优先**：页面拒绝「虚假按钮」——联系候选人、邮箱刷新、密钥连通性测试等均打通到后端真实能力，失败时明确提示而非静默 mock。
- **幂等交互**：发送 Offer 等关键操作可安全重试（草稿复用），重复提交不会产生脏数据。

## 跟踪文档

| 文件 | 作用 |
|------|------|
| `Memory.md` | 进度记录 + 下一步计划 |
| `Learning.md` | 复盘 + 项目规则 |
| `Wiki.md` | 业务口径 + 架构说明 |
| `Claude.md` | 协作约束（无 emoji 图标 / 无渐变 / 密钥不硬编码等） |
| `README.md` | （本文件）项目概览 |
