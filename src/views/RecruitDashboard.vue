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
      <!-- Immersive data-screen background -->
      <div class="funnel-hero-bg" aria-hidden="true">
        <div class="funnel-bg-grid"></div>
        <div class="funnel-bg-radial" v-for="r in 3" :key="'r'+r"
          :style="'--r-size:'+(40+r*28)+'px;--r-x:'+(20+r*25)+'%;--r-y:'+(30+r*15)+'%'"></div>
        <div v-if="motionOK" class="funnel-bg-scanline"></div>
        <div v-if="motionOK" v-for="p in ambientParticles" :key="'ap'+p.id" class="funnel-ambient-dot"
          :style="p.style"></div>
        <!-- Floating tech 'nodes' -->
        <div class="funnel-bg-node" v-for="n in techNodes" :key="'tn'+n.id" :style="n.style"></div>
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
            <svg viewBox="0 0 320 520" class="funnel-viz-svg">
              <defs>
                <filter id="funnelGlow"><feGaussianBlur stdDeviation="2" result="blur"/><feMerge><feMergeNode in="blur"/><feMergeNode in="SourceGraphic"/></feMerge></filter>
              </defs>
              <!-- Decorative concentric rings -->
              <ellipse cx="160" cy="260" rx="180" ry="50" fill="none" stroke="var(--c-primary)" stroke-width="0.5" opacity="0.04"/>
              <ellipse cx="160" cy="260" rx="150" ry="42" fill="none" stroke="var(--c-primary)" stroke-width="0.5" opacity="0.06"/>
              <ellipse cx="160" cy="260" rx="120" ry="34" fill="none" stroke="var(--c-primary)" stroke-width="0.5" opacity="0.08"/>
              <!-- Double helix strands -->
              <g v-for="(hPath, hi) in helixPaths" :key="'h'+hi">
                <path :id="'funnelHelix'+(hi+1)" :d="hPath" fill="none" class="funnel-helix-glow"
                  :class="{ 'helix-flow': motionOK }" />
                <path :d="hPath" fill="none" class="funnel-helix-core"
                  :class="{ 'helix-flow': motionOK }" stroke-dasharray="16 10" />
              </g>
              <!-- Animated particles on helix -->
              <template v-if="motionOK">
                <circle v-for="p in 16" :key="'pp'+p" r="2.5" fill="var(--c-primary)" opacity="0.6">
                  <animateMotion :dur="(4 + p * 0.5) + 's'" repeatCount="indefinite" rotate="auto">
                    <mpath :href="'#funnelHelix' + ((p % 2) + 1)" />
                  </animateMotion>
                </circle>
              </template>
              <!-- Static particles -->
              <template v-else>
                <circle v-for="(sp, spi) in staticParticles" :key="'sp'+spi"
                  :cx="sp.x" :cy="sp.y" r="2" fill="var(--c-primary)" opacity="0.35" />
              </template>
              <!-- SVG ambient dots -->
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
                <ellipse :cx="d.cx" :cy="d.cy + d.ry + d.wallH + 6" :rx="d.rx" :ry="d.rx * 0.08"
                  fill="rgba(23,32,51,0.07)" />
                <path :d="d.wallPath" :fill="d.accent" :opacity="d.wallOpacity" />
                <ellipse :cx="d.cx" :cy="d.cy" :rx="d.rx" :ry="d.ry"
                  :fill="d.accent" :opacity="d.faceOpacity" />
                <ellipse v-if="selected === i" :cx="d.cx" :cy="d.cy"
                  :rx="d.rx + 4" :ry="d.ry + 2"
                  fill="none" stroke="var(--c-primary)" stroke-width="1.5" opacity="0.5"
                  filter="url(#funnelGlow)" />
                <text :x="d.cx" :y="d.cy - 5" text-anchor="middle"
                  fill="#fff" font-size="15" font-weight="800"
                  style="font-variant-numeric:tabular-nums;text-shadow:0 1px 3px rgba(0,0,0,.35)"
                  pointer-events="none">{{ d.count }}</text>
                <text :x="d.cx" :y="d.cy + 15" text-anchor="middle"
                  fill="#fff" font-size="11" font-weight="600"
                  style="text-shadow:0 1px 2px rgba(0,0,0,.3)"
                  pointer-events="none">{{ d.label }}</text>
              </g>
            </svg>
          </div>
          <!-- Insight panel (right) -->
          <div class="funnel-insight-panel" v-if="sel" :key="selected">
            <div class="insight-panel-inner">
              <!-- Header: label + badges -->
              <div class="insight-header">
                <span class="insight-dot" :style="{ background: selAccent }"></span>
                <span class="insight-label-main">{{ sel.label }}</span>
                <span v-if="sel.bottleneck" class="badge-bottleneck">瓶颈</span>
                <span v-else class="health-badge" :class="'health-'+sel.health">{{ healthLabel(sel.health) }}</span>
              </div>
              <!-- Core metrics: big numbers -->
              <div class="insight-metrics-row">
                <div class="insight-metric-card">
                  <div class="im-val">{{ sel.count }}<span class="im-unit">人</span></div>
                  <div class="im-sub">在{{ sel.label }}阶段</div>
                </div>
                <div class="insight-metric-card" v-if="sel.conv">
                  <div class="im-val">{{ sel.conv }}</div>
                  <div class="im-sub">入口转化率</div>
                </div>
                <div class="insight-metric-card">
                  <div class="im-val">{{ sel.pct }}</div>
                  <div class="im-sub">占总简历</div>
                </div>
              </div>
              <!-- WoW + Dwell row -->
              <div class="insight-detail-row">
                <div class="insight-detail-item">
                  <span class="detail-label">环比</span>
                  <span class="wow-delta" :class="sel.wowUp ? 'up' : 'down'">
                    <svg viewBox="0 0 10 10" style="width:10px;height:10px">
                      <polyline v-if="sel.wowUp" points="1,8 5,2 9,8" fill="none" stroke="currentColor" stroke-width="1.5"/>
                      <polyline v-else points="1,2 5,8 9,2" fill="none" stroke="currentColor" stroke-width="1.5"/>
                    </svg>
                    {{ sel.wow }}
                    <span class="wow-abs">{{ sel.wowUp ? '▲' : '▼' }}{{ sel.spark ? ((Math.round((sel.spark[sel.spark.length-1] - sel.spark[0]) / sel.spark[0] * 100) || 0) + '%') : '' }}</span>
                  </span>
                </div>
                <div class="insight-detail-item">
                  <span class="detail-label">平均停留</span>
                  <span class="detail-val">{{ sel.dwell }}</span>
                </div>
                <div class="insight-detail-item">
                  <span class="detail-label">负责人</span>
                  <span class="detail-val detail-owner">{{ sel.owner }}</span>
                </div>
              </div>
              <!-- Sparkline + chain conversion -->
              <div class="insight-chart-block">
                <div class="block-label">近 7 天趋势</div>
                <svg viewBox="0 0 180 48" class="insight-spark-lg">
                  <polyline :points="sparkPath(sel.spark, 180, 44, 4)" fill="none" :stroke="selAccent" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
                  <polygon :points="sparkFillPath(sel.spark, 180, 44, 4)" :fill="selAccent" opacity="0.08"/>
                  <text v-if="sel.spark" :x="4" :y="12" fill="var(--c-sub)" font-size="8">{{ Math.max(...sel.spark) }}</text>
                  <text v-if="sel.spark" :x="4" :y="43" fill="var(--c-sub)" font-size="8">{{ Math.min(...sel.spark) }}</text>
                </svg>
              </div>
              <!-- Conversion chain: all stages compact -->
              <div class="insight-chain">
                <div class="block-label">全链路转化</div>
                <div class="chain-bars">
                  <div v-for="(st, ci) in FUNNEL_STEPS" :key="ci" class="chain-step"
                    :class="{ chainActive: ci === selected }"
                    @click="selectStage(ci)">
                    <div class="chain-bar-wrap">
                      <div class="chain-bar" :style="'height:' + chainHeight(ci) + '%;--chain-accent:' + accentColor(st)"></div>
                    </div>
                    <span class="chain-label">{{ st.label }}</span>
                    <span class="chain-val">{{ st.count }}</span>
                  </div>
                </div>
              </div>
              <!-- Insight note -->
              <div class="insight-note-block">
                <svg viewBox="0 0 24 24" style="width:14px;height:14px;flex-shrink:0;fill:none;stroke:var(--c-primary);stroke-width:2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
                {{ sel.note }}
              </div>
              <!-- CTA -->
              <button class="insight-cta" @click="router.push(sel.link)">查看 {{ sel.label }} 详情 →</button>
            </div>
          </div>
        </div>
      </div>
      <!-- Bottom stepper -->
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
  for (let i = 0; i < 40; i++) {
    dots.push({
      id: i,
      style: {
        left: (Math.random() * 100) + '%',
        top: (Math.random() * 100) + '%',
        width: (Math.random() * 5 + 2) + 'px',
        height: (Math.random() * 5 + 2) + 'px',
        '--ad-dur': (10 + Math.random() * 25) + 's',
        '--ad-delay': (Math.random() * 15) + 's',
      }
    });
  }
  return dots;
});

// -- Floating tech nodes in background --
const techNodes = computed(() => {
  const nodes = [];
  const labels = ['HR', 'JD', 'OKR', 'HC', 'KPI', 'ATS', 'CRM'];
  for (let i = 0; i < 12; i++) {
    nodes.push({
      id: i,
      style: {
        left: (5 + Math.random() * 90) + '%',
        top: (5 + Math.random() * 90) + '%',
        fontSize: (8 + Math.random() * 4) + 'px',
        opacity: (0.03 + Math.random() * 0.05),
        '--nd-dur': (15 + Math.random() * 30) + 's',
        '--nd-delay': (Math.random() * 10) + 's',
        content: '"' + (labels[i % labels.length]) + '"',
      }
    });
  }
  return nodes;
});

// -- Chain bar height for all-stages chart --
function chainHeight(i) {
  const maxCount = Math.max(...FUNNEL_STEPS.map(s => s.count));
  return Math.round((FUNNEL_STEPS[i].count / maxCount) * 100);
}

// -- Sparkline helpers --
function sparkPath(spark, w, h, pad) {
  w = w || 140; h = h || 28; pad = pad || 2;
  if (!spark || !spark.length) return pad + ',' + (h/2) + ' ' + (w-pad) + ',' + (h/2);
  const maxV = Math.max(...spark);
  const minV = Math.min(...spark);
  const range = maxV - minV || 1;
  return spark.map((v, i) => {
    const x = pad + (i / (spark.length - 1)) * (w - pad * 2);
    const y = pad + (1 - (v - minV) / range) * (h - pad * 2);
    return `${parseFloat(x.toFixed(1))},${parseFloat(y.toFixed(1))}`;
  }).join(' ');
}

function sparkFillPath(spark, w, h, pad) {
  w = w || 140; h = h || 28; pad = pad || 2;
  if (!spark || !spark.length) return pad + ',' + h + ' ' + (w-pad) + ',' + h + ' ' + (w-pad) + ',' + (h+2) + ' ' + pad + ',' + (h+2);
  const pts = sparkPath(spark, w, h, pad).split(' ').map(p => p.split(',').map(Number));
  const last = pts[pts.length - 1];
  const first = pts[0];
  return `${first[0]},${h} ${pts.map(p => `${p[0]},${p[1]}`).join(' ')} ${last[0]},${h}`;
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

/* ===== Funnel Hero Card — 千亿级数据大屏 ===== */
.funnel-hero-card {
  position: relative;
  overflow: hidden;
  min-height: 700px;
  background: linear-gradient(165deg, var(--c-surface, #fff) 0%, #F0F4FF 100%);
  border: 1px solid rgba(79,110,247,0.08);
}

/* — Immersive animated background — */
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
    linear-gradient(rgba(79,110,247,0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(79,110,247,0.04) 1px, transparent 1px);
  background-size: 36px 36px;
}
.funnel-bg-radial {
  position: absolute;
  width: var(--r-size);
  height: var(--r-size);
  left: var(--r-x);
  top: var(--r-y);
  border-radius: 50%;
  background: radial-gradient(circle, rgba(79,110,247,0.06) 0%, transparent 70%);
  transform: translate(-50%, -50%);
}
.funnel-bg-scanline {
  position: absolute;
  inset: 0;
  background: repeating-linear-gradient(
    0deg,
    transparent,
    transparent 2px,
    rgba(79,110,247,0.015) 2px,
    rgba(79,110,247,0.015) 4px
  );
  animation: scanlineMove 8s linear infinite;
}
@keyframes scanlineMove {
  from { transform: translateY(0); }
  to { transform: translateY(4px); }
}
.funnel-ambient-dot {
  position: absolute;
  border-radius: 50%;
  background: var(--c-primary);
  opacity: 0.05;
  animation: ambientDrift var(--ad-dur) ease-in-out infinite;
  animation-delay: var(--ad-delay);
}
@keyframes ambientDrift {
  0%, 100% { transform: translate(0, 0) scale(1); opacity: 0.03; }
  25% { transform: translate(25px, -18px) scale(1.3); opacity: 0.08; }
  50% { transform: translate(-12px, 12px) scale(0.8); opacity: 0.04; }
  75% { transform: translate(18px, 22px) scale(1.15); opacity: 0.06; }
}
.funnel-bg-node {
  position: absolute;
  color: var(--c-primary);
  font-weight: 800;
  letter-spacing: 2px;
  animation: nodeDrift var(--nd-dur) ease-in-out infinite;
  animation-delay: var(--nd-delay);
  content: attr(data-text);
}
.funnel-bg-node::before {
  position: absolute;
  content: '';
  width: 3px; height: 3px;
  background: var(--c-primary);
  border-radius: 50%;
  top: -4px; left: 50%;
  transform: translateX(-50%);
}
@keyframes nodeDrift {
  0%, 100% { transform: translate(0, 0) rotate(0deg); }
  33% { transform: translate(10px, -8px) rotate(2deg); }
  66% { transform: translate(-5px, 6px) rotate(-1deg); }
}

.funnel-hero-title { position: relative; z-index: 2; }

.funnel-hero-body {
  position: relative;
  z-index: 1;
}
.funnel-viz-row {
  display: flex;
  gap: 24px;
  align-items: flex-start;
  justify-content: center;
  padding: 8px 0 0;
}

/* — SVG viz area (taller, wider) — */
.funnel-viz-area {
  flex: 0 0 320px;
  min-width: 260px;
  max-width: 340px;
}
.funnel-viz-svg {
  width: 100%;
  height: 520px;
  display: block;
  filter: drop-shadow(0 8px 32px rgba(79,110,247,0.08));
}

/* — Double helix — */
.funnel-helix-glow {
  stroke: var(--c-primary);
  stroke-width: 1.2;
  opacity: 0.08;
  filter: blur(2px);
}
.funnel-helix-core {
  stroke: var(--c-primary);
  stroke-width: 1.5;
  opacity: 0.18;
  stroke-linecap: round;
}
.funnel-helix-core.helix-flow,
.funnel-helix-glow.helix-flow {
  animation: helixOrbit 4.0s linear infinite;
}
@keyframes helixOrbit {
  from { stroke-dashoffset: 0; }
  to { stroke-dashoffset: -52; }
}

/* — SVG ambient dots — */
.funnel-svg-ambient {
  animation: svgAmbientDrift var(--sa-dur) ease-in-out infinite;
  animation-delay: var(--sa-delay);
}
@keyframes svgAmbientDrift {
  0%, 100% { opacity: 0.05; transform: translate(0, 0); }
  25% { opacity: 0.12; transform: translate(14px, -10px); }
  50% { opacity: 0.03; transform: translate(-8px, 8px); }
  75% { opacity: 0.09; transform: translate(10px, 14px); }
}

/* — Disc interaction — */
.funnel-hero-disc { cursor: pointer; transition: opacity .3s ease; }
.funnel-hero-disc.dimmed { opacity: 0.40; }
.funnel-hero-disc.selected { opacity: 1; }
.funnel-hero-disc:hover { filter: brightness(1.10); }

/* ===== Insight Panel — Bigger, richer ===== */
.funnel-insight-panel {
  flex: 1;
  min-width: 260px;
  max-width: 420px;
  border-left: 1px solid rgba(79,110,247,0.1);
  padding: 12px 0 12px 24px;
  display: flex;
  align-items: stretch;
}
.insight-panel-inner {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 14px;
  animation: insightFadeIn .25s ease;
}
@keyframes insightFadeIn {
  from { opacity: 0; transform: translateX(10px); }
  to { opacity: 1; transform: translateX(0); }
}

/* Header */
.insight-header { display: flex; align-items: center; gap: 10px; }
.insight-dot { width: 12px; height: 12px; border-radius: 50%; flex-shrink: 0; }
.insight-label-main {
  font-size: 22px;
  font-weight: 800;
  color: var(--c-text);
  letter-spacing: 0.5px;
}
.badge-bottleneck {
  font-size: 11px;
  font-weight: 700;
  padding: 3px 10px;
  border-radius: 12px;
  background: var(--c-reject);
  color: #fff;
}
.health-badge {
  font-size: 11px;
  font-weight: 700;
  padding: 3px 10px;
  border-radius: 12px;
}
.health-good { background: rgba(34,197,94,0.15); color: var(--c-done); }
.health-watch { background: rgba(245,158,11,0.15); color: var(--c-warn); }
.health-risk { background: rgba(239,68,68,0.15); color: var(--c-reject); }

/* — Metric cards row (3 big numbers) — */
.insight-metrics-row {
  display: flex;
  gap: 10px;
}
.insight-metric-card {
  flex: 1;
  background: var(--c-bg);
  border: 1px solid var(--c-border-light);
  border-radius: 8px;
  padding: 10px 12px;
}
.im-val {
  font-size: 22px;
  font-weight: 800;
  color: var(--c-text);
  font-variant-numeric: tabular-nums;
  display: flex;
  align-items: baseline;
  gap: 2px;
}
.im-unit { font-size: 13px; font-weight: 400; color: var(--c-sub); margin-left: 2px; }
.im-sub { font-size: 11px; color: var(--c-sub); margin-top: 3px; }

/* — WoW + dwell row — */
.insight-detail-row {
  display: flex;
  gap: 16px;
  padding: 6px 0;
  border-bottom: 1px solid var(--c-border-light);
}
.insight-detail-item {
  flex: 1;
}
.detail-label {
  display: block;
  font-size: 10px;
  color: var(--c-sub);
  margin-bottom: 2px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.detail-val {
  font-size: 15px;
  font-weight: 700;
  color: var(--c-text);
  font-variant-numeric: tabular-nums;
}
.detail-owner { font-size: 13px; font-weight: 500; color: var(--c-body); }
.wow-delta {
  font-size: 14px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.wow-delta.up { color: var(--c-done); }
.wow-delta.down { color: var(--c-reject); }
.wow-abs {
  font-size: 10px;
  font-weight: 600;
  opacity: 0.6;
}

/* — Sparkline block — */
.insight-chart-block {
  background: var(--c-bg);
  border-radius: 8px;
  padding: 10px 12px;
  border: 1px solid var(--c-border-light);
}
.block-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--c-sub);
  margin-bottom: 6px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.insight-spark-lg {
  width: 100%;
  height: 48px;
  display: block;
}

/* — Conversion chain bars — */
.insight-chain {
  background: var(--c-bg);
  border-radius: 8px;
  padding: 10px 12px;
  border: 1px solid var(--c-border-light);
}
.chain-bars {
  display: flex;
  gap: 6px;
  align-items: flex-end;
  height: 70px;
  padding-top: 4px;
}
.chain-step {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  cursor: pointer;
  transition: opacity .2s;
}
.chain-step:hover { opacity: 0.8; }
.chain-step.chainActive { opacity: 1; }
.chain-bar-wrap {
  flex: 1;
  width: 100%;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}
.chain-bar {
  width: 60%;
  border-radius: 3px 3px 0 0;
  background: var(--chain-accent, var(--c-primary));
  opacity: 0.6;
  min-height: 4px;
  transition: opacity .2s;
}
.chain-step.chainActive .chain-bar {
  opacity: 1;
  box-shadow: 0 0 8px rgba(79,110,247,0.15);
}
.chain-label {
  font-size: 9px;
  color: var(--c-sub);
  white-space: nowrap;
}
.chain-val {
  font-size: 11px;
  font-weight: 700;
  color: var(--c-text);
  font-variant-numeric: tabular-nums;
}

/* — Insight note — */
.insight-note-block {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 13px;
  color: var(--c-body);
  line-height: 1.6;
  padding: 10px 12px;
  background: rgba(79,110,247,0.04);
  border-radius: 8px;
  border-left: 3px solid var(--c-primary);
}

/* — CTA — */
.insight-cta {
  width: 100%;
  padding: 10px 0;
  border: 1px solid var(--c-primary);
  border-radius: 8px;
  background: transparent;
  color: var(--c-primary);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all .2s;
  font-family: inherit;
}
.insight-cta:hover {
  background: var(--c-primary);
  color: #fff;
  box-shadow: 0 4px 16px rgba(79,110,247,0.2);
}

/* — Bottom stepper chips — */
.funnel-stepper {
  display: flex;
  gap: 6px;
  justify-content: center;
  padding: 16px 0 4px;
  flex-wrap: wrap;
  border-top: 1px solid var(--c-border-light);
  margin-top: 12px;
  position: relative;
  z-index: 2;
}
.viz-funnel-step.funnel-step-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: 18px;
  border: 1px solid var(--c-border);
  background: var(--c-card);
  cursor: pointer;
  font-size: 12px;
  font-weight: 500;
  color: var(--c-body);
  transition: all .2s ease;
  white-space: nowrap;
}
.viz-funnel-step.funnel-step-chip:hover,
.viz-funnel-step.funnel-step-chip.active {
  border-color: var(--c-primary);
  color: var(--c-primary);
  background: rgba(79,110,247,0.06);
}
.viz-funnel-step.funnel-step-chip.active { font-weight: 700; }
.step-chip-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.step-chip-count { font-weight: 700; font-variant-numeric: tabular-nums; color: var(--c-text); }

/* — Reduced motion — */
@media (prefers-reduced-motion: reduce) {
  .dashboard-kpi-card { transition: none !important; transform: none !important; }
  .funnel-hero-disc { transition: none !important; }
  .funnel-helix-core.helix-flow,
  .funnel-helix-glow.helix-flow { animation: none !important; }
  .funnel-ambient-dot { animation: none !important; opacity: 0.04 !important; }
  .funnel-bg-scanline { animation: none !important; }
  .funnel-svg-ambient { animation: none !important; }
  .funnel-bg-node { animation: none !important; }
  .insight-panel-inner { animation: none !important; }
  .funnel-step-chip { transition: none !important; }
}

/* — Mobile — */
@media (max-width: 780px) {
  .funnel-hero-card { min-height: auto; }
  .funnel-viz-row { flex-direction: column; align-items: center; }
  .funnel-viz-area { flex: 0 0 auto; max-width: 300px; }
  .funnel-viz-svg { height: 400px; }
  .funnel-insight-panel { max-width: none; border-left: none; border-top: 1px solid var(--c-border); padding: 16px 0 0; }
  .insight-metrics-row { flex-wrap: wrap; }
  .insight-metric-card { min-width: 80px; }
  .funnel-stepper { gap: 6px; }
  .viz-funnel-step.funnel-step-chip { padding: 5px 10px; font-size: 11px; }
}
</style>