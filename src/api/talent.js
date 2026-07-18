// api/talent.js — talent API calls
import { api } from './index.js';

export async function fetchTalent(params = {}) {
  const qs = new URLSearchParams(params).toString();
  return await api.get(`/talent/list${qs ? '?' + qs : ''}`);
}

export async function updateTalentNote(id, note) {
  const r = await api.patch(`/talent/${id}/note`, { note });
  return r.data;
}

export async function fetchMatchResults(demandId) {
  const r = await api.get(`/talent/match?demandId=${demandId}`);
  return r.data;
}
