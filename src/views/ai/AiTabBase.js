import { ref, computed } from 'vue';
import { useClipboard } from '../../composables/useClipboard.js';

export function useAiTab() {
  const loading = ref(false);
  const error = ref('');
  const status = computed(() => loading.value ? 'submitted' : (error.value ? 'error' : 'ready'));
  const { copy, copied } = useClipboard();

  function resetError() { error.value = ''; }

  return { loading, error, status, copy, copied, resetError };
}
