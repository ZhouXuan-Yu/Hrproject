// api/demand.js — demand API calls
import { api } from './index.js';

export async function fetchDemands(params = {}) {
  const qs = new URLSearchParams(params).toString();
  const r = await api.get(`/demand/list${qs ? '?' + qs : ''}`);
  return { data: r.data, total: r.total || 0 };
}

export async function submitForApproval(demandId) {
  const r = await api.post(`/demand/${demandId}/submit`);
  return r.data;
}

export async function approveDemandApi(demandId, data) {
  const r = await api.post(`/demand/${demandId}/approve`, data);
  return r.data;
}

export async function rejectDemandApi(demandId, data) {
  const r = await api.post(`/demand/${demandId}/reject`, data);
  return r.data;
}

export async function fetchDemandDetail(id) {
  const r = await api.get(`/demand/${id}`);
  return r.data;
}

export async function fetchDemandCandidates(id, params = {}) {
  const qs = new URLSearchParams(params).toString();
  const r = await api.get(`/demand/${id}/candidates${qs ? '?' + qs : ''}`);
  return r.data;
}

export async function createDemand(data) {
  const r = await api.post('/demand/create', data);
  return r.data;
}

export async function updateDemand(demandId, data) {
  const r = await api.patch(`/demand/${demandId}`, data);
  return r.data;
}

export async function deleteDemand(demandId) {
  const r = await api.delete(`/demand/${demandId}`);
  return r.data;
}

export async function linkCandidateToDemand(demandId, name) {
  // 候选人姓名含中文/特殊字符，路径段必须 URL 编码
  const r = await api.post(`/demand/${demandId}/candidates/${encodeURIComponent(name)}/link`, { link: true });
  return r.data;
}
