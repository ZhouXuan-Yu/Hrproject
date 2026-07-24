// api/talent.js — talent API calls
import { api } from './index.js';

export async function fetchTalent(params = {}) {
  const qs = new URLSearchParams({ pageSize: 20, ...params }).toString();
  const r = await api.get(`/talent/list${qs ? '?' + qs : ''}`);
  // success_list 契约：r.data 即候选人数组，r.total 总数
  return { ext: Array.isArray(r.data) ? r.data : [], total: r.total ?? 0 };
}

export async function updateTalentNote(id, note) {
  const r = await api.patch(`/talent/${id}/note`, { note });
  return r.data;
}

export async function fetchMatchResults(demandId) {
  const r = await api.get(`/talent/match?demandId=${demandId}`);
  return r.data;
}

export async function fetchCandidateDetail(candidateId) {
  const r = await api.get(`/talent/candidate/${candidateId}`);
  return r.data;
}

export async function fetchEmployeeDetail(employeeId) {
  const r = await api.get(`/talent/employee/${employeeId}`);
  return r.data;
}

export async function linkTalentToDemand(demandId, names) {
  const r = await api.post('/talent/link', { demandId, names });
  return r.data;
}

export async function recordTalentContact(candidateId, method = '系统记录') {
  const r = await api.post('/talent/contact', { candidateId, method });
  return r.data;
}

export async function fetchCandidateContact(candidateId) {
  const r = await api.get(`/talent/candidate/${candidateId}/contact-info`);
  return r.data;
}

export async function sendTalentContact(payload) {
  const r = await api.post('/talent/contact', payload);
  return r.data;
}

export async function fetchIngestLog(limit = 10) {
  const r = await api.get(`/talent/ingest-log?limit=${limit}`);
  return r.data;
}

export async function fetchMailLog(limit = 50) {
  const r = await api.get(`/talent/mail-log?limit=${limit}`);
  return r.data;
}

export async function batchContactCandidates(names, method = '系统记录') {
  const r = await api.post('/talent/contact', { names, method });
  return r.data;
}

export async function uploadResumeFile(file) {
  const fd = new FormData();
  fd.append('file', file);
  const r = await api.post('/talent/upload-resume', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return r.data;
}

export async function fetchResumeFile(resumeId) {
  // 文件下载不能用 api 封装（它强制 resp.json()，会把二进制解析坏），
  // 这里直接用原生 fetch 拿 Blob。
  const token = localStorage.getItem('hr_token');
  const resp = await fetch(`/api/talent/resume-file/${resumeId}`, {
    headers: {
      'X-Requested-With': 'XMLHttpRequest',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  });
  if (!resp.ok) {
    let msg = `下载失败 (${resp.status})`;
    try {
      const j = await resp.json();
      msg = (j && j.error && j.error.message) || j.message || msg;
    } catch (e) { /* 非 JSON 错误响应，用默认信息 */ }
    throw new Error(msg);
  }
  return await resp.blob();
}
