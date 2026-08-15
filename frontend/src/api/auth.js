// api/auth.js — auth API calls
import { api } from './index.js';

export async function fetchMe() {
  const resp = await api.get('/auth/me');
  return resp.data;
}

export async function login(username, password, rememberMe = false) {
  const resp = await api.post('/auth/login', { username, password, rememberMe });
  return resp.data;
}

export async function logout() {
  await api.post('/auth/logout');
  localStorage.removeItem('hr_token');
  localStorage.removeItem('hr_session');
  localStorage.removeItem('hr_role');
  localStorage.removeItem('hr_user');
  localStorage.removeItem('hr_menus');
}

// ── User management (admin only) ──
export async function fetchUsers(params) {
  const qs = new URLSearchParams({ pageSize: 20, ...params }).toString();
  const r = await api.get(`/auth/users?${qs}`);
  return { items: r.data || [], total: r.total ?? 0, page: r.page, pageSize: r.pageSize };
}
export async function createUser(data) { const r = await api.post('/auth/users', data); return r.data; }
export async function updateUser(id, data) { const r = await api.put(`/auth/users/${id}`, data); return r.data; }
export async function toggleUserStatus(id) { const r = await api.put(`/auth/users/${id}/status`); return r.data; }
export async function deleteUser(id) { const r = await api.delete(`/auth/users/${id}`); return r.data; }
export async function resetUserPassword(id) { const r = await api.put(`/auth/users/${id}/reset-password`); return r.data; }
export async function fetchPositions() { const r = await api.get('/auth/positions'); return r.data; }
export async function changePassword(oldPwd, newPwd) { const r = await api.put('/auth/change-password', { oldPassword: oldPwd, newPassword: newPwd }); return r.data; }
export async function forgotPassword(username, channel) { const r = await api.post('/auth/forgot-password', { username, channel }); return r.data; }
export async function verifyResetCode(username, code, newPassword) { const r = await api.post('/auth/verify-reset-code', { username, code, newPassword }); return r.data; }
export async function batchCreateUsers(users) { const r = await api.post("/auth/users/batch", { users }); return r.data; }

// ── 角色数据范围（五档，admin only）──
export async function fetchRoleDataScopes() { const r = await api.get('/auth/role-data-scopes'); return r.data; }
export async function updateRoleDataScopes(d) { const r = await api.put('/auth/role-data-scopes', d); return r.data; }

export async function firstTimeSetup(data) { const r = await api.post('/auth/setup', data); return r.data; }

// ── 部门管理 (admin only) ──
export async function createDepartment(data) { const r = await api.post('/auth/departments', data); return r.data; }
export async function updateDepartment(id, data) { const r = await api.put(`/auth/departments/${id}`, data); return r.data; }
export async function deleteDepartment(id) { const r = await api.delete(`/auth/departments/${id}`); return r.data; }
export async function toggleDepartmentStatus(id, status) { const r = await api.put(`/auth/departments/${id}/status`, { status }); return r.data; }

// ── 岗位管理 (admin only) ──
export async function createPosition(data) { const r = await api.post('/auth/positions', data); return r.data; }
export async function fetchPendingAccounts() { const r = await api.get('/auth/pending-accounts'); return r.data; }
export async function updatePosition(id, data) { const r = await api.put(`/auth/positions/${id}`, data); return r.data; }
export async function deletePosition(id) { const r = await api.delete(`/auth/positions/${id}`); return r.data; }
export async function togglePositionStatus(id, status) { const r = await api.put(`/auth/positions/${id}/status`, { status }); return r.data; }
