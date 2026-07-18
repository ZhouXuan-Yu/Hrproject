// data/config.js — 招聘基础配置页面 mock 数据
// 提取自 public/legacy/recruit-config.html

export const EMAIL_ACCOUNTS = [
  { address: 'hr-recruit@company.com', type: '企业邮箱', freq: '每 30 分钟', status: '正常', statusColor: 'done', lastSync: '07-14 14:30' },
  { address: 'hr-recruit@qq.com', type: 'QQ 邮箱', freq: '每 60 分钟', status: '异常', statusColor: 'warn', lastSync: '07-13 09:00' },
];

export const CHANNELS = [
  { code: 'BOSS', name: 'Boss直聘', type: '招聘平台', cost: '¥8,000', status: '启用' },
  { code: 'LIEPIN', name: '猎聘', type: '猎头平台', cost: '¥12,000', status: '启用' },
  { code: 'EMAIL', name: '邮箱采集', type: '自动管道', cost: '¥0', status: '启用' },
  { code: 'NEITUI', name: '内部推荐', type: '内部渠道', cost: '¥3,000', status: '启用' },
];

export const SCORE_RULES = {
  profileWeight: 0.10,
  matchWeight: 0.90,
  decay30: 1.0,
  decay90: 0.85,
  decayOver90: 0.70,
  passLine: 60,
  topCount: 5,
  searchRange: '近 3 个月',
};

export const NOTIFY_TEMPLATES = [
  { name: '面试邀请通知', type: '面试', method: '飞书 + 短信', updated: '07-10' },
  { name: 'Offer 发送模板', type: 'Offer', method: '邮件', updated: '07-08' },
  { name: '未通过通知', type: '淘汰', method: '短信', updated: '07-01' },
  { name: '面试提醒（前一天）', type: '提醒', method: '飞书 + 短信', updated: '06-28' },
];

export const ROLE_PERMISSIONS = [
  { role: '管理员', badgeClass: 'role-admin', menus: '全部 6 项', dataScope: '全量无隔离', ops: '全部' },
  { role: 'HR 专员', badgeClass: 'role-hr', menus: '看板/需求/人才库/面试', dataScope: '全公司', ops: 'CRUD + 发Offer' },
  { role: '面试官', badgeClass: 'role-interviewer', menus: '看板/面试计划', dataScope: '仅自己场次', ops: '填评价' },
  { role: '部门负责人', badgeClass: '', menus: '看板/需求管理', dataScope: '本部门', ops: '审批需求', style: 'background:#f1f5f9;color:#475569' },
  { role: '基层员工', badgeClass: '', menus: '看板/需求管理', dataScope: '仅自己的需求', ops: '提交需求', style: 'background:#f1f5f9;color:#475569' },
  { role: '无权限员工', badgeClass: '', menus: '侧边栏隐藏', dataScope: '—', ops: '—', style: 'background:#fef2f2;color:#991b1b' },
];

export const AUDIT_LOGS = [
  { time: '07-14 14:30', user: '张HR', module: '面试', action: '发起面试', detail: '张三 → 高级Java工程师初试，面试官李面试官' },
  { time: '07-14 11:20', user: '李面试官', module: '面试', action: '提交评价', detail: '郑一·前端终面·通过' },
  { time: '07-14 10:05', user: '张HR', module: '需求', action: '新建需求', detail: 'DM2026070005 高级Java工程师·技术部·2人' },
  { time: '07-14 09:00', user: '系统', module: '邮件', action: '自动同步', detail: 'hr-recruit@company.com 拉取 3 封邮件，识别 2 封简历' },
];

export const EMAIL_PRESETS = {
  qq:     { server: 'imap.qq.com', port: '993', proto: 'IMAP（推荐）', ssl: 'SSL/TLS' },
  '163':  { server: 'imap.163.com', port: '993', proto: 'IMAP（推荐）', ssl: 'SSL/TLS' },
  gmail:  { server: 'imap.gmail.com', port: '993', proto: 'IMAP（推荐）', ssl: 'SSL/TLS' },
  corp:   { server: 'outlook.office365.com', port: '993', proto: 'IMAP（推荐）', ssl: 'SSL/TLS' },
  custom: { server: '', port: '', proto: 'IMAP（推荐）', ssl: 'SSL/TLS' },
};
