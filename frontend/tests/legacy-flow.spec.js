import { expect, test } from '@playwright/test';

const ok = (data, extra = {}) => ({ code: 0, message: 'ok', data, ...extra });

const demandDetail = {
  id: 'DM-E2E-001',
  position: 'Backend Engineer',
  dept: 'Engineering',
  hc: 2,
  urgency: 'normal',
  salary: '25k-35k',
  date: '2026-08-01',
  submitter: 'HR',
  submitDate: '2026-07-24',
  channels: ['mail'],
  progress: { hired: 0, total: 2, pct: 0 },
  description: 'Build reliable hiring services',
  requiredSkills: ['Java', 'SQL'],
  plusSkills: ['K8s'],
  approvalNodes: [],
};

const demandCandidates = [
  {
    id: 'C-E2E-001',
    name: 'Alice Candidate',
    profileScore: 88,
    profileGrade: 'excellent',
    matchScore: 92,
    comprehensiveScore: 91,
    source: 'mail',
    sourceLabel: 'mail',
    ageDays: 3,
    status: 'available',
    statusLabel: 'available',
    edu: 'Bachelor',
    years: '5+',
    skills: ['Java', 'SQL'],
    processStatus: 0,
    matchReason: 'Skill match is high',
    matchDetail: 'Experience and required skills are aligned.',
  },
  {
    id: 'C-E2E-002',
    name: 'Bob Candidate',
    profileScore: 62,
    profileGrade: 'baseline',
    matchScore: 58,
    source: 'boss',
    sourceLabel: 'boss',
    ageDays: 12,
    status: 'available',
    statusLabel: 'available',
    edu: 'Bachelor',
    years: '3-5',
    skills: ['Vue'],
    processStatus: 0,
  },
];

const talentRows = [
  {
    id: 'C-TAL-001',
    name: 'Talent One',
    portrait: 'excellent 90',
    portraitClass: 'score-high',
    edu: 'Bachelor',
    years: '5+',
    skills: ['Java', 'K8s'],
    skillsHtml: '<span class="tag-item tag-hit">Java</span><span class="tag-item tag-hit">K8s</span>',
    company: 'XX Company',
    source: 'mail',
    inDate: '2026-07-24',
    status: 'available',
    statusLabel: 'available',
    locked: false,
    note: '',
  },
  {
    id: 'C-TAL-002',
    name: 'Talent Two',
    portrait: 'medium 76',
    portraitClass: 'score-mid',
    edu: 'Master',
    years: '3-5',
    skills: ['Vue'],
    skillsHtml: '<span class="tag-item tag-hit">Vue</span>',
    company: 'YY Company',
    source: 'boss',
    inDate: '2026-07-23',
    status: 'available',
    statusLabel: 'available',
    locked: false,
    note: '',
  },
];

const interviews = [
  {
    id: 'INT-E2E-001',
    name: 'Alice Candidate',
    candidateId: 'C-E2E-001',
    position: 'Backend Engineer',
    round: 'Round 1',
    interviewer: 'Interviewer A',
    date: '07-24',
    time: '10:00',
    method: 'online',
    status: 'scheduled',
    statusLabel: 'scheduled',
    isMine: true,
    createdBy: 'E2E Admin',
  },
  {
    id: 'INT-E2E-002',
    name: 'Bob Candidate',
    candidateId: 'C-E2E-002',
    position: 'Frontend Engineer',
    round: 'Round 2',
    interviewer: 'Interviewer B',
    date: '07-24',
    time: '14:00',
    method: 'offline',
    status: 'offer',
    statusLabel: 'offer',
    offerStatus: 1,
    offerNo: 'OF-E2E-001',
    emailSent: true,
    isMine: true,
    createdBy: 'E2E Admin',
  },
];

async function seedAuth(page) {
  await page.addInitScript(() => {
    localStorage.setItem('hr_token', 'e2e-test-token-admin');
    localStorage.setItem('hr_role', 'admin');
    localStorage.setItem('hr_user', 'E2E Admin');
    window.__E2E_DISABLE_TOAST__ = true;
  });
}

async function routeCoreApis(page) {
  await page.route('**/api/auth/login**', (route) => route.fulfill({
    json: ok({ token: 'e2e-test-token-admin', user: { role: 'admin', name: 'E2E Admin' } }),
  }));

  await page.route('**/api/dashboard/**', (route) => route.fulfill({ json: ok({}) }));
  await page.route('**/api/demand/list**', (route) => route.fulfill({
    json: ok([
      { id: 'DM-E2E-001', position: 'Backend Engineer', status: 'open', dept: 'Engineering' },
    ], { total: 1 }),
  }));
  await page.route('**/api/demand/DM-E2E-001**', (route) => route.fulfill({ json: ok(demandDetail) }));
  await page.route('**/api/demand/DM-E2E-001/candidates**', (route) => route.fulfill({ json: ok(demandCandidates) }));
  await page.route('**/api/demand/DM-E2E-001/candidates/*/detail**', (route) => route.fulfill({
    json: ok({
      summary: { profileScore: 88, matchScore: 92, comprehensiveScore: 91 },
      breakdown: {
        match: { reason: 'Skill match is high', detail: 'Experience and required skills are aligned.' },
        profile: { components: { education: { score: 20, max: 25, label: 'Bachelor' } } },
      },
      hardFilter: { passed: true },
    }),
  }));

  await page.route('**/api/talent/list**', (route) => route.fulfill({ json: ok(talentRows, { total: talentRows.length }) }));
  await page.route('**/api/talent/ingest-log**', (route) => route.fulfill({
    json: ok({ items: [{ resumeId: 'R1', candidate: 'Talent One', candidateNo: 'C-TAL-001', source: 'mail', engine: 'deepseek', storageTime: '2026-07-24 10:00' }] }),
  }));
  await page.route('**/api/talent/mail-log**', (route) => route.fulfill({
    json: ok({ items: [{ id: 'M1', ok: true, typeLabel: 'system', subject: 'Interview Notice', sender: 'hr@example.com', recipient: 'alice@example.com', time: '07-24 10:00' }] }),
  }));
  await page.route('**/api/config/email-accounts/sync**', (route) => route.fulfill({ json: ok({ accounts: [] }) }));
  await page.route('**/api/config/**', (route) => route.fulfill({ json: ok([]) }));
  await page.route('**/api/ai/**', (route) => route.fulfill({ json: ok({}) }));

  await page.route('**/api/interview/list**', (route) => route.fulfill({
    json: ok(interviews, { total: interviews.length, page: 1, pageSize: 20 }),
  }));
  await page.route('**/api/interview/alerts**', (route) => route.fulfill({ json: ok([]) }));
  await page.route('**/api/interview/calendar**', (route) => route.fulfill({
    json: ok({
      month: '2026-07',
      monthStart: '2026-07-01',
      monthEnd: '2026-07-31',
      events: interviews.map((item) => ({
        id: item.id,
        title: item.name,
        position: item.position,
        round: item.round,
        interviewer: item.interviewer,
        method: item.method,
        status: item.status,
        statusLabel: item.statusLabel,
        start: `2026-07-24T${item.time}:00`,
        end: `2026-07-24T${item.time === '10:00' ? '11:00' : '15:00'}:00`,
      })),
    }),
  }));
}

test.beforeEach(async ({ page }) => {
  await seedAuth(page);
  await routeCoreApis(page);
});

test('login routes authenticated users to the new home page', async ({ page }) => {
  await page.goto('/login');
  await page.locator('[data-role="hr"]').click();
  await page.locator('.btn-login').click();
  await expect(page).toHaveURL(/\/home$/);
  await expect(page.locator('.metric-row')).toBeVisible();
});

test('main Vue routes render inside the workbench shell', async ({ page }) => {
  for (const path of ['/home', '/recruit-dashboard', '/recruit-demand', '/recruit-talent', '/recruit-interview', '/recruit-ai', '/recruit-config']) {
    await page.goto(path);
    await expect(page.locator('#sidebar')).toBeVisible();
    await expect(page.locator('.content')).toBeVisible();
  }
});

test('talent page keeps the current paginated library workflow alive', async ({ page }) => {
  await page.goto('/recruit-talent');
  await expect(page.locator('.tabs')).toBeVisible();
  await expect(page.locator('.mail-sync-btn')).toBeVisible();
  await expect(page.locator('.pagination-bar')).toBeVisible();
  await expect(page.locator('.ext-check:not([disabled])').first()).toBeVisible();
  await page.locator('.ext-check:not([disabled])').first().check();
  await expect(page.locator('#batchBarExt')).toBeVisible();
  await page.getByRole('button').filter({ hasText: /contact|Contact|联系|鑱旂郴/ }).first().click();
  await expect(page.locator('.contact-modal')).toBeVisible();
});

test('config page owns the mail monitor panels after the cleanup', async ({ page }) => {
  await page.goto('/recruit-config');
  const mailMonitor = page.locator('.accordion').filter({ has: page.locator('.pipeline-panel') }).first();
  await mailMonitor.locator('.accordion-header').click();
  const panels = mailMonitor.locator('.pipeline-panel');
  await expect(panels).toHaveCount(2);
  await expect(panels.nth(0)).toBeVisible();
  await expect(panels.nth(1)).toBeVisible();
  const heights = await panels.evaluateAll((nodes) => nodes.map((node) => node.getBoundingClientRect().height));
  expect(heights[1]).toBeGreaterThanOrEqual(Math.min(heights[0], 300));
});

test('demand detail requires an id and opens current candidate modals when data exists', async ({ page }) => {
  await page.goto('/recruit-demand-detail?id=DM-E2E-001');
  await expect(page.locator('#candidateTable')).toBeVisible();
  const firstRow = page.locator('#candidateTable tbody tr').first();

  await firstRow.locator('button').nth(0).click();
  await expect(page.locator('.demand-match-drawer-overlay .drawer-panel')).toBeVisible();
  await expect(page.locator('.demand-match-drawer-overlay .drawer-kpis')).toBeVisible();
  await page.locator('.demand-match-drawer-overlay .drawer-header button').click();

  await firstRow.locator('button').nth(1).click();
  await expect(page.locator('.comm-modal')).toBeVisible();
  await page.locator('.comm-modal .drawer-close').click();

  await firstRow.locator('button').nth(2).click();
  await expect(page.locator('.schedule-modal')).toBeVisible();
});

test('interview page exposes the embedded calendar and row action modal', async ({ page }) => {
  await page.goto('/recruit-interview');
  await expect(page.getByTestId('interview-calendar-card')).toBeVisible();
  await expect(page.getByTestId('calendar-month-select')).toBeVisible();
  await expect(page.getByTestId('calendar-week-select')).toBeVisible();
  await expect(page.locator('.inline-calendar-day')).toHaveCount(7);
  await expect(page.getByTestId('calendar-agenda')).toContainText('Alice Candidate');

  const aliceRow = page.locator('tr', { hasText: 'Alice Candidate' }).first();
  await aliceRow.locator('button').nth(1).click();
  await expect(page.getByTestId('interview-action-modal')).toBeVisible();
  await expect(page.getByTestId('interview-action-option')).toHaveCount(2);
});

test('interview rows support multi-select clear and delete confirmation', async ({ page }) => {
  const deleted = [];
  await page.route('**/api/interview/INT-E2E-*', (route) => {
    if (route.request().method() === 'DELETE') {
      deleted.push(new URL(route.request().url()).pathname.split('/').pop());
      return route.fulfill({ json: ok({ deleted: true }) });
    }
    return route.fallback();
  });

  await page.goto('/recruit-interview');
  const activePanel = page.locator('.tab-panel.active').first();
  const checks = activePanel.getByTestId('interview-row-check');
  await expect(checks).toHaveCount(2);
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
  await expect.poll(() => deleted.sort().join(',')).toBe('INT-E2E-001,INT-E2E-002');
});
