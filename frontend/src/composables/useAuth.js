// composables/useAuth.js — 角色 & 用户信息读取
// 凭据不再存 localStorage，改用 httpOnly Cookie + 内存缓存

export const MENU_ROUTES = [
  { id:'home',              label:'首页',       href:'/home' },
  { id:'recruit-dashboard', label:'招聘看板',   href:'/recruit-dashboard' },
  { id:'recruit-demand',    label:'需求管理',   href:'/recruit-demand' },
  { id:'recruit-talent',    label:'人才库',     href:'/recruit-talent' },
  { id:'recruit-interview', label:'面试计划',   href:'/recruit-interview' },
  { id:'recruit-ai',        label:'招聘辅助中心', href:'/recruit-ai' },
  { id:'recruit-config',    label:'招聘基础配置', href:'/recruit-config' },
  { id:'recruit-accounts', label:'账号管理',     href:'/recruit-accounts' },
];

// Map menu IDs to route paths (derived from MENU_ROUTES, kept for fast lookup)
export const MENU_TO_ROUTE = Object.fromEntries(
  MENU_ROUTES.map(r => [r.id, r.href])
);

/** Resolve the landing page for the given role */
export function getRoleLanding(role) {
  const menus = resolveMenuIds();
  return menus[0] ? (MENU_TO_ROUTE[menus[0]] || '/login') : '/login';
}

export const ROLE_MENUS = {
  no_recruit:       [],
  employee:         ['recruit-demand'],
  temp_interviewer: ['recruit-interview'],
  interviewer:      ['recruit-interview', 'recruit-talent'],
  dept_head:        ['recruit-demand', 'recruit-talent', 'recruit-interview'],
  director:         ['home', 'recruit-dashboard', 'recruit-demand'],
  hr:               ['home', 'recruit-dashboard', 'recruit-demand', 'recruit-talent', 'recruit-interview', 'recruit-ai'],
  admin:            ['home', 'recruit-dashboard', 'recruit-demand', 'recruit-talent', 'recruit-interview', 'recruit-ai', 'recruit-config', 'recruit-accounts'],
};

export const ROLE_LABELS = {
  no_recruit:'无权限员工', employee:'基层员工', dept_head:'部门负责人', director:'总监',
  interviewer:'面试官', temp_interviewer:'临时面试官', hr:'HR 专员', admin:'管理员'
};

export const ROLE_CLASS = {
  admin:'role-admin',
  hr:'role-hr',
  interviewer:'role-interviewer',
  temp_interviewer:'role-interviewer',
  employee:'role-hr',
  dept_head:'role-admin',
};

// ── In-memory user state (cleared on page reload) ──
// sessionStorage survives refreshes but not tab close — used as fast bootstrap
let _role = null;
let _user = null;
let _userId = null;
let _menus = null;

export function setAuth(user, role, userId, menus) {
  _user = user;
  _role = role;
  _userId = userId;
  if (Array.isArray(menus)) _menus = menus;
}

export function getUserId() {
  if (_userId) return _userId;
  return localStorage.getItem('hr_userId') || null;
}

export function getRole() {
  if (_role) return _role;
  return localStorage.getItem('hr_role') || null;
}

export function getUser() {
  if (_user) return _user;
  return localStorage.getItem('hr_user') || null;
}

/**
 * 解析当前用户可见的菜单 ID 列表（DB 配置优先）。
 * 优先级：内存缓存 → localStorage（刷新恢复）→ 硬编码默认（按角色兜底）。
 */
function resolveMenuIds() {
  if (Array.isArray(_menus)) return _menus;
  try {
    const raw = localStorage.getItem('hr_menus');
    if (raw) {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) return parsed;
    }
  } catch (e) { /* ignore */ }
  const role = getRole() || 'no_recruit';
  return ROLE_MENUS[role] || [];
}

/** 当前用户允许访问的菜单 ID（供路由守卫使用） */
export function getAllowedMenuIds() {
  return resolveMenuIds();
}

export function getVisibleMenus(role) {
  const ids = resolveMenuIds();
  return MENU_ROUTES.filter(r => ids.includes(r.id));
}

export function clearAuth() {
  _role = null;
  _user = null;
  _userId = null;
  _menus = null;
}
