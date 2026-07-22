// 关键链路 E2E：面试计划页的状态显示契约
// 覆盖本轮修复的核心场景：
//  1. 评价拒绝的面试显示「已淘汰 · 回流人才库」，绝不显示「已入职」
//  2. Offer 已发送的面试不再出现「发Offer」按钮，提示等待候选人确认
//  3. 候选人接受 Offer 后显示「已录用，待入职」
import { expect, test } from '@playwright/test';

const mockList = [
  {
    id: 'INT9001', name: '淘汰甲', candidateId: 'C9001', resumeId: 901, demandId: 2,
    position: '高级Java工程师', round: '初试(1轮)', interviewer: '待分配',
    date: '07-20', time: '10:00', method: '线下', meetingUrl: '',
    status: 'done', statusLabel: '已淘汰', result: 'reject', score: 55,
    offerStatus: null, offerNo: null, candidateConfirm: null, emailSent: false,
    createdBy: '系统', isMine: false,
  },
  {
    id: 'INT9002', name: '待发乙', candidateId: 'C9002', resumeId: 902, demandId: 2,
    position: '产品经理', round: '终面(2轮)', interviewer: '待分配',
    date: '07-21', time: '14:00', method: '线下', meetingUrl: '',
    status: 'offer', statusLabel: '待录用', result: 'pass', score: 88,
    offerStatus: null, offerNo: null, candidateConfirm: null, emailSent: false,
    createdBy: '系统', isMine: false,
  },
  {
    id: 'INT9003', name: '确认丙', candidateId: 'C9003', resumeId: 903, demandId: 2,
    position: '前端工程师', round: '终面(2轮)', interviewer: '待分配',
    date: '07-22', time: '09:00', method: '线下', meetingUrl: '',
    status: 'offer', statusLabel: 'Offer待确认', result: 'pass', score: 90,
    offerStatus: 1, offerNo: 'OF999001', candidateConfirm: null, emailSent: true,
    createdBy: '系统', isMine: false,
  },
  {
    id: 'INT9004', name: '录用丁', candidateId: 'C9004', resumeId: 904, demandId: 2,
    position: '数据分析师', round: '终面(2轮)', interviewer: '待分配',
    date: '07-22', time: '11:00', method: '线下', meetingUrl: '',
    status: 'onboard', statusLabel: '已录用', result: 'pass', score: 92,
    offerStatus: 2, offerNo: 'OF999002', candidateConfirm: 'accept', emailSent: true,
    createdBy: '系统', isMine: false,
  },
];

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('hr_token', 'e2e-test-token-admin');
    localStorage.setItem('hr_role', 'admin');
    localStorage.setItem('hr_user', '测试用户');
    window.__E2E_DISABLE_TOAST__ = true;
  });
  await page.route('**/api/interview/list**', (route) =>
    route.fulfill({ json: { code: 0, message: 'ok', data: mockList, total: mockList.length, page: 1, pageSize: 20 } })
  );
  await page.route('**/api/interview/alerts**', (route) =>
    route.fulfill({ json: { code: 0, message: 'ok', data: [] } })
  );
});

test('淘汰的面试显示「已淘汰 · 回流人才库」而不是「已入职」', async ({ page }) => {
  await page.goto('/recruit-interview');
  const row = page.locator('tr', { hasText: '淘汰甲' });
  await expect(row).toContainText('已淘汰');
  await expect(row).toContainText('已淘汰 · 回流人才库');
  await expect(row).not.toContainText('已入职');
});

test('Offer 已发送后不再显示「发Offer」按钮，提示等待候选人确认', async ({ page }) => {
  await page.goto('/recruit-interview');
  const sentRow = page.locator('tr', { hasText: '确认丙' });
  await expect(sentRow).toContainText('等待候选人确认');
  await expect(sentRow).not.toContainText('发Offer');
  await expect(sentRow).toContainText('OF999001');

  // 未发 Offer 的通过面试仍保留发Offer入口
  const pendingRow = page.locator('tr', { hasText: '待发乙' });
  await expect(pendingRow).toContainText('发Offer');
});

test('候选人接受 Offer 后显示「已录用，待入职」', async ({ page }) => {
  await page.goto('/recruit-interview');
  const row = page.locator('tr', { hasText: '录用丁' });
  await expect(row).toContainText('已录用');
  await expect(row).toContainText('已录用，待入职');
  await expect(row).not.toContainText('已入职 ·');
});
