# 智能招聘系统前端

Vue 3 + Vite + Vue Router 前端工作台，页面覆盖登录、首页、招聘看板、需求管理、需求详情、人才库、面试计划、招聘辅助中心和招聘基础配置。

## 启动

后端需要先运行在 `http://127.0.0.1:5000`，前端 `/api` 请求会由 Vite 代理到后端。

```powershell
cd D:\WorkProject\HrProject\hr-web\frontend
npm install
npm run dev
```

访问：`http://127.0.0.1:7100/login`

默认账号：`admin / admin123`

## 页面截图

README 中展示的页面截图由 Playwright 从本地前后端实采生成，输出到根目录的 `docs/screenshots/`。

```powershell
cd D:\WorkProject\HrProject\hr-web\frontend
node scripts\capture-readme-shots.mjs
```

如需临时指定前端地址：

```powershell
$env:README_SHOT_BASE="http://127.0.0.1:7100"
node scripts\capture-readme-shots.mjs
```

## 测试与构建

```powershell
npm run build
npx playwright test --workers=2
```
