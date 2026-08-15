<template>
  <section class="dashboard-panel dashboard-risk-panel" aria-labelledby="risk-title" data-testid="dashboard-risks">
    <div class="dashboard-panel-head">
      <div>
        <span class="dashboard-eyebrow">经理待办</span>
        <h2 id="risk-title">风险与进展</h2>
        <p>优先处理红色和黄色事项，绿色事项用于确认闭环。</p>
      </div>
    </div>

    <DashboardEmptyState v-if="!items.length" title="暂无风险提醒" description="当前没有需要处理的异常事项。" test-id="risk-empty" />
    <ul v-else class="dashboard-risk-list">
      <li v-for="(item, index) in items" :key="`${item.text}-${index}`" class="dashboard-risk-item" :class="`is-${riskClass(item.type)}`" data-testid="dashboard-risk-item">
        <span class="risk-indicator" aria-hidden="true"></span>
        <span class="risk-copy">{{ item.text || '暂无说明' }}</span>
        <RouterLink v-if="item.link" :to="item.link" class="dashboard-risk-action">{{ item.action || '查看' }}</RouterLink>
      </li>
    </ul>
  </section>
</template>

<script setup>
import DashboardEmptyState from './DashboardEmptyState.vue';
defineProps({ items: { type: Array, default: () => [] } });
function riskClass(type) {
  if (type === 'reject' || type === 'risk') return 'risk';
  if (type === 'done' || type === 'success') return 'done';
  return 'watch';
}
</script>
