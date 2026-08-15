<template>
  <WorkbenchLayout title="招聘项目" :breadcrumb="{ text: '招聘管理', href: '/recruit-dashboard' }">
    <template #topbar-actions>
      <span class="dashboard-refresh-time">{{ statusText }} · {{ lastUpdate }}</span>
      <select v-model="selectedYear" aria-label="选择年份" @change="onFilterChange">
        <option v-for="year in years" :key="year" :value="year">{{ year }}年</option>
      </select>
      <select v-model="selectedDept" aria-label="选择部门" @change="onFilterChange">
        <option value="">全部部门</option>
        <option v-for="dept in deptOptions" :key="dept.id || dept.name" :value="dept.id">{{ dept.name }}</option>
      </select>
      <select v-model="selectedPosition" aria-label="选择岗位" @change="onFilterChange">
        <option value="">全部岗位</option>
        <option v-for="position in positionOptions" :key="position.id || position.name" :value="position.id">{{ position.name }}</option>
      </select>
      <button class="btn btn-ghost btn-sm dashboard-refresh" type="button" :disabled="loading" aria-label="刷新看板数据" @click="refreshData">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20 11a8 8 0 0 0-14.9-3M4 5v4h4M4 13a8 8 0 0 0 14.9 3M20 19v-4h-4"/></svg>
        刷新
      </button>
    </template>

    <div class="dashboard-page" data-testid="recruit-dashboard-page">
      <DataLoadingOverlay :visible="loading" text="正在汇总招聘项目与面试数据" />

      <div v-if="loadError" class="dashboard-error-banner" role="alert" data-testid="dashboard-error">
        <span>{{ loadError }}</span>
        <button class="btn btn-outline btn-sm" type="button" @click="refreshData">重新加载</button>
      </div>

      <HeroUiRecruitCharts
        :demands="demands"
        :events="calendarEvents"
        :months="monthlyData"
        :calendar-month="calendarMonth"
        :year="selectedYear"
        @calendar-month-change="loadCalendar"
      />

      <div class="dashboard-footnote">
        <span class="dashboard-footnote-dot" aria-hidden="true"></span>
        数据来自招聘需求、候选人流程和面试预约接口；无数据时保留空状态，不以静态数字填充。
      </div>
    </div>
  </WorkbenchLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import WorkbenchLayout from '../layouts/WorkbenchLayout.vue';
import DataLoadingOverlay from '../components/DataLoadingOverlay.vue';
import HeroUiRecruitCharts from '../components/dashboard/HeroUiRecruitCharts.vue';
import { fetchDepartments } from '../api/config.js';
import { fetchPositions } from '../api/auth.js';
import { fetchDemands } from '../api/demand.js';
import { fetchInterviewCalendar } from '../api/interview.js';
import { fetchMonthlyStats } from '../api/dashboard.js';

const loading = ref(true);
const loadError = ref('');
const partialFailure = ref(false);
const demands = ref([]);
const monthlyData = ref([]);
const calendarEvents = ref([]);
const selectedYear = ref(new Date().getFullYear());
const selectedDept = ref('');
const selectedPosition = ref('');
const calendarMonth = ref(formatMonthKey(new Date()));
const years = [2024, 2025, 2026, 2027];
const lastUpdate = ref('—');
const deptOptions = ref([]);
const positionOptions = ref([]);

const statusText = computed(() => {
  if (loading.value) return '数据加载中';
  if (partialFailure.value) return '部分数据不可用';
  if (!demands.value.length && !monthlyData.value.length && !calendarEvents.value.length) return '暂无项目数据';
  return '项目数据已更新';
});

async function refreshData() {
  loadError.value = '';
  partialFailure.value = false;
  loading.value = true;

  const params = { year: selectedYear.value };
  const dept = deptOptions.value.find((item) => String(item.id) === String(selectedDept.value));
  const position = positionOptions.value.find((item) => String(item.id) === String(selectedPosition.value));
  if (selectedDept.value) params.dept_id = selectedDept.value;
  if (selectedPosition.value) params.position_id = selectedPosition.value;

  const results = await Promise.allSettled([
    // The active Spring backend treats status as Integer; omitting it means "all".
    fetchDemands({ pageSize: 100 }),
    fetchMonthlyStats(params),
    loadCalendar(calendarMonth.value, false),
  ]);

  const [demandResult, monthly] = results;
  const allDemands = demandResult.status === 'fulfilled' ? normalizeArray(demandResult.value) : [];
  demands.value = allDemands.filter((item) => {
    const deptMatches = !dept || item.dept === dept.name || String(item.deptId) === String(dept.id);
    const positionMatches = !position || item.position === position.name || String(item.positionId) === String(position.id);
    return deptMatches && positionMatches;
  });
  monthlyData.value = monthly.status === 'fulfilled' ? normalizeMonths(monthly.value) : [];

  const failureCount = results.filter((result) => result.status === 'rejected').length;
  partialFailure.value = failureCount > 0 && failureCount < results.length;
  if (failureCount === results.length) loadError.value = '招聘看板数据暂时无法加载，请检查后端服务后重试。';
  lastUpdate.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
  loading.value = false;
}

async function loadCalendar(month = calendarMonth.value, updateLoading = true) {
  if (updateLoading) loading.value = true;
  calendarMonth.value = month;
  try {
    const result = await fetchInterviewCalendar({ month });
    const data = result?.events ? result : (result?.data || {});
    calendarEvents.value = Array.isArray(data.events) ? data.events : [];
  } catch (error) {
    calendarEvents.value = [];
    if (updateLoading) partialFailure.value = true;
  } finally {
    if (updateLoading) loading.value = false;
  }
}

function normalizeArray(payload) {
  if (Array.isArray(payload)) return payload;
  if (Array.isArray(payload?.data)) return payload.data;
  if (Array.isArray(payload?.items)) return payload.items;
  return [];
}

function normalizeMonths(payload) {
  if (Array.isArray(payload)) return payload;
  return payload?.months || payload?.data?.months || [];
}

function formatMonthKey(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
}

function onFilterChange() {
  refreshData();
}

async function loadFilterOptions() {
  const [departmentsResult, positionsResult] = await Promise.allSettled([fetchDepartments(), fetchPositions()]);
  deptOptions.value = departmentsResult.status === 'fulfilled' ? normalizeArray(departmentsResult.value) : [];
  positionOptions.value = positionsResult.status === 'fulfilled' ? normalizeArray(positionsResult.value) : [];
}

onMounted(() => {
  loadFilterOptions();
  refreshData();
});
</script>

<style>
.dashboard-page { --dashboard-line:#dfe5ef; --dashboard-ink:#172033; --dashboard-muted:#68758a; position:relative; min-height:720px; color:var(--dashboard-ink); }
.dashboard-refresh-time { color:var(--c-sub); font-size:11px; white-space:nowrap; }
.dashboard-refresh { display:inline-flex; align-items:center; gap:5px; }
.dashboard-refresh svg { width:14px; height:14px; fill:none; stroke:currentColor; stroke-width:1.8; stroke-linecap:round; stroke-linejoin:round; }
.dashboard-error-banner { display:flex; align-items:center; justify-content:space-between; gap:12px; margin-bottom:14px; padding:10px 14px; border:1px solid rgba(214,65,77,.24); border-radius:8px; background:#fff7f7; color:#a32935; font-size:13px; }
.dashboard-footnote { display:flex; align-items:center; gap:7px; margin:14px 0 0; color:#8a96aa; font-size:11px; }
.dashboard-footnote-dot { width:6px; height:6px; flex:0 0 auto; border-radius:50%; background:#16845a; }
@media (max-width: 680px) { .dashboard-refresh-time { display:none; } .dashboard-footnote { align-items:flex-start; line-height:1.5; } }
</style>
