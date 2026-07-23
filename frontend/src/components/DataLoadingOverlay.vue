<template>
  <div v-if="visible" class="data-loading-overlay" role="status" aria-live="polite">
    <div class="data-loading-card">
      <span class="data-loading-spinner" aria-hidden="true"></span>
      <span>{{ text }}</span>
    </div>
  </div>
</template>

<script setup>
defineProps({
  visible: { type: Boolean, default: false },
  text: { type: String, default: '数据正在加载中' },
});
</script>

<style scoped>
.data-loading-overlay {
  position: absolute;
  inset: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 120px;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.72), rgba(246, 248, 251, 0.58)),
    rgba(246, 248, 251, 0.66);
  backdrop-filter: blur(10px) saturate(1.25);
  -webkit-backdrop-filter: blur(10px) saturate(1.25);
}

.data-loading-card {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-height: 38px;
  padding: 10px 16px;
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.74);
  backdrop-filter: blur(14px) saturate(1.2);
  -webkit-backdrop-filter: blur(14px) saturate(1.2);
  color: var(--c-text);
  font-size: 13px;
  font-weight: 600;
  box-shadow:
    0 18px 50px rgba(23, 32, 51, 0.14),
    inset 0 1px 0 rgba(255, 255, 255, 0.86);
}

.data-loading-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid var(--c-border);
  border-top-color: var(--c-primary);
  border-radius: 50%;
  animation: data-loading-spin 0.8s linear infinite;
}

@keyframes data-loading-spin {
  to { transform: rotate(360deg); }
}

@media (prefers-reduced-motion: reduce) {
  .data-loading-spinner { animation: none; }
}
</style>
