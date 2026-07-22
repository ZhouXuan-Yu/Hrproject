# Memory.md — 项目进度追踪

## 当前状态

**日期**：2026-07-21
**阶段**：✅ **阶段 0-4 完成 | BOSS 登录打通 | 飞书 HTTP 直连 | 去重 | 抽屉 | Toast | 面试会议链接**
**状态**：系统稳定，18/18 后端测试，45/45 E2E 全绿

**最终验证**：18/18 backend ✅ + build ✅ + 45/45 E2E ✅（commit `a8b991c`）

### 2026-07-21 思考过程面板内容修正 ✅

- 问题：流式生成原文显示在思考面板**外面**的 AI 气泡里（裸 JSON），思考面板内只有静态提示
- 修复：JD/匹配 tab 的实时生成流并入 AiThinking 面板（静态提示 + 生成原文拼接），删除裸 JSON 流式气泡；AiThinking 文本体限高 240px + 流式期间自动跟随滚动到底
- E2E **49/49**，build 504ms

### 2026-07-21 招聘辅助中心 6 工作台改造 ✅

- **结构化输出缺失修复**：JD_GENERATE_SYSTEM/MATCH_SYSTEM 强制纯 JSON 输出（禁围栏）；流式端 `_parse_accumulated_json` 加尾逗号容错；前端流式结束后结构化字段缺失时自动调阻塞式 API 字段级补齐 —— 岗位职责/必备技能/任职资格/待补足技能永远有内容
- **思考过程可视化**：新增 `AiThinking.vue`（可折叠面板，active 自动展开/完成自动收起，text 流式 + steps 分步两种模式）+ `AiConversation.vue`（转译 HeroUIPro chat-conversation：role="log"、ResizeObserver 自动跟随滚动、回到底部按钮）；JD/Match 接真实 thinking，4 个阻塞 tab 用 steps 模式并诚实标注「处理过程」
- **隐藏 bug 修复**：RecruitAI 候选人下拉 `talentRes.data`→`talentRes.ext`；AiTabSearch 输入框永禁用的鸡生蛋 bug
- E2E 46→**49/49**，pytest **33/33**，build 782ms

### 2026-07-21 简历处理管道可视化 + 入库不显示 bug 修复 ✅

- **根因 bug**：`fetchTalent()` 读 `r.data.items`，但 `success_list()` 契约是 `r.data` 直接为数组 → 人才库永远渲染 mock 数据，真实入库简历从不显示。已修复并按 `pageSize=100` 拉取
- **删除**：app.js 的「人才资产工作台」（人才池质量分层/标签覆盖/今日动作）注入配置
- **新增**：RecruitTalent「简历处理管道」面板（Vue 原生）——本次同步逐账号/逐邮件步骤条（收取→附件识别→AI解析→入库 + 候选人/跳过原因）+ 最近入库记录表
- **新端点** `GET /api/talent/ingest-log`：Resume 表最近入库记录（候选人/编号/来源/解析引擎/入库时间）
- **合并**：顶栏重复的「刷新邮箱简历」按钮（另一会话加的）已移除，统一为 tab 行按钮；上传简历/同步后同时刷新列表和入库记录
- E2E：工作台断言排除 recruit-talent，新增管道面板断言 → **46/46**；pytest **33/33**

### 2026-07-21 人才库手动刷新邮箱简历 ✅

- 复用已有端点 `POST /api/config/email-accounts/sync`（同步执行 IMAP 拉取 → 解析 → 入库；定时任务仍为 Celery Beat 30min）
- RecruitTalent 简历储备库 tab 行最右侧新增「刷新邮箱简历」按钮：旋转动画 + 防重复点击，完成后 toast 汇总（新邮件 N 封 / 新入库简历 M 份 / 无启用邮箱提示 / 部分失败警告）并自动重载列表

### 2026-07-21 人才库「联系」真实化（电话/邮件）✅

- 简历解析提取链路已有（`resume_service.py`：DeepSeek + 正则兜底提取 phone/email → `Candidate.mobile/email`）
- 新端点 `GET /api/talent/candidate/<id>/contact-info`：返回完整手机号/邮箱（不打码），写审计日志
- ContactModal 重写：打开即拉取联系方式；电话 → `tel:` 拨打并记录、邮件 → `mailto:` 写邮件并记录、复制按钮；无对应数据时渠道禁用并提示「简历中未识别」；飞书保持辅助记录口径
- 种子数据补齐 6 个候选人的 mobile/email（真实 sha256 mobile_hash）
- 新增 `test_contact_info.py` 4 用例 → pytest **31/31**，E2E 45/45

### 2026-07-21 API 密钥连通性测试 ✅

- 后端 `config_service.test_api_key()`：deepseek（GET /models 探活）、feishu（tenant_access_token 换取）、dify/其他标记不支持；env 密钥优先于 DB，返回 `source` 标识
- 新端点 `POST /api/config/api-keys/test`（写审计日志）
- 前端 RecruitConfig：保存密钥后自动测试并显示内联结果（成功绿/失败红/测试中灰 + 密钥来源），新增「测试连接」按钮可随时复测
- 新增 `backend/tests/test_api_key_test.py` 9 用例 → pytest 18→**27/27**，E2E 45/45

### 2026-07-21 面试会议链接（meeting_url）✅

- `InterviewBook.meeting_url` 字段 + 幂等迁移脚本 `scripts/migrate_meeting_url.py`
- `feishu_client.create_vc_meeting()`（`/vc/v1/reserves/apply`，Mock 返回 `vc.feishu.cn/j/<9位>`）
- `create_interview()` 按类型生成链接：飞书→VC API（失败本地 fallback）、腾讯→本地拼 URL、其他线上→用户自定义、线下→空
- 建面试后 best-effort 飞书邀约通知（卡片含「进入面试」按钮），快照写入 `invite_json`
- 前端 RecruitInterview 三个表格方式列渲染可点击会议链接
- 修复隐藏 bug：前端传 `round: i+1`（int）导致 `'复试' in round_label` TypeError → 防御性 `str()` 转换
- 环境补齐：`backend/.venv` 补装 `requests`（既有声明依赖）
- 新增 `backend/tests/test_meeting_url.py` 4 用例 → pytest 14→18

---

## 按 DEVELOPMENT_PLAN.md 逐轮对照

| 轮次 | 计划内容 | 完成度 |
|------|------|:--:|
| **阶段 0** | 安全与基础修复（密钥/中间件/Alembic/JWT） | ✅ 5/5 |
| **阶段 1** | 核心流程真实化（审批/Offer/面试/候选人/测试） | ✅ 8/8 |
| **阶段 2** | 自动化管道（Celery/飞书/配置 DB 化/CRUD API） | ✅ 10/10 |
| **阶段 3** | 权限与 IAM（路由守卫/角色-JWT 对齐/菜单同步） | ✅ 4/4 |
| **飞书集成** | lark-cli→requests HTTP 直连（8 API 全重写） | ✅ |
| **对抗性审查** | 安全扫描 + 8 项修复 | ✅ 8/8 |

**共计 34+ 个子任务完成，14/14 后端测试，33/33 E2E，11/11 飞书 Mock。**

## 已完成 vs 待完成

| 状态 | 内容 |
|:--:|------|
| ✅ | 安全底座（密钥外置、JWT、Alembic、认证收紧、int 验证） |
| ✅ | 核心流程（审批落库、Offer/Entry API、面试 fail 修复、候选人锁定、需求详情候选人） |
| ✅ | 前端弹窗（ScheduleInterviewModal、OfferModal） |
| ✅ | 集成（飞书 Open API 直连 8 API、Celery 通知/匹配/邮件、Beat 调度） |
| ✅ | 配置管理（邮箱/渠道/评分/通知模板 DB CRUD） |
| ✅ | 权限（路由守卫 beforeEach、7 角色-JWT-菜单 三方对齐） |
| ✅ | 审批→Celery 匹配串联（`_fire_match_batch` → `batch_match_demand.delay()`） |
| ✅ | AI 能力状态修正（done→对话术/Offer/入职，warn→去重/垃圾过滤，draft→风险预警） |
| ✅ | 简历去重（`dedup_service.py` + `/api/dedup/*` — 手机/邮箱/姓名查重 + 全池扫描 + 合并） |
| ✅ | 抽屉组件（`BaseDrawer.vue` — 右侧滑入面板，Teleport，Escape，响应式） |
| ✅ | 全局 Toast（`ToastContainer.vue` + `useToast.js` — 4 色提示 + API 错误自动弹窗） |
| ⏳ | 前端单元测试（需要 Vitest + 组件测试） |

## 质量基线 🔒

| 项目 | 结果 |
|------|:--:|
| Backend pytest | 14/14 |
| Feishu mock tests | 11/11 |
| Frontend build | ✅ |
| Playwright E2E | 45/45 ✅ |
| 飞书 8 API | ✅ Mock/生产可切换 |
| 密钥安全 | ✅ 全部外置 |
| SQL/注入安全 | ✅ 0 风险 |
| JWT 认证 | ✅ 真实签发 + Bearer scheme 验证 |
| 审批链路 | ✅ DB 落库 + Celery match_batch 串联 |
| 路由守卫 | ✅ beforeEach token + 7角色页面映射 |
| 角色-菜单-JWT | ✅ 后端 enums.py ↔ 前端 useAuth.js ↔ /api/auth/login 三方一致 |

## 阶段 3：权限与路由守卫 ✅

| 任务 | 状态 | 说明 |
|------|:--:|------|
| 3.1 路由守卫 `beforeEach` | ✅ | `router/index.js` 新增 `beforeEach` — 无 token → /login |
| 3.2 角色-页面映射 | ✅ | `ROLE_PAGE_MAP` 6 角色 × 默认页面映射；admin 全通 |
| 3.3 E2E 测试适配 | ✅ | `beforeEach` 注入 `hr_token`；33/33 全部通过 |

## 飞书集成 — Open API 直连架构 ✅

**v2.0 重写**：移除 `lark-cli` subprocess 依赖，改用 `requests` 直调飞书 Open API。

| 对比 | v1.0 (lark-cli) | v2.0 (requests直连) |
|------|------|------|
| 依赖 | Node + npm + lark-cli | `requests`（已有） |
| 部署 | Windows PATH 差异、cmd/sh | 跨平台统一 |
| Token | lark-cli keychain | 内存缓存 + 自动刷新 |
| 调用 | subprocess 200ms | HTTP 5ms |
| 安全 | 外部进程 | Python 内 HTTPS |

| API | 实现方式 |
|-----|------|
| `send_text_message` | `POST /im/v1/messages` (Bot, `msg_type=text`) |
| `send_card_message` | `POST /im/v1/messages` (Bot, `msg_type=interactive`) |
| `send_interview_invite` | 双发：面试官卡片 + 候选人文本（支持 `*_open_id` 参数绕过 search_user） |
| `send_reminder` | Bot 卡片 + `recipient_open_id` |
| `send_overdue_alert` | Bot 卡片 + `recipient_open_id` |
| `check_freebusy` | `POST /calendar/v4/freebusy/list` |
| `create_calendar_event` | `POST /calendar/v4/calendars/primary/events` |
| `search_user` | `POST /contact/v3/users/batch_get_id` |

**Mock/生产切换**：
- `FEISHU_MOCK_MODE=true`（默认）→ 零依赖，本地开发直接用
- `FEISHU_MOCK_MODE=false` → 需要 `FEISHU_APP_ID` + `FEISHU_APP_SECRET`
- `FEISHU_RECIPIENT_OPEN_IDS='{"张三":"ou_xxx"}'` → Bot 模式免 search_user

**日历 API 已申请通过** ✅ scope 已开通，生产部署时填入 `FEISHU_APP_SECRET` + `FEISHU_MOCK_MODE=false` 即可真实打通全部 8 个 API。

## 阶段 0：安全与基础修复 ✅

| 任务 | 状态 | 说明 |
|------|:--:|------|
| 0.1 密钥外置 | ✅ | SECRET_KEY/JWT_SECRET_KEY/DEEPSEEK_API_KEY 强制从 env 读取，无硬编码 fallback |
| 0.2 MOCK_FALLBACK 开关 | ✅ | 新增 `MOCK_FALLBACK` + `should_mock_fallback()`；5 个 service `_mock_enabled()` 已统一 |
| 0.3 修复面试 fail 分支 | ✅ | `status='rejected'` + `statusLabel='已淘汰'` + `_release_candidate_lock()` |
| 0.4 Alembic 初始迁移 | ✅ | `migrations/versions/fb166b2c1092_initial.py` 已生成，27 张表 |
| 0.5 认证中间件收紧 | ✅ | 移除 DEBUG 绕过；真实 JWT；Bearer scheme 验证；JWT 过期 3600s |

## 阶段 2：自动化管道 + 配置落库 ✅

| 任务 | 状态 | 说明 |
|------|:--:|------|
| 2.1 配置服务 DB 化 | ✅ | 9 个函数全面改写：邮箱/渠道/评分规则/通知模板全部 DB-first |
| 2.2 配置 API CRUD | ✅ | 新增 PUT/DELETE 邮箱、PUT 渠道、PUT 评分、POST/PUT 通知模板 |
| 2.3 Celery 邮件同步 | ✅ | `email_sync.py` 查询 RecruitMailAccount → 逐账号处理 → 返回汇总 |
| 2.4 Celery 批量匹配 | ✅ | `match_batch.py` 调用 `match_service.batch_match_demand()` → 真实打分 |
| 2.5 Celery 飞书通知 | ✅ | `notify.py` 查 InterviewBook → 解析候选人/面试官 → 调 feishu_client |
| 2.6 逾期检查任务 | ✅ | 新增 `check_overdue()`: 3 天未评价 → 自动飞书催办，每小时 Beat |
| 2.7 Beat 调度 | ✅ | 邮件 30min + 逾期检查 1h 启用
| 2.8 审批→Celery 匹配串联 | ✅ | `approval_service._fire_match_batch` → `batch_match_demand.delay()` 真实入队
| 2.9 后端测试 | ✅ | 14 个 pytest 用例：auth(5) + demand(6) + offer(3) |
| 2.10 路由守卫 | ✅ | `router/index.js` beforeEach: 无 token→/login, no_recruit→/login

## 对抗性审查 ✅

| 发现 | 严重性 | 状态 |
|------|:--:|:--:|
| int() 无验证 → safe_int() | 高 | ✅ 已添加 `app/utils/response.py:safe_int()` |
| auth.get_me 在白名单中 | 中 | ✅ 已移除 |
| Authorization Bearer 未验证 scheme | 中 | ✅ 已添加 startswith('Bearer ') |
| app.py debug=True 硬编码 | 中 | ✅ 改为条件启用 |
| health.py 泄露密钥格式 | 中 | ✅ 移除 `'sk-' in` 检查 |
| CORS 默认 * | 中 | ⚠️ 已有 .env 默认值 |
| 用户输入反射 | 低 | ⚠️ 前端 JSON API 不渲染 HTML |
| JWT 生命周期 24h | 低 | ✅ 已改为 3600s |

## 核心修复统计

| 类型 | 数量 | 说明 |
|------|:--:|------|
| 密钥硬编码 | 3 处 | 全部仅从 env 读取 |
| DEBUG 认证绕过 | 1 处 | 移除自动 admin |
| 面试 fail 分支 | 1 处 | rejected/已淘汰 |
| 审批全内存 | 1 个服务 | 重写为 DB-only |
| Mock 开关统一 | 5 个 service | `should_mock_fallback()` |
| 新增文件 | 8 个 | hire.py API + hire_service.py + 2 弹窗组件 + 4 测试文件 |
| 新增端点 | 12+ | /api/hire/*(6) + config CRUD(5) + approve 支持 demand_no |
| 安全修复 | 8 处 | Bearer scheme / me 白名单 / debug / 密钥格式 / int 验证 / lark-cli移除 / token缓存 / 路由守卫 |
| 飞书重写 | 8 API | subprocess → requests HTTP 直连，零外部依赖 |
| 路由守卫 | 1 套 | beforeEach token + 7角色页面映射 |

## 启动 🚀

```bash
# Backend
cd backend/ && python scripts/seed.py && python app.py  # :5000
cd backend/ && python -m pytest tests/ -v                # 37/37

# Frontend
cd frontend/ && npm run dev                              # :5173
cd frontend/ && npx playwright test --workers=2          # 49/49 E2E
```

## 2026-07-22 凌晨批次 ✅

| 事项 | 说明 |
|------|------|
| 502 抖动修复 | 根因=debug reloader 重启窗口；前端 api/ai.js + useStreaming.js 加 3s/6s 长退避重试，api/index.js 增 `silent` 选项（AI 工作流不再刷全局错误 toast），502 提示按路径区分 boss-cli |
| Offer 草稿陷阱修复 | `interview_service.send_offer` 现在真正完成 draft→sent（此前只建草稿导致重复发送被 DUPLICATE_OFFER 堵死）；已有草稿自动复用，已发送则明确报错；新增 tests/test_interview_offer.py 4 用例 |
| README 重写 | 8 张真实数据 UI 截图（docs/screenshots/，1440×900@1.5x）+ 全链路功能/结构/启动/环境变量文档；截图脚本 `frontend/scripts/capture-readme-shots.mjs` |
| 待办 | 工作区仍有另一会话未提交改动（腾讯会议/邮件同步）；建议后端统一用 .venv 启动、reloader 做成环境变量开关 |
