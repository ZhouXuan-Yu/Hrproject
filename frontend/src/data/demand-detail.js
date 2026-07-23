// data/demand-detail.js — 需求详情页面 mock 数据
// 提取自 public/legacy/recruit-demand-detail.html

export const DEMAND_INFO = {
  id: 'DM2026070005',
  position: '高级Java工程师',
  dept: '技术部',
  hc: 2,
  urgency: '紧急',
  salary: '¥15K - ¥25K / 月',
  date: '2026-08-01',
  submitter: '刘博',
  submitDate: '2026-07-12',
  channels: ['Boss直聘', '猎聘', '邮箱采集'],
  progress: { hired: 1, total: 2, pct: 50 },
  description: '负责公司电商中台核心服务的架构设计与开发，主导微服务拆分和容器化改造，参与技术选型和 code review，指导初中级工程师成长。直接汇报给技术总监，有转管理通道。',
  requiredSkills: ['Java · 5年以上', 'Spring Boot/Cloud', 'MySQL 调优', 'Kubernetes', '微服务架构设计', '分布式系统'],
  plusSkills: ['团队管理经验', 'DevOps/CI-CD', '多语言（Go/Python）', '技术博客/开源贡献'],
  approvalNodes: [
    { actor: '刘博', role: '部门负责人', status: '已通过', date: '2026-07-12 14:30' },
    { actor: '张HR', role: 'HR', status: '已通过', date: '2026-07-13 09:15' },
    { actor: '高管', role: '高管', status: '已通过', date: '2026-07-13 16:00' }
  ]
};

export const ALL_CANDIDATES = [
  {name:'张三', profileScore:88, profileGrade:'A', matchScore:92, ageDays:4, source:'direct', sourceLabel:'直接投递', status:'interviewing', statusLabel:'面试中', notRecReason:null},
  {name:'孙九', profileScore:68, profileGrade:'B', matchScore:72, ageDays:3, source:'direct', sourceLabel:'直接投递', status:'available', statusLabel:'可联系', notRecReason:null},
  {name:'刘八', profileScore:48, profileGrade:'C', matchScore:null, ageDays:2, source:'direct', sourceLabel:'直接投递', status:'available', statusLabel:'可联系', notRecReason:'学历不符（大专）'},
  {name:'王五', profileScore:80, profileGrade:'B+', matchScore:86, ageDays:4, source:'external', sourceLabel:'人才库检索', status:'available', statusLabel:'可联系', notRecReason:null},
  {name:'李四', profileScore:76, profileGrade:'B+', matchScore:85, ageDays:5, source:'external', sourceLabel:'人才库检索', status:'available', statusLabel:'可联系', notRecReason:null},
  {name:'吴六', profileScore:78, profileGrade:'B+', matchScore:82, ageDays:7, source:'external', sourceLabel:'人才库检索', status:'available', statusLabel:'可联系', notRecReason:null},
  {name:'赵十', profileScore:65, profileGrade:'B', matchScore:70, ageDays:12, source:'external', sourceLabel:'人才库检索', status:'available', statusLabel:'可联系', notRecReason:null},
  {name:'周明', profileScore:72, profileGrade:'B', matchScore:81, ageDays:36, source:'external', sourceLabel:'人才库检索', status:'available', statusLabel:'可联系', notRecReason:'匹配分超期 36 天'},
  {name:'陈二', profileScore:56, profileGrade:'C+', matchScore:62, ageDays:7, source:'external', sourceLabel:'人才库检索', status:'available', statusLabel:'可联系', notRecReason:null},
  {name:'钱七', profileScore:55, profileGrade:'C+', matchScore:52, ageDays:15, source:'external', sourceLabel:'人才库检索', status:'available', statusLabel:'可联系', notRecReason:'经验不足 6 个月'},
  {name:'孙八', profileScore:68, profileGrade:'B', matchScore:null, ageDays:60, source:'external', sourceLabel:'人才库检索', status:'available', statusLabel:'可联系', notRecReason:'未在检索范围'},
  {name:'郑七', profileScore:50, profileGrade:'C', matchScore:null, ageDays:45, source:'external', sourceLabel:'人才库检索', status:'available', statusLabel:'可联系', notRecReason:'学历不符（大专）'},
  {name:'王工', profileScore:92, profileGrade:'A', matchScore:92, ageDays:1278, source:'internal', sourceLabel:'内部员工', status:'available', statusLabel:'可调岗', notRecReason:null, isEmployee:true},
  {name:'钱工', profileScore:95, profileGrade:'A+', matchScore:88, ageDays:1780, source:'internal', sourceLabel:'内部员工', status:'available', statusLabel:'可调岗', notRecReason:null, isEmployee:true},
  {name:'赵工', profileScore:65, profileGrade:'B', matchScore:42, ageDays:760, source:'internal', sourceLabel:'内部员工', status:'available', statusLabel:'不可调', notRecReason:'入职不满 2 年', isEmployee:true}
];

export const CANDIDATE_META = {
  '张三':{edu:'本科', years:'5+'}, '孙九':{edu:'本科', years:'5+'}, '刘八':{edu:'大专', years:'1-3'},
  '王五':{edu:'硕士', years:'3-5'}, '李四':{edu:'硕士', years:'3-5'}, '吴六':{edu:'本科', years:'5+'},
  '赵十':{edu:'本科', years:'3-5'}, '周明':{edu:'本科', years:'5+'}, '陈二':{edu:'本科', years:'1-3'},
  '钱七':{edu:'本科', years:'fresh'}, '孙八':{edu:'硕士', years:'5+'}, '郑七':{edu:'大专', years:'3-5'},
  '王工':{edu:'硕士', years:'3-5'}, '钱工':{edu:'硕士', years:'5+'}, '赵工':{edu:'硕士', years:'1-3'}
};
