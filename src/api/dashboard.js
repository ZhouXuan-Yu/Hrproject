// api/dashboard.js — dashboard API calls
import { api } from './index.js';

export async function fetchKpi()     { const r = await api.get('/dashboard/kpi'); return r.data; }
export async function fetchFunnel()  { const r = await api.get('/dashboard/funnel'); return r; }
export async function fetchDeptProgress() { const r = await api.get('/dashboard/dept-progress'); return r.data; }
export async function fetchChannel() { const r = await api.get('/dashboard/channel'); return r.data; }
export async function fetchRiskAlerts() { const r = await api.get('/dashboard/risk-alerts'); return r.data; }
