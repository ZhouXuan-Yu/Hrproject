// api/auth.js — auth API calls
import { api } from './index.js';

export async function fetchMe() {
  const role = localStorage.getItem('hr_role') || 'admin';
  const resp = await api.get(`/auth/me?role=${role}`);
  return resp.data;
}

export async function login(username, role) {
  const resp = await api.post('/auth/login', { username, role });
  return resp.data;
}
