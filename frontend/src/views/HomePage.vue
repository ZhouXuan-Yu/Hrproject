<template>
  <WorkbenchLayout title="首页" :breadcrumb="{ text: '招聘管理', href: '/home' }">
    <template #topbar-actions>
      <span style="font-size:11px;color:var(--c-sub)">更新于 {{ lastUpdate }}</span>
    </template>

    <!-- KPI row: 4 cards -->
    <div class="metric-row dashboard-kpi-row data-region" style="margin-bottom:20px">
      <DataLoadingOverlay :visible="loading" />
      <div
        v-for="(kpi, i) in homeKpis"
        :key="i"
        class="metric-card dashboard-kpi-card"
        :style="{ '--kpi-accent': kpiAccent(i) }"
      >
        <div class="metric-icon dashboard-kpi-icon" v-html="kpi.icon"></div>
        <div>
          <div class="metric-value">{{ kpi.val }}</div>
          <div class="metric-label">{{ kpi.label }}</div>
        </div>
      </div>
    </div>

    <!-- Funnel Hero -->
    <FunnelHero ref="funnelRef" />

    <!-- Department progress (collapsible) -->
    <div class="card data-region" style="margin-bottom:12px">
      <DataLoadingOverlay :visible="loading" />
      <div class="collapse-toggle" :class="{ open: deptOpen }" role="button" tabindex="0" :aria-expanded="deptOpen ? 'true' : 'false'" aria-controls="bodyDept" data-collapse-enhanced="true" @click="deptOpen = !deptOpen" @keydown.enter.space.prevent="deptOpen = !deptOpen">
        <svg viewBox="0 0 24 24" style="width:16px;height:16px;fill:none;stroke:var(--c-body);stroke-width:2;stroke-linecap:round;stroke-linejoin:round;transition:transform .2s;flex-shrink:0"><polyline points="9 18 15 12 9 6"/></svg>
        <span class="card-title" style="margin-bottom:0">部门招聘进度</span>
        <span class="collapse-summary">{{ deptSummary }}</span>
      </div>
      <div class="collapse-body" id="bodyDept" :class="{ show: deptOpen }">
        <div v-for="(d, i) in DEPT_PROGRESS_" :key="i" class="progress-inline">
          <span style="min-width:80px;font-weight:600;white-space:nowrap">{{ d.dept }}</span>
          <span style="width:40px;color:var(--c-sub);text-align:right">{{ d.hired }}/{{ d.total }}</span>
          <template v-for="j in d.total" :key="j">
            <span :class="j <= d.hired ? 'bar-filled' : 'bar-empty'" style="width:60px"></span>
          </template>
          <span :style="{fontWeight:'700', color: d.pct === 100 ? 'var(--c-done)' : (d.pct === 0 ? 'var(--c-sub)' : 'var(--c-primary)'), marginLeft:'8px'}">{{ d.pct }}%<template v-if="d.pct === 100"> ✓</template></span>
        </div>
      </div>
    </div>

    <!-- Channel effectiveness (collapsible) -->
    <div class="card data-region">
      <DataLoadingOverlay :visible="loading" />
      <div class="collapse-toggle" :class="{ open: channelOpen }" role="button" tabindex="0" :aria-expanded="channelOpen ? 'true' : 'false'" aria-controls="bodyChannel" data-collapse-enhanced="true" @click="channelOpen = !channelOpen" @keydown.enter.space.prevent="channelOpen = !channelOpen">
        <svg viewBox="0 0 24 24" style="width:16px;height:16px;fill:none;stroke:var(--c-body);stroke-width:2;stroke-linecap:round;stroke-linejoin:round;transition:transform .2s;flex-shrink:0"><polyline points="9 18 15 12 9 6"/></svg>
        <span class="card-title" style="margin-bottom:0">渠道效果统计</span>
        <span class="collapse-summary">{{ channelSummary }}</span>
      </div>
      <div class="collapse-body" id="bodyChannel" :class="{ show: channelOpen }">
        <table><thead><tr><th>渠道</th><th>简历</th><th>通过</th><th>面试</th><th>录用</th><th>人均成本</th></tr></thead><tbody>
          <tr v-for="(c, i) in CHANNEL_DATA_" :key="i">
            <td>{{ c.channel }}</td>
            <td class="numeric">{{ c.resume }}</td>
            <td class="numeric">{{ c.pass }}</td>
            <td class="numeric">{{ c.interview }}</td>
            <td class="numeric">{{ c.hire }}</td>
            <td class="numeric">{{ c.cost }}</td>
          </tr>
        </tbody></table>
        <div class="table-count">共 {{ CHANNEL_DATA_.length }} 条渠道数据</div>
      </div>
    </div>
  </WorkbenchLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import WorkbenchLayout from '../layouts/WorkbenchLayout.vue';
import FunnelHero from '../components/FunnelHero.vue';
import { fetchKpi, fetchDeptProgress, fetchChannel } from '../api/dashboard.js';
import DataLoadingOverlay from '../components/DataLoadingOverlay.vue';

const loading = ref(true);
const apiKpis = ref([]);
const apiDeptProgress = ref([]);
const apiChannelData = ref([]);
const lastUpdate = ref('—');
const deptOpen = ref(false);
const channelOpen = ref(false);

const DEPT_PROGRESS_ = computed(() => apiDeptProgress.value || []);
const CHANNEL_DATA_ = computed(() => apiChannelData.value || []);
const deptSummary = computed(() => DEPT_PROGRESS_.value.map(d => d.dept + ' ' + d.hired + '/' + d.total).join(' · '));
const channelSummary = computed(() => CHANNEL_DATA_.value.map(c => c.channel + ' ' + c.resume).join(' · '));

const HOME_KPI_KEYS = ['在招岗位', '待面试', '面试通过', '待入职'];
const HOME_KPI_COLORS = ['var(--c-primary)', 'var(--c-warn)', 'var(--c-done)', '#8B5CF6'];

const homeKpis = computed(() => {
  const all = apiKpis.value || [];
  // Try to match by label, fallback to first 4 items
  const matched = HOME_KPI_KEYS.map((key, i) => {
    const hit = all.find(k => k.label === key);
    return hit || { label: key, val: '—', icon: '' };
  });
  return matched;
});

function kpiAccent(i) {
  return HOME_KPI_COLORS[i % HOME_KPI_COLORS.length];
}

async function loadData() {
  loading.value = true;
  try {
    const [kpiData, deptData, channelData] = await Promise.all([
      fetchKpi(), fetchDeptProgress(), fetchChannel()
    ]);
    apiKpis.value = Array.isArray(kpiData) ? kpiData : [];
    apiDeptProgress.value = Array.isArray(deptData) ? deptData : [];
    apiChannelData.value = Array.isArray(channelData) ? channelData : [];
  } catch (e) {
    console.warn('Home data fetch failed:', e.message);
    apiKpis.value = [];
    apiDeptProgress.value = [];
    apiChannelData.value = [];
  } finally {
    loading.value = false;
    lastUpdate.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
  }
}

onMounted(() => {
  loadData();
});
</script>

<style scoped>
/* Inherit dashboard KPI styles */
.dashboard-kpi-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  perspective: 800px;
}
.dashboard-kpi-card {
  position: relative;
  transition: transform .25s ease, box-shadow .25s ease;
  cursor: default;
  overflow: hidden;
}
.dashboard-kpi-card::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 3px;
  background: var(--kpi-accent);
  opacity: 0;
  transition: opacity .2s;
}
.dashboard-kpi-card:hover::before { opacity: 1; }
.dashboard-kpi-card:hover { box-shadow: 0 12px 32px rgba(23,32,51,.1); }
.data-region { position: relative; min-height: 80px; }

@media (max-width: 768px) {
  .dashboard-kpi-row {
    grid-template-columns: repeat(2, 1fr);
  }
}

.collapse-toggle { display: flex; align-items: center; gap: 6px; cursor: pointer; user-select: none; padding: 2px 0; }
.collapse-toggle.open :deep(svg) { transform: rotate(90deg); }
.collapse-body { display: none; margin-top: 12px; }
.collapse-body.show { display: block; }
.collapse-summary { font-size: 12px; color: var(--c-sub); margin-left: 8px; font-weight: 400; }

@media (prefers-reduced-motion: reduce) {
  .dashboard-kpi-card { transition: none !important; transform: none !important; }
}
</style>
