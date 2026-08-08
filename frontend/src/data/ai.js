// data/ai.js — 招聘辅助中心页面静态配置

export const AI_TABS = [
  { id: 'jd', number: '①', title: 'JD 草稿生成' },
  { id: 'search', number: '②', title: '语义简历搜索' },
  { id: 'match', number: '③', title: '人岗匹配工作台' },
  { id: 'interview', number: '④', title: '面试辅助' },
  { id: 'report', number: '⑤', title: '招聘深度报表' },
  { id: 'chat', number: '⑥', title: '候选人沟通助手' },
];

// --- Embedded AI capabilities table ---

export const EMBEDDED_AI = [
  { ability: '简历 AI 解析 + 画像生成 + 标签打标', page: '邮件管理 / 人才库上传', trigger: '邮箱定时同步 or 手动上传 PDF/DOCX', workflow: '① 简历画像解析', status: 'done' },
  { ability: '审批通过自动匹配（内外并行）', page: '需求管理', trigger: '三步审批全部通过后系统自动触发', workflow: '② 人岗匹配打分', status: 'done' },
  { ability: 'AI 辅助联系话术', page: '需求详情 / 面试计划弹窗', trigger: '约面前生成电话/邮件/飞书联系话术，人工确认意向后记录结果', workflow: '需新增候选人沟通工作流', status: 'done' },
  { ability: 'AI 面试评价草稿', page: '面试计划', trigger: '面试结束后自动生成', workflow: '③ 面试问题生成（扩展）', status: 'warn' },
  { ability: '简历去重合并', page: '人才库', trigger: '上传/同步时自动检测跨渠道重复', workflow: '① 画像解析（扩展）', status: 'warn' },
  { ability: '简历识别 + 垃圾过滤', page: '邮件管理', trigger: '收邮件时预处理，过滤非简历邮件', workflow: '需新增分类器', status: 'warn' },
  { ability: '<b>Offer 草稿与审批辅助</b>', page: '面试计划 / Offer管理', trigger: '填写审批信息后生成草稿，审批后发送', workflow: '需新增 Offer 工作流', status: 'done' },
  { ability: '<b>入职包草稿与推送辅助</b>', page: '面试计划 / 入职管理', trigger: '候选人接受 Offer 后系统生成入职材料清单，HR 确认后推送', workflow: '需新增入职工作流', status: 'done' },
  { ability: '招聘风险预警', page: '招聘看板', trigger: '页面加载时自动分析', workflow: '规则引擎 + AI 异常检测', status: 'draft' },
];
