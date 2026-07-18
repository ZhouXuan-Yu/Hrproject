// composables/useMockData.js — 从 public/js/app.js 提取
// Mock 数据生成、评分计算、部门选项

export const HR_DEPARTMENTS = ['技术部','产品部','运营部','数据部','财务部','人力资源部'];

export function renderDepartmentOptions(selected, includeAll = false){
  let opts = includeAll ? '<option value="all">全公司</option>' : '';
  HR_DEPARTMENTS.forEach(d => {
    opts += `<option value="${d}"${d === selected ? ' selected' : ''}>${d}</option>`;
  });
  return opts;
}

const DECAY_COEFFICIENT = [
  { maxDays: 30,  coeff: 1.0 },
  { maxDays: 90,  coeff: 0.85 },
  { maxDays: 999, coeff: 0.70 }
];

export function getDecayCoefficient(daysAgo){
  for(let i = 0; i < DECAY_COEFFICIENT.length; i++){
    if(daysAgo <= DECAY_COEFFICIENT[i].maxDays) return DECAY_COEFFICIENT[i].coeff;
  }
  return 0.70;
}

export function calcRecommendScore(profileScore, matchScore, calcDaysAgo){
  const decay = getDecayCoefficient(calcDaysAgo);
  return Math.round((profileScore * 0.1 + (matchScore * decay) * 0.9) * 10) / 10;
}

export function calcDirectScore(profileScore, matchScore){
  return Math.round((profileScore * 0.1 + matchScore * 0.9) * 10) / 10;
}

export function profileColor(score){
  return score >= 80 ? 'var(--c-done)' : (score >= 60 ? 'var(--c-warn)' : 'var(--c-draft)');
}

export function profileGradeLabel(data){
  return (data.profileGrade || 'B') + ' · ' + (data.profileScore || 0);
}

const FIRST_NAMES = ['王','李','张','刘','陈','杨','赵','黄','周','吴','徐','孙','马','朱','胡','林','郭','何','高','郑'];
const LAST_NAMES_M = ['工','博','磊','涛','伟','强','鹏','军','勇','杰'];
const LAST_NAMES_F = ['芳','琳','婷','敏','静','丽','雪','娟','红','玲'];

function pick(arr){ return arr[Math.floor(Math.random() * arr.length)]; }

export function mockCandidate(name){
  const gender = Math.random() > 0.5 ? '男' : '女';
  const lastName = gender === '男' ? pick(LAST_NAMES_M) : pick(LAST_NAMES_F);
  const fullName = name || (pick(FIRST_NAMES) + lastName);
  const profileScore = Math.floor(Math.random() * 30) + 70;
  const matchScore = Math.floor(Math.random() * 40) + 60;
  const daysAgo = Math.floor(Math.random() * 60);
  return {
    name: fullName,
    gender,
    age: Math.floor(Math.random() * 15) + 25,
    city: pick(['上海','北京','深圳','杭州','广州']),
    education: pick(['本科','硕士','博士','大专']),
    school: pick(['复旦大学','上海交大','浙江大学','南京大学','武汉大学']),
    currentCompany: pick(['字节跳动','阿里巴巴','腾讯','美团','百度']),
    position: pick(['高级Java工程师','产品经理','运营总监','前端架构师','数据分析师']),
    profileScore,
    profileGrade: profileScore >= 90 ? 'A' : (profileScore >= 75 ? 'B' : 'C'),
    matchScore,
    matchDaysAgo: daysAgo,
    recommendScore: calcRecommendScore(profileScore, matchScore, daysAgo),
    directScore: calcDirectScore(profileScore, matchScore),
    salary: pick(['25K','30K','35K','40K','45K','50K']),
    phone: '138' + String(Math.floor(Math.random() * 90000000 + 10000000)),
    email: fullName.toLowerCase() + '@example.com',
    skills: ['Vue','React','Java','Python','K8s','SQL','TypeScript','Go','Docker'].slice(0, Math.floor(Math.random() * 5) + 3),
    status: pick(['待联系','已联系','已约面','已评价','待入职']),
    source: pick(['直接投递','系统推荐','内部匹配','猎头推荐']),
  };
}

export function mockEmployee(name){
  const fullName = name || (pick(FIRST_NAMES) + pick(LAST_NAMES_M));
  return {
    name: fullName,
    department: pick(HR_DEPARTMENTS),
    position: pick(['高级Java工程师','技术经理','产品总监','数据架构师']),
    manager: pick(FIRST_NAMES) + '总',
    phone: '139' + String(Math.floor(Math.random() * 90000000 + 10000000)),
    email: fullName.toLowerCase() + '@company.com',
    feishuId: fullName.toLowerCase() + '.feishu',
    matchScore: Math.floor(Math.random() * 30) + 65,
    profileScore: Math.floor(Math.random() * 25) + 70,
  };
}
