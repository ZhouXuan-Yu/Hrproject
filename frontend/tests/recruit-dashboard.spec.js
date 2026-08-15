import { test, expect } from '@playwright/test';

const response = (data) => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify({ data }),
});

async function seedDashboardApis(page) {
  await page.addInitScript(() => {
    localStorage.setItem('hr_token', 'e2e-test-token-admin');
    localStorage.setItem('hr_role', 'admin');
    localStorage.setItem('hr_user', 'E2E Admin');
    window.__E2E_DISABLE_TOAST__ = true;
  });

  await page.route('**/api/auth/departments**', (route) => route.fulfill(response([
    { id: 1, name: '技术研发部' },
    { id: 2, name: '产品部' },
  ])));
  await page.route('**/api/auth/positions**', (route) => route.fulfill(response([
    { id: 1, name: '前端工程师' },
    { id: 2, name: '产品经理' },
  ])));
  await page.route('**/api/demand/list**', (route) => route.fulfill(response([
    { id: 'REQ-001', position: '运营总监', dept: '运营部', hc: 2, linkedCount: 0, status: 'open', statusLabel: '招聘中', urgency: 'very', urgencyLabel: '非常紧急', date: '2026-08-20' },
    { id: 'REQ-002', position: '产品经理', dept: '产品部', hc: 3, linkedCount: 2, status: 'open', statusLabel: '招聘中', urgency: 'high', urgencyLabel: '紧急', date: '2026-09-01' },
    { id: 'REQ-003', position: '前端工程师', dept: '技术部', hc: 5, linkedCount: 6, status: 'approval', statusLabel: '审批中', urgency: 'normal', urgencyLabel: '普通', date: '2026-09-10' },
    { id: 'REQ-004', position: '数据分析师', dept: '数据部', hc: 2, linkedCount: 2, status: 'closed', statusLabel: '已关闭', urgency: 'normal', urgencyLabel: '普通', date: '2026-07-30' },
  ])));
  await page.route('**/api/interview/calendar**', (route) => route.fulfill(response({
    month: '2026-08',
    monthStart: '2026-08-01',
    monthEnd: '2026-08-31',
    events: [
      { id: 'INT0001', title: '林晓', position: '产品经理', start: '2026-08-12 10:00', end: '2026-08-12 11:00', status: 'scheduled', statusLabel: '待面试', round: '初试(1轮)', interviewer: '王经理', method: '飞书视频' },
      { id: 'INT0002', title: '周宁', position: '前端工程师', start: '2026-08-19 14:00', end: '2026-08-19 15:00', status: 'evaluating', statusLabel: '待评价', round: '复试(2轮)', interviewer: '陈经理', method: '线下' },
    ],
  })));
  await page.route('**/api/dashboard/kpi**', (route) => route.fulfill(response([
    { label: '全公司待面试', val: 8, trend: '较上月 +12%' },
    { label: '待评价', val: 12, trend: '待处理 4 项' },
    { label: '在招岗位', val: 8, trend: '技术研发部 3 项' },
    { label: '本月入职总量', val: 5, trend: '较上月 +1' },
  ])));
  await page.route('**/api/dashboard/funnel**', (route) => route.fulfill(response({
    overallRate: '1.4%',
    stages: [
      { label: '收简历', count: 346, pct: '100%', health: 'good', note: '入口流量稳定，简历池充足。', link: '/recruit-talent' },
      { label: '筛选通过', count: 89, pct: '25.7%', conv: '25.7%', health: 'good', note: '继续优化 JD 精准度。', link: '/recruit-demand' },
      { label: '面试', count: 42, pct: '12.1%', conv: '47.2%', health: 'watch', note: '关注面试评价一致性。', link: '/recruit-interview' },
      { label: 'Offer', count: 8, pct: '2.3%', conv: '19.0%', health: 'risk', bottleneck: true, note: '当前最大瓶颈，建议复盘决策时效。', link: '/recruit-interview' },
      { label: '入职', count: 5, pct: '1.4%', conv: '62.5%', health: 'good', note: 'Offer 到入职转化健康。', link: '/recruit-demand' },
    ],
  })));
  await page.route('**/api/dashboard/channel**', (route) => route.fulfill(response([
    { channel: '邮箱采集', resume: 120, pass: 35, interview: 18, hire: 2, cost: '¥0' },
    { channel: 'Boss 直聘', resume: 98, pass: 28, interview: 12, hire: 1, cost: '¥8K' },
    { channel: '猎聘', resume: 65, pass: 15, interview: 7, hire: 1, cost: '¥12K' },
  ])));
  await page.route('**/api/dashboard/monthly**', (route) => route.fulfill(response({ months: [
    { label: '1月', resumes: 18, interviews: 9, hires: 1 },
    { label: '2月', resumes: 24, interviews: 11, hires: 2 },
    { label: '3月', resumes: 31, interviews: 13, hires: 2 },
    { label: '4月', resumes: 28, interviews: 10, hires: 1 },
    { label: '5月', resumes: 36, interviews: 16, hires: 3 },
    { label: '6月', resumes: 42, interviews: 18, hires: 4 },
    { label: '7月', resumes: 39, interviews: 17, hires: 5 },
  ] })));
}

test('招聘运营中心展示需求组合、日历和行动模块', async ({ page }) => {
  await seedDashboardApis(page);
  await page.goto('/recruit-dashboard');

  await expect(page).toHaveURL(/\/recruit-dashboard$/);
  await expect(page.locator('[data-testid="dashboard-pro-charts"]')).toContainText('招聘项目运营中心');
  await expect(page.locator('[data-testid="dashboard-pro-charts"]')).toContainText('招聘项目组合');
  await expect(page.locator('[data-testid="dashboard-pro-charts"]')).toContainText('岗位交付健康度');
  await expect(page.locator('[data-testid="dashboard-pro-charts"]')).toContainText('招聘交付趋势');
  await expect(page.locator('[data-testid="recruit-project-row"]')).toHaveCount(4);
  await expect(page.locator('[data-testid="recruit-project-row"]').first()).toContainText('运营部');
  await expect(page.locator('[data-testid="recruit-agenda"]')).toBeVisible();
  await expect(page.locator('[data-testid="recruit-agenda"]')).toContainText('林晓');
  await expect(page.locator('.recruit-pro-card--resource')).toContainText('面试资源调度');
  await expect(page.locator('.recruit-pro-card--resource')).toContainText('管理结论');
});

test('招聘看板移动端不产生页面级横向溢出', async ({ page }) => {
  await seedDashboardApis(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/recruit-dashboard');

  const overflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth);
  expect(overflow).toBe(false);
  await expect(page.locator('[data-testid="dashboard-pro-charts"]')).toBeVisible();
});
