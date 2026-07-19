// useToast.js — Global toast notification composable (singleton state)
import { reactive } from 'vue';

const toasts = reactive([]);
let _id = 0;

// Listen for API error events dispatched by api/index.js
if (typeof window !== 'undefined') {
  window.addEventListener('api:error', (event) => {
    const { message, code } = event.detail || {};
    addToast(message || code || '请求失败', 'error', 5000);
  });
}

function addToast(message, type = 'info', duration = 4000) {
  const id = ++_id;
  toasts.push({ id, message, type, duration });
  if (toasts.length > 5) toasts.shift();
  if (duration > 0) {
    setTimeout(() => {
      const idx = toasts.findIndex(t => t.id === id);
      if (idx !== -1) toasts.splice(idx, 1);
    }, duration);
  }
  return id;
}

export function useToast() {
  function removeToast(id) {
    const idx = toasts.findIndex(t => t.id === id);
    if (idx !== -1) toasts.splice(idx, 1);
  }

  return {
    toasts,
    toast: {
      success: (msg, dur) => addToast(msg, 'success', dur),
      error: (msg, dur) => addToast(msg, 'error', dur),
      warning: (msg, dur) => addToast(msg, 'warning', dur),
      info: (msg, dur) => addToast(msg, 'info', dur),
    },
    removeToast,
  };
}
