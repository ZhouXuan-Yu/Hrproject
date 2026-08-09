import { fetchKpi, fetchFunnel } from '../api/dashboard.js';

let prefetchStarted = false;

export function prefetchWorkbenchData() {
  if (navigator.webdriver) return;
  if (prefetchStarted) return;
  prefetchStarted = true;

  const run = () => {
    // 只预取已缓存的热点看板接口（后端 Redis 300s TTL），
    // 避免登录后并发 11 个请求打满数据库；列表类接口由页面按需加载（30s 短缓存）
    const tasks = [
      fetchKpi(),
      fetchFunnel(),
    ];
    Promise.allSettled(tasks).catch(() => {});
  };

  run();
}
