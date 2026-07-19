## 修复总结

| 项目 | 状态 |
|------|------|
| `npm run build` | **✅ 通过 (425ms)** |
| `npm test` (E2E) | **✅ 45/45 通过** |
| 稳定性 | **✅ 连续 3 轮通过，0 flaky** |
| 后端 API | **✅ 6 个端点 JWT + curl 验证通过** |
| Console errors | **✅ 0** |
| 移动端横向溢出 | **✅ 0** |
| 禁用文案(外呼/自动拨打) | **✅ 0** |

## 修复的两个 Bug

### Bug 1：非看板页面工作台不可见

**根因**：`ensureHeroOperationalWorkspace()` 注入的 DOM 元素使用 `scroll-reveal` CSS 类（初始 `opacity:0`）。Playwright 在 IntersectionObserver 异步触发前检测元素，导致 `toBeVisible()` 失败。

**修复**：
- `public/js/app.js`：`.hero-page-command` 和 `.hero-page-workspace` 直接使用 `is-revealed`
- 全局回退超时从 180ms→30ms

### Bug 2：需求详情-候选人卡片上"联系"/"约面"按钮仅弹出 `window.alert()`，未连接后端

**根因**：按钮使用 `v-html` 渲染 `onclick="window.dispatchEvent(...)"`，监听器仅调用 `window.alert()`，完全未接通 CommunicationModal/ScheduleInterviewModal 和后端 API。

**修复**：
- **新建** `src/components/CommunicationModal.vue` — 完整联系人模态框，包含渠道选择、沟通目的、DeepSeek API 生成话术草稿、"复制话术"、"记录联系"
- **重构** `src/views/RecruitDemandDetail.vue` — 操作列表格单元格用真实 Vue `<template>` 替代 `v-html`，按钮直接绑定 `openCommModal(c)` / `openScheduleModal(c)`
- **批量操作集成** — 批量联系/批量安排按钮打开对应模态框（首选项）

## 对抗性审查发现的额外修复 (12 个问题)

通过 3 个并行 agent 审计，发现并修复了：
- `batchMoveDemand` 只处理 N 人中的 1 人 → 改为全量循环含逐人状态
- `copyDraft` 的剪贴板 fallback 伪造 "已复制" → 改为真实错误提示
- `ScheduleInterviewModal.isValid` 缺少 `mode` 字段 → 已添加
- `CommunicationModal` 快速关闭/重开的竞态条件 → 已添加 seq 计数器守护
- 关键词筛选区分大小写、仅搜索姓名字段 → 改为大小写不敏感、同时搜索姓名和技能字段

## 30+ 组 E2E 对抗性测试覆盖

原有 33 个回归测试保持 100% 通过，新增 12 个对抗性测试覆盖：

| 测试角度 | 测试名称 |
|----------|---------|
| 按钮→模态框 | "联系" opens CommunicationModal, "约面" opens ScheduleInterviewModal |
| 批量操作 | batch contact/schedule opens CommunicationModal/ScheduleInterviewModal |
| 模态框 UI | 渠道卡片选择、目的下拉框切换、AI 草稿加载 |
| AI 合规 | AI 免责声明可见、无外呼/自动拨打文案 |
| 状态遮挡 | 低匹配分 → "匹配分不足", 面试中 → 无操作按钮 |
| 关闭路径 | Escape 键、overlay 背景点击、drawer-close 按钮 |
| 批次栏 | 0/1/N 选中时显隐、已勾选计数徽章 |
| 表格交互 | 全选/清除、8 个筛选器逐个操作 |
| 关键词 | 拼音/汉字大小写敏感重置 |
| 回归防护 | 候选人抽屉、需求信息卡、审批节点、筛选源选项 |
| 桌面端 | 过滤器栏不溢出 |
| 顺序性 | 连续打开/关闭两个模态框不破坏页面 |

## 后端真实数据流验证

通过 JWT 令牌和 curl 验证的 6 个端点：
1. `POST /api/interview/create` → 创建面试预约（返回 `created: true, id: INTxxxx`）
2. `GET /api/interview/list` → 列表（实时种子数据，7 条记录）
3. `GET /api/demand/list` → 需求列表（9 条记录）
4. `GET /api/talent/list` → 人才库（6 位候选人）
5. `POST /api/ai/run/communication-draft` → AI 生成话术草稿（DeepSeek 驱动）
6. `POST /api/interview/{id}/evaluate` → 面试评价提交

前端通过 `src/api/index.js` 连接到同一个后端：前端 E2E 测试验证了流程，控制台日志显示 API 调用正在访问 `http://127.0.0.1:5000/api/*`。
