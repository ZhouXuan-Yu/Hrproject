<template>
  <div class="dashboard-empty-state" :data-testid="testId">
    <span class="dashboard-empty-icon" aria-hidden="true">—</span>
    <strong>{{ title }}</strong>
    <span>{{ description }}</span>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, default: '暂无数据' },
  description: { type: String, default: '当前筛选范围内没有可展示的数据。' },
  testId: { type: String, default: 'dashboard-empty-state' },
});
</script>
