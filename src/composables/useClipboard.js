import { ref } from 'vue';

/**
 * Vue 3 composable for clipboard operations with fallback support.
 *
 * Usage:
 *   const { copy, copied } = useClipboard();
 *   await copy('text to copy');
 *   // copied.value is true for 2s after success
 */
export function useClipboard() {
  const copied = ref(false);

  async function copy(text) {
    try {
      await navigator.clipboard.writeText(text);
      copied.value = true;
      setTimeout(() => { copied.value = false; }, 2000);
      return true;
    } catch {
      // Fallback for non-HTTPS contexts
      const ta = document.createElement('textarea');
      ta.value = text;
      ta.style.position = 'fixed';
      ta.style.left = '-9999px';
      document.body.appendChild(ta);
      ta.select();
      try {
        document.execCommand('copy');
        copied.value = true;
        setTimeout(() => { copied.value = false; }, 2000);
        return true;
      } catch {
        return false;
      } finally {
        document.body.removeChild(ta);
      }
    }
  }

  return { copy, copied };
}
