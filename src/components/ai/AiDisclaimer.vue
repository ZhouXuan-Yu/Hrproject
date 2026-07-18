<template>
  <div data-slot="ai-disclaimer">
    <svg viewBox="0 0 24 24" style="width:16px;height:16px;flex-shrink:0" aria-hidden="true">
      <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2" fill="none"/>
      <line x1="12" y1="8" x2="12" y2="12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
      <circle cx="12" cy="16" r="0.5" fill="currentColor" stroke="none"/>
    </svg>
    <span>{{ text }}</span>
  </div>
</template>

<script setup>
defineProps({
  text: {
    type: String,
    default: '此内容由AI生成，请人工审核确认后使用',
  },
});
</script>

<style scoped>
[data-slot="ai-disclaimer"] {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  margin-top: 16px;
  background: #FFF8E1;
  border: 1px solid #FEE9CC;
  border-radius: var(--radius-sm, 6px);
  font-size: var(--fs-caption, 12px);
  color: #B45309;
  line-height: 1.5;
}
</style>
