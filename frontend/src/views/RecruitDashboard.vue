<template>
  <WorkbenchLayout title="招聘看板" :breadcrumb="{ text: '招聘管理', href: '/recruit-dashboard' }">
    <template #topbar-actions>
      <span style="font-size:11px;color:var(--c-sub)">数据更新于 {{ lastUpdate }}</span>
      <select v-model="selectedYear" @change="refreshData" style="padding:5px 10px;border:1px solid var(--c-border);border-radius:6px;font-size:13px">
        <option v-for="y in years" :key="y" :value="y">{{ y }}年</option>
      </select>
      <select v-model="dimension" @change="refreshData" style="padding:5px 10px;border:1px solid var(--c-border);border-radius:6px;font-size:13px;margin-left:6px">
        <option value="month">按月</option>
        <option value="dept">按部门</option>
        <option value="position">按岗位</option>
      </select>
    </template>

    <DataLoadingOverlay :visible="loading" />

    <!-- Summary cards row -->
    <div class="analytics-cards" v-if="!loading">
      <div class="ana-card" v-for="(c, i) in summaryCards" :key="i" :style="{ '--card-accent': c.color }">
        <div class="ana-card-val">{{ c.val }}</div>
        <div class="ana-card-label">{{ c.label }}</div>
        <div class="ana-card-sub" v-if="c.sub">{{ c.sub }}</div>
      </div>
    </div>

    <!-- Chart grid: 4 bar charts -->
    <div class="chart-grid" v-if="!loading">
      <div class="chart-card" v-for="(chart, ci) in chartData" :key="ci">
        <div class="chart-title">{{ chart.title }}</div>
        <svg :viewBox="'0 0 ' + chartW + ' ' + chartH" class="chart-svg" role="img" :aria-label="chart.title">
          <!-- Grid lines -->
          <line v-for="n in 5" :key="'g' + n" :x1="gridLeft" :y1="gridTop + (n - 1) * yStep" :x2="gridRight" :y2="gridTop + (n - 1) * yStep" stroke="#f0f2f7" stroke-width="1" />
          <!-- Y axis labels -->
          <text v-for="n in 5" :key="'yl' + n" :x="gridLeft - 8" :y="gridTop + (n - 1) * yStep + 4" text-anchor="end" font-size="10" fill="#8C95A6">{{ yLabel(chart, n) }}</text>
          <!-- Bars -->
          <g v-for="(m, mi) in chart.months" :key="'bar' + mi">
            <rect
              v-for="(bar, bi) in m.bars"
              :key="'b' + bi"
              :x="barX(mi, bi, chart)"
              :y="barY(bar.val, chart)"
              :width="barW(chart)"
              :height="Math.max(0, barH(bar.val, chart))"
              :fill="bar.color"
              :opacity="barH(bar.val, chart) > 0 ? 1 : 0"
              rx="2"
            >
              <title>{{ bar.label || '' }} {{ m.label }}: {{ bar.val }}</title>
            </rect>
          </g>
          <!-- X axis labels -->
          <text v-for="(m, mi) in chart.months" :key="'xl' + mi" :x="barCenter(mi, chart)" :y="chartH - 6" text-anchor="middle" font-size="10" fill="#8C95A6">{{ m.label }}</text>
        </svg>
        <!-- Legend -->
        <div class="chart-legend" v-if="chart.legend">
          <span v-for="leg in chart.legend" :key="leg.label" class="legend-item">
            <i :style="{ background: leg.color }"></i>{{ leg.label }}
          </span>
        </div>
      </div>
    </div>

    <div v-if="!loading" class="table-count" style="margin-top:8px">
      共 4 个统计维度 · 数据源：{{ dimensionLabel }}
    </div>
  </WorkbenchLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import WorkbenchLayout from '../layouts/WorkbenchLayout.vue';
import DataLoadingOverlay from '../components/DataLoadingOverlay.vue';

// ── State ──
const loading = ref(true);
const selectedYear = ref(2026);
const dimension = ref('month');
const years = [2024, 2025, 2026, 2027];
const lastUpdate = ref('—');

// ── Chart layout constants ──
const chartW = 520;
const chartH = 200;
const gridLeft = 50;
const gridRight = 500;
const gridTop = 16;
const gridBottom = 170;
const yStep = (gridBottom - gridTop) / 4;
const barGroupW = (gridRight - gridLeft) / 12;

// ── Mock data ──
const MONTH_LABELS = ['1月','2月','3月','4月','5月','6月','7月','8月','9月','10月','11月','12月'];

function genMockMonths() {
  return MONTH_LABELS.map((label, i) => {
    const base = 5 + i * 1.5;
    return {
      label,
      plan: Math.round(base + Math.random() * 4),
      actual: Math.round((base + Math.random() * 4) * (0.6 + Math.random() * 0.4)),
      resumes: Math.round(20 + i * 8 + Math.random() * 30),
      interviews: Math.round(8 + i * 3 + Math.random() * 10),
      passed: Math.round(3 + i * 1.5 + Math.random() * 5),
    };
  });
}

const mockData = ref(genMockMonths());

const dimensionLabel = computed(() => {
  const map = { month: '按月统计', dept: '按部门统计', position: '按岗位统计' };
  return map[dimension.value] || '按月统计';
});

// ── Summary cards ──
const summaryCards = computed(() => {
  const d = mockData.value;
  const totalPlan = d.reduce((s, m) => s + m.plan, 0);
  const totalActual = d.reduce((s, m) => s + m.actual, 0);
  const totalResumes = d.reduce((s, m) => s + m.resumes, 0);
  const totalInterviews = d.reduce((s, m) => s + m.interviews, 0);
  const totalPassed = d.reduce((s, m) => s + m.passed, 0);
  const interviewRate = totalResumes > 0 ? Math.round((totalInterviews / totalResumes) * 100) : 0;
  const passRate = totalInterviews > 0 ? Math.round((totalPassed / totalInterviews) * 100) : 0;
  return [
    { label: '计划招聘', val: totalPlan + '人', sub: selectedYear.value + '年目标', color: '#4F6EF7' },
    { label: '投递简历', val: totalResumes + '份', sub: '较去年 +12%', color: '#F59E0B' },
    { label: '面试总数', val: totalInterviews + '场', sub: '面试率 ' + interviewRate + '%', color: '#8B5CF6' },
    { label: '面试通过', val: totalPassed + '人', sub: '通过率 ' + passRate + '%', color: '#06B6D4' },
    { label: '实际到岗', val: totalActual + '人', sub: '完成率 ' + Math.round((totalActual / totalPlan) * 100) + '%', color: '#22C55E' },
  ];
});

// ── Chart data ──
const chartData = computed(() => {
  const d = mockData.value;
  return [
    {
      title: '计划 vs 实际招聘',
      legend: [{ label: '计划', color: '#CBD5E1' }, { label: '实际', color: '#4F6EF7' }],
      maxVal: Math.max(...d.map(m => Math.max(m.plan, m.actual))) + 2,
      months: d.map(m => ({
        label: m.label,
        bars: [
          { val: m.plan, color: '#CBD5E1', label: '计划' },
          { val: m.actual, color: '#4F6EF7', label: '实际' },
        ],
      })),
    },
    {
      title: '每月投递简历数',
      legend: [{ label: '投递', color: '#F59E0B' }],
      maxVal: Math.max(...d.map(m => m.resumes)) + 5,
      months: d.map(m => ({
        label: m.label,
        bars: [{ val: m.resumes, color: '#F59E0B', label: '投递' }],
      })),
    },
    {
      title: '每月面试场次',
      legend: [{ label: '面试', color: '#8B5CF6' }],
      maxVal: Math.max(...d.map(m => m.interviews)) + 3,
      months: d.map(m => ({
        label: m.label,
        bars: [{ val: m.interviews, color: '#8B5CF6', label: '面试' }],
      })),
    },
    {
      title: '每月面试通过数',
      legend: [{ label: '通过', color: '#22C55E' }],
      maxVal: Math.max(...d.map(m => m.passed)) + 2,
      months: d.map(m => ({
        label: m.label,
        bars: [{ val: m.passed, color: '#22C55E', label: '通过' }],
      })),
    },
  ];
});

// ── Chart helpers ──
function yLabel(chart, n) {
  const max = chart.maxVal || 10;
  const val = max - ((n - 1) * max) / 4;
  return Math.round(val);
}

function barX(monthIdx, barIdx, chart) {
  const barsPerGroup = chart.months[0]?.bars?.length || 1;
  const gap = 2;
  const totalBarW = barGroupW - gap * 2;
  const eachW = totalBarW / barsPerGroup;
  return gridLeft + monthIdx * barGroupW + gap + barIdx * eachW + 1;
}

function barW(chart) {
  const barsPerGroup = chart.months[0]?.bars?.length || 1;
  const gap = 2;
  const totalBarW = barGroupW - gap * 2;
  return Math.max(2, totalBarW / barsPerGroup - 2);
}

function barY(val, chart) {
  const max = chart.maxVal || 1;
  return gridBottom - (val / max) * (gridBottom - gridTop);
}

function barH(val, chart) {
  const max = chart.maxVal || 1;
  return (val / max) * (gridBottom - gridTop);
}

function barCenter(monthIdx, chart) {
  const barsPerGroup = chart.months[0]?.bars?.length || 1;
  return gridLeft + monthIdx * barGroupW + barGroupW / 2;
}

// ── Lifecycle ──
function refreshData() {
  loading.value = true;
  // Simulate API delay
  setTimeout(() => {
    mockData.value = genMockMonths();
    loading.value = false;
    lastUpdate.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
  }, 300);
}

onMounted(() => {
  refreshData();
});
</script>

<style scoped>
/* Summary cards */
.analytics-cards {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 14px;
  margin-bottom: 20px;
}
.ana-card {
  background: var(--c-card, #fff);
  border: 1px solid var(--c-border, #E1E6EF);
  border-radius: 10px;
  padding: 16px;
  position: relative;
  overflow: hidden;
}
.ana-card::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 3px;
  background: var(--card-accent);
}
.ana-card-val {
  font-size: 22px;
  font-weight: 700;
  color: var(--c-text, #172033);
  font-variant-numeric: tabular-nums;
}
.ana-card-label {
  font-size: 13px;
  color: var(--c-sub, #5B6475);
  margin-top: 2px;
}
.ana-card-sub {
  font-size: 11px;
  color: var(--c-muted, #8C95A6);
  margin-top: 2px;
}

/* Chart grid */
.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}
.chart-card {
  background: var(--c-card, #fff);
  border: 1px solid var(--c-border, #E1E6EF);
  border-radius: 10px;
  padding: 16px;
}
.chart-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--c-text, #172033);
  margin-bottom: 8px;
}
.chart-svg {
  width: 100%;
  height: auto;
}
.chart-legend {
  display: flex;
  gap: 16px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--c-sub, #5B6475);
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 5px;
}
.legend-item i {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 2px;
}

@media (max-width: 1024px) {
  .analytics-cards { grid-template-columns: repeat(3, 1fr); }
  .chart-grid { grid-template-columns: 1fr; }
}
@media (max-width: 640px) {
  .analytics-cards { grid-template-columns: repeat(2, 1fr); }
}
</style>
