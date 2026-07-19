// api/config.js — config API calls
import { api } from './index.js';

export async function fetchEmailAccounts()    { const r = await api.get('/config/email-accounts'); return r.data; }
export async function createEmailAccount(d)   { const r = await api.post('/config/email-accounts', d); return r.data; }
export async function updateEmailAccount(id,d){ const r = await api.put(`/config/email-accounts/${id}`, d); return r.data; }
export async function deleteEmailAccount(id)  { const r = await api.delete(`/config/email-accounts/${id}`); return r.data; }

export async function fetchChannels()         { const r = await api.get('/config/channels'); return r.data; }
export async function createChannel(d)        { const r = await api.post('/config/channels', d); return r.data; }
export async function updateChannel(code, d)  { const r = await api.put(`/config/channels/${code}`, d); return r.data; }

export async function fetchScoreRules()       { const r = await api.get('/config/score-rules'); return r.data; }
export async function updateScoreRules(d)     { const r = await api.put('/config/score-rules', d); return r.data; }

export async function fetchNotifyTemplates()  { const r = await api.get('/config/notify-templates'); return r.data; }
export async function createNotifyTemplate(d) { const r = await api.post('/config/notify-templates', d); return r.data; }
export async function updateNotifyTemplate(id,d){const r = await api.put(`/config/notify-templates/${id}`, d); return r.data; }

export async function fetchRolePermissions()  { const r = await api.get('/config/role-permissions'); return r.data; }
export async function fetchAuditLogs()        { const r = await api.get('/config/audit-logs'); return r.data; }
export async function fetchAiCapabilities()   { const r = await api.get('/ai/capabilities'); return r.data; }
