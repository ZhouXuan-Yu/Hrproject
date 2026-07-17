import { expect, test } from '@playwright/test';

const pages = [
  ['/recruit-dashboard', '招聘看板'],
  ['/recruit-demand', '需求管理'],
  ['/recruit-demand-detail', '需求详情'],
  ['/recruit-talent', '人才库'],
  ['/recruit-interview', '面试计划'],
  ['/recruit-ai', '招聘辅助中心'],
  ['/recruit-config', '招聘基础配置'],
];

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('hr_role', 'admin');
    localStorage.setItem('hr_user', '测试用户');
  });
});

test('login selects role and enters dashboard', async ({ page }) => {
  await page.goto('/login');
  await page.getByText('HR 专员').click();
  await page.locator('.btn-login').click();
  await expect(page).toHaveURL(/\/recruit-dashboard$/);
  await expect(page.getByRole('heading', { name: '招聘看板' })).toBeVisible();
});

test('login background responds to mouse movement', async ({ page }) => {
  await page.goto('/login');
  const stage = page.locator('#loginStage');
  await expect(stage).toBeVisible();
  await page.mouse.move(1200, 180);
  await expect.poll(async () => stage.evaluate((el) => getComputedStyle(el).getPropertyValue('--mx').trim())).not.toBe('50%');
  await expect.poll(async () => stage.evaluate((el) => getComputedStyle(el).getPropertyValue('--my').trim())).not.toBe('50%');
  await expect.poll(async () => stage.evaluate((el) => getComputedStyle(el).getPropertyValue('--pupil-x').trim())).not.toBe('0px');
});

test('login radar module reacts to username and password fields', async ({ page }) => {
  await page.goto('/login');
  const stage = page.locator('#loginStage');

  await page.locator('#username').focus();
  await expect(stage).toHaveClass(/username-mode/);

  await page.locator('#password').focus();
  await expect(stage).toHaveClass(/password-mode/);

  await page.locator('#password').blur();
  await expect(stage).not.toHaveClass(/password-mode/);
});

test('login exposes full role set and trims menu by permission', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByText('部门负责人')).toBeVisible();
  await expect(page.getByText('基层员工')).toBeVisible();
  await expect(page.getByText('临时面试官')).toBeVisible();
  await expect(page.getByText('无招聘权限')).toBeVisible();

  await page.goto('/recruit-dashboard');
  await expect(page.locator('#sidebar')).toBeVisible();
  await page.evaluate(() => {
    localStorage.setItem('hr_role', 'dept_head');
    localStorage.setItem('hr_user', '部门负责人');
    window.renderSidebar('recruit-dashboard');
  });
  await expect(page.locator('#sidebar').getByText('需求管理')).toBeVisible();
  await expect(page.locator('#sidebar').getByText('人才库')).toHaveCount(0);

  await page.goto('/recruit-dashboard');
  await expect(page.locator('#sidebar')).toBeVisible();
  await page.evaluate(() => {
    localStorage.setItem('hr_role', 'no_recruit');
    localStorage.setItem('hr_user', '无权限员工');
    window.renderSidebar('recruit-dashboard');
  });
  await expect(page.getByText('暂无招聘模块权限')).toBeVisible();
});

for (const [path, heading] of pages) {
  test(`${heading} renders under Vue router`, async ({ page }) => {
    await page.goto(path);
    await expect(page.getByRole('heading', { name: heading })).toBeVisible();
    await expect(page.locator('#sidebar')).toBeVisible();
  });
}

test('sidebar navigation stays inside Vue routes', async ({ page }) => {
  await page.goto('/recruit-dashboard');
  await page.getByRole('link', { name: /需求管理/ }).click();
  await expect(page).toHaveURL(/\/recruit-demand$/);
  await expect(page.getByRole('heading', { name: '需求管理' })).toBeVisible();
});

test('command palette supports keyboard navigation', async ({ page }) => {
  await page.goto('/recruit-dashboard');
  await page.keyboard.press('Control+K');
  await expect(page.locator('#commandPalette')).toBeVisible();
  await page.locator('#commandInput').fill('人才库');
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/recruit-talent$/);
  await expect(page.getByRole('heading', { name: '人才库' })).toBeVisible();
});

test('global workbench shell exposes topbar actions and current navigation state', async ({ page }) => {
  await page.goto('/recruit-dashboard');
  await expect(page.locator('body')).toHaveAttribute('data-route', 'recruit-dashboard');
  await expect(page.locator('.content')).toHaveClass(/workbench-ready/);
  await expect(page.locator('.topbar-actions')).toBeVisible();
  await expect(page.locator('#sidebar')).toHaveAttribute('role', 'navigation');
  await expect(page.locator('.nav-flyout-item.active')).toHaveAttribute('aria-current', 'page');

  await page.locator('#commandTrigger').click();
  await expect(page.locator('#commandPalette')).toBeVisible();
  await expect(page.locator('#commandInput')).toBeFocused();
});

test('core data components expose density, sorting, reset, KPI context, and dialog semantics', async ({ page }) => {
  await page.goto('/recruit-dashboard');
  await expect(page.locator('.metric-window').first()).toContainText('当前筛选范围');
  await expect(page.locator('.component-viz-card[aria-label*="招聘全漏斗"]')).toBeVisible();
  await expect(page.locator('.viz-funnel-step').first()).toHaveAttribute('role', 'link');

  await page.goto('/recruit-demand');
  const table = page.locator('.component-table').first();
  await expect(table).toBeVisible();
  await table.locator('.table-density button[data-density="comfortable"]').click();
  await expect(table).toHaveAttribute('data-density', 'comfortable');

  const positionHeader = page.locator('th.sortable-th', { hasText: '岗位' }).first();
  await positionHeader.click();
  await expect(positionHeader).toHaveAttribute('aria-sort', 'ascending');

  await page.locator('#demandSearch').fill('运营总监');
  await expect(page.locator('#demandFilterCount')).toContainText('共 2 条需求');
  await page.locator('.filter-reset').click();
  await expect(page.locator('#demandSearch')).toHaveValue('');
  await expect(page.locator('#demandStatus')).toHaveValue('all');
  await expect(page.locator('#demandFilterCount')).toContainText('共 6 条需求');

  await page.getByRole('button', { name: '+ 新建需求' }).click();
  await expect(page.locator('#demandModal .modal-box')).toHaveAttribute('role', 'dialog');
  await expect(page.locator('#demandModal .modal-box')).toHaveAttribute('aria-modal', 'true');
});

test('demand list supports filtering and create modal', async ({ page }) => {
  await page.goto('/recruit-demand');
  await page.locator('#demandSearch').fill('运营总监');
  await expect(page.locator('#demandFilterCount')).toContainText('共 2 条需求');
  await page.locator('#demandStatus').selectOption('approval');
  await expect(page.locator('#demandFilterCount')).toContainText('共 1 条需求');

  await page.getByRole('button', { name: '+ 新建需求' }).click();
  await expect(page.locator('#demandModal')).toBeVisible();
  await page.locator('#newDemandPosition').fill('测试岗位');
  let demandMessage = '';
  page.once('dialog', async (dialog) => {
    demandMessage = dialog.message();
    await dialog.accept();
  });
  await page.getByRole('button', { name: '提交审批' }).click();
  expect(demandMessage).toContain('已提交审批');
});

test('demand detail enhanced filters and batch actions are available', async ({ page }) => {
  await page.goto('/recruit-demand-detail');
  await page.locator('#filterEdu').selectOption('大专');
  await expect(page.locator('#filterCount')).toContainText('共 2 人');
  await page.locator('.row-check').first().check();
  await expect(page.getByRole('button', { name: '批量加入需求' })).toBeVisible();
  await expect(page.getByRole('button', { name: '批量移出需求' })).toBeVisible();
  await expect(page.getByRole('button', { name: '标记不合适' })).toBeVisible();
  await expect(page.getByRole('button', { name: '导出' })).toBeVisible();
});

test('talent library filters and contact flow avoid unrealistic outbound calling', async ({ page }) => {
  await page.goto('/recruit-talent');
  await page.locator('#extSkill').selectOption('K8s');
  await expect(page.locator('#extCount')).toContainText('共 1 人');
  await page.locator('.ext-check').first().check();
  let contactMessage = '';
  page.once('dialog', async (dialog) => {
    contactMessage = dialog.message();
    await dialog.accept();
  });
  await page.getByRole('button', { name: '批量联系' }).click();
  expect(contactMessage).toContain('电话 / 邮件 / 飞书');
  expect(contactMessage).not.toMatch(/外呼|自动拨打/);
});

test('interview plan covers full six-state workflow and calendar', async ({ page }) => {
  await page.goto('/recruit-interview');
  await expect(page.getByText('待入职').first()).toBeVisible();
  await expect(page.getByText('已入职').first()).toBeVisible();
  await page.getByRole('button', { name: /日程/ }).click();
  await expect(page.locator('#calendarViewModal')).toBeVisible();
});

test('recruiting assistant includes candidate communication helper and no outbound-call copy', async ({ page }) => {
  await page.goto('/recruit-ai');
  await page.getByText('⑥ 候选人沟通助手').click();
  await expect(page.getByRole('heading', { name: '候选人沟通助手' })).toBeVisible();
  await expect(page.locator('body')).not.toContainText(/外呼|自动拨打/);
});

test('all main pages avoid AI outbound-call wording', async ({ page }) => {
  for (const [path] of pages) {
    await page.goto(path);
    await expect(page.locator('body')).not.toContainText(/外呼|自动拨打/);
  }
});

test('candidate drawer and schedule modal still work', async ({ page }) => {
  await page.goto('/recruit-demand-detail');
  await page.getByRole('button', { name: '查看' }).first().click();
  await expect(page.locator('#candidateDrawer')).toHaveClass(/open/);
  await page.locator('.drawer-close').click();
  await expect(page.locator('#candidateDrawer')).not.toHaveClass(/open/);

  await page.getByRole('button', { name: /约面/ }).first().click();
  await expect(page.locator('#globalScheduleModal')).toBeVisible();
  await page.getByRole('button', { name: '取消' }).click();
  await expect(page.locator('#globalScheduleModal')).toHaveCount(0);
});

test('tabs, accordion, alerts, and config modal interactions are alive', async ({ page }) => {
  await page.goto('/recruit-ai');
  await page.locator('#aiTabs .tab[data-tab="search"]').click();
  await expect(page.locator('#panel-search')).toHaveClass(/active/);

  await page.goto('/recruit-config');
  await page.getByText('添加邮箱账号').click();
  await expect(page.locator('#emailModal')).toBeVisible();
  await page.getByRole('button', { name: '取消' }).click();
  await expect(page.locator('#emailModal')).toBeHidden();

  await page.goto('/recruit-interview');
  await page.getByRole('button', { name: /提醒/ }).click();
  await expect(page.locator('#alertDropdown')).toBeVisible();
});
