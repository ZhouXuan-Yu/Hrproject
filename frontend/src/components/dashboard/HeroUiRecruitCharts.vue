<template>
  <div ref="mountPoint" class="hero-ui-recruit-charts"></div>
</template>

<script setup>
import React from 'react';
import { createRoot } from 'react-dom/client';
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import RecruitAnalyticsPro from './heroui-pro/RecruitAnalyticsPro.jsx';
import './heroui-pro/recruit-analytics-pro.css';

const props = defineProps({
  demands: { type: Array, default: () => [] },
  events: { type: Array, default: () => [] },
  months: { type: Array, default: () => [] },
  calendarMonth: { type: String, default: '' },
  year: { type: [String, Number], default: '' },
});
const emit = defineEmits(['calendar-month-change']);

const mountPoint = ref(null);
let root;

function renderCharts() {
  if (!root) return;
  root.render(React.createElement(RecruitAnalyticsPro, {
    demands: props.demands,
    events: props.events,
    months: props.months,
    calendarMonth: props.calendarMonth,
    year: props.year,
    onCalendarMonthChange: (month) => emit('calendar-month-change', month),
  }));
}

onMounted(() => {
  root = createRoot(mountPoint.value);
  renderCharts();
});

watch(
  () => [props.demands, props.events, props.months, props.calendarMonth, props.year],
  renderCharts,
  { deep: true },
);

onBeforeUnmount(() => {
  root?.unmount();
  root = null;
});
</script>
