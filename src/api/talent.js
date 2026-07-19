// api/talent.js — talent API calls
import { api } from './index.js';

export async function fetchTalent(params = {}) {
  const qs = new URLSearchParams(params).toString();
  const r = await api.get(`/talent/list${qs ? '?' + qs : ''}`);
  return { data: r.data.items, total: r.data.total };
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

export async function batchContactCandidates(names, method = '系统记录') {
  const r = await api.post('/talent/contact', { names, method });
  return r.data;
}
