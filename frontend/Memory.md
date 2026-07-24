# Memory.md - 项目进度追踪

> 记录当前做到了哪、刚刚确认了什么、下次从哪里继续。

## 当前状态

- 日期：2026-07-24
- 项目阶段：新环境部署完成 + MySQL RDS 连接 + 审批权限体系验证
- 后端：Flask + MySQL RDS（hr_recruitment_db）+ Redis + Windows环境
- 数据库：阿里云 RDS，34 张表，4 个 IAM 用户

## 2026-07-24 新环境部署与验证

### 已完成

1. **环境配置**
   - MySQL RDS：`rm-8vb7m858r8wt3b10hjo.mysql.zhangbei.rds.aliyuncs.com:3306`
   - 数据库：`hr_recruitment_db`，用户 `hr_recruitment`
   - SSL 未启用，明文传输
   - 新建 `uploads/` 目录解决简历文件存储

2. **审批权限体系验证**
   - 三级审批：部门负责人(dept_head) → HR(hr) → 高管(executive)
   - 身份校验生效：`_assert_can_approve()` 检查 role_code 匹配
   - 审批身份均未绑定具体用户（user_id=None），同角色任何人都能批
   - admin 可代批所有级别
   - dept_head 只能批 level 1，"编辑"按钮在审批中状态正确禁用

3. **通知模板调研**
   - `t_hr_notify_template` 表有 5 条模板（面试/Offer/拒信/提醒/通用）
   - 发邮件未读 DB 模板，走 `confirm_service.py` 硬编码 HTML
   - 模板引擎已实现后撤回（用户要求保持原始代码）

4. **创建的 JD / AI 功能**（上游已有）
   - 创建需求表单内嵌「AI 生成岗位说明」→ 调 `POST /api/ai/run/jd-generate`
   - 需求详情页候选人抽屉调 `GET /api/demand/{id}/candidates/{name}/detail` 获取匹配详情
   - `calc_match_score` 仍为 MD5 哈希假匹配

### 已知问题

1. **简历原件无法预览**：MySQL 存的是旧机器磁盘路径，新机器无对应文件。需同步旧 `uploads/` 或通过刷新邮箱重新下载
2. **邮件通知模板未接入**：模板引擎代码已撤回，发邮件仍用硬编码
3. **招聘需求→人才库全池匹配**：新需求无已关联候选人时，「重新匹配」返回空

### 下次继续点

1. 简历原件问题 — 拷旧机器 uploads/ 或接受不可预览
2. 匹配算法替换为真实 AI 语义匹配
3. 候选人确认 H5 页面（/confirm/:token 路由）
- 后端：Flask + SQLite，6 端点全部通过真实 JWT + curl 验证
- API 契约：`success_list()` → `{data:[...], total:N, page:N, pageSize:N}`（前端可直接遍历 data）

## 2026-07-19 需求详情-候选人操作-全链路打通 + 对抗性审查 + 前后端数据流端到端验证

### 已完成

1. **Bug #1：页面级工作台可见性** — 已修复
   - `public/js/app.js`：`ensureHeroOperationalWorkspace()` 注入元素从 `scroll-reveal`→`is-revealed`（立即可见）
   - `enhanceScrollReveal()` 回退超时从 180ms→30ms

2. **Bug #2：需求详情-"联系"/"约面"按钮孤立弹窗** — 已打通完整链路
   - 新建 `src/components/CommunicationModal.vue`：渠道选择 + 目的 + DeepSeek API 话术草稿 + 复制 + 联系记录
   - `RecruitDemandDetail.vue`：模板渲染替代 `v-html`，直接打开模态框
   - 批量操作也接入了模态框

3. **对抗性审查** — 3 子 agent 并行审计，发现 12 个问题
   - `batchMoveDemand` 仅处理 1/N → 已修复为全量循环
   - `copyDraft` fallback 移除虚假 success 提示
   - `isValid` 增加 mode 必填校验
   - `CommunicationModal` 竞态守卫（seq 计数器）
   - 关键词筛选增加 skills 搜索 + null guard

4. **关键数据流修复** — `backend/app/utils/response.py` 重新部署后 `success_list()` 直接返回 `{data:[...], total:N}`
   - 前端 `res.data` 即为数组，`.filter()` 可直接遍历
   - 6 个后端端点全部通过 JWT + curl 真实数据验证

5. **30+ 组 E2E 测试** — 全部覆盖并通过：
   CommunicationModal 打开/关闭、ScheduleInterviewModal 打开/关闭、批量操作模态框集成、渠道选择/目的切换/AI 草稿、AI 免责声明/禁用文案、低匹配分/面试中状态、Escape/overlay/X 按钮关闭、批次栏显隐(0/1/N)、全选/取消全选、8 筛选器、大小写不敏感关键词、顺序模态框、筛选源验证、桌面溢出防护、候选人抽屉信息、需求信息卡/审批节点/状态徽章、批次栏 7 按钮、已勾选计数

### 当前验证基线

- `npm run build`：通过，~425ms
- `npm test`：**45/45** 连续 3 轮通过，0 flaky
- 后端 6 端点：全部 JWT + curl 验证通过
- Console error：0（仅预期的 mock fallback 警告）
- 移动端横向溢出：0
- 禁用文案：0
| **关键 Bug** | `base.py` `func.now()` 冻结修复 → `server_default` + `default=datetime.now` |
| **数据库级分页** | `talent_service.py`、`demand_service.py`、`interview_service.py` — `.all()` → `.offset().limit()` + SQL 级过滤 |
| **前端桩操作消除** | RecruitDemand: `saveDraft`/`approveDemand`/`moreOps` → 真实 API；RecruitInterview: `handleCancel` → 调用 `cancelInterview`；RecruitDemandDetail: `batchExport` → CSV 导出 |
| **前端内部标 tab 过滤器** | RecruitTalent: 内部员工 tab 的搜索/部门/排序控件添加 `v-model` |
| **前端 API 模块** | `src/api/interview.js` 扩展为 10 个完整 CRUD 函数；`src/api/demand.js` 新增审批/驳回接口 |
| **3D 漏斗性能** | MeshStandardMaterial 替代 PhysicalMaterial + 不可见时暂停 rAF + 移除 backdrop-filter |
| **配置页全栈打通** | 6 个模块完整 CRUD + 审计日志自动写入 |

### 审计发现 (Phase 1)

- **前端 16+ 个桩操作** — 已修复 12 个，剩余 4 个低优先级（上传简历、黑名单按钮、记住密码、忘记密码）
- **后端关键 Bug**: `func.now()` 冻结所有行的时间戳在同一秒 — 已修复
- **后端无分页**: talent/demand/interview 全量加载到内存 — 已修复
- **后端无输入验证**: 所有 POST/PUT 端点缺少 `@require_fields()` — 待评估
- **后端 N+1 查询**: 多个服务存在 — 待优化
- **音频 fallback 模式**: `MOCK_FALLBACK=false` 时正常，但无索引支持

### 下次继续点

1. 添加数据库索引（高并发过滤器字段）
2. 应用 `@transactional` 装饰器到多步写入操作
3. 使用 `@require_fields()` 装饰器到所有 POST/PUT 端点
4. 添加时区感知 (`datetime.now(timezone.utc)`)
5. 消除剩余 N+1 查询（Candidate._get_skills_for, InterviewBook._db_to_dict）
- 技术栈：Vue 3 + Vite + Vue Router + Playwright
- Git 当前最新提交：以 `git log -1 --oneline` 为准。

## 已完成

1. 本地 Git 已建立，并提交了关键阶段：
   - `d195f06 chore: baseline static frontend`
   - `26c0315 feat: migrate frontend to vue shell`
   - `5aa755c feat: redesign dynamic login page`
   - `8caca43 feat: add owl watcher login interaction`
   - `774d541 feat: integrate enterprise eye-following login`
   - `d95d91d feat: create original enterprise radar login`
2. 前端已迁移到 Vue shell：
   - Vite 构建可用。
   - Vue Router 承载 legacy 页面路由。
   - 旧页面仍保留在 `public/legacy/`。
3. 登录页已完成原创企业级改造：
   - 方向：人才雷达中控台。
   - 保留鼠标跟随交互。
   - 不复制参考 GitHub 仓库的动物、角色、布局、文案、源码或素材。
   - 增加 skip link、focus-visible、`prefers-reduced-motion`。
4. 自动化测试已建立：
   - `npm test` 当前 13/13 通过。
   - 覆盖登录、路由、侧边栏、抽屉、弹窗、标签页和配置交互。
5. 知识图谱索引已运行：
   - 项目名：`D-WorkProject-HrProject-hr-web-frontend`
   - 注意：索引工具排除了 `public/`，所以 legacy 静态页仍需直接文件检查。

## 刚确认的事情

- 用户明确要求登录页必须是“专属于本项目”的原创设计，避免版权风险。
- 参考仓库只能作为交互原理参考，不能复刻视觉。
- 前端功能梳理文档已作为本轮功能补齐依据。
- “AI 外呼联系”不符合实际业务，已统一改为 AI 辅助联系话术；实际联系方式保留电话、邮件、飞书和内部直属上级沟通。
- 登录页需要覆盖 6 类角色，并额外支持临时面试官演示。
- 看板、需求、人才库、面试、AI 中心都需要能被实际操作和 E2E 覆盖。
- 当前四个长期文档需要补齐并长期维护：
  - `Claude.md`
  - `Memory.md`
  - `Learning.md`
  - `Wiki.md`
- 用户要求每三轮修改更新一次长期追踪文档。

## 当前验证基线

最近一次验证：

- `npm run build`：通过
- `npm test`：通过，20/20
- 桌面截图：`test-results/login-original-radar-final.png`
- 移动截图：`test-results/login-original-radar-mobile.png`
- 本轮全流程截图：
  - `test-results/full-flow-login.png`
  - `test-results/full-flow-demand.png`
  - `test-results/full-flow-demand-detail.png`
  - `test-results/full-flow-talent.png`
  - `test-results/full-flow-interview.png`
  - `test-results/full-flow-ai.png`

## 未跟踪文件说明

当前曾出现以下未跟踪文件：

- `Claude.md`
- `Learning.md`
- `Memory.md`
- `README.md`
- `Wiki.md`

本轮用户明确要求维护四个文档，因此应纳入 Git：

- `Claude.md`
- `Learning.md`
- `Memory.md`
- `Wiki.md`

`README.md` 不是本轮要求，除非用户后续指定，否则不纳入提交。

## 三轮修改计数器

从本文件更新后重新计数：

| 计数 | 内容 | 状态 |
|---|---|---|
| 1/3 | 补齐四个长期文档 | 已完成 |
| 2/3 | 按《前端功能梳理.md》补齐前端功能并去除 AI 外呼口径 | 当前轮 |
| 3/3 | 待记录并触发文档更新 | 未开始 |

触发规则：每完成 3 轮实际修改，更新本文件；如产生复盘或稳定知识，同步更新 `Learning.md` / `Wiki.md` / `Claude.md`。

## 下次继续

优先建议：

1. 将业务页面中的 emoji 或非正式符号逐步替换为企业级图标或文本按钮。
2. 把 `public/legacy/` 中最稳定的页面拆成 Vue 组件，而不是一次性全量重写。
3. 增加登录页可访问性测试或视觉回归截图流程。
4. 梳理 `README.md` 是否需要作为项目对外说明纳入 Git。

## 2026-07-17 UI v6 改造记录

- 使用 `DESIGN.md` 作为“千亿级管理后台”项目设计 skill，落地方向为 Enterprise Command Center：高密度、低噪音、表格优先、可审计、可恢复。
- 已完成全局 `Enterprise UI v6` token 覆盖层：颜色、字号、表格、筛选、KPI、按钮、抽屉、弹窗、命令面板、响应式规则统一收敛。
- 登录页保留原创鼠标跟随双镜片/雷达交互，同时降低 demo 感，改为更克制的企业入口。
- 全局新增 `Ctrl/Cmd + K` 快速跳转命令面板，并新增 Playwright 键盘路径测试。
- `/recruit-ai` 已改名为 `/recruit-ai` 路由下的“招聘辅助中心”，不再使用“AI 智能自动化中心”口径；Offer、入职包、候选人联系均明确为草稿/辅助/人工确认。
- `LegacyPage.vue` 补齐加载状态、错误状态和重试入口，避免 legacy 页面加载失败时白屏。
- 移动端巡检发现看板页表格横向溢出，已通过移动端卡片内部滚动修复。

当前验证基线：
- `npm run build`：通过
- `npm test`：通过，21/21
- 最终视觉巡检：16 张桌面/移动截图，0 console error，0 移动端横向溢出
- 巡检报告：`test-results/ui-v6-console-report.json`

下一步建议：
1. 继续把 `public/legacy/` 中最稳定的页面拆为 Vue 组件。
2. 逐步清理动态 JS 中剩余的非正式符号和旧文案。
3. 增加视觉回归基线，避免后续 UI token 调整造成页面错位。

## 2026-07-17 Phase 1 任务固化与 Token 补齐

- 已新增 `任务.md`，将 Phase 0 到 Phase 6 的 UI 优化改造步骤固化为长期任务清单。
- 当前按用户要求完成“第二节阶段”，即 Phase 1：设计 Token 层。
- 本次代码补齐：`LegacyPage.vue` 的 loading/error/retry 状态改为复用 `--e-*` token；`style.css` 增加 `--e-on-primary`。
- 阶段验证：
  - `npm run build`：通过
  - `npm test`：通过，21/21
  - Phase 1 截图巡检：16 张
  - Console error：0
  - 移动端横向溢出：0
  - 报告：`test-results/phase1-token-console-report.json`

下一步进入 Phase 2：全局框架重构复审，重点检查侧边栏、顶部栏、页面标题区、命令入口和工作台布局是否还有遗留不一致。

## 2026-07-17 Phase 2 全局框架重构复审与深化

- 已完成 Phase 2：全局框架重构复审与深化。
- 本次代码补齐：
  - `public/js/app.js` 新增企业工作台外壳增强：路由标记、主内容语义、侧栏导航语义、当前页状态。
  - 顶栏控件统一归入 `topbar-actions`，命令入口支持按钮点击和 `Ctrl/Cmd + K`。
  - 命令面板按当前角色权限过滤可访问页面。
  - `LegacyPage.vue` 在 legacy 页面脚本执行后重新安装外壳，解决旧页面注入时序问题。
  - `style.css` 优化顶栏动作区、移动端横向导航、内容区容器边界和窄屏防溢出。
  - `tests/legacy-flow.spec.js` 新增全局工作台外壳回归测试。
- 阶段验证：
  - `npm run build`：通过
  - `npm test`：通过，22/22
  - Phase 2 截图巡检：16 张
  - Console error：0
  - 移动端横向溢出：0
  - 外壳缺项：0
  - 报告：`test-results/phase2-shell-console-report.json`

下一步进入 Phase 3：核心组件升级，重点检查表格、筛选区、KPI、弹窗/抽屉和空/加载/错误状态是否一致。

## 2026-07-17 Phase 3 核心组件升级

- 用户指出多个页面 UI 仍偏 low，不符合千亿级数据可视化后台要求。
- 已确认长期组件参考源：`D:\WorkProject\HeroUIPro\herouipro-v3`。以后任何项目做后台组件、数据可视化、表格、KPI、筛选、弹窗、抽屉时，先查看该目录；有合适组件可以复制或改写。
- 本轮已索引并参考 HeroUIPro：
  - `template/template-dashboard/src/widgets/employees-table.tsx`
  - `template/template-dashboard/src/widgets/kpi-row.tsx`
  - `template/template-dashboard/src/widgets/analytics-kpi-row.tsx`
  - `template/template-dashboard/src/widgets/sessions-over-time-card.tsx`
- 已完成 Phase 3 第一版核心组件升级：
  - `public/js/app.js` 新增核心组件增强器，覆盖 KPI、筛选栏、表格、批量条、弹窗/抽屉、空态、数据可视化卡。
  - `public/css/style.css` 补齐表格密度、排序、固定首列、筛选查询条、KPI 口径、批量条、dialog focus、漏斗/图表卡专业化样式。
  - `tests/legacy-flow.spec.js` 新增核心组件回归测试。
- 阶段验证：
  - `npm run build`：通过
  - `npm test`：通过，23/23
  - Phase 3 截图巡检：16 张
  - Console error：0
  - 移动端横向溢出：0
  - 核心组件缺项：0
  - 报告：`test-results/phase3-components-console-report.json`

下一步建议：继续 Phase 3 第二轮，重点把招聘看板、需求详情、人才库的具体数据可视化区域进一步 HeroUIPro 化，而不是只依赖全局增强器。

## 2026-07-17 Phase 3 二次深化：HeroUIPro 浅玻璃工作台

- 用户补充 7 张参考图，方向为浅色玻璃后台、左侧导航、顶部业务胶囊导航、KPI 卡片、趋势图、环形图、表格和轻量弹窗。
- 本轮已将该方向落到当前项目，而不是照抄参考图：
  - `public/css/style.css` 新增 `HeroUIPro glass-light page-level refinement` 覆盖层。
  - `public/js/app.js` 新增页面级模块 tabs、招聘看板趋势图/候选人分布图、非看板页关键指标摘要。
  - `tests/legacy-flow.spec.js` 修正导航测试定位，避免新增顶部模块导航后出现同名链接歧义。
- 当前视觉基线：浅色企业工作台 + 暗色 active 胶囊 + 高密 KPI + 数据图表 + 玻璃表格/弹窗。
- 本轮验证：
  - `npm run build`：通过
  - `npm test`：通过，23/23
  - Playwright 截图巡检：16 张桌面/移动截图
  - Console error：0
  - 移动端页面级横向溢出：0
  - “AI 外呼/外呼/自动拨打”页面文案：0
  - 巡检报告：`test-results/phase3-heropro-final-console-report.json`

下一步：进入 Phase 4 时逐页做业务级重构，不再只靠全局增强；优先改需求详情、人才库、面试计划这类业务对象页。

## 2026-07-18 看板总控页与折叠栏修复

- 用户反馈折叠栏不能正常折叠，并明确要求禁止渐变色、拒绝 AI 味。
- 已定位折叠栏根因：Vue shell 注入 legacy body 时不会保留页面 `<head><style>`，导致 `recruit-dashboard.html` 内联的 `.collapse-body{display:none}` 没有生效。
- 已修复：
  - `public/js/app.js` 新增 `enhanceCollapses()`，统一增强 `.collapse-toggle` 和 `.accordion-header`，补齐 `aria-expanded`、键盘 Enter/Space 操作，并移除脆弱的内联 onclick 依赖。
  - `public/css/style.css` 新增全局 `.collapse-toggle + .collapse-body` 显隐规则，确保折叠状态和可见性一致。
  - 招聘看板新增总控区域：招聘项目总览、阶段转化、待处理事项、岗位风险、渠道效率、近期面试，并联动到需求、人才库、面试、需求详情、招聘辅助中心。
  - 全项目 `public/src` 已清除 `gradient` 用法；登录页和 loading skeleton 改为纯色/细线。
  - 去掉“智能风险预警”等 AI 味文案，改为“招聘风险预警”。
- 本轮验证：
  - `npm run build`：通过
  - `npm test`：通过，25/25
  - Playwright 截图巡检：16 张桌面/移动截图
  - Console error：0
  - 禁用文案：0
  - 移动端页面级横向溢出：0
  - 巡检报告：`test-results/phase-dashboard-command-final-report.json`

下一步：Phase 4 中优先把旧“招聘全漏斗”明细区组件化，移动端避免下方漏斗文字挤压。
## 2026-07-18 招聘看板材质化经营台深化

- 用户再次反馈招聘看板不够美观，要求参考官网级产品表达、HeroUIPro 模板和 Kinetic Typography / Glassmorphism 2.0 / Micro-Delight 等趋势，但仍保留此前“禁止渐变、拒绝 AI 味”的项目约束。
- 已调研并参考：
  - Apple Vision Pro 官网：滚动叙事和空间层次只抽象为节奏感，不把后台做成营销页。
  - Stripe Dashboard 文档：后台核心应服务搜索、资源管理、团队协作和监控。
  - Supabase 官网：企业级表达要清晰、克制、可规模化。
  - Muzli 2026 Dashboard：玻璃态和软光感必须转译为业务材质层，而不是装饰光效。
- 已检查 `D:\WorkProject\HeroUIPro\herouipro-v3\template`，重点参考 `template-dashboard` / `template-finances` 的 toolbar、KPI strip、主图表、侧栏判断卡和数据表组织方式。当前项目是 Vue shell + legacy HTML，不直接搬 React 组件，改为复用组件结构与信息密度。
- 本轮落地：
  - `public/js/app.js` 新增 `ensureHeroDashboardMaterial()`，为招聘看板增加经营工具条、经营信号卡、瓶颈地图、负责人负载、下一动作队列。
  - 旧的“招聘全漏斗 / 部门招聘进度 / 渠道效果统计”被标记为 `明细层`，主视觉让位给经营判断台。
  - 新增 `enhanceKineticTypography()` 与 `enhanceScrollReveal()`，支持轻量动态标题和滚动/入场显现；同时设置自动落稳，避免截图或低速设备出现内容长期透明。
  - `public/css/style.css` 增加无渐变材质系统：半透明表面、12px blur、细边框、focus ring、hover scale、click scale、图表色板与响应式布局。
  - `tests/legacy-flow.spec.js` 扩展招聘看板断言，覆盖 toolbar、signal grid、瓶颈地图、负责人负载、下一动作队列。
- 验证结果：
  - `node --check public/js/app.js`：通过
  - `npm run build`：通过
  - `npm test`：通过，25/25
  - Playwright 巡检：16 个桌面/移动端页面，0 console error，0 page error，0 禁用词，0 横向溢出
  - 巡检报告：`test-results/phase-dashboard-material-final-report.json`
  - 关键截图：`test-results/phase-dashboard-material-dashboard-desktop.png`、`test-results/phase-dashboard-material-dashboard-mobile.png`

下一步建议：进入 Phase 4 页面逐个业务重构时，优先处理需求详情、人才库、面试计划，把当前招聘看板的“经营判断台”信息架构扩展到对象详情页和高密度列表页。
## 2026-07-18 Phase 4 其他主页面工作台化

- 用户要求“其他页面也按照要求进行优化一遍”，本轮将招聘看板的经营台方法扩展到除登录页以外的主要业务页。
- 设计锚点继续采用 Swiss 企业工作台：白色/浅灰表面、细线、清晰网格、蓝色操作色、无渐变、无 AI 味。
- 已完成页面级增强器：
  - 需求管理：需求流转工作台，突出审批队列、招聘中岗位、风险岗位和协作动作。
  - 需求详情：岗位对象详情台，突出候选人推进路径、批量动作、待评价和审计关注。
  - 人才库：人才资产工作台，突出外部候选人、内部人才、黑名单、标签覆盖和人工联系动作。
  - 面试计划：面试任务流工作台，突出待安排、待评价、待入职、面试官负载和日程动作。
  - 招聘辅助中心：候选人沟通辅助工作台，只保留摘要、话术草稿、字段提醒、效率分析和人工确认口径。
  - 招聘基础配置：配置影响工作台，突出邮箱、渠道、流程、模板的影响范围和审计风险。
- 实现位置：
  - `public/js/app.js` 新增 `ensureHeroOperationalWorkspace()`。
  - `public/css/style.css` 新增 `.hero-page-command`、`.hero-page-workspace` 等页面级工作台样式。
  - `tests/legacy-flow.spec.js` 新增非看板页面工作台回归测试。
- 验证结果：
  - `node --check public/js/app.js`：通过
  - `npm run build`：通过
  - `npm test`：通过，26/26
  - Playwright 巡检：16 个桌面/移动端页面，0 console error，0 page error，0 禁用词，0 横向溢出，0 未显现内容
  - 巡检报告：`test-results/phase4-operational-pages-report.json`
  - 关键截图：`test-results/phase4-operational-demand-desktop.png`、`test-results/phase4-operational-talent-desktop.png`、`test-results/phase4-operational-interview-desktop.png`、`test-results/phase4-operational-config-mobile.png`

下一步建议：继续 Phase 4 深化时，不再只加顶部判断层，而是逐页把 legacy 表格和表单拆成更稳定的 Vue 组件，优先顺序为需求详情、人才库、面试计划。

## 2026-07-18 Phase 5 交互精化与质感深化

- 从 HeroUIPro v3 (`D:\WorkProject\HeroUIPro\herouipro-v3`) 67 个专业组件中提取 CSS 模式和交互结构，不再从零造轮子。
- 本轮落地：
  - **命令面板 2.0** (`app.js` `renderPalette()`)：箭头导航 + `data-selected` 选中态、动作命令（刷新/清空筛选/切换密度）、最近使用历史（`localStorage.hr_palette_history`，最多 5 条）、分组渲染（最近使用/页面/动作）、Escape 后焦点返回 trigger。
  - **表格交互升级** (`app.js` `enhanceTables()`)：密度按钮 `aria-pressed`、列显隐下拉 + checkbox + `localStorage.hr_cols_*` 持久化、排序状态保存/恢复 + `restoreTableSortState()` + `wrapRenderFunctions()`。
  - **统一状态层** (`app.js` `window.showState()`)：empty/loading/error 三态，skeleton shimmer 动画（`@keyframes shimmer`）。
  - **移动端底部导航 + 汉堡菜单** (`app.js` `enhanceMobileShell()`)：4 入口 bottom nav、汉堡菜单 + 全屏遮罩 + 侧栏面板、纯 CSS 汉堡 → X 动画。
- **CSS 新增** (`style.css`)：
  - HeroUIPro 模式提取：命令面板 `.command__group-heading` / `.command-result[data-selected]` / `.command-empty`、空态 `.state-overlay` / `.skeleton-line` shimmer、移动端 `.mobile-nav-bar` / `.mobile-menu-toggle` / `.mobile-menu-overlay` / `.mobile-menu-panel`。
  - 列显隐 `.col-hidden` / `.table-column-toggle` / `.table-column-menu`。
  - 密度按钮 `[aria-pressed="true"]`。
  - `prefers-reduced-motion` 覆盖新增动效。
- **测试追加**：8 个新 Playwright 用例（密度 aria-pressed、列显隐切换、排序持久化、命令面板箭头/历史、移动端底部导航/汉堡菜单、禁用外呼文案）。
- 验证结果：
  - `npm run build`：通过
  - `npm test`：通过，**33/33**
  - `node --check public/js/app.js`：通过

下一步：进入 Phase 6 时逐页把 legacy 表格/表单拆成 Vue 组件。

## 2026-07-18 Phase 6A 基础设施 + Phase 6B 招聘基础配置迁移

- **Phase 6A — 基础设施**：
  - 新建 `src/router/index.js`：从 `main.js` 拆分路由。
  - 新建 `src/composables/useAuth.js`：从 `app.js` 提取 `getRole()` / `getUser()` / `getVisibleMenus()` / `MENU_ROUTES` / `ROLE_MENUS` / 角色标签/样式。
  - 新建 `src/composables/useMockData.js`：从 `app.js` 提取 `mockCandidate()` / `mockEmployee()` / 评分计算 / 部门选项 / `renderDepartmentOptions()`。
  - 新建 `src/layouts/WorkbenchLayout.vue`：侧边栏 + 顶栏 + 命令入口 + `<slot>`，完全 Vue 响应式渲染。
  - 新建 `src/components/BaseAccordion.vue`（手风琴）、`BaseModal.vue`（Teleport 弹窗）、`StatusBadge.vue`（状态标签）。
  - 修改 `src/main.js`：导入 router + 全局注册 BaseAccordion / BaseModal / StatusBadge。
  - 目录结构建立：`src/composables/` / `src/layouts/` / `src/components/` / `src/views/` / `src/data/`。
- **Phase 6B — 招聘基础配置 Vue 化**：
  - 新建 `src/data/config.js`：从 `recruit-config.html` 提取 6 组 mock 数据（邮箱/渠道/打分/通知/角色/日志）+ 邮箱预设。
  - 新建 `src/views/RecruitConfig.vue`：完整 Vue SFC，6 个 BaseAccordion + 1 个添加邮箱 modal。
  - 修改 `src/router/index.js`：新增 `/recruit-config` → `RecruitConfig`，移除 legacy 路由。
  - 修改 `tests/legacy-flow.spec.js`：适配 Vue 版 modal 选择器（`#emailModal` → `.modal-overlay.open`）。
- 验证：
  - `npm run build`：通过
  - `npm test`：通过，**33/33**

下一步：Phase 6C — 招聘辅助中心 (`recruit-ai`) Vue 化。

## 2026-07-18 Phase 6C 招聘辅助中心 Vue 化

- 新建 `src/data/ai.js`：从 `recruit-ai.html` 提取 6 个 tab 面板数据 + 9 条嵌入式 AI 能力 + API 架构说明。
- 新建 `src/views/RecruitAI.vue`：完整 Vue SFC，使用 WorkbenchLayout + StatusBadge，6 tab 切换（按钮 + aria role="tab"）+ 嵌入式能力表格。
- 修改 `src/router/index.js`：新增 `/recruit-ai` → `RecruitAI`，移除 legacy 路由。
- 修改 `tests/legacy-flow.spec.js`：适配 Vue 版 tab 选择器（`#aiTabs .tab[data-tab]` → `.tabs button.tab`）。
- 验证：
  - `npm run build`：通过
  - `npm test`：通过，**33/33**

下一步：Phase 6G — 需求详情 (`recruit-demand-detail`) Vue 化（8 筛选器 + 批量栏 + 候选人抽屉 + 约面弹窗）。

## 2026-07-18 Phase 6E 面试计划 Vue 化

- 新建 `src/data/interview.js`：从 `recruit-interview.html` 提取 19 条 mock 面试记录 + 7 种状态 + 6 条提醒数据。
- 新建 `src/views/RecruitInterview.vue`：6 KPI + 2 tab（全部面试/我的待办）+ 状态芯片筛选 + 双表格 + 提醒下拉 + 日历视图弹窗 + 角色权限感知（面试官角色隐藏全部面试 tab 和新建面试按钮）。
- 修改 `src/router/index.js`：新增 `/recruit-interview` → `RecruitInterview`，移除 legacy 路由。
- 验证：
  - `npm run build`：通过
  - `npm test`：通过，**33/33**

## 2026-07-18 Phase 6F 人才库 Vue 化

- 新建 `src/data/talent.js`：从 `recruit-talent.html` 提取 6 条外部候选人 + 3 条内部员工 + 2 条黑名单 + 需求选项 + 匹配结果。
- 新建 `src/views/RecruitTalent.vue`：3 tab（简历储备库/内部员工库/黑名单）+ 外部 8 筛选器 + 双批量栏 + 备注弹窗 + 内部匹配弹窗 + 提醒下拉。
- 修复：`v-html` 模板表达式转函数 `wrapSkills()` 避免 Vue 编译器报错。
- 验证：
  - `npm run build`：通过
  - `npm test`：通过，**33/33**

## 2026-07-18 Phase 6G 需求详情 Vue 化

- 新建 `src/data/demand-detail.js`：从 `recruit-demand-detail.html` 提取需求信息 + 15 条候选人 + 学历/年限 meta + 审批记录。
- 新建 `src/views/RecruitDemandDetail.vue`：需求信息卡片 + 审批记录 + 候选人 8 筛选器 + 7 按钮批量栏。
- 修改 `src/router/index.js`：新增 `/recruit-demand-detail` → `RecruitDemandDetail`，移除 legacy 路由。
- 测试适配：`#candidateDrawer` / `#globalScheduleModal` legacy DOM 选择器 → `window.alert` dialog 监听。
- 验证：
  - `npm run build`：通过
  - `npm test`：通过，**33/33**

## 2026-07-18 Phase 6J 后续：Bug 修复 + 看板 3D 升级

### Bug 修复：配置页折叠栏打不开
- **根因**：`app.js` 的 `enhanceCollapses()` 中 `.accordion-header` 增强器与 `BaseAccordion.vue` 的 `@click="toggle"` 同时触发——app.js 切换 `.open` class，Vue 切换 `isOpen` ref，两者都翻转等于双击。
- **修复**：`BaseAccordion.vue` 的 header 加 `data-accordion-enhanced="true"`，app.js 跳过已增强元素。看板 `.collapse-toggle` 已带 `data-collapse-enhanced="true"`，不受影响。

### 看板 3D 升级（保持现有 4 组件不变）
- **KPI 卡片**：`perspective(600px)` + `mousemove` 实时 3D 倾斜（rotateX/Y），顶部 accent 条显现，icon 微缩放，hover 浮现趋势文字，卡片浮起阴影。
- **漏斗**：每步带入场动画（stagger delay），hover 时 `translateZ(12px)` 浮起，步间转化率标注（▽ 25.7% 等），标题栏新增总转化率。
- **CSS**：纯 CSS 3D transform + perspective，无 gradient，`prefers-reduced-motion` 全量覆盖。

### 验证
- `npm run build`：通过
- `npm test`：通过，**33/33**
- 截图：`test-results/dashboard-3d-desktop.png`、`test-results/config-accordion-fix.png`、`test-results/config-bottom.png`

## 当前状态

- 日期：2026-07-18
- 所有 9 个页面已 Vue 3 SFC 化，路由为纯 Vue Router
- Legacy shell（`LegacyPage.vue` + `public/legacy/`）已完全移除
- `app.js` enhancers 保留作为通用组件增强层（命令面板、表格排序/密度/列显隐、折叠面板、移动端壳）
- `style.css` 保留作为全局 CSS 系统
- 看板已 3D 化（KPI 透视倾斜 + 漏斗浮起入场）
- **DeepSeek API 已集成**：5 个 AI 工作流后端端点上线（JD 生成/简历搜索/人岗匹配/面试问题/沟通话术/分析报告）
- **boss-cli 已集成**：后端 subprocess 封装 + 10 个 REST 端点（岗位同步/候选搜索/聊天/打招呼/简历预览）
- **RecruitAI 页面已激活**：6 个 tab 从静态占位符变为全功能交互式工作台，不再标记"二期开发中"
- **BossIntegration.vue**：BOSS 直聘集成组件已嵌入 RecruitAI 页面
- 基础已打牢，进入接口对接 + AI 功能激活阶段

## 2026-07-18 Phase 7：后端 AI 引擎升级 + boss-cli 集成 + RecruitAI 激活

### DeepSeek API 集成

- 新建 `backend/app/services/deepseek_client.py`：基于 OpenAI SDK 的 DeepSeek 客户端，兼容 `/chat/completions`，支持 `chat_completion()` 和 `chat_completion_json()`，3 次指数退避重试（2s/4s/8s），全量日志。
- 更新 `backend/config.py`：新增 `DEEPSEEK_API_KEY` / `DEEPSEEK_BASE_URL` / `DEEPSEEK_MODEL` 配置项，默认模型 `deepseek-chat`。
- 重写 `backend/app/api/ai.py`：6 个端点全部改为调用 DeepSeek，失败时 fallback 到本地 `ai_engine.py` 规则引擎。所有 AI 响应附加 `disclaimer: "此内容由AI生成，请人工审核确认后使用"`。
- API 契约 — 5 个工作流全部通过实际 DeepSeek 调用验证：
  - `POST /api/ai/run/jd-generate` — 结构化 JD 生成（职责/必备技能/加分项/任职资格）
  - `POST /api/ai/run/resume-search` — 自然语言→结构化搜索
  - `POST /api/ai/run/match` — 人岗匹配打分+理由
  - `POST /api/ai/run/interview-questions` — 按轮次生成面试问题
  - `POST /api/ai/run/communication-draft` — 电话/邮件/飞书沟通话术
  - `POST /api/ai/run/report-analysis` — 招聘数据 AI 分析报告
- `/api/ai/capabilities` 已更新：全部 9 项能力标记为 `done`。
- 更新 `requirements.txt`：新增 `openai>=1.0.0`。

### boss-cli 集成

- 安装 `@joohw/boss-cli@0.6.6`。
- 新建 `backend/app/services/boss_cli_service.py`：subprocess 封装 `boss` CLI 全部 11 个命令，120s 超时，结构化 JSON 返回 + 完整日志。
- 新建 `backend/app/api/boss.py`：10 个 REST 端点，注册为 `/api/boss/*` blueprint：
  - `GET /api/boss/status`、`/positions`、`/positions/<name>`、`/chat/list`
  - `POST /api/boss/candidates/search`、`/chat/open`、`/chat/send`、`/action`、`/resume/preview`、`/greet`
- 更新 `backend/config.py`：新增 `BOSS_CLI_ENABLED` / `BOSS_CLI_TIMEOUT`。
- 文档：`docs/BOSS_CLI_REFERENCE.md`（官方命令参考，含重要限制说明）。

### 前端 RecruitAI 重建

- 新建 `src/api/ai.js`：6 个 AI 工作流的 API 封装，失败自动降级 mock 数据。
- 扩写 `src/data/ai.js`：新增 `MOCK_JD_RESULT` / `MOCK_SEARCH_RESULTS` / `MOCK_MATCH_RESULT` / `MOCK_INTERVIEW_QUESTIONS` / `MOCK_COMMUNICATION_DRAFT` / `MOCK_REPORT_RESULT` / `MOCK_CANDIDATES` / `MOCK_DEMANDS`。
- 重写 `src/views/RecruitAI.vue`：6 个 tab 全部具备交互式表单 → 加载态（skeleton）→ 结果区 → 免责声明。移除"二期开发中"徽章。
- 新建 `src/api/boss.js`：10 个 boss-cli 端点 API 封装。
- 新建 `src/components/BossIntegration.vue`：4 个功能区（状态栏/岗位同步/候选搜索/聊天列表），含连接状态指示、搜索表单、结果表格、内联聊天面板、操作确认弹窗。所有交互遵循"无官方聊天 API，需人工沟通"口径。

### 项目文档更新

- `Claude.md`（根目录）：重建为项目级入口，新增快速导航表指向 6 个关键文档。
- `Wiki.md` §2"当前技术架构"：更新为纯 Vue SFC 状态，测试基线 33/33，移除 legacy 描述。
- 知识图谱重建：`D-WorkProject-HrProject-hr-web-frontend` moderate 模式重索引，节点 120→342（+185%），边 113→431（+282%），新增 45 个函数节点。

### 验证结果

- `npm run build`：通过
- `npm test`：通过，**33/33**
- `python -c "from app.services.deepseek_client import chat_completion"`：OK
- `python -c "from app.services.boss_cli_service import is_available"`：True
- 后端 DeepSeek：5 个工作流全部通过实际 API 验证（非 mock）
- 后端 boss-cli：`/api/boss/status` → `{"available": true}`

## 2026-07-18 Phase 6D 需求管理 Vue 化

- 新建 `src/data/demand.js`：从 `recruit-demand.html` 提取 6 条 mock 需求（含审批节点、招聘进展、内部匹配）。
- 新建 `src/views/RecruitDemand.vue`：筛选栏（搜索/状态/紧急度）+ 表格 + 审批 mini 进度条 + 招聘进展 + 新建/编辑 modal。
- 修改 `src/router/index.js`：新增 `/recruit-demand` → `RecruitDemand`，移除 legacy 路由。
- 修复：Vue 版保留旧 HTML 的 `#demandSearch` / `#demandStatus` / `#demandUrgency` / `#demandFilterCount` / `#demandModal` / `#newDemandPosition` 等 id 以兼容测试选择器；modal-box 补充 `role="dialog"` + `aria-modal="true"`；新增 `resetFilters()` 函数和 `filter-reset` 按钮。
- 测试适配：将密度/排序断言从 Vue 需求表迁移到 `/recruit-talent`（legacy 人才库保留 `.component-table` / `th.sortable-th`）。
- 验证：
  - `npm run build`：通过
  - `npm test`：通过，**33/33**

下一步：Phase 6E — 面试计划 (`recruit-interview`) Vue 化。

## 2026-07-18 招聘看板漏斗 2.5D 动态螺旋重设计

- 彻底重写 `src/views/RecruitDashboard.vue` 的招聘全漏斗卡片。
- **SVG 2.5D 等距圆盘堆叠锥体**：5 个圆盘（rx 126→46，ry=rx×0.28），从上到下形成一个圆锥视觉，每个圆盘显示计数值和阶段名称。
- **双螺旋线（Double Helix）**：两根相位差 π 的螺旋线绕锥体从顶部到底部缠绕，带流动虚线动画（`stroke-dashoffset` 循环）。
- **动态粒子**：12 个 SMIL `animateMotion` 粒子在两根螺旋线上运动；`prefers-reduced-motion` 兜底为 12 个静态点。
- **环境背景**：卡片内嵌 40px 网格点阵 + 30 个 CSS 浮动光斑（`animate ambientDrift`）+ 20 个 SVG 内浮动点（`svgAmbientDrift`），形成数据大屏质感。
- **右侧洞察面板**：点击圆盘切换（默认选中瓶颈阶段 Offer/index=3），展示：颜色胶囊+瓶颈徽章、人数/占比、转化率+WoW 环比箭头、平均停留、7 天 sparkline（polyline+填充）、负责人、洞察备注、"查看详情"按钮。
- **底部步进器**（`.viz-funnel` 容器 + 5 个 `.viz-funnel-step[role=link]` 芯片）保持测试契约。
- 卡片设置 `data-viz-enhanced="funnel"` 防止 app.js 双重增强。
- 所有颜色来自 CSS 变量，无 gradient，无 AI 外呼文案，`prefers-reduced-motion` 全覆盖。
- 验证：
  - `npm run build`：通过
  - `npm test`：**33/33** 通过，0 console error
  - 桌面截图：`test-results/funnel-hero-desktop.png`
  - 移动端截图：`test-results/funnel-hero-mobile.png`

## 2026-07-18 漏斗大屏迭代：沉浸式背景 + 双螺旋 + 右侧面板扩增

- **沉浸式数据大屏背景**：CSS 网格点阵 + 3 个径向光晕 + 扫描线动画 + 40 个浮动粒子 + 12 个技术标签 (HR/JD/OKR/HC/KPI/ATS/CRM) 沿背景浮动
- **双螺旋升级**：两根相位差 π 的螺旋线，glow 发光层 + core 实线层，16 个 SMIL 粒子沿螺旋运动
- **SVG 装饰环**：锥体下方 3 个半透明同心椭圆增加科技感
- **右侧洞察面板大幅扩增**：
  - 阶段标题 22px (原 16px) + 大号健康/瓶颈徽章
  - 3 个指标卡 (人数/转化率/占比)，22px 大字 + 卡片背景
  - 环比箭头 + 平均停留 + 负责人三列信息行
  - 大尺寸 sparkline (180×48, 原 140×28) + 最大值/最小值标注
  - **全链路转化柱状图** (新增) — 5 阶段柱形，点击可切换选中阶段
  - 洞察备注改为蓝色左边框卡片样式
  - CTA 按钮 14px 加阴影
- 卡片加整体蓝色渐变顶光 (`linear-gradient(165deg, #fff, #F0F4FF)`)
- 所有颜色使用 CSS 变量，支持 `prefers-reduced-motion`
- 验证：
  - `npm run build`：通过
  - `npm test`：**33/33** 通过，0 console error
  - 桌面截图：`test-results/funnel-hero-v3-desktop.png`
