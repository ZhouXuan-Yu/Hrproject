import { expect, test } from '@playwright/test';

const pages = [
  ['/recruit-dashboard', '招聘看板'],
  ['/recruit-demand', '需求管理'],
  ['/recruit-demand-detail', '需求详情'],
  ['/recruit-talent', '人才库'],
  ['/recruit-interview', '面试计划'],
  ['/recruit-ai', 'AI 智能自动化中心'],
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
