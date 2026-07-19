// api/interview.js — interview API calls
import { api } from './index.js';

export async function fetchInterviews(params = {}) {
  const qs = new URLSearchParams(params).toString();
  const r = await api.get(`/interview/list${qs ? '?' + qs : ''}`);
  return r.data;
}

export async function fetchInterviewAlerts() {
  const r = await api.get('/interview/alerts');
  return r.data;
}

export async function createInterview(data) {
  const r = await api.post('/interview/create', data);
  return r.data;
}

export async function scheduleInterview(data) {
  const r = await api.post('/interview/schedule', data);
  return r.data;
}

export async function completeInterview(id, data = {}) {
  const r = await api.post(`/interview/${id}/complete`, data);
  return r.data;
}

export async function evaluateInterview(id, data) {
  const r = await api.post(`/interview/${id}/evaluate`, data);
  return r.data;
}

export async function sendOffer(id, data = {}) {
  const r = await api.post(`/interview/${id}/offer`, data);
  return r.data;
}

export async function confirmOnboard(id) {
  const r = await api.post(`/interview/${id}/onboard`);
  return r.data;
}

export async function cancelInterview(id, reason = '') {
  const r = await api.delete(`/interview/${id}`, { body: JSON.stringify({ reason }) });
  return r.data;
}

export async function fetchInterviewDetail(bookId) {
  const r = await api.get(`/interview/${bookId}`);
  return r.data;
}

export async function fetchInterviewCalendar(weekStart) {
  const qs = weekStart ? `?week_start=${weekStart}` : '';
  const r = await api.get(`/interview/calendar${qs}`);
  return r.data;
}
