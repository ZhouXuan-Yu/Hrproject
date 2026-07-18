// data/talent.js — 人才库页面 mock 数据
// 提取自 public/legacy/recruit-talent.html

export const EXT_DATA = [
  {id:'C2026070012',name:'张三',portraitClass:'score-high',portrait:'A · 88',edu:'本科',years:'5年',skillsHtml:'<span class="tag-item tag-hit">Java</span><span class="tag-item tag-hit">K8s</span><span class="tag-item tag-hit">微服务</span>',company:'阿里巴巴',source:'邮箱',inDate:'07-12',status:'available',statusLabel:'可联系',note:'',locked:false},
  {id:'C2026070010',name:'郑一',portraitClass:'score-high',portrait:'A- · 84',edu:'本科',years:'4年',skillsHtml:'<span class="tag-item tag-hit">React</span><span class="tag-item tag-hit">RN</span><span class="tag-item tag-hit">Flutter</span>',company:'美团',source:'猎聘',inDate:'07-10',status:'locked',statusLabel:'面试中(锁定)',note:'',locked:true},
  {id:'C2026070011',name:'李四',portraitClass:'score-mid',portrait:'B+ · 76',edu:'硕士',years:'3年',skillsHtml:'<span class="tag-item tag-hit">Vue</span><span class="tag-item tag-hit">React</span><span class="tag-item tag-hit">TS</span>',company:'腾讯',source:'Boss',inDate:'07-11',status:'available',statusLabel:'可联系',note:'',locked:false},
  {id:'C2026070007',name:'王五',portraitClass:'score-high',portrait:'B+ · 80',edu:'硕士',years:'3年',skillsHtml:'<span class="tag-item tag-hit">Python</span><span class="tag-item tag-hit">Spark</span><span class="tag-item tag-hit">SQL</span>',company:'网易',source:'Boss',inDate:'07-12',status:'reserve',statusLabel:'储备',note:'',locked:false},
  {id:'C2026070009',name:'孙九',portraitClass:'score-mid',portrait:'B · 68',edu:'本科',years:'6年',skillsHtml:'<span class="tag-item tag-hit">Go</span><span class="tag-item tag-hit">Redis</span><span class="tag-item tag-hit">Kafka</span>',company:'字节跳动',source:'内推',inDate:'07-10',status:'locked',statusLabel:'面试中(锁定)',note:'',locked:true},
  {id:'C2024070001',name:'孙七',portraitClass:'',portrait:'—',edu:'硕士',years:'7年',skillsHtml:'<span style="color:var(--c-sub)">—</span>',company:'—',source:'猎聘',inDate:'2024-07',status:'archived',statusLabel:'已封存',note:'',locked:true}
];

export const INT_DATA = [
  {id:'EMP001',name:'王工',scoreHtml:'<span class="portrait-score score-high">A · 92</span>',dept:'技术部',pos:'高级Java',years:'3年',perf:'A',matchHtml:'<span style="font-size:11px;color:var(--c-sub)">架构方向</span> <span style="color:var(--c-done);font-weight:700">92</span>',tagsHtml:'<span class="tag-item tag-hit">Java</span><span class="tag-item tag-hit">K8s</span><span class="tag-item tag-hit">SpringBoot</span><span class="tag-item tag-hit">MySQL</span><span class="tag-fold" title="Redis Kafka Elasticsearch Linux Git Jenkins 微服务架构 系统设计 性能优化">+11</span>',transfer:true,note:''},
  {id:'EMP023',name:'钱工',scoreHtml:'<span class="portrait-score score-high">A+ · 95</span>',dept:'产品部',pos:'高级产品经理',years:'5年',perf:'A+',matchHtml:'<span style="font-size:11px;color:var(--c-sub)">产品总监</span> <span style="color:var(--c-done);font-weight:700">88</span>',tagsHtml:'<span class="tag-item tag-hit">B端</span><span class="tag-item tag-hit">SaaS</span><span class="tag-item tag-hit">用户研究</span><span class="tag-item tag-hit">需求管理</span><span class="tag-fold" title="数据分析 敏捷开发 竞品分析 产品规划 SQL Figma 项目管理">+7</span>',transfer:true,note:''},
  {id:'EMP015',name:'赵工',scoreHtml:'<span class="portrait-score score-mid">B · 65</span>',dept:'数据部',pos:'数据分析师',years:'2年',perf:'B+',matchHtml:'<span style="font-size:11px;color:var(--c-sub)">高级数据分析师</span> <span style="color:var(--c-warn);font-weight:700">65</span>',tagsHtml:'<span class="tag-item tag-hit">Python</span><span class="tag-item tag-hit">SQL</span><span class="tag-item tag-neutral">Spark</span><span class="tag-item tag-neutral">Tableau</span><span class="tag-fold" title="数据分析 统计学 机器学习 ETL">+4</span>',transfer:false,note:''}
];

export const BLACKLIST_DATA = [
  {name:'赵六',phone:'150****',date:'2026-06-20',reason:'简历造假（虚构工作经历）',operator:'张HR',expiry:'永久'},
  {name:'周八',phone:'189****',date:'2026-05-15',reason:'面试严重违纪（代面）',operator:'李HR',expiry:'2027-05-15'}
];

export const DEMAND_OPTIONS = [
  {id:'DM2026070005',name:'高级Java工程师',dept:'技术部',status:'招聘中'},
  {id:'DM2026070004',name:'产品经理',dept:'产品部',status:'审批中'},
  {id:'DM2026070003',name:'运营总监',dept:'运营部',status:'招聘中'},
  {id:'DM2026070002',name:'前端工程师',dept:'技术部',status:'已关闭'}
];

export const MATCH_RESULTS = {
  java: [
    {id:'EMP001',name:'王工',dept:'技术部',curPos:'高级Java',perf:'A',score:92,transferable:true},
    {id:'EMP015',name:'赵工',dept:'数据部',curPos:'数据分析师',perf:'B+',score:42,transferable:false}
  ],
  pm: [{id:'EMP023',name:'钱工',dept:'产品部',curPos:'高级产品经理',perf:'A+',score:88,transferable:true}],
  frontend: [{id:'EMP001',name:'王工',dept:'技术部',curPos:'高级Java',perf:'A',score:55,transferable:true}],
  data: []
};
