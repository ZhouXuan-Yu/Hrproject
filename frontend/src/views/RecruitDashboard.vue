<template>
  <WorkbenchLayout title="招聘看板" :breadcrumb="{ text: '招聘管理', href: '/recruit-dashboard' }">
    <template #topbar-actions>
      <span style="font-size:11px;color:var(--c-sub)">数据更新于 {{ lastUpdate }}</span>
      <select v-model="selectedYear" @change="onFilterChange" style="padding:5px 10px;border:1px solid var(--c-border);border-radius:6px;font-size:13px">
        <option v-for="y in years" :key="y" :value="y">{{ y }}年</option>
      </select>
      <select v-model="selectedDept" @change="onFilterChange" style="padding:5px 10px;border:1px solid var(--c-border);border-radius:6px;font-size:13px">
        <option value="">全部部门</option>
        <option v-for="d in deptOptions" :key="d.id" :value="d.id">{{ d.name }}</option>
      </select>
      <select v-model="selectedPosition" @change="onFilterChange" style="padding:5px 10px;border:1px solid var(--c-border);border-radius:6px;font-size:13px">
        <option value="">全部岗位</option>
        <option v-for="p in positionOptions" :key="p.id" :value="p.id">{{ p.name }}</option>
      </select>
    </template>

    <DataLoadingOverlay :visible="loading" />

    <!-- KPI cards row -->
    <div class="analytics-cards" v-if="!loading">
      <div class="ana-card" v-for="(c, i) in summaryCards" :key="i" :style="{ '--card-accent': c.color }">
        <div class="ana-card-val">{{ c.val }}</div>
        <div class="ana-card-label">{{ c.label }}</div>
        <div class="ana-card-sub" v-if="c.sub">{{ c.sub }}</div>
      </div>
    </div>

    <!-- 4 bar charts — monthly trends (unique to dashboard, not on homepage) -->
    <div class="chart-grid" v-if="!loading">
      <div class="chart-card" v-for="(chart, ci) in chartData" :key="ci">
        <div class="chart-title">{{ chart.title }}</div>
        <svg :viewBox="'0 0 ' + chartW + ' ' + chartH" class="chart-svg" role="img" :aria-label="chart.title">
          <line v-for="n in 5" :key="'g'+n" :x1="gridLeft" :y1="gridTop+(n-1)*yStep" :x2="gridRight" :y2="gridTop+(n-1)*yStep" stroke="#f0f2f7" stroke-width="1"/>
          <text v-for="n in 5" :key="'yl'+n" :x="gridLeft-8" :y="gridTop+(n-1)*yStep+4" text-anchor="end" font-size="10" fill="#8C95A6">{{ yLabel(chart, n) }}</text>
          <g v-for="(m, mi) in chart.months" :key="'bar'+mi">
            <rect v-for="(bar, bi) in m.bars" :key="'b'+bi"
              :x="barX(mi, bi, chart)" :y="barY(bar.val, chart)"
              :width="barW(chart)" :height="Math.max(0, barH(bar.val, chart))"
              :fill="bar.color" :opacity="barH(bar.val, chart) > 0 ? 1 : 0" rx="2">
              <title>{{ bar.label }} {{ m.label }}: {{ bar.val }}</title>
            </rect>
          </g>
          <text v-for="(m, mi) in chart.months" :key="'xl'+mi" :x="barCenter(mi, chart)" :y="chartH-6" text-anchor="middle" font-size="10" fill="#8C95A6">{{ m.label }}</text>
        </svg>
        <div class="chart-legend" v-if="chart.legend">
          <span v-for="leg in chart.legend" :key="leg.label" class="legend-item">
            <i :style="{ background: leg.color }"></i>{{ leg.label }}
          </span>
        </div>
      </div>
    </div>

    <div v-if="!loading" class="table-count" style="margin-top:8px">
      {{ selectedYear }}年 · 共 {{ summaryCards.length }} 项指标 · 数据更新于 {{ lastUpdate }}
    </div>
  </WorkbenchLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import WorkbenchLayout from '../layouts/WorkbenchLayout.vue';
import DataLoadingOverlay from '../components/DataLoadingOverlay.vue';
import { fetchKpi, fetchMonthlyStats } from '../api/dashboard.js';
import { fetchPositions } from '../api/auth.js';

const loading = ref(true);
const loadError = ref('');
const apiKpis = ref([]);
const monthlyData = ref([]);
const selectedYear = ref(new Date().getFullYear());
const selectedDept = ref('');
const selectedPosition = ref('');
const years = [2024, 2025, 2026, 2027];
const lastUpdate = ref('—');
const deptOptions = ref([]);
const positionOptions = ref([]);

// Chart layout
const chartW = 520, chartH = 200;
const gridLeft = 50, gridRight = 500, gridTop = 16, gridBottom = 170;
const yStep = (gridBottom - gridTop) / 4;
const barGroupW = (gridRight - gridLeft) / 12;

// KPI
const KPI_COLORS = ['#4F6EF7', '#F59E0B', '#8B5CF6', '#06B6D4', '#22C55E'];
const summaryCards = computed(() => {
  const kpis = apiKpis.value || [];
  return kpis.slice(0, 4).map((k, i) => ({
    label: k.label || '—', val: k.val != null ? k.val : '—',
    sub: k.trend || '', color: KPI_COLORS[i % KPI_COLORS.length],
  }));
});

// Bar charts from real monthly data
const chartData = computed(() => {
  const months = monthlyData.value || [];
  if (!months.length) return [];

  const maxResume = Math.max(...months.map(m => m.resumes || 0), 1);
  const maxInterview = Math.max(...months.map(m => m.interviews || 0), 1);
  const maxHire = Math.max(...months.map(m => m.hires || 0), 1);

  return [
    {
      title: '每月简历入库',
      maxVal: maxResume + Math.ceil(maxResume * 0.2),
      legend: [{ label: '简历', color: '#4F6EF7' }],
      months: months.map(m => ({ label: m.label, bars: [{ val: m.resumes || 0, color: '#4F6EF7', label: '简历' }] })),
    },
    {
      title: '每月面试场次',
      maxVal: maxInterview + Math.ceil(maxInterview * 0.2),
      legend: [{ label: '面试', color: '#F59E0B' }],
      months: months.map(m => ({ label: m.label, bars: [{ val: m.interviews || 0, color: '#F59E0B', label: '面试' }] })),
    },
    {
      title: '每月入职人数',
      maxVal: maxHire + Math.ceil(maxHire * 0.2),
      legend: [{ label: '入职', color: '#22C55E' }],
      months: months.map(m => ({ label: m.label, bars: [{ val: m.hires || 0, color: '#22C55E', label: '入职' }] })),
    },
    {
      title: '招聘漏斗对比（简历→面试→入职）',
      maxVal: Math.max(maxResume, maxInterview, maxHire) + 2,
      legend: [
        { label: '简历', color: '#CBD5E1' },
        { label: '面试', color: '#F59E0B' },
        { label: '入职', color: '#22C55E' },
      ],
      months: months.map(m => ({
        label: m.label,
        bars: [
          { val: m.resumes || 0, color: '#CBD5E1', label: '简历' },
          { val: m.interviews || 0, color: '#F59E0B', label: '面试' },
          { val: m.hires || 0, color: '#22C55E', label: '入职' },
        ],
      })),
    },
  ];
});

// Chart helpers
function yLabel(chart, n) { const max = chart.maxVal || 10; return Math.round(max - ((n-1)*max)/4); }
function barX(mi, bi, chart) { const n = chart.months[0]?.bars?.length || 1; const each = (barGroupW-4)/n; return gridLeft + mi*barGroupW + 2 + bi*each + 1; }
function barW(chart) { const n = chart.months[0]?.bars?.length || 1; return Math.max(2, (barGroupW-4)/n - 2); }
function barY(val, chart) { const max = chart.maxVal || 1; return gridBottom - (val/max)*(gridBottom-gridTop); }
function barH(val, chart) { const max = chart.maxVal || 1; return (val/max)*(gridBottom-gridTop); }
function barCenter(mi, chart) { return gridLeft + mi*barGroupW + barGroupW/2; }

async function refreshData() {
  loadError.value = '';
  loading.value = true;
  try {
    const params = { year: selectedYear.value };
    if (selectedDept.value) params.dept_id = selectedDept.value;
    if (selectedPosition.value) params.position_id = selectedPosition.value;
    const [kpiResult, monthlyResult] = await Promise.allSettled([
      fetchKpi(params),
      fetchMonthlyStats(params),
    ]);
    apiKpis.value = kpiResult.status === 'fulfilled' ? (Array.isArray(kpiResult.value) ? kpiResult.value : []) : [];
    if (monthlyResult.status === 'fulfilled') {
      monthlyData.value = monthlyResult.value?.months || monthlyResult.value?.data?.months || [];
    }
  } catch (e) {
    loadError.value = '数据加载失败';
    console.warn('[Dashboard] fetch failed:', e);
  }
  lastUpdate.value = new Date().toLocaleTimeString('zh-CN', { hour:'2-digit', minute:'2-digit' });
  loading.value = false;
}

function onFilterChange() {
  refreshData();
}

async function loadFilterOptions() {
  try {
    const { fetchDepartments } = await import('../api/config.js');
    deptOptions.value = Array.isArray(await fetchDepartments()) ? await fetchDepartments() : [];
  } catch { deptOptions.value = []; }
  try {
    positionOptions.value = Array.isArray(await fetchPositions()) ? await fetchPositions() : [];
  } catch { positionOptions.value = []; }
}

onMounted(() => {
  loadFilterOptions();
  refreshData();
});
</script>

<style scoped>
.analytics-cards { display:grid; grid-template-columns:repeat(4,1fr); gap:16px; margin-bottom:20px; perspective:800px; }
.ana-card { background:var(--c-card,#fff); border:1px solid var(--c-border,#E1E6EF); border-radius:10px; padding:16px; position:relative; overflow:hidden; transition:transform .25s,box-shadow .25s; }
.ana-card::before { content:''; position:absolute; top:0; left:0; right:0; height:3px; background:var(--card-accent); }
.ana-card:hover { box-shadow:0 12px 32px rgba(23,32,51,.1); }
.ana-card-val { font-size:22px; font-weight:700; color:var(--c-text,#172033); font-variant-numeric:tabular-nums; }
.ana-card-label { font-size:13px; color:var(--c-sub,#5B6475); margin-top:2px; }
.ana-card-sub { font-size:11px; color:var(--c-muted,#8C95A6); margin-top:2px; }
.chart-grid { display:grid; grid-template-columns:repeat(2,1fr); gap:16px; }
.chart-card { background:var(--c-card,#fff); border:1px solid var(--c-border,#E1E6EF); border-radius:10px; padding:16px; }
.chart-title { font-size:14px; font-weight:600; color:var(--c-text,#172033); margin-bottom:8px; }
.chart-svg { width:100%; height:auto; }
.chart-legend { display:flex; gap:16px; margin-top:6px; font-size:12px; color:var(--c-sub,#5B6475); }
.legend-item { display:flex; align-items:center; gap:5px; }
.legend-item i { display:inline-block; width:10px; height:10px; border-radius:2px; }
.table-count { font-size:12px; color:var(--c-muted,#8C95A6); }
@media (max-width:768px) { .analytics-cards{grid-template-columns:repeat(2,1fr)} .chart-grid{grid-template-columns:1fr} }
</style>
