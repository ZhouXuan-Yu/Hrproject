import { createRouter, createWebHistory } from 'vue-router';
import RecruitConfig from '../views/RecruitConfig.vue';
import RecruitAccounts from '../views/RecruitAccounts.vue';
import RecruitAI from '../views/RecruitAI.vue';
import RecruitDemand from '../views/RecruitDemand.vue';
import RecruitInterview from '../views/RecruitInterview.vue';
import RecruitTalent from '../views/RecruitTalent.vue';
import RecruitDemandDetail from '../views/RecruitDemandDetail.vue';
import RecruitDashboard from '../views/RecruitDashboard.vue';
import RecruitMyProfile from '../views/RecruitMyProfile.vue';
import HomePage from '../views/HomePage.vue';
import LoginPage from '../views/LoginPage.vue';
import { MENU_TO_ROUTE, getRoleLanding, getAllowedMenuIds } from '../composables/useAuth.js';

// ── Auth guard ──────────────────────────────────────────────────────────
const AUTH_GUARD_ENABLED = true;
const PUBLIC_ROUTES = new Set(['/login']);

// demand-detail is a sub-page of demand; anyone with demand access gets it
const SUB_ROUTES = {
  '/recruit-demand-detail': 'recruit-demand',
};

async function checkAuth(to) {
  if (!AUTH_GUARD_ENABLED) return null;
  if (PUBLIC_ROUTES.has(to.path)) return null;

  const token = localStorage.getItem('hr_token');
  if (!token) return '/login';

  const allowedMenus = getAllowedMenuIds();

  // Check if the route is a sub-route of an allowed menu
  const parentMenu = SUB_ROUTES[to.path];
  if (parentMenu) {
    if (allowedMenus.includes(parentMenu)) return null;
    return getRoleLanding();
  }

  // Check if the route matches an allowed menu
  const matchedMenu = Object.entries(MENU_TO_ROUTE).find(
    ([, route]) => route === to.path
  );
  if (matchedMenu && allowedMenus.includes(matchedMenu[0])) return null;
  if (!matchedMenu) return null;

  // Redirect to the role's landing page
  return getRoleLanding();
}
// ─────────────────────────────────────────────────────────────────────────

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: () => getRoleLanding() },
    { path: '/login', component: LoginPage, meta: { title: '登录', noCache: true } },
    { path: '/home', component: HomePage, meta: { title: '首页' } },
    { path: '/recruit-dashboard', component: RecruitDashboard, meta: { title: '招聘看板' } },
    { path: '/my-profile', component: RecruitMyProfile, meta: { title: '我的档案' } },
    { path: '/recruit-demand', component: RecruitDemand, meta: { title: '需求管理' } },
    { path: '/recruit-demand-detail', component: RecruitDemandDetail, meta: { title: '需求详情' } },
    { path: '/recruit-talent', component: RecruitTalent, meta: { title: '人才库' } },
    { path: '/recruit-interview', component: RecruitInterview, meta: { title: '面试计划' } },
    { path: '/recruit-ai', component: RecruitAI, meta: { title: '招聘辅助中心' } },
    { path: '/recruit-config', component: RecruitConfig, meta: { title: '招聘基础配置' } },
    { path: '/recruit-accounts', component: RecruitAccounts, meta: { title: '账号管理' } },
    { path: '/:pathMatch(.*)*', redirect: '/login' },
  ],
});

router.beforeEach((to, from) => {
  const redirect = checkAuth(to);
  if (redirect && redirect !== to.path) {
    return redirect;
  }
});

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - 智能招聘系统` : '智能招聘系统';
});
