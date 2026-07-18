// data/interview.js — 面试计划页面 mock 数据
// 提取自 public/legacy/recruit-interview.html

export const ALL_INTERVIEWS = [
  {name:'王五', position:'数据分析师', round:'初试(1轮)', interviewer:'张HR', date:'待定', time:'待定', method:'待定', status:'pending', statusLabel:'待安排', createdBy:'张HR', isMine:true},
  {name:'吴六', position:'高级Java工程师', round:'初试(1轮)', interviewer:'待分配', date:'待定', time:'待定', method:'待定', status:'pending', statusLabel:'待安排', createdBy:'张HR', isMine:true},
  {name:'赵十', position:'高级Java工程师', round:'初试(1轮)', interviewer:'待分配', date:'待定', time:'待定', method:'待定', status:'pending', statusLabel:'待安排', createdBy:'刘博', isMine:false},
  {name:'钱七', position:'产品经理', round:'初试(1轮)', interviewer:'待分配', date:'待定', time:'待定', method:'待定', status:'pending', statusLabel:'待安排', createdBy:'张HR', isMine:true},
  {name:'张三', position:'高级Java工程师', round:'初试(1/3轮)', interviewer:'李面试官', date:'07-16', time:'14:00', method:'飞书视频', status:'scheduled', statusLabel:'待面试', createdBy:'张HR', isMine:false},
  {name:'李四', position:'前端工程师', round:'初试(1/2轮)', interviewer:'王面试官', date:'07-17', time:'10:00', method:'飞书视频', status:'scheduled', statusLabel:'待面试', createdBy:'张HR', isMine:false},
  {name:'张三', position:'高级Java工程师', round:'复试(2/3轮)', interviewer:'李面试官', date:'07-22', time:'10:00', method:'现场', status:'scheduled', statusLabel:'待面试', createdBy:'张HR', isMine:false},
  {name:'王五', position:'数据分析师', round:'初试(2轮)', interviewer:'张HR', date:'07-18', time:'11:00', method:'电话', status:'scheduled', statusLabel:'待面试', createdBy:'张HR', isMine:true},
  {name:'钱工', position:'高级Java工程师', round:'内部面试', interviewer:'张HR', date:'07-19', time:'14:00', method:'现场', status:'scheduled', statusLabel:'待面试', createdBy:'张HR', isMine:true},
  {name:'王工', position:'高级Java工程师', round:'内部面试', interviewer:'刘博', date:'07-20', time:'09:00', method:'飞书视频', status:'scheduled', statusLabel:'待面试', createdBy:'刘博', isMine:false},
  {name:'李四', position:'前端工程师', round:'复试(2/2轮)', interviewer:'王面试官', date:'07-25', time:'15:00', method:'飞书视频', status:'scheduled', statusLabel:'待面试', createdBy:'张HR', isMine:false},
  {name:'周明', position:'高级Java工程师', round:'初试(1/3轮)', interviewer:'李面试官', date:'07-21', time:'13:00', method:'现场', status:'scheduled', statusLabel:'待面试', createdBy:'刘博', isMine:false},
  {name:'孙九', position:'高级Java工程师', round:'复试(2/3轮)', interviewer:'张HR', date:'07-13', time:'10:00', method:'现场', status:'evaluating', statusLabel:'待评价', createdBy:'张HR', isMine:true},
  {name:'陈二', position:'产品经理', round:'初试(2/2轮)', interviewer:'张HR', date:'07-14', time:'09:00', method:'电话', status:'evaluating', statusLabel:'待评价', createdBy:'张HR', isMine:true},
  {name:'吴六', position:'运营总监', round:'初试(1/3轮)', interviewer:'刘博', date:'07-12', time:'11:00', method:'飞书视频', status:'evaluating', statusLabel:'待评价', createdBy:'刘博', isMine:false},
  {name:'郑一', position:'前端工程师', round:'终面(2/2轮)', interviewer:'王面试官', date:'07-12', time:'15:00', method:'现场', status:'offer', statusLabel:'待录用', createdBy:'王面试官', isMine:false},
  {name:'王五', position:'数据分析师', round:'Offer确认', interviewer:'张HR', date:'07-26', time:'09:30', method:'飞书消息', status:'onboard', statusLabel:'待入职', createdBy:'张HR', isMine:true},
  {name:'周工', position:'产品经理', round:'初试(1/2轮)', interviewer:'张HR', date:'07-05', time:'10:00', method:'现场', status:'done', statusLabel:'已入职', createdBy:'张HR', isMine:true},
  {name:'陈二', position:'产品经理', round:'初试(1/2轮)', interviewer:'张HR', date:'07-10', time:'09:00', method:'电话', status:'done', statusLabel:'已入职', createdBy:'张HR', isMine:true, result:'reject'}
];

export const STATUSES = ['all','pending','scheduled','evaluating','offer','onboard','done'];

export const STATUS_LABELS = {
  all: '全部', pending: '待安排', scheduled: '待面试', evaluating: '待评价',
  offer: '待录用', onboard: '待入职', done: '已入职'
};

export const STATUS_TYPE_MAP = {
  pending: 'draft', scheduled: 'progress', evaluating: 'warn',
  offer: 'done', onboard: 'done', done: 'done'
};

export const ALERTS = [
  { text: '孙九 · 复试超3天未评价', type: 'reject', action: '去评价', actionMsg: '填写对孙九的评价' },
  { text: '陈二 · 初试超5天未评价', type: 'reject', action: '去评价', actionMsg: '填写对陈二的评价' },
  { text: '张三 · 07-16 14:00 初试', type: 'warn', action: '查看', actionMsg: '' },
  { text: '李四 · 07-17 10:00 初试', type: 'warn', action: '查看', actionMsg: '' },
  { text: '郑一 · 已通过，待发Offer', type: 'done', action: '发Offer', actionMsg: '发起Offer' },
  { text: '王工 · 内部面试已通过', type: 'done', action: '发起调岗', actionMsg: '发起调岗' },
];
