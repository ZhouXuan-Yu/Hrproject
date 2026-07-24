import { expect, test } from '@playwright/test';

const ok = (data, extra = {}) => ({ code: 0, message: 'ok', data, ...extra });

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('hr_token', 'e2e-test-token-admin');
    localStorage.setItem('hr_role', 'admin');
    localStorage.setItem('hr_user', 'E2E Admin');
    window.__E2E_DISABLE_TOAST__ = true;
  });
});

test('mail monitor panels live in config and keep useful vertical space', async ({ page }) => {
  await page.route('**/api/config/**', (route) => route.fulfill({ json: ok([]) }));
  await page.route('**/api/talent/ingest-log**', (route) => route.fulfill({
    json: ok({ items: [{ resumeId: 'R1', candidate: 'Alice', candidateNo: 'C1', source: 'mail', engine: 'deepseek', storageTime: '2026-07-24' }] }),
  }));
  await page.route('**/api/talent/mail-log**', (route) => route.fulfill({
    json: ok({ items: [{ id: 'M1', ok: true, typeLabel: 'system', subject: 'Notice', sender: 'hr@example.com', recipient: 'alice@example.com', time: '07-24' }] }),
  }));

  await page.goto('/recruit-config');
  const mailMonitor = page.locator('.accordion').filter({ has: page.locator('.pipeline-panel') }).first();
  await mailMonitor.locator('.accordion-header').click();
  const panels = mailMonitor.locator('.pipeline-panel');
  await expect(panels).toHaveCount(2);
  await expect(panels.nth(0)).toBeVisible();
  await expect(panels.nth(1)).toBeVisible();
  const heights = await panels.evaluateAll((nodes) => nodes.map((node) => node.getBoundingClientRect().height));
  expect(Math.min(...heights)).toBeGreaterThan(120);
});

test('knowledge base loads and saves through the config API', async ({ page }) => {
  let savedPayload = null;

  await page.route('**/api/config/**', (route) => route.fulfill({ json: ok([]) }));
  await page.route('**/api/talent/ingest-log**', (route) => route.fulfill({ json: ok({ items: [] }) }));
  await page.route('**/api/talent/mail-log**', (route) => route.fulfill({ json: ok({ items: [] }) }));
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

test('candidate match view opens a centered rich analysis modal with mocked demand data', async ({ page }) => {
  await page.route('**/api/demand/DM-REG-001**', (route) => route.fulfill({
    json: ok({
      id: 'DM-REG-001',
      position: 'Backend Engineer',
      dept: 'Engineering',
      hc: 1,
      progress: { hired: 0, total: 1, pct: 0 },
      channels: [],
      requiredSkills: ['Java'],
      plusSkills: [],
      approvalNodes: [],
    }),
  }));
  await page.route('**/api/demand/DM-REG-001/candidates**', (route) => route.fulfill({
    json: ok([{
      id: 'C-REG-001',
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
      skills: ['Java'],
      processStatus: 0,
    }]),
  }));
  await page.route('**/api/demand/DM-REG-001/candidates/Alice%20Candidate/detail**', (route) => route.fulfill({
    json: ok({
      summary: { profileScore: 88, matchScore: 92, comprehensiveScore: 91 },
      breakdown: {
        match: { reason: 'Skill match is high', detail: 'Detailed analysis text.' },
        profile: { components: { education: { score: 20, max: 25, label: 'Bachelor' } } },
      },
      hardFilter: { passed: true },
    }),
  }));

  await page.goto('/recruit-demand-detail?id=DM-REG-001');
  await expect(page.locator('#candidateTable')).toBeVisible();
  await page.locator('#candidateTable tbody tr').first().locator('button').first().click();
  const modal = page.locator('.demand-match-drawer-overlay .drawer-panel');
  await expect(modal).toBeVisible();
  await expect(modal.locator('.drawer-kpis')).toBeVisible();
  await expect(modal.locator('.profile-component-grid')).toBeVisible();
  await expect.poll(() => modal.evaluate((el) => getComputedStyle(el).borderRadius)).toBe('10px');
});
