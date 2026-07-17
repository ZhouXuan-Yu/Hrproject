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
