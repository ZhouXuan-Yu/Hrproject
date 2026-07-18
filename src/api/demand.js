// api/demand.js — demand API calls
import { api } from './index.js';

export async function fetchDemands(params = {}) {
  const qs = new URLSearchParams(params).toString();
  return await api.get(`/demand/list${qs ? '?' + qs : ''}`);
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

export async function linkCandidateToDemand(demandId, name) {
  const r = await api.post(`/demand/${demandId}/candidates/${name}/link`, { link: true });
  return r.data;
}
