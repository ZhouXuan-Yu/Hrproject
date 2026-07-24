import { expect, test } from '@playwright/test';

const ok = (data, extra = {}) => ({ code: 0, message: 'ok', data, ...extra });

const mockList = [
  {
    id: 'INT-HIRE-001',
    name: 'Rejected Candidate',
    candidateId: 'C-HIRE-001',
    position: 'Backend Engineer',
    round: 'Final',
    interviewer: 'Interviewer A',
    date: '07-24',
    time: '10:00',
    method: 'offline',
    status: 'done',
    statusLabel: 'done',
    result: 'reject',
    offerStatus: null,
    isMine: false,
  },
  {
    id: 'INT-HIRE-002',
    name: 'Offer Pending Candidate',
    candidateId: 'C-HIRE-002',
    position: 'Product Manager',
    round: 'Final',
    interviewer: 'Interviewer B',
    date: '07-24',
    time: '11:00',
    method: 'offline',
    status: 'offer',
    statusLabel: 'offer',
    result: 'pass',
    offerStatus: null,
    offerNo: null,
    emailSent: false,
    isMine: false,
  },
  {
    id: 'INT-HIRE-003',
    name: 'Offer Sent Candidate',
    candidateId: 'C-HIRE-003',
    position: 'Frontend Engineer',
    round: 'Final',
    interviewer: 'Interviewer C',
    date: '07-24',
    time: '14:00',
    method: 'online',
    status: 'offer',
    statusLabel: 'offer sent',
    result: 'pass',
    offerStatus: 1,
    offerNo: 'OF999001',
    emailSent: true,
    isMine: false,
  },
  {
    id: 'INT-HIRE-004',
    name: 'Accepted Candidate',
    candidateId: 'C-HIRE-004',
    position: 'Data Analyst',
    round: 'Final',
    interviewer: 'Interviewer D',
    date: '07-24',
    time: '15:00',
    method: 'online',
    status: 'onboard',
    statusLabel: 'accepted',
    result: 'pass',
    offerStatus: 2,
    offerNo: 'OF999002',
    candidateConfirm: 'accept',
    emailSent: true,
    isMine: false,
  },
];

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('hr_token', 'e2e-test-token-admin');
    localStorage.setItem('hr_role', 'admin');
    localStorage.setItem('hr_user', 'E2E Admin');
    window.__E2E_DISABLE_TOAST__ = true;
  });
  await page.route('**/api/interview/list**', (route) =>
    route.fulfill({ json: ok(mockList, { total: mockList.length, page: 1, pageSize: 20 }) })
  );
  await page.route('**/api/interview/alerts**', (route) => route.fulfill({ json: ok([]) }));
  await page.route('**/api/interview/calendar**', (route) => route.fulfill({
    json: ok({
      month: '2026-07',
      monthStart: '2026-07-01',
      monthEnd: '2026-07-31',
      events: mockList.map((item) => ({
        id: item.id,
        title: item.name,
        position: item.position,
        round: item.round,
        interviewer: item.interviewer,
        method: item.method,
        status: item.status,
        statusLabel: item.statusLabel,
        start: `2026-07-24T${item.time}:00`,
        end: `2026-07-24T16:00:00`,
      })),
    }),
  }));
});

test('rejected interviews stay in done state and do not look onboarded', async ({ page }) => {
  await page.goto('/recruit-interview');
  const row = page.locator('tr', { hasText: 'Rejected Candidate' }).first();
  await expect(row).toBeVisible();
  await expect(row).toContainText('done');
  await expect(row).not.toContainText('onboard');
});

test('sent offers do not expose another offer button and keep the offer number visible', async ({ page }) => {
  await page.goto('/recruit-interview');
  const listPanel = page.locator('.tab-panel.active').first();
  const sentRow = listPanel.locator('tr', { hasText: 'Offer Sent Candidate' }).first();
  await expect(sentRow).toBeVisible();
  await expect(sentRow).toContainText('OF999001');
  await expect(sentRow).not.toContainText('Offer Pending Candidate');

  const pendingRow = listPanel.locator('tr', { hasText: 'Offer Pending Candidate' }).first();
  await expect(pendingRow).toBeVisible();
  await expect(pendingRow.locator('button')).toHaveCount(3);
});

test('accepted offers stay visible as onboard-stage records', async ({ page }) => {
  await page.goto('/recruit-interview');
  const row = page.locator('tr', { hasText: 'Accepted Candidate' }).first();
  await expect(row).toBeVisible();
  await expect(row).toContainText('accepted');
  await expect(row).not.toContainText('Rejected Candidate');
});
