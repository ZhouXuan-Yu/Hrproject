// data/demand.js — 需求管理页面 mock 数据
// 提取自 public/legacy/recruit-demand.html

export const DEMANDS = [
  {
    id: 'DM2026070006', position: '运营总监', dept: '运营部', hc: 1,
    urgency: 'very', urgencyLabel: '非常紧急', urgencyType: 'reject',
    submitter: '陈总', status: 'approval', statusLabel: '审批中', statusType: 'warn',
    approvalNodes: [
      { label: '部门负责人', state: 'current' },
      { label: 'HR', state: 'pending' },
      { label: '财务总监', state: 'pending' },
    ],
    linkedCount: 0,
  },
  {
    id: 'DM2026070004', position: '产品经理', dept: '产品部', hc: 1,
    urgency: 'normal', urgencyLabel: '普通', urgencyType: 'draft',
    submitter: '周博', status: 'approval', statusLabel: '审批中', statusType: 'warn',
    approvalNodes: [
      { label: '部门负责人', state: 'done' },
      { label: 'HR', state: 'current' },
      { label: '财务总监', state: 'pending' },
    ],
    linkedCount: 0,
  },
  {
    id: 'DM2026070005', position: '高级Java工程师', dept: '技术部', hc: 2,
    urgency: 'high', urgencyLabel: '紧急', urgencyType: 'warn',
    submitter: '刘博', status: 'open', statusLabel: '招聘中', statusType: 'progress',
    approvalNodes: [
      { label: '部门负责人', state: 'done' },
      { label: 'HR', state: 'done' },
      { label: '财务总监', state: 'done' },
    ],
    directApply: 4, systemRecommend: 5, internalMatch: 2,
    internalNames: ['王工·92', '赵工·42'],
    interviewing: 5,
    linkedCount: 0,
  },
  {
    id: 'DM2026070003', position: '运营总监', dept: '运营部', hc: 1,
    urgency: 'very', urgencyLabel: '非常紧急', urgencyType: 'reject',
    submitter: '陈总', status: 'open', statusLabel: '招聘中', statusType: 'progress',
    approvalNodes: [
      { label: '部门负责人', state: 'done' },
      { label: 'HR', state: 'done' },
      { label: '财务总监', state: 'done' },
    ],
    directApply: 1, systemRecommend: 0, internalMatch: 0,
    internalNames: [],
    interviewing: 0,
    linkedCount: 0,
  },
  {
    id: 'DM2026070002', position: '前端工程师', dept: '技术部', hc: 3,
    urgency: 'high', urgencyLabel: '紧急', urgencyType: 'warn',
    submitter: '刘博', status: 'closed', statusLabel: '已关闭', statusType: 'draft',
    approvalNodes: [
      { label: '部门负责人', state: 'done' },
      { label: 'HR', state: 'done' },
      { label: '财务总监', state: 'done' },
    ],
    linkedCount: 0,
  },
  {
    id: 'DM2026070001', position: '数据分析师', dept: '数据部', hc: 1,
    urgency: 'normal', urgencyLabel: '普通', urgencyType: 'draft',
    submitter: '陈博', status: 'draft', statusLabel: '草稿', statusType: 'draft',
    approvalNodes: [],
    linkedCount: 0,
  },
];

// Pre-compute linked candidate counts from localStorage
export function getLinkedCount(demandId){
  try {
    const key = 'demand_' + demandId + '_linked';
    const data = JSON.parse(localStorage.getItem(key));
    return data ? data.length : 0;
  } catch(e){ return 0; }
}
