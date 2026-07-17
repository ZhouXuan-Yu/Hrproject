# Memory.md - 项目进度追踪

> 记录当前做到了哪、刚刚确认了什么、下次从哪里继续。

## 当前状态

- 日期：2026-07-17
- 项目：智能招聘系统前端
- 目录：`D:\WorkProject\HrProject\hr-web\frontend`
- 阶段：原型保真迁移 + 企业级 UI 优化
- 技术栈：Vue 3 + Vite + Vue Router + Playwright
- Git 当前最新提交：`d95d91d feat: create original enterprise radar login`

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
