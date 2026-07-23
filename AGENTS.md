# AGENTS.md — 智能招聘系统 · 项目入口

> 本文件是项目级入口。详细的 agent 工作指南、代码风格、验收基线和当前口径见 `frontend/AGENTS.md`。

---

## 项目身份

智能招聘系统 — 企业级 HR 招聘管理平台，覆盖从招聘需求、候选人入库、AI 画像、人岗匹配、面试安排到 Offer/入职的完整链路。

- **前端**：`frontend/` — Vue 3 + Vite + Vue Router + Playwright (33 E2E)
- **后端**：待开发 (Python Flask + MySQL + Dify)
- **设计文档**：`docs/PRD_FINAL.md` (产品方案) · `docs/SUMMARY.md` (全量决策记录)

## 快速导航

| 想了解 | 读这个 |
|--------|--------|
| Agent 工作方式和代码风格 | `frontend/AGENTS.md` |
| 当前进度和下次继续点 | `frontend/Memory.md` |
| 业务口径、角色权限、架构约定 | `frontend/Wiki.md` |
| 复盘教训 | `frontend/Learning.md` |
| 产品方案 (6 角色/全流程权限) | `docs/PRD_FINAL.md` |
| 全部对话总结和关键决策 | `docs/SUMMARY.md` |

## 设计 Token 速查

```css
/* 亮色 */
--bg: #F6F8FB;
--surface: #FFFFFF;
--surface-elevated: #F9FAFC;
--surface-overlay: #FFFFFF;
--primary: #4F6EF7;
--text-primary: #172033;
--text-secondary: #5B6475;
--text-muted: #8C95A6;
--border: #E1E6EF;
--border-light: #EFF1F5;
--success: #22C55E;
--warning: #F59E0B;
--error: #EF4444;
/* 字号 */
--fs-title: 18px;
--fs-body: 14px;
--fs-caption: 12px;
/* 数字等宽 */
font-variant-numeric: tabular-nums;
```

## 禁止事项

| ❌ 禁止 | ✅ 替代 |
|---------|--------|
| emoji 当图标 | Lucide Icons SVG |
| 紫蓝渐变背景 | 纯色 Canvas |
| hover 才显示操作 | 可见按钮或溢出菜单 |
| 编造真实数据 | `[sample]` 标记 |
| AI 外呼/自动拨打 | 联系话术辅助、人工确认 |
