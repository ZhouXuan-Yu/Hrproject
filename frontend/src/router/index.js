import { createRouter, createWebHistory } from 'vue-router';
import RecruitConfig from '../views/RecruitConfig.vue';
import RecruitAI from '../views/RecruitAI.vue';
import RecruitDemand from '../views/RecruitDemand.vue';
import RecruitInterview from '../views/RecruitInterview.vue';
import RecruitTalent from '../views/RecruitTalent.vue';
import RecruitDemandDetail from '../views/RecruitDemandDetail.vue';
import RecruitDashboard from '../views/RecruitDashboard.vue';
import LoginPage from '../views/LoginPage.vue';

// ── Auth guard ──────────────────────────────────────────────────────────
const AUTH_GUARD_ENABLED = true;
const PUBLIC_ROUTES = new Set(['/login']);
const ROLE_PAGE_MAP = {
  admin:           '*',
  hr:              '*',            // HR: full access
  dept_head:       '*',            // dept_head: full access (dashboard + demand)
  employee:        '*',            // employee: can view dashboard
  interviewer:     '*',            // interviewer: can view dashboard + interview
  temp_interviewer:'*',            // temp_interviewer: same as interviewer
  no_recruit:      '/login',
};

function checkAuth(to) {
  if (!AUTH_GUARD_ENABLED) return null;
  if (PUBLIC_ROUTES.has(to.path)) return null;

  const token = localStorage.getItem('hr_token');
  if (!token) return '/login';

  // Role-based page access (soft redirect)
  const role = localStorage.getItem('hr_role') || 'no_recruit';
  const allowed = ROLE_PAGE_MAP[role];
  if (allowed === '*') return null;           // admin: all pages
  if (allowed === to.path) return null;        // exact match
  if (to.path.startsWith(allowed)) return null; // subtree match

  // Redirect to the role's default page (soft)
  return allowed !== '/login' ? allowed : '/login';
}
// ─────────────────────────────────────────────────────────────────────────

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', component: LoginPage, meta: { title: '登录', noCache: true } },
    { path: '/recruit-dashboard', component: RecruitDashboard, meta: { title: '招聘看板' } },
    { path: '/recruit-demand', component: RecruitDemand, meta: { title: '需求管理' } },
    { path: '/recruit-demand-detail', component: RecruitDemandDetail, meta: { title: '需求详情' } },
    { path: '/recruit-talent', component: RecruitTalent, meta: { title: '人才库' } },
    { path: '/recruit-interview', component: RecruitInterview, meta: { title: '面试计划' } },
    { path: '/recruit-ai', component: RecruitAI, meta: { title: '招聘辅助中心' } },
    { path: '/recruit-config', component: RecruitConfig, meta: { title: '招聘基础配置' } },
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
