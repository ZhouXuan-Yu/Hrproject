// data/ai.js — 招聘辅助中心页面 mock 数据

export const AI_TABS = [
  { id: 'jd', number: '①', title: 'JD 草稿生成' },
  { id: 'search', number: '②', title: '语义简历搜索' },
  { id: 'match', number: '③', title: '人岗匹配工作台' },
  { id: 'interview', number: '④', title: '面试辅助' },
  { id: 'report', number: '⑤', title: '招聘深度报表' },
  { id: 'chat', number: '⑥', title: '候选人沟通助手' },
];

// --- Mock dropdown data ---
export const MOCK_CANDIDATES = [
  { id: 'C2026070012', name: '张三', title: '高级Java工程师', dept: '技术部', company: '阿里巴巴', years: 5, edu: '本科' },
  { id: 'C2026070010', name: '郑一', title: '前端架构师', dept: '技术部', company: '美团', years: 4, edu: '本科' },
  { id: 'C2026070011', name: '李四', title: '全栈工程师', dept: '技术部', company: '腾讯', years: 3, edu: '硕士' },
  { id: 'C2026070007', name: '王五', title: '高级数据分析师', dept: '数据部', company: '网易', years: 3, edu: '硕士' },
  { id: 'C2026070009', name: '孙九', title: '后端开发专家', dept: '技术部', company: '字节跳动', years: 6, edu: '本科' },
  { id: 'EMP001', name: '王工', title: '高级Java工程师', dept: '技术部', company: '内部员工', years: 3, edu: '硕士' },
  { id: 'EMP023', name: '钱工', title: '高级产品经理', dept: '产品部', company: '内部员工', years: 5, edu: '硕士' },
];

export const MOCK_DEMANDS = [
  { id: 'DM2026070005', name: '高级Java工程师', dept: '技术部', status: '招聘中' },
  { id: 'DM2026070004', name: '产品经理', dept: '产品部', status: '审批中' },
  { id: 'DM2026070003', name: '运营总监', dept: '运营部', status: '招聘中' },
  { id: 'DM2026070002', name: '前端工程师', dept: '技术部', status: '已关闭' },
  { id: 'DM2026070006', name: '数据分析师', dept: '数据部', status: '招聘中' },
];

export const MOCK_DEPARTMENTS = ['技术部', '产品部', '运营部', '数据部', '财务部', '人力资源部'];

// --- Mock workflow results ---

export const MOCK_JD_RESULT = {
  jd_text: '',
  position: '高级Java工程师',
  department: '技术部',
  responsibilities: [
    '负责核心业务系统的架构设计与技术选型，确保系统高可用、高扩展性',
    '主导微服务架构的拆分与演进，制定服务治理规范和接口标准',
    '参与需求评审与代码review，保障代码质量和团队技术水平持续提升',
    '解决生产环境中的性能瓶颈和疑难技术问题，编写技术方案文档',
    '指导初中级工程师成长，推动团队内部技术分享和最佳实践落地',
  ],
  required_skills: [
    { name: 'Java', weight: '必须', description: '5年以上Java开发经验，熟悉JVM调优' },
    { name: '微服务架构', weight: '必须', description: 'Spring Boot/Spring Cloud或Dubbo实际项目经验' },
    { name: '数据库', weight: '必须', description: 'MySQL优化经验，熟悉Redis、Elasticsearch' },
    { name: '分布式系统', weight: '必须', description: '掌握消息队列(Kafka/RocketMQ)和分布式事务方案' },
  ],
  plus_skills: [
    { name: 'Kubernetes', description: '有K8s生产环境使用和排障经验' },
    { name: '大数据', description: '了解Spark/Flink等大数据处理框架' },
    { name: '多语言', description: '熟悉Go或Python，能做跨语言技术方案' },
  ],
  qualifications: {
    education: '本科及以上学历，计算机相关专业',
    experience: '5年以上Java后端开发经验，有大厂背景优先',
    industry: '互联网/金融/电商行业背景优先',
    soft: '良好的沟通能力和团队协作精神，有技术团队管理经验者加分',
  },
  disclaimer: '此内容由AI生成，请人工审核确认后使用',
};

export const MOCK_SEARCH_RESULTS = [
  {
    id: 'C2026070012', name: '张三', portraitScore: 88, matchScore: 92,
    match_reasons: ['Java技术栈完全匹配', '5年大厂经验（阿里巴巴）', '有微服务架构实际项目经验', 'K8s生产环境使用经验'],
  },
  {
    id: 'C2026070009', name: '孙九', portraitScore: 80, matchScore: 85,
    match_reasons: ['后端开发专家，6年经验', '熟悉Kafka等消息队列', '字节跳动背景，高并发场景经验', 'Go语言技术栈补充'],
  },
  {
    id: 'EMP001', name: '王工', portraitScore: 92, matchScore: 78,
    match_reasons: ['内部员工，画像评分A级', 'Java + K8s技术栈', '当前岗位高级Java工程师', '绩效A，内部调动优先'],
  },
];

export const MOCK_MATCH_RESULT = {
  profile_score: 88,
  match_score: 92,
  overall_score: 90,
  grade: 'A',
  reasons: [
    '候选人技术栈(Java/K8s/微服务)与岗位要求高度一致',
    '5年大厂经验，项目复杂度与岗位需求匹配',
    '学历背景(本科/计算机)满足岗位要求',
    '工作稳定性好，跳槽频率合理',
  ],
  missing_skills: [
    { skill: 'Go语言', importance: '加分项', note: '岗位描述中提及但不强制' },
    { skill: '大数据处理经验', importance: '加分项', note: 'Spark/Flink相关项目经验' },
  ],
  strengths: [
    'Java和微服务架构深度经验，直接对位岗位核心需求',
    '阿里巴巴P7级别，有大规模分布式系统实战经验',
    'K8s生产环境使用经验，运维能力强',
  ],
  disclaimer: '此内容由AI生成，请人工审核确认后使用',
};

export const MOCK_INTERVIEW_QUESTIONS = {
  questions: [
    {
      question: '请描述你在阿里巴巴负责的最复杂的微服务项目，你在其中承担什么角色？遇到过哪些技术难题？',
      dimension: '项目经验',
      expected_answer_hints: '关注候选人是否能够清晰描述项目背景、个人贡献、技术难点和解决思路。重点考察系统规模(日活/QPS/数据量)和架构决策能力。',
    },
    {
      question: '如果让你设计一个支持10万QPS的秒杀系统，你会如何考虑架构设计？请从流量接入、服务层、数据层分别说明。',
      dimension: '架构设计',
      expected_answer_hints: '期望答案包含：CDN+SLB流量接入、服务层限流熔断降级、缓存预热+异步削峰+最终一致性。考察系统性思考能力和高并发场景经验。',
    },
    {
      question: '你在使用Spring Cloud时遇到过哪些问题？如何解决的？有没有对比过Service Mesh方案？',
      dimension: '技术深度',
      expected_answer_hints: '关注候选人对微服务框架的深度理解，是否了解服务治理(注册发现/配置中心/网关/链路追踪)的底层原理，而不是仅停留在使用层面。',
    },
    {
      question: '假设数据库查询变慢影响线上服务，你会如何排查和优化？请描述你的排查思路。',
      dimension: '问题排查',
      expected_answer_hints: '期望步骤：监控告警确认→慢查询日志分析→执行计划分析→索引优化/SQL改写/读写分离/缓存方案→验证效果。考察实战排障能力。',
    },
    {
      question: '你如何做技术方案评审和代码Review？有没有带过团队或指导过初中级工程师？请举一个具体例子。',
      dimension: '团队协作',
      expected_answer_hints: '期望候选人有团队管理或指导经验，能说出具体的协作流程、Code Review标准和技术分享实践。考察沟通和领导力。',
    },
  ],
  disclaimer: '此内容由AI生成，请人工审核确认后使用',
};

export const MOCK_COMMUNICATION_DRAFT = {
  draft: `您好，我是XX公司HR部门的小张。注意到您在招聘平台上对「高级Java工程师」岗位表达了兴趣，想跟您做一个初步沟通。

我们公司目前正在扩展核心业务系统，技术栈以Java为主，微服务架构已经落地两年，目前在向Service Mesh方向演进。团队有30+人的技术团队，技术氛围浓厚。

想了解一下：
1. 您目前的状态——还在看机会吗？大概什么时候方便到岗？
2. 您对下一份工作的主要期待是什么（技术方向/团队规模/工作内容/薪资范围）？
3. 方便的话可以发一份您的最新简历给我。

期待您的回复。`,
  suggestions: [
    '首次联系建议在工作日上午10:00-11:30或下午14:30-17:00发送',
    '如果候选人2天未回复，可以在飞书或微信上做一次跟进提醒',
    '建议在联系前先查看候选人完整简历，准备2-3个针对性话题',
    '注意：薪资范围应在双方意向明确后再讨论，初次联系不建议主动报薪资',
  ],
  disclaimer: '此内容由AI生成，请人工审核确认后使用',
};

export const MOCK_REPORT_RESULT = {
  summary: '2026年7月1日-7月15日招聘数据分析报告。本周期共启动招聘需求12个，收到简历286份，进入面试环节48人，发放Offer 8份，最终入职3人。整体招聘漏斗转化率为1.05%，较上月(1.2%)略有下降。',
  insights: [
    '技术部「高级Java工程师」岗位简历量最大(86份)，但面试转化率偏低(12.8%)，远低于平均线(16.8%)，建议与用人部门沟通JD准确性和面试评价标准',
    '猎聘渠道虽然简历量少(42份)，但Offer率最高(4.8%)，人均成本4920元，性价比优于Boss直聘(人均成本6380元)',
    '终面环节平均耗时4.2天，为主要瓶颈节点，建议协调面试官时间或增加面试官资源',
    'Offer阶段拒绝主因：薪资竞争力不足(3例)、通勤距离远(1例)、其他Offer(1例)',
  ],
  anomalies: [
    '数据处理工程师岗位：投放14天收到简历仅12份，远低于预期，建议检查JD关键词优化和渠道投放策略',
    '产品经理岗位面试通过率异常高(40%)，与行业均值(25%)偏差较大，建议Review面试评价标准一致性',
  ],
  recommendations: [
    '调整渠道预算分配：增加猎聘投放比例至35%(当前20%)，Boss直聘降至50%(当前65%)',
    '高级Java工程师面试流程优化：增加技术笔试环节，减少无效面试轮次',
    '建立面试评价标准化Checklist，减少不同面试官评价偏差',
    '对于终面环节卡顿的岗位，启用临时面试官补充机制',
  ],
  disclaimer: '此内容由AI生成，请人工审核确认后使用',
};

// --- Embedded AI capabilities table (kept from original) ---

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
