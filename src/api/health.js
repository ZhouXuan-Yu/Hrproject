// api/health.js — health check API call
import { api } from './index.js';

export async function fetchHealth() {
  return await api.get('/health');
}
