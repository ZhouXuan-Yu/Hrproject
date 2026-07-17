import { createApp } from 'vue';
import { createRouter, createWebHistory } from 'vue-router';
import App from './App.vue';
import LegacyPage from './components/LegacyPage.vue';

const legacyPages = [
  { path: '/login', page: 'login', title: '登录' },
  { path: '/recruit-dashboard', page: 'recruit-dashboard', title: '招聘看板' },
  { path: '/recruit-demand', page: 'recruit-demand', title: '需求管理' },
  { path: '/recruit-demand-detail', page: 'recruit-demand-detail', title: '需求详情' },
  { path: '/recruit-talent', page: 'recruit-talent', title: '人才库' },
  { path: '/recruit-interview', page: 'recruit-interview', title: '面试计划' },
  { path: '/recruit-ai', page: 'recruit-ai', title: '招聘辅助中心' },
  { path: '/recruit-config', page: 'recruit-config', title: '招聘基础配置' },
];

const routes = [
  { path: '/', redirect: '/login' },
  ...legacyPages.map((meta) => ({
    path: meta.path,
    component: LegacyPage,
    props: meta,
    meta: { title: meta.title },
  })),
  { path: '/:pathMatch(.*)*', redirect: '/login' },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - 智能招聘系统` : '智能招聘系统';
});

window.addEventListener('legacy:navigate', (event) => {
  const target = event.detail || '/login';
  router.push(target);
});

createApp(App).use(router).mount('#app');
