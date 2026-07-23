import { expect, test } from '@playwright/test';

const ok = (data) => ({ code: 0, message: 'ok', data });

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('hr_token', 'e2e-test-token-admin');
    localStorage.setItem('hr_role', 'admin');
    localStorage.setItem('hr_user', 'E2E Admin');
    window.__E2E_DISABLE_TOAST__ = true;
  });
});

test('system mail board keeps useful vertical space', async ({ page }) => {
  await page.goto('/recruit-talent');
  const board = page.getByTestId('system-mail-board');
  await expect(board).toBeVisible({ timeout: 10000 });
  await expect.poll(() => board.evaluate((el) => el.getBoundingClientRect().height)).toBeGreaterThanOrEqual(360);
});

test('knowledge base loads and saves through the config API', async ({ page }) => {
  let savedPayload = null;

  await page.route('**/api/config/email-accounts**', (route) => {
    if (route.request().method() === 'GET') return route.fulfill({ json: ok([]) });
    return route.fulfill({ json: ok({}) });
  });
  await page.route('**/api/config/channels**', (route) => route.fulfill({ json: ok([]) }));
  await page.route('**/api/config/score-rules**', (route) => route.fulfill({ json: ok({}) }));
  await page.route('**/api/config/notify-templates**', (route) => route.fulfill({ json: ok([]) }));
  await page.route('**/api/config/role-permissions**', (route) => route.fulfill({ json: ok([]) }));
  await page.route('**/api/config/audit-logs**', (route) => route.fulfill({ json: ok([]) }));
  await page.route('**/api/config/api-keys**', (route) => route.fulfill({ json: ok([]) }));
  await page.route('**/api/config/tencent-meeting/status**', (route) => route.fulfill({ json: ok({ configured: false }) }));
  await page.route('**/api/config/feishu/status**', (route) => route.fulfill({ json: ok({ configured: false }) }));
  await page.route('**/api/config/knowledge-base**', async (route) => {
    if (route.request().method() === 'PUT') {
      savedPayload = route.request().postDataJSON();
      return route.fulfill({ json: ok({ updated: true, data: savedPayload }) });
    }
    return route.fulfill({
      json: ok({
        companyName: 'XX Company',
        industry: 'Recruiting SaaS',
        website: 'https://example.com',
        companyProfile: 'Existing profile',
        hiringPrinciples: 'Existing principles',
        aiContext: 'Existing AI context',
      }),
    });
  });

  await page.goto('/recruit-config');
  await page.getByTestId('knowledge-base-panel').locator('.accordion-header').click();
  await expect(page.getByTestId('kb-company-name')).toBeVisible();
  await page.getByTestId('kb-company-name').fill('Commercial Grade HR');
  await page.getByTestId('kb-industry').fill('Enterprise hiring platform');
  await page.getByTestId('kb-company-profile').fill('A testable company profile for AI grounding.');
  await page.getByTestId('kb-hiring-principles').fill('Use compliant, factual, role-aware recruiting language.');
  await page.getByTestId('kb-ai-context').fill('Prefer company facts from this knowledge base.');
  await page.getByTestId('kb-save').click();

  await expect.poll(() => savedPayload?.companyName).toBe('Commercial Grade HR');
  expect(savedPayload.industry).toBe('Enterprise hiring platform');
  expect(savedPayload.aiContext).toContain('knowledge base');
});

test('interview rows support multi-select, clear, and delete requests', async ({ page }) => {
  const deleted = [];
  const interviews = [
    { id: 'INT-A', name: 'Alice', candidateId: 'C-A', position: 'Engineer', round: 'Round 1', interviewer: 'HR', date: '07-23', time: '10:00', method: 'Offline', status: 'scheduled', statusLabel: 'Scheduled', isMine: true },
    { id: 'INT-B', name: 'Bob', candidateId: 'C-B', position: 'Designer', round: 'Round 1', interviewer: 'HR', date: '07-23', time: '11:00', method: 'Offline', status: 'scheduled', statusLabel: 'Scheduled', isMine: true },
  ];

  await page.route('**/api/interview/list**', (route) =>
    route.fulfill({ json: { code: 0, message: 'ok', data: interviews, total: interviews.length, page: 1, pageSize: 20 } })
  );
  await page.route('**/api/interview/alerts**', (route) => route.fulfill({ json: ok([]) }));
  await page.route('**/api/interview/INT-*', (route) => {
    deleted.push(new URL(route.request().url()).pathname.split('/').pop());
    return route.fulfill({ json: ok({ deleted: true }) });
  });
  page.on('dialog', (dialog) => {
    throw new Error(`Unexpected native dialog: ${dialog.message()}`);
  });

  await page.goto('/recruit-interview');
  await page.locator('tr', { hasText: 'Alice' }).getByRole('button', { name: '完成面试' }).click();
  await expect(page.getByTestId('interview-action-modal')).toBeVisible();
  await expect(page.getByTestId('interview-action-option')).toHaveCount(2);
  await page.getByTestId('interview-action-modal').getByRole('button', { name: '关闭' }).click();
  await page.locator('tr', { hasText: 'Alice' }).getByRole('button', { name: '取消' }).click();
  await expect(page.getByTestId('interview-action-modal')).toBeVisible();
  await expect(page.getByTestId('interview-action-option')).toHaveCount(4);
  await page.getByTestId('interview-action-modal').getByRole('button', { name: '关闭' }).click();

  const checks = page.getByTestId('interview-row-check');
  await expect(checks).toHaveCount(4);
  await checks.nth(0).check();
  await expect(page.getByTestId('interview-batch-bar')).toBeVisible();
  await checks.nth(1).check();
  await page.getByTestId('interview-clear-selection').click();
  await expect(page.getByTestId('interview-batch-bar')).toHaveCount(0);

  await checks.nth(0).check();
  await checks.nth(1).check();
  await page.getByTestId('interview-delete-selected').click();
  await expect(page.getByTestId('interview-action-modal')).toBeVisible();
  await page.getByTestId('interview-action-confirm').click();
  await expect.poll(() => deleted.sort().join(',')).toBe('INT-A,INT-B');
});

test('candidate match view opens a centered rich analysis modal', async ({ page }) => {
  await page.goto('/recruit-demand-detail');
  await page.waitForSelector('#candidateTable', { timeout: 10000 });
  await page.locator('#candidateTable tbody tr').first().getByRole('button').filter({ hasText: /查看|鏌ョ湅/ }).click();
  const modal = page.locator('.demand-match-drawer-overlay .drawer-panel');
  await expect(modal).toBeVisible({ timeout: 5000 });
  await expect(modal.locator('.drawer-kpis')).toBeVisible();
  await expect(modal.locator('.analysis-grid').first()).toBeVisible();
  await expect.poll(() => modal.evaluate((el) => getComputedStyle(el).borderRadius)).toBe('10px');
});
