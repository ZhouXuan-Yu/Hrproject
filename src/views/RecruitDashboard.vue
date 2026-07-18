<template>
  <WorkbenchLayout title="招聘看板" :breadcrumb="{ text: '招聘管理', href: '/recruit-dashboard' }">
    <template #topbar-actions>
      <span style="font-size:11px;color:var(--c-sub)">更新于 07-15 09:00</span>
      <select id="timeRange" v-model="timeRange" @change="refreshDashboard">
        <option value="month">本月</option><option value="week">本周</option><option value="today">今日</option>
      </select>
      <select v-if="!isInterviewerRole" id="deptScope" v-model="deptScope" @change="refreshDashboard">
        <option value="all">全公司</option>
      </select>
      <!-- Risk alert bell -->
      <div style="position:relative">
        <button class="bell-btn" @click="showAlerts = !showAlerts" id="alertBtn" title="风险预警">
          <svg viewBox="0 0 24 24" style="width:18px;height:18px;stroke:var(--c-warn);fill:#FFF5E0;stroke-width:2;stroke-linecap:round;stroke-linejoin:round"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>
          <span class="badge">4</span>
        </button>
        <div id="alertDropdown" v-if="showAlerts" style="display:block;position:absolute;top:calc(100% + 6px);right:0;width:400px;background:var(--c-card);border:1px solid var(--c-border);border-radius:12px;padding:16px;box-shadow:0 8px 32px rgba(0,0,0,.12);z-index:100;font-size:13px">
          <div style="font-weight:700;margin-bottom:10px;color:var(--c-text);font-size:14px">招聘风险预警</div>
          <div v-for="(alert, i) in RISK_ALERTS" :key="i" style="display:flex;align-items:center;justify-content:space-between;padding:7px 0">
            <span><span class="alert-dot" :class="alert.type"></span> {{ alert.text }}</span>
            <button class="btn btn-outline btn-sm" @click="navigateTo(alert.link)">{{ alert.action }}</button>
          </div>
        </div>
      </div>
    </template>

    <!-- KPI row -->
    <div class="metric-row dashboard-kpi-row" style="margin-bottom:20px">
      <div v-for="(kpi, i) in kpis" :key="i" class="metric-card dashboard-kpi-card"
        :style="{ '--kpi-accent': kpiAccent(i) }"
        @mousemove="onKpiHover(i, $event)" @mouseleave="onKpiLeave(i)">
        <div class="metric-icon dashboard-kpi-icon" v-html="kpi.icon"></div>
        <div><div class="metric-value">{{ kpi.val }}</div><div class="metric-label">{{ kpi.label }}</div></div>
        <div class="kpi-trend">{{ kpiTrend(i) }}</div>
      </div>
    </div>

    <!-- Funnel -->
    <div class="card dashboard-funnel-card" style="margin-bottom:12px">
      <div class="card-title">
        <svg viewBox="0 0 24 24" style="width:18px;height:18px"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>
        招聘全漏斗
        <span style="font-weight:400;font-size:11px;color:var(--c-sub);margin-left:8px">本月 · 点击跳转</span>
        <span style="font-weight:400;font-size:11px;color:var(--c-primary);margin-left:auto">总转化率 1.4%</span>
      </div>
      <div class="funnel-viz" :class="{ 'funnel-entered': funnelAnimated }" style="max-width:720px;margin:0 auto">
        <div v-for="(step, i) in FUNNEL_STEPS" :key="i"
          class="funnel-step"
          :class="{ 'funnel-hovered': funnelHover === i }"
          :style="{ '--w': calcFunnelWidth(i), '--h': '52px', '--delay': (i * 80) + 'ms', '--accent': step.color || 'var(--c-primary)', '--opacity': step.opacity }"
          @click="$router.push(step.link)"
          @mouseenter="funnelHover = i" @mouseleave="funnelHover = -1">
          <div class="funnel-step-content">
            <span class="funnel-step-label">{{ step.label }}</span>
            <span class="funnel-step-count"><b>{{ step.count }}</b></span>
            <span class="funnel-step-pct">{{ step.pct }}</span>
          </div>
          <div v-if="i < FUNNEL_STEPS.length - 1" class="funnel-conversion">
            <svg width="12" height="8" viewBox="0 0 12 8"><path d="M6 8L0 0h12z" fill="var(--c-sub)"/></svg>
            <span>{{ conversionRate(i) }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Department progress (collapsible) -->
    <div class="card" style="margin-bottom:12px">
      <div class="collapse-toggle" :class="{ open: deptOpen }" role="button" tabindex="0" :aria-expanded="deptOpen ? 'true' : 'false'" aria-controls="bodyDept" data-collapse-enhanced="true" @click="deptOpen = !deptOpen" @keydown.enter.space.prevent="deptOpen = !deptOpen">
        <svg viewBox="0 0 24 24" style="width:16px;height:16px;fill:none;stroke:var(--c-body);stroke-width:2;stroke-linecap:round;stroke-linejoin:round;transition:transform .2s;flex-shrink:0"><polyline points="9 18 15 12 9 6"/></svg>
        <span class="card-title" style="margin-bottom:0">部门招聘进度</span>
        <span class="collapse-summary">{{ deptSummary }}</span>
      </div>
      <div class="collapse-body" id="bodyDept" :class="{ show: deptOpen }">
        <div v-for="(d, i) in DEPT_PROGRESS" :key="i" class="progress-inline">
          <span style="width:64px;font-weight:600">{{ d.dept }}</span>
          <span style="width:40px;color:var(--c-sub);text-align:right">{{ d.hired }}/{{ d.total }}</span>
          <template v-for="j in d.total" :key="j">
            <span :class="j <= d.hired ? 'bar-filled' : 'bar-empty'" style="width:60px"></span>
          </template>
          <span :style="{fontWeight:'700', color: d.pct === 100 ? 'var(--c-done)' : (d.pct === 0 ? 'var(--c-sub)' : 'var(--c-primary)'), marginLeft:'8px'}">{{ d.pct }}%<template v-if="d.pct === 100"> ✓</template></span>
        </div>
      </div>
    </div>

    <!-- Channel effectiveness (collapsible) -->
    <div class="card">
      <div class="collapse-toggle" :class="{ open: channelOpen }" role="button" tabindex="0" :aria-expanded="channelOpen ? 'true' : 'false'" aria-controls="bodyChannel" data-collapse-enhanced="true" @click="channelOpen = !channelOpen" @keydown.enter.space.prevent="channelOpen = !channelOpen">
        <svg viewBox="0 0 24 24" style="width:16px;height:16px;fill:none;stroke:var(--c-body);stroke-width:2;stroke-linecap:round;stroke-linejoin:round;transition:transform .2s;flex-shrink:0"><polyline points="9 18 15 12 9 6"/></svg>
        <span class="card-title" style="margin-bottom:0">渠道效果统计</span>
        <span class="collapse-summary">{{ channelSummary }}</span>
      </div>
      <div class="collapse-body" id="bodyChannel" :class="{ show: channelOpen }">
        <table><thead><tr><th>渠道</th><th>简历</th><th>通过</th><th>面试</th><th>录用</th><th>人均成本</th></tr></thead><tbody>
          <tr v-for="(c, i) in CHANNEL_DATA" :key="i">
            <td>{{ c.channel }}</td>
            <td class="numeric">{{ c.resume }}</td>
            <td class="numeric">{{ c.pass }}</td>
            <td class="numeric">{{ c.interview }}</td>
            <td class="numeric">{{ c.hire }}</td>
            <td class="numeric">{{ c.cost }}</td>
          </tr>
        </tbody></table>
        <div class="table-count">共 {{ CHANNEL_DATA.length }} 条渠道数据 · 上次更新 07-15 09:00</div>
      </div>
    </div>
  </WorkbenchLayout>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import WorkbenchLayout from '../layouts/WorkbenchLayout.vue';
import { KPI_SETS, FUNNEL_STEPS, DEPT_PROGRESS, CHANNEL_DATA, RISK_ALERTS } from '../data/dashboard.js';

const router = useRouter();
const timeRange = ref('month');
const deptScope = ref('all');
const showAlerts = ref(false);
const deptOpen = ref(false);
const channelOpen = ref(false);
const funnelHover = ref(-1);
const funnelAnimated = ref(false);
const kpiTransforms = ref({});

const role = localStorage.getItem('hr_role') || 'hr';
const isInterviewerRole = role === 'interviewer' || role === 'temp_interviewer';

const kpis = computed(() => {
  if (role === 'admin') return KPI_SETS.admin;
  if (role === 'interviewer' || role === 'temp_interviewer') return KPI_SETS.interviewer;
  return KPI_SETS.hr;
});

const deptSummary = computed(() => DEPT_PROGRESS.map(d => d.dept + ' ' + d.hired + '/' + d.total).join(' · '));
const channelSummary = computed(() => CHANNEL_DATA.map(c => c.channel + ' ' + c.resume).join(' · '));

function calcFunnelWidth(i) {
  const widths = ['100%', '82%', '60%', '38%', '24%'];
  return widths[i] || '100%';
}

function conversionRate(i) {
  const steps = FUNNEL_STEPS;
  if (i >= steps.length - 1) return '';
  const cur = steps[i].count, next = steps[i + 1].count;
  return ((next / cur) * 100).toFixed(1) + '%';
}

function kpiAccent(i) {
  const colors = ['var(--c-primary)', 'var(--c-done)', 'var(--c-warn)', 'var(--c-reject)'];
  return colors[i % colors.length];
}

function kpiTrend(i) {
  const trends = ['+2 昨日', '+3 昨日', '+1 昨日', '持平'];
  return trends[i % trends.length];
}

function onKpiHover(i, e) {
  const el = e.currentTarget;
  const rect = el.getBoundingClientRect();
  const x = (e.clientX - rect.left) / rect.width - 0.5;
  const y = (e.clientY - rect.top) / rect.height - 0.5;
  kpiTransforms.value[i] = 'perspective(600px) rotateY(' + (x * 6) + 'deg) rotateX(' + (-y * 4) + 'deg) translateZ(8px)';
  el.style.transform = kpiTransforms.value[i];
  el.style.zIndex = '2';
}

function onKpiLeave(i) {
  kpiTransforms.value[i] = '';
  const el = document.querySelectorAll('.dashboard-kpi-card')[i];
  if (el) { el.style.transform = ''; el.style.zIndex = ''; }
}

function refreshDashboard() {}
function navigateTo(path) {
  showAlerts.value = false;
  router.push(path);
}

// Close alerts on external click
function onDocClick(e) {
  const btn = document.getElementById('alertBtn'), dd = document.getElementById('alertDropdown');
  if (showAlerts.value && dd && btn && !btn.contains(e.target) && !dd.contains(e.target)) showAlerts.value = false;
}
onMounted(() => {
  document.addEventListener('click', onDocClick);
  // Trigger funnel entrance animation
  setTimeout(() => { funnelAnimated.value = true; }, 100);
});
onUnmounted(() => document.removeEventListener('click', onDocClick));
</script>

<style scoped>
.bell-btn {
  position: relative; width: 34px; height: 34px; border-radius: 50%;
  border: 1px solid var(--c-border); background: var(--c-card); cursor: pointer;
  display: flex; align-items: center; justify-content: center; transition: all .2s;
}
.bell-btn:hover { background: #FFF5F5; border-color: var(--c-warn); }
.bell-btn .badge {
  position: absolute; top: -5px; right: -5px; min-width: 18px; height: 18px;
  border-radius: 9px; background: var(--c-reject); color: #fff;
  font-size: 10px; line-height: 18px; text-align: center; font-weight: 700; padding: 0 5px;
}
@keyframes ring { 0%,100%{transform:rotate(0)} 10%{transform:rotate(12deg)} 20%{transform:rotate(-12deg)} 30%{transform:rotate(8deg)} 40%{transform:rotate(-8deg)} 50%{transform:rotate(0)} }
.bell-btn:hover :deep(svg) { animation: ring .6s ease-in-out; }
.collapse-toggle { display: flex; align-items: center; gap: 6px; cursor: pointer; user-select: none; padding: 2px 0; }
.collapse-toggle.open :deep(svg) { transform: rotate(90deg); }
.collapse-body { display: none; margin-top: 12px; }
.collapse-body.show { display: block; }
.collapse-summary { font-size: 12px; color: var(--c-sub); margin-left: 8px; font-weight: 400; }
.alert-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 6px; vertical-align: middle; }
.alert-dot.reject { background: var(--c-reject); }
.alert-dot.warn { background: var(--c-warn); }
.alert-dot.done { background: var(--c-done); }

/* ===== Dashboard 3D Professional ===== */

/* KPI cards — 3D tilt on hover */
.dashboard-kpi-row {
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
.dashboard-kpi-card:hover {
  box-shadow: 0 12px 32px rgba(23,32,51,.1);
}
.dashboard-kpi-icon {
  transition: transform .2s ease;
}
.dashboard-kpi-card:hover .dashboard-kpi-icon {
  transform: scale(1.1);
}
.kpi-trend {
  position: absolute;
  top: 12px; right: 14px;
  font-size: 10px;
  color: var(--c-sub);
  font-weight: 600;
  opacity: 0;
  transition: opacity .2s;
}
.dashboard-kpi-card:hover .kpi-trend { opacity: 1; }

/* Funnel — 3D isometric */
.funnel-viz {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0;
  perspective: 900px;
  padding: 20px 0;
}
.funnel-step {
  position: relative;
  width: var(--w);
  height: var(--h);
  margin-bottom: 6px;
  cursor: pointer;
  transform-style: preserve-3d;
  opacity: 0;
  transform: translateY(20px) rotateX(-8deg);
  transition: opacity .4s ease var(--delay), transform .4s ease var(--delay);
}
.funnel-entered .funnel-step {
  opacity: 1;
  transform: translateY(0) rotateX(0deg);
}
.funnel-step-content {
  width: 100%;
  height: 100%;
  background: var(--accent);
  opacity: var(--opacity);
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  color: #fff;
  font-size: 14px;
  position: relative;
  z-index: 2;
  transition: transform .2s ease, box-shadow .2s ease;
  box-shadow: 0 4px 12px rgba(23,32,51,.15);
}
.funnel-step:hover .funnel-step-content {
  transform: translateZ(12px) scale(1.02);
  box-shadow: 0 8px 24px rgba(23,32,51,.2);
}
.funnel-step-label {
  font-weight: 600;
}
.funnel-step-count b {
  font-size: 18px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}
.funnel-step-pct {
  font-size: 12px;
  opacity: .85;
}
.funnel-conversion {
  position: absolute;
  bottom: -14px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 10px;
  color: var(--c-sub);
  font-weight: 600;
  z-index: 1;
  opacity: 0;
  transition: opacity .3s;
}
.funnel-step:hover .funnel-conversion,
.funnel-entered .funnel-conversion {
  opacity: 1;
}

/* Reduced motion */
@media (prefers-reduced-motion: reduce) {
  .dashboard-kpi-card,
  .funnel-step,
  .funnel-step-content {
    transition: none !important;
    transform: none !important;
  }
  .funnel-entered .funnel-step {
    opacity: 1;
    transform: none;
  }
}
</style>