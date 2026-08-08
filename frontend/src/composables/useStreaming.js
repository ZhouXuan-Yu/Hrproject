import { ref } from 'vue';

/**
 * Vue 3 composable for consuming SSE (Server-Sent Events) streams
 * via fetch + ReadableStream.
 *
 * Usage:
 *   const { content, thinking, isStreaming, error, result, start, stop } = useStreaming();
 *   await start('jd-generate', { position: '...', department: '...' });
 */
export function useStreaming() {
  const content = ref('');
  const thinking = ref('');
  const isStreaming = ref(false);
  const error = ref(null);
  const result = ref(null);
  let controller = null;

  /**
   * Start consuming an SSE stream.
   *
   * @param {string} workflow - e.g. 'jd-generate' | 'match'
   * @param {object} params  - POST body params
   * @param {string} baseUrl - base URL prefix, default '/api/ai/stream'
   */
  async function start(workflow, params, baseUrl = '/api/ai/stream') {
    // Reset state
    content.value = '';
    thinking.value = '';
    error.value = null;
    result.value = null;
    isStreaming.value = true;

    controller = new AbortController();

    // Initial connection gets long-backoff retries to ride out transient 502s
    // (backend dev-server reloader restarts). Mid-stream failures are not retried.
    const CONNECT_RETRY_DELAYS = [3000, 6000];
    let response = null;
    for (let attempt = 0; attempt <= CONNECT_RETRY_DELAYS.length; attempt++) {
      try {
        // Auth is via httpOnly cookie — no need to inject role into URL
        const url = `${baseUrl}/${workflow}`;

        response = await fetch(url, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(params),
          signal: controller.signal,
        });

        if (!response.ok) {
          const err = new Error(`HTTP ${response.status}`);
          err.status = response.status;
          throw err;
        }
        break;
      } catch (e) {
        if (e.name === 'AbortError') throw e;
        const transient = (e.status >= 500 && e.status < 600) || e.message === 'Failed to fetch';
        if (transient && attempt < CONNECT_RETRY_DELAYS.length) {
          await new Promise(r => setTimeout(r, CONNECT_RETRY_DELAYS[attempt]));
          continue;
        }
        throw e;
      }
    }

    try {
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || ''; // Keep incomplete line in buffer

        for (const line of lines) {
          if (line.startsWith('data: ')) {
            try {
              const data = JSON.parse(line.slice(6));
              switch (data.type) {
                case 'thinking':
                  thinking.value += data.content;
                  break;
                case 'token':
                  content.value += data.content;
                  break;
                case 'result':
                  result.value = { ...result.value, [data.key]: data.value };
                  break;
                case 'error':
                  error.value = data.message;
                  break;
                case 'done':
                  isStreaming.value = false;
                  if (data.disclaimer) {
                    result.value = { ...result.value, disclaimer: data.disclaimer };
                  }
                  break;
              }
            } catch (_parseErr) {
              // Skip malformed JSON lines — stream may contain partial data
            }
          }
        }
      }
    } catch (e) {
      if (e.name !== 'AbortError') {
        error.value = e.message || '连接中断';
        isStreaming.value = false;
      }
    } finally {
      isStreaming.value = false;
    }
  }

  /**
   * Abort the active stream.
   */
  function stop() {
    if (controller) {
      controller.abort();
      controller = null;
    }
    isStreaming.value = false;
  }

  return { content, thinking, isStreaming, error, result, start, stop };
}
