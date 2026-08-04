// Capture README screenshots of all main pages with real backend data.
// Usage: ensure dev server on :5173 and backend on :5000, then `node scripts/capture-readme-shots.mjs`
import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const BASE = 'http://127.0.0.1:5173';
const OUT = resolve(dirname(fileURLToPath(import.meta.url)), '../../docs/screenshots');
mkdirSync(OUT, { recursive: true });

// Real login so pages render live data instead of mock fallback
const loginResp = await fetch(`${BASE}/api/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: 'admin123' }),
});
const loginBody = await loginResp.json();
const token = loginBody?.data?.token;
if (!token) throw new Error(`login failed: ${JSON.stringify(loginBody)}`);
console.log('logged in, token acquired');

const browser = await chromium.launch();
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1.5 });

async function shot(name, path, { auth = true, after } = {}) {
  const page = await ctx.newPage();
  if (auth) {
    await page.addInitScript((t) => {
      localStorage.setItem('hr_token', t);
      localStorage.setItem('hr_role', 'admin');
      localStorage.setItem('hr_user', '管理员');
      window.__E2E_DISABLE_TOAST__ = true;
    }, token);
  }
  await page.goto(`${BASE}${path}`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(1500); // let charts / three.js settle
  if (after) await after(page);
  await page.screenshot({ path: resolve(OUT, name) });
  console.log('saved', name);
  await page.close();
}

await shot('01-login.png', '/login', { auth: false });
await shot('02-dashboard.png', '/recruit-dashboard');
await shot('03-demand.png', '/recruit-demand');
await shot('04-demand-detail.png', '/recruit-demand', {
  after: async (page) => {
    // open the first demand via its 查看详情 link to reach the detail page
    const link = page.getByText('查看详情').first();
    if (await link.count()) {
      await link.click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(1500);
    }
  },
});
await shot('05-talent.png', '/recruit-talent');
await shot('06-interview.png', '/recruit-interview');
await shot('07-ai.png', '/recruit-ai');
await shot('08-config.png', '/recruit-config');

await browser.close();
console.log('done ->', OUT);
