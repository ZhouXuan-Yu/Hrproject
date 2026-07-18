<template>
  <div
    data-slot="ai-skeleton"
    :data-variant="variant"
    :role="variant === 'text' ? 'status' : undefined"
    :aria-label="variant === 'text' ? '加载中' : undefined"
    :aria-busy="variant === 'text' ? 'true' : undefined"
  >
    <template v-if="variant === 'dots'">
      <span data-slot="ai-skeleton-dot" v-for="i in 3" :key="i" :style="{ animationDelay: (i - 1) * 0.15 + 's' }"></span>
    </template>
    <template v-else-if="variant === 'text'">
      <div data-slot="ai-skeleton-line" v-for="(w, i) in lineWidths" :key="i" :style="{ width: w }"></div>
    </template>
    <template v-else-if="variant === 'spinner'">
      <div data-slot="ai-skeleton-spinner"></div>
    </template>
    <template v-else-if="variant === 'pulse'">
      <div data-slot="ai-skeleton-pulse"></div>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  variant: {
    type: String,
    default: 'text',
    validator: (v) => ['text', 'dots', 'spinner', 'pulse'].includes(v),
  },
  lines: { type: Number, default: 3 },
  widths: {
    type: Array,
    default: () => ['60%', '80%', '70%'],
  },
});

const lineWidths = computed(() => {
  const safe = props.widths.length > 0 ? props.widths : ['60%', '80%', '70%'];
  if (safe.length >= props.lines) return safe.slice(0, props.lines);
  const result = [];
  for (let i = 0; i < props.lines; i++) {
    result.push(safe[i % safe.length]);
  }
  return result;
});
</script>

<style scoped>
[data-slot="ai-skeleton"] { }

/* text variant — shimmer bars */
[data-slot="ai-skeleton-line"] {
  height: 12px;
  background: var(--c-border-light, #EFF1F5);
  border-radius: var(--radius-sm, 6px);
  margin-bottom: 8px;
  animation: ai-shimmer 1.8s ease-in-out infinite;
  background: linear-gradient(90deg, var(--c-border-light, #EFF1F5) 25%, var(--c-bg, #F6F8FB) 50%, var(--c-border-light, #EFF1F5) 75%);
  background-size: 200% 100%;
}

/* dots variant — 3 bouncing dots */
[data-slot="ai-skeleton"][data-variant="dots"] {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 8px 0;
}
[data-slot="ai-skeleton-dot"] {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--c-primary, #4F6EF7);
  opacity: 0.35;
  animation: ai-dot-bounce 0.9s ease-in-out infinite;
}
@keyframes ai-dot-bounce {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.35; }
  30% { transform: translateY(-6px); opacity: 0.8; }
}

/* spinner variant — rotating ring */
[data-slot="ai-skeleton-spinner"] {
  width: 20px;
  height: 20px;
  border: 2px solid var(--c-border-light, #EFF1F5);
  border-top-color: var(--c-primary, #4F6EF7);
  border-radius: 50%;
  animation: ai-spin 0.7s linear infinite;
}
@keyframes ai-spin {
  to { transform: rotate(360deg); }
}

/* pulse variant — single breathing dot */
[data-slot="ai-skeleton-pulse"] {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--c-primary, #4F6EF7);
  opacity: 0.4;
  animation: ai-pulse 1.4s ease-in-out infinite;
}
@keyframes ai-pulse {
  0%, 100% { opacity: 0.25; transform: scale(0.85); }
  50% { opacity: 0.7; transform: scale(1.15); }
}

@keyframes ai-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

@media (prefers-reduced-motion: reduce) {
  [data-slot="ai-skeleton-line"],
  [data-slot="ai-skeleton-spinner"],
  [data-slot="ai-skeleton-dot"],
  [data-slot="ai-skeleton-pulse"] {
    animation: none;
  }
}
</style>
