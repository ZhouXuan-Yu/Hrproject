// api/ai.js — AI workflow API calls
// All POST to /api/ai/run/<workflow>
// Never returns local mock data; failures must surface to the caller.
import { api } from './index.js';
import { useStreaming } from '../composables/useStreaming.js';

// Export SSE streaming API (to be used with useStreaming composable)
export const STREAM_WORKFLOWS = {
  'jd-generate': '/api/ai/stream/jd-generate',
  'match': '/api/ai/stream/match',
};

function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

// Wrap API call with bounded retry.
// Transient gateway errors (backend dev-server restart / cold start) get a few
// long-backoff retries before surfacing the real failure.
const AI_RETRY_DELAYS = [3000, 6000];

function isTransientGatewayError(e) {
  return (e.status >= 500 && e.status < 600) || !e.status || e.code === 'NETWORK_ERROR' || e.code === 'TIMEOUT';
}

async function aiPost(workflow, params) {
  let lastError = null;
  for (let attempt = 0; attempt <= AI_RETRY_DELAYS.length; attempt++) {
    try {
      // silent: AI workflows surface their own fallback UI, no global error toast
      const r = await api.post(`/ai/run/${workflow}`, params, { silent: true });
      const data = r.data || r;
      // Surface fallback warnings to the user
      if (data._fallback) {
        console.warn(`[AI API] ${workflow} returned fallback data:`, data._fallback_reason || 'unknown reason');
      }
      return data;
    } catch (e) {
      lastError = e;
      if (isTransientGatewayError(e) && attempt < AI_RETRY_DELAYS.length) {
        console.warn(`[AI API] ${workflow} transient error (${e.message}), retrying in ${AI_RETRY_DELAYS[attempt] / 1000}s...`);
        await delay(AI_RETRY_DELAYS[attempt]);
        continue;
      }
      break;
    }
  }
  console.warn(`[AI API] ${workflow} failed:`, lastError.message);
  throw lastError;
}

export async function runJdGenerate(params) {
  return aiPost('jd-generate', params);
}

export async function runResumeSearch(params) {
  return aiPost('resume-search', params);
}

export async function runMatch(params) {
  return aiPost('match', params);
}

export async function runInterviewQuestions(params) {
  return aiPost('interview-questions', params);
}

export async function runCommunicationDraft(params) {
  return aiPost('communication-draft', params);
}

export async function runReportAnalysis(params) {
  return aiPost('report-analysis', params);
}
