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

    <!-- Funnel Hero Section -->
    <div class="card funnel-hero-card" style="margin-bottom:12px" data-viz-enhanced="funnel">
      <!-- Ambient dynamic background -->
      <div class="funnel-hero-bg" aria-hidden="true">
        <div class="funnel-bg-grid"></div>
        <div v-if="motionOK" v-for="p in ambientParticles" :key="'ap'+p.id" class="funnel-ambient-dot"
          :style="p.style"></div>
      </div>
      <div class="card-title funnel-hero-title">
        <svg viewBox="0 0 24 24" style="width:18px;height:18px"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>
        招聘全漏斗
        <span style="font-weight:400;font-size:11px;color:var(--c-sub);margin-left:8px">点击圆盘查看阶段洞察</span>
        <span style="font-weight:400;font-size:11px;color:var(--c-primary);margin-left:auto">总转化率 1.4%</span>
      </div>
      <div class="funnel-hero-body">
        <div class="funnel-viz-row">
          <!-- Centered SVG cone -->
          <div class="funnel-viz-area">
            <svg viewBox="0 0 320 460" class="funnel-viz-svg">
              <defs>
                <filter id="funnelGlow"><feGaussianBlur stdDeviation="1.5" result="blur"/><feMerge><feMergeNode in="blur"/><feMergeNode in="SourceGraphic"/></feMerge></filter>
              </defs>
              <!-- Double helix strands -->
              <g v-for="(hPath, hi) in helixPaths" :key="'h'+hi">
                <path :id="'funnelHelix'+(hi+1)" :d="hPath" fill="none" class="funnel-helix-glow"
                  :class="{ 'helix-flow': motionOK }" />
                <path :d="hPath" fill="none" class="funnel-helix-core"
                  :class="{ 'helix-flow': motionOK }" stroke-dasharray="12 8" />
              </g>
              <!-- Animated particles on helix strands (motionOK) -->
              <template v-if="motionOK">
                <circle v-for="p in 12" :key="'pp'+p" r="2.5" fill="var(--c-primary)" opacity="0.5">
                  <animateMotion :dur="(3.5 + p * 0.7) + 's'" repeatCount="indefinite" rotate="auto">
                    <mpath :href="'#funnelHelix' + ((p % 2) + 1)" />
                  </animateMotion>
                </circle>
              </template>
              <!-- Static particles (reduced-motion) -->
              <template v-else>
                <circle v-for="(sp, spi) in staticParticles" :key="'sp'+spi"
                  :cx="sp.x" :cy="sp.y" r="2" fill="var(--c-primary)" opacity="0.35" />
              </template>
              <!-- Ambient particles within SVG (motionOK only) -->
              <template v-if="motionOK">
                <circle v-for="ap in svgAmbient" :key="'sa'+ap.id"
                  :cx="ap.x" :cy="ap.y" :r="ap.r" fill="var(--c-primary)" opacity="0.08"
                  class="funnel-svg-ambient" :style="{ '--sa-dur': ap.dur+'s', '--sa-delay': ap.delay+'s' }" />
              </template>
              <!-- 5 stacked isometric discs -->
              <g v-for="(d, i) in discs" :key="'d'+i"
                class="funnel-hero-disc"
                :class="{ selected: selected === i, dimmed: selected !== null && selected !== i }"
                @click="selectStage(i)">
                <!-- Drop shadow -->
                <ellipse :cx="d.cx" :cy="d.cy + d.ry + d.wallH + 6" :rx="d.rx" :ry="d.rx * 0.08"
                  fill="rgba(23,32,51,0.07)" />
                <!-- Side wall -->
                <path :d="d.wallPath" :fill="d.accent" :opacity="d.wallOpacity" />
                <!-- Top face -->
                <ellipse :cx="d.cx" :cy="d.cy" :rx="d.rx" :ry="d.ry"
                  :fill="d.accent" :opacity="d.faceOpacity" />
                <!-- Selection ring -->
                <ellipse v-if="selected === i" :cx="d.cx" :cy="d.cy"
                  :rx="d.rx + 3" :ry="d.ry + 1.5"
                  fill="none" stroke="var(--c-primary)" stroke-width="1.5" opacity="0.45"
                  filter="url(#funnelGlow)" />
                <!-- Stage label + count -->
                <text :x="d.cx" :y="d.cy - 4" text-anchor="middle"
                  fill="#fff" font-size="14" font-weight="800"
                  style="font-variant-numeric:tabular-nums;text-shadow:0 1px 3px rgba(0,0,0,.3)"
                  pointer-events="none">{{ d.count }}</text>
                <text :x="d.cx" :y="d.cy + 14" text-anchor="middle"
                  fill="#fff" font-size="10" font-weight="600"
                  style="text-shadow:0 1px 2px rgba(0,0,0,.25)"
                  pointer-events="none">{{ d.label }}</text>
              </g>
            </svg>
          </div>
          <!-- Insight panel (right of SVG) -->
          <div class="funnel-insight-panel" v-if="sel" :key="selected">
            <div class="insight-panel-inner">
              <div class="insight-header">
                <span class="insight-dot" :style="{ background: selAccent }"></span>
                <span class="insight-label">{{ sel.label }}</span>
                <span v-if="sel.bottleneck" class="badge-bottleneck">瓶颈</span>
                <span v-else class="health-badge" :class="'health-'+sel.health">{{ healthLabel(sel.health) }}</span>
              </div>
              <div class="insight-metrics">
                <div class="insight-metric">
                  <div class="insight-metric-val">{{ sel.count }}<span class="insight-metric-unit"> 人</span></div>
                  <div class="insight-metric-sub">占 {{ sel.pct }}</div>
                </div>
                <div class="insight-metric" v-if="sel.conv">
                  <div class="insight-metric-val">
                    {{ sel.conv }}
                    <span class="wow-delta" :class="sel.wowUp ? 'up' : 'down'">
                      <svg viewBox="0 0 10 10" style="width:8px;height:8px">
                        <polyline v-if="sel.wowUp" points="1,7 5,2 9,7" fill="none" stroke="currentColor" stroke-width="1.5"/>
                        <polyline v-else points="1,3 5,8 9,3" fill="none" stroke="currentColor" stroke-width="1.5"/>
                      </svg>
                      {{ sel.wow }}
                    </span>
                  </div>
                  <div class="insight-metric-sub">转化率 · WoW</div>
                </div>
                <div class="insight-metric">
                  <div class="insight-metric-val">{{ sel.dwell }}</div>
                  <div class="insight-metric-sub">平均停留</div>
                </div>
              </div>
              <div class="insight-spark-wrap">
                <svg viewBox="0 0 140 32" class="insight-spark">
                  <polyline :points="sparkPath(sel.spark)" fill="none" :stroke="selAccent" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                  <polygon :points="sparkFillPath(sel.spark)" :fill="selAccent" opacity="0.08"/>
                </svg>
                <span class="insight-spark-label">7 天趋势</span>
              </div>
              <div class="insight-note">{{ sel.note }}</div>
              <div class="insight-owner">
                <svg viewBox="0 0 24 24" style="width:14px;height:14px;fill:none;stroke:var(--c-sub);stroke-width:2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
                {{ sel.owner }}
              </div>
              <button class="insight-cta" @click="router.push(sel.link)">查看 {{ sel.label }} 详情 →</button>
            </div>
          </div>
        </div>
      </div>
      <!-- Bottom stepper (test contract) -->
      <div class="funnel-stepper viz-funnel">
        <div v-for="(step, i) in FUNNEL_STEPS" :key="'step'+i"
          class="viz-funnel-step funnel-step-chip"
          :class="{ active: selected === i }"
          role="link" :tabindex="0"
          :aria-label="'跳转到 ' + step.label + ' 详情'"
          @click="selectStage(i)"
          @keydown.enter.space.prevent="selectStage(i)">
          <span class="step-chip-dot" :style="{ background: accentColor(step) }"></span>
          {{ step.label }}
          <span class="step-chip-count">{{ step.count }}</span>
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
const kpiTransforms = ref({});

// Funnel state
const selected = ref(3); // default: bottleneck stage (Offer)
const motionOK = ref(true);

const role = localStorage.getItem('hr_role') || 'hr';
const isInterviewerRole = role === 'interviewer' || role === 'temp_interviewer';

const kpis = computed(() => {
  if (role === 'admin') return KPI_SETS.admin;
  if (role === 'interviewer' || role === 'temp_interviewer') return KPI_SETS.interviewer;
  return KPI_SETS.hr;
});

const deptSummary = computed(() => DEPT_PROGRESS.map(d => d.dept + ' ' + d.hired + '/' + d.total).join(' · '));
const channelSummary = computed(() => CHANNEL_DATA.map(c => c.channel + ' ' + c.resume).join(' · '));

// -- Funnel: selected stage --
const sel = computed(() => FUNNEL_STEPS[selected.value] || FUNNEL_STEPS[0]);
const selAccent = computed(() => accentColor(sel.value));

function accentColor(step) { return step.color || 'var(--c-primary)'; }

// -- Isometric disc geometry (5 stacked, top→bottom) --
const discs = computed(() => {
  const cx = 160; // wider viewBox center
  const rxValues = [126, 106, 86, 66, 46];
  const wallH = 16;
  const cyValues = [60, 148, 236, 324, 412];
  return FUNNEL_STEPS.map((step, i) => {
    const rx = rxValues[i];
    const ry = rx * 0.28;
    const cy = cyValues[i];
    const accent = accentColor(step);
    const faceOpacity = 0.50 + (step.opacity || 1) * 0.45;
    const wallOpacity = faceOpacity * 0.55;
    const wallTopY = cy + ry;
    const wallBotY = cy + ry + wallH;
    const wallPath = [
      `M ${cx - rx} ${wallTopY}`,
      `A ${rx} ${ry} 0 0 0 ${cx + rx} ${wallTopY}`,
      `L ${cx + rx} ${wallBotY}`,
      `A ${rx} ${ry * 1.05} 0 0 1 ${cx - rx} ${wallBotY}`,
      `Z`
    ].join(' ');
    return { cx, rx, ry, cy, wallH, accent, faceOpacity, wallOpacity, wallPath, count: step.count, label: step.label };
  });
});

// -- Double helix paths (opposite phase) --
const helixPaths = computed(() => {
  function makeHelix(phaseOffset) {
    const totalTurns = 3.0;
    const pts = 80;
    const topY = 16;
    const botY = 450;
    const cx = 160;
    const maxRx = 145;
    const minRx = 36;
    let d = '';
    for (let i = 0; i <= pts; i++) {
      const t = i / pts;
      const angle = t * totalTurns * Math.PI * 2 + phaseOffset;
      const y = topY + t * (botY - topY);
      const r = maxRx + t * (minRx - maxRx);
      const x = cx + Math.cos(angle) * r;
      const py = y + Math.sin(angle) * r * 0.26;
      if (i === 0) d += `M ${x.toFixed(1)} ${py.toFixed(1)}`;
      else d += ` L ${x.toFixed(1)} ${py.toFixed(1)}`;
    }
    return d;
  }
  return [makeHelix(0), makeHelix(Math.PI)];
});

// -- Static particles along helix (reduced-motion) --
const staticParticles = computed(() => {
  const pts = [];
  const totalTurns = 3.0;
  const topY = 16;
  const botY = 450;
  const cx = 160;
  const maxRx = 145;
  const minRx = 36;
  const strands = [0, Math.PI];
  for (let s = 0; s < 2; s++) {
    for (let p = 0; p < 6; p++) {
      const t = (p + (s * 0.15)) / 6;
      const angle = t * totalTurns * Math.PI * 2 + strands[s];
      const y = topY + t * (botY - topY);
      const r = maxRx + t * (minRx - maxRx);
      const x = cx + Math.cos(angle) * r;
      const py = y + Math.sin(angle) * r * 0.26;
      pts.push({ x: parseFloat(x.toFixed(1)), y: parseFloat(py.toFixed(1)) });
    }
  }
  return pts;
});

// -- SVG ambient floating dots --
const svgAmbient = computed(() => {
  const dots = [];
  for (let i = 0; i < 20; i++) {
    dots.push({
      id: i,
      x: parseFloat((Math.random() * 280 + 20).toFixed(1)),
      y: parseFloat((Math.random() * 420 + 20).toFixed(1)),
      r: parseFloat((Math.random() * 6 + 2).toFixed(1)),
      dur: parseFloat((12 + Math.random() * 18).toFixed(1)),
      delay: parseFloat((Math.random() * 10).toFixed(1)),
    });
  }
  return dots;
});

// -- CSS ambient background particles --
const ambientParticles = computed(() => {
  const dots = [];
  for (let i = 0; i < 30; i++) {
    dots.push({
      id: i,
      style: {
        left: (Math.random() * 100) + '%',
        top: (Math.random() * 100) + '%',
        width: (Math.random() * 4 + 2) + 'px',
        height: (Math.random() * 4 + 2) + 'px',
        '--ad-dur': (8 + Math.random() * 20) + 's',
        '--ad-delay': (Math.random() * 12) + 's',
      }
    });
  }
  return dots;
});

// -- Sparkline helpers --
function sparkPath(spark) {
  if (!spark || !spark.length) return '0,16 140,16';
  const maxV = Math.max(...spark);
  const minV = Math.min(...spark);
  const range = maxV - minV || 1;
  const w = 140, h = 28, pad = 2;
  return spark.map((v, i) => {
    const x = pad + (i / (spark.length - 1)) * (w - pad * 2);
    const y = pad + (1 - (v - minV) / range) * (h - pad * 2);
    return `${parseFloat(x.toFixed(1))},${parseFloat(y.toFixed(1))}`;
  }).join(' ');
}

function sparkFillPath(spark) {
  if (!spark || !spark.length) return '0,30 140,30 140,32 0,32';
  const pts = sparkPath(spark).split(' ').map(p => p.split(',').map(Number));
  const last = pts[pts.length - 1];
  const first = pts[0];
  return `${first[0]},32 ${pts.map(p => `${p[0]},${p[1]}`).join(' ')} ${last[0]},32`;
}

function healthLabel(h) {
  const map = { good: '健康', watch: '关注', risk: '风险' };
  return map[h] || h;
}

function selectStage(i) { selected.value = i; }

// -- KPI 3D Tilt --
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

function onDocClick(e) {
  const btn = document.getElementById('alertBtn'), dd = document.getElementById('alertDropdown');
  if (showAlerts.value && dd && btn && !btn.contains(e.target) && !dd.contains(e.target)) showAlerts.value = false;
}

onMounted(() => {
  document.addEventListener('click', onDocClick);
  const mq = window.matchMedia('(prefers-reduced-motion: reduce)');
  motionOK.value = !mq.matches;
  mq.addEventListener('change', (e) => { motionOK.value = !e.matches; });
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

/* Funnel Hero Card — Centered 2.5D Cone + Double Helix + Ambient Background */
.funnel-hero-card {
  position: relative;
  overflow: hidden;
  min-height: 620px;
}
/* — Ambient animated background layer — */
.funnel-hero-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  overflow: hidden;
}
.funnel-bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(79,110,247,0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(79,110,247,0.03) 1px, transparent 1px);
  background-size: 40px 40px;
}
.funnel-ambient-dot {
  position: absolute;
  border-radius: 50%;
  background: var(--c-primary);
  opacity: 0.06;
  animation: ambientDrift var(--ad-dur) ease-in-out infinite;
  animation-delay: var(--ad-delay);
}
@keyframes ambientDrift {
  0%, 100% { transform: translate(0, 0) scale(1); opacity: 0.04; }
  25% { transform: translate(20px, -15px) scale(1.2); opacity: 0.08; }
  50% { transform: translate(-10px, 10px) scale(0.9); opacity: 0.05; }
  75% { transform: translate(15px, 20px) scale(1.1); opacity: 0.07; }
}

/* — Title stays above bg — */
.funnel-hero-title {
  position: relative;
  z-index: 2;
}

/* — Main body — */
.funnel-hero-body {
  position: relative;
  z-index: 1;
}
.funnel-viz-row {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: center;
  padding: 4px 0 0;
}

/* — SVG viz area (centered) — */
.funnel-viz-area {
  flex: 0 0 320px;
  min-width: 260px;
  max-width: 340px;
}
.funnel-viz-svg {
  width: 100%;
  height: 460px;
  display: block;
  filter: drop-shadow(0 4px 20px rgba(79,110,247,0.06));
}

/* — Double helix styles — */
.funnel-helix-glow {
  stroke: var(--c-primary);
  stroke-width: 1.0;
  opacity: 0.10;
  filter: blur(2px);
}
.funnel-helix-core {
  stroke: var(--c-primary);
  stroke-width: 1.2;
  opacity: 0.15;
  stroke-linecap: round;
}
.funnel-helix-core.helix-flow,
.funnel-helix-glow.helix-flow {
  animation: helixOrbit 3.0s linear infinite;
}
@keyframes helixOrbit {
  from { stroke-dashoffset: 0; }
  to { stroke-dashoffset: -40; }
}

/* — SVG ambient floating dots (drift within SVG) — */
.funnel-svg-ambient {
  animation: svgAmbientDrift var(--sa-dur) ease-in-out infinite;
  animation-delay: var(--sa-delay);
}
@keyframes svgAmbientDrift {
  0%, 100% { opacity: 0.06; transform: translate(0, 0); }
  25% { opacity: 0.12; transform: translate(12px, -8px); }
  50% { opacity: 0.04; transform: translate(-6px, 6px); }
  75% { opacity: 0.10; transform: translate(8px, 12px); }
}

/* — Disc interaction — */
.funnel-hero-disc { cursor: pointer; transition: opacity .25s ease; }
.funnel-hero-disc.dimmed { opacity: 0.48; }
.funnel-hero-disc.selected { opacity: 1; }
.funnel-hero-disc:hover {
  filter: brightness(1.08);
}

/* — Insight panel (right side) — */
.funnel-insight-panel {
  flex: 1;
  min-width: 200px;
  max-width: 320px;
  border-left: 1px solid var(--c-border);
  padding: 8px 0 8px 20px;
  display: flex;
  align-items: stretch;
}
.insight-panel-inner {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
  animation: insightFadeIn .2s ease;
}
@keyframes insightFadeIn {
  from { opacity: 0; transform: translateX(6px); }
  to { opacity: 1; transform: translateX(0); }
}
.insight-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
.insight-dot {
  width: 10px; height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}
.insight-label {
  font-size: 16px;
  font-weight: 700;
  color: var(--c-text);
}
.badge-bottleneck {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 10px;
  background: var(--c-reject);
  color: #fff;
  line-height: 16px;
}
.health-badge {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 10px;
  line-height: 16px;
}
.health-good { background: rgba(34,197,94,0.12); color: var(--c-done); }
.health-watch { background: rgba(245,158,11,0.12); color: var(--c-warn); }
.health-risk { background: rgba(239,68,68,0.12); color: var(--c-reject); }

.insight-metrics {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
}
.insight-metric { flex: 1; min-width: 70px; }
.insight-metric-val {
  font-size: 18px;
  font-weight: 800;
  color: var(--c-text);
  font-variant-numeric: tabular-nums;
  display: flex;
  align-items: center;
  gap: 4px;
}
.insight-metric-unit { font-size: 12px; font-weight: 400; color: var(--c-sub); }
.insight-metric-sub { font-size: 11px; color: var(--c-sub); margin-top: 2px; }
.wow-delta { font-size: 11px; font-weight: 600; display: inline-flex; align-items: center; gap: 1px; }
.wow-delta.up { color: var(--c-done); }
.wow-delta.down { color: var(--c-reject); }

.insight-spark-wrap { display: flex; align-items: center; gap: 8px; }
.insight-spark { width: 120px; height: 28px; flex-shrink: 0; }
.insight-spark-label { font-size: 10px; color: var(--c-sub); }
.insight-note {
  font-size: 12px;
  color: var(--c-body);
  line-height: 1.6;
  padding: 8px 10px;
  background: var(--c-bg);
  border-radius: 6px;
  border: 1px solid var(--c-border-light);
}
.insight-owner { font-size: 12px; color: var(--c-sub); display: flex; align-items: center; gap: 6px; }
.insight-cta {
  width: 100%;
  padding: 8px 0;
  border: 1px solid var(--c-primary);
  border-radius: 6px;
  background: transparent;
  color: var(--c-primary);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background .15s, color .15s;
  font-family: inherit;
}
.insight-cta:hover { background: var(--c-primary); color: #fff; }

/* — Bottom stepper chips — */
.funnel-stepper {
  display: flex;
  gap: 4px;
  justify-content: center;
  padding: 16px 0 4px;
  flex-wrap: wrap;
  border-top: 1px solid var(--c-border-light);
  margin-top: 10px;
  position: relative;
  z-index: 2;
}
.viz-funnel-step.funnel-step-chip {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 5px 12px;
  border-radius: 16px;
  border: 1px solid var(--c-border);
  background: var(--c-card);
  cursor: pointer;
  font-size: 12px;
  font-weight: 500;
  color: var(--c-body);
  transition: all .18s ease;
  white-space: nowrap;
}
.viz-funnel-step.funnel-step-chip:hover,
.viz-funnel-step.funnel-step-chip.active {
  border-color: var(--c-primary);
  color: var(--c-primary);
  background: rgba(79,110,247,0.06);
}
.viz-funnel-step.funnel-step-chip.active { font-weight: 700; }
.step-chip-dot { width: 7px; height: 7px; border-radius: 50%; flex-shrink: 0; }
.step-chip-count { font-weight: 700; font-variant-numeric: tabular-nums; color: var(--c-text); }

/* — Reduced motion — */
@media (prefers-reduced-motion: reduce) {
  .dashboard-kpi-card { transition: none !important; transform: none !important; }
  .funnel-hero-disc { transition: none !important; }
  .funnel-helix-core.helix-flow,
  .funnel-helix-glow.helix-flow { animation: none !important; }
  .funnel-ambient-dot { animation: none !important; opacity: 0.05 !important; }
  .funnel-svg-ambient { animation: none !important; }
  .insight-panel-inner { animation: none !important; }
  .funnel-step-chip { transition: none !important; }
}

/* — Mobile: stack vertically — */
@media (max-width: 720px) {
  .funnel-hero-card { min-height: auto; }
  .funnel-viz-row { flex-direction: column; align-items: center; }
  .funnel-viz-area { flex: 0 0 auto; max-width: 300px; }
  .funnel-viz-svg { height: 380px; }
  .funnel-insight-panel { max-width: none; border-left: none; border-top: 1px solid var(--c-border); padding: 14px 0 0; }
  .funnel-stepper { gap: 6px; }
  .viz-funnel-step.funnel-step-chip { padding: 4px 10px; font-size: 11px; }
}
</style>