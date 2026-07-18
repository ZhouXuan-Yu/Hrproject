// api/interview.js — interview API calls
import { api } from './index.js';

export async function fetchInterviews(params = {}) {
  const qs = new URLSearchParams(params).toString();
  return await api.get(`/interview/list${qs ? '?' + qs : ''}`);
}

export async function fetchInterviewAlerts() {
  const r = await api.get('/interview/alerts');
  return r.data;
}

export async function createInterview(data) {
  const r = await api.post('/interview/create', data);
  return r.data;
}

export async function evaluateInterview(id, data) {
  const r = await api.post(`/interview/${id}/evaluate`, data);
  return r.data;
}
