// api/dashboard.js — dashboard API calls
import { api } from './index.js';

function qs(params) {
  const p = new URLSearchParams();
  Object.entries(params || {}).forEach(([k, v]) => { if (v != null && v !== '') p.append(k, v); });
  const s = p.toString();
  return s ? '?' + s : '';
}

export async function fetchKpi(params)     { const r = await api.get('/dashboard/kpi' + qs(params)); return r.data; }
export async function fetchFunnel(params)  { const r = await api.get('/dashboard/funnel' + qs(params)); return r.data; }
export async function fetchDeptProgress(params) { const r = await api.get('/dashboard/dept-progress' + qs(params)); return r.data; }
export async function fetchChannel(params) { const r = await api.get('/dashboard/channel' + qs(params)); return r.data; }
export async function fetchRiskAlerts(params) { const r = await api.get('/dashboard/risk-alerts' + qs(params)); return r.data; }
export async function fetchMonthlyStats(params) { const r = await api.get('/dashboard/monthly' + qs(params)); return r.data; }
