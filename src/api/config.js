// api/config.js — config API calls
import { api } from './index.js';

export async function fetchEmailAccounts()  { const r = await api.get('/config/email-accounts'); return r.data; }
export async function fetchChannels()       { const r = await api.get('/config/channels'); return r.data; }
export async function fetchScoreRules()     { const r = await api.get('/config/score-rules'); return r.data; }
export async function fetchNotifyTemplates(){ const r = await api.get('/config/notify-templates'); return r.data; }
export async function fetchRolePermissions(){ const r = await api.get('/config/role-permissions'); return r.data; }
export async function fetchAuditLogs()      { const r = await api.get('/config/audit-logs'); return r.data; }
export async function fetchAiCapabilities() { const r = await api.get('/ai/capabilities'); return r.data; }
