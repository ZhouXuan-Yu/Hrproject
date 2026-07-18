// composables/useAuth.js — 从 public/js/app.js 提取
// 角色 & 用户信息读取，不依赖 Vue，纯函数

export const MENU_ROUTES = [
  { id:'recruit-dashboard', label:'招聘看板', href:'/recruit-dashboard' },
  { id:'recruit-demand',    label:'需求管理',   href:'/recruit-demand' },
  { id:'recruit-talent',    label:'人才库',     href:'/recruit-talent' },
  { id:'recruit-interview', label:'面试计划',   href:'/recruit-interview' },
  { id:'recruit-ai',        label:'招聘辅助中心', href:'/recruit-ai' },
  { id:'recruit-config',    label:'招聘基础配置', href:'/recruit-config' },
];

export const ROLE_MENUS = {
  no_recruit:       [],
  employee:         ['recruit-dashboard','recruit-demand'],
  dept_head:        ['recruit-dashboard','recruit-demand'],
  interviewer:      ['recruit-dashboard','recruit-interview'],
  temp_interviewer: ['recruit-dashboard','recruit-interview'],
  hr:               ['recruit-dashboard','recruit-demand','recruit-talent','recruit-interview'],
  admin:            ['recruit-dashboard','recruit-demand','recruit-talent','recruit-interview','recruit-ai','recruit-config'],
};

export const ROLE_LABELS = {
  no_recruit:'无权限员工', employee:'基层员工', dept_head:'部门负责人',
  interviewer:'面试官', temp_interviewer:'临时面试官', hr:'HR 专员', admin:'管理员'
};

export const ROLE_CLASS = {
  admin:'role-admin', hr:'role-hr', interviewer:'role-interviewer',
  temp_interviewer:'role-interviewer', employee:'role-hr', dept_head:'role-admin'
};

export function getRole(){
  if(sessionStorage.getItem('hr_temp_interviewer') === 'true') return 'temp_interviewer';
  return localStorage.getItem('hr_role') || 'hr';
}

export function getUser(){
  return localStorage.getItem('hr_user') || '用户';
}

export function getVisibleMenus(role){
  const ids = ROLE_MENUS[role] || ROLE_MENUS['employee'];
  return MENU_ROUTES.filter(r => ids.includes(r.id));
}
