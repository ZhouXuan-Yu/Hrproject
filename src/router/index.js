import { createRouter, createWebHistory } from 'vue-router';
import RecruitConfig from '../views/RecruitConfig.vue';
import RecruitAI from '../views/RecruitAI.vue';
import RecruitDemand from '../views/RecruitDemand.vue';
import RecruitInterview from '../views/RecruitInterview.vue';
import RecruitTalent from '../views/RecruitTalent.vue';
import RecruitDemandDetail from '../views/RecruitDemandDetail.vue';
import RecruitDashboard from '../views/RecruitDashboard.vue';
import LoginPage from '../views/LoginPage.vue';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', component: LoginPage, meta: { title: '登录' } },
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

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - 智能招聘系统` : '智能招聘系统';
});
