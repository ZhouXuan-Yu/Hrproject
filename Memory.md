# Memory.md - 项目进度追踪

> 记录当前做到了哪、刚刚确认了什么、下次从哪里继续。

## 当前状态

- 日期：2026-07-17
- 项目：智能招聘系统前端
- 目录：`D:\WorkProject\HrProject\hr-web\frontend`
- 阶段：原型保真迁移 + 企业级 UI 优化
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
- 基础已打牢，可进入模块化开发或接口对接阶段

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
