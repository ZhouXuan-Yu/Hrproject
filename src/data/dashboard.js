// data/dashboard.js — 招聘看板页面 mock 数据
// 提取自 public/legacy/recruit-dashboard.html

export const KPI_SETS = {
  admin: [
    { val: 8, label:'全公司待面试', icon:'<svg viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>' },
    { val: 12, label:'待评价', icon:'<svg viewBox="0 0 24 24"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>' },
    { val: 8, label:'在招岗位', icon:'<svg viewBox="0 0 24 24"><rect x="2" y="7" width="20" height="14" rx="2"/><path d="M8 7V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>' },
    { val: 5, label:'本月入职总量', icon:'<svg viewBox="0 0 24 24"><path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/><polyline points="15,2 9,2 9,16 12,14 15,16"/></svg>' }
  ],
  interviewer: [
    { val: 2, label:'本人待面试', icon:'<svg viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>' },
    { val: 1, label:'待评价', icon:'<svg viewBox="0 0 24 24"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>' },
    { val: 8.5, label:'均分', icon:'<svg viewBox="0 0 24 24"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26"/></svg>' },
    { val: 0, label:'已完成', icon:'<svg viewBox="0 0 24 24"><polyline points="20 6 9 17 4 12"/></svg>' }
  ],
  hr: [
    { val: 3, label:'本人今日待面试', icon:'<svg viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>' },
    { val: 5, label:'待评价面试', icon:'<svg viewBox="0 0 24 24"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>' },
    { val: 7, label:'待跟进候选人', icon:'<svg viewBox="0 0 24 24"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>' },
    { val: 2, label:'待审批岗位', icon:'<svg viewBox="0 0 24 24"><rect x="2" y="7" width="20" height="14" rx="2"/><path d="M8 7V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>' }
  ]
};

export const FUNNEL_STEPS = [
  { label: '收简历', count: 346, pct: '100%', link: '/recruit-talent', opacity: 1 },
  { label: '筛选通过', count: 89, pct: '25.7%', link: '/recruit-demand', opacity: 0.85 },
  { label: '面试', count: 42, pct: '12.1%', link: '/recruit-interview', opacity: 0.72 },
  { label: 'Offer', count: 8, pct: '2.3%', link: '/recruit-interview', opacity: 0.58 },
  { label: '入职', count: 5, pct: '1.4%', link: '/recruit-demand', opacity: 1, color: 'var(--c-done)' }
];

export const DEPT_PROGRESS = [
  { dept: '技术部', hired: 3, total: 5, pct: 60 },
  { dept: '产品部', hired: 1, total: 3, pct: 33 },
  { dept: '运营部', hired: 0, total: 2, pct: 0 },
  { dept: '数据部', hired: 2, total: 2, pct: 100 }
];

export const CHANNEL_DATA = [
  { channel: '邮箱采集', resume: 120, pass: 35, interview: 18, hire: 2, cost: '¥0' },
  { channel: 'Boss 直聘', resume: 98, pass: 28, interview: 12, hire: 1, cost: '¥8K' },
  { channel: '猎聘', resume: 65, pass: 15, interview: 7, hire: 1, cost: '¥12K' },
  { channel: '内推', resume: 42, pass: 8, interview: 4, hire: 1, cost: '¥3K' }
];

export const RISK_ALERTS = [
  { text: '运营部·运营总监 — 发布20天零简历', type: 'reject', link: '/recruit-demand-detail', action: '查看' },
  { text: '技术部·前端工程师 — HC仅剩1个', type: 'warn', link: '/recruit-demand-detail', action: '查看' },
  { text: '3名候选人超7天未安排面试', type: 'warn', link: '/recruit-interview', action: '安排' },
  { text: '数据部·数据分析师 — 昨日已招满', type: 'done', link: '/recruit-demand-detail', action: '查看' }
];
