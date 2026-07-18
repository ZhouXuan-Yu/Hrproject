// api/index.js — HTTP client for backend API
const BASE = '/api';

async function request(path, options = {}) {
  const url = `${BASE}${path}`;
  const config = {
    headers: { 'Content-Type': 'application/json', ...options.headers },
    ...options,
  };

  // In dev mode, forward the current role as query param for auth
  const role = localStorage.getItem('hr_role');
  if (role && !url.includes('role=')) {
    const sep = url.includes('?') ? '&' : '?';
    const urlWithRole = `${url}${sep}role=${role}`;
    const resp = await fetch(urlWithRole, config);
    return handleResponse(resp);
  }

  const resp = await fetch(url, config);
  return handleResponse(resp);
}

async function handleResponse(resp) {
  const json = await resp.json();
  if (!resp.ok) {
    const err = new Error(json?.error?.message || json?.message || '请求失败');
    err.code = json?.error?.code || 'ERROR';
    err.status = resp.status;
    throw err;
  }
  return json;
}

export const api = {
  get: (path) => request(path),
  post: (path, data) => request(path, { method: 'POST', body: JSON.stringify(data) }),
  patch: (path, data) => request(path, { method: 'PATCH', body: JSON.stringify(data) }),
  put: (path, data) => request(path, { method: 'PUT', body: JSON.stringify(data) }),
};
