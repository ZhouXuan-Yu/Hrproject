<template>
  <div ref="cardEl" class="card funnel-hero-card" style="margin-bottom:12px" data-viz-enhanced="funnel">
    <div class="card-title funnel-hero-title">
      <svg viewBox="0 0 24 24" style="width:18px;height:18px"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>
      招聘全漏斗
      <span class="funnel-title-hint" style="font-weight:400;font-size:11px;color:var(--c-sub);margin-left:8px">点击圆盘查看阶段洞察</span>
      <span style="font-weight:400;font-size:11px;color:var(--c-primary);margin-left:auto">总转化率 {{ FUNNEL_STEPS[FUNNEL_STEPS.length - 1].pct }}</span>
    </div>
    <div class="funnel-hero-body">
      <div class="funnel-viz-row">
        <!-- 3D layered-glass cone (Three.js) -->
        <div class="funnel-viz-area">
          <div v-show="webglOK" ref="vizWrap" class="funnel-three-wrap">
            <!-- HUD connector lines (positions driven per-frame) -->
            <svg v-if="webglOK" class="funnel-hud-lines" aria-hidden="true">
              <template v-for="(st, i) in FUNNEL_STEPS" :key="'hl' + i">
                <line :ref="(el) => (lineEls[i] = el)" x1="0" y1="0" x2="0" y2="0"
                  :stroke="stageCss(i)" :class="{ active: selected === i }" />
                <circle :ref="(el) => (dotEls[i] = el)" r="2.5" cx="-10" cy="-10"
                  :fill="stageCss(i)" :class="{ active: selected === i }" />
              </template>
            </svg>
            <!-- HUD stage chips -->
            <div v-for="(st, i) in FUNNEL_STEPS" :key="'hud' + i"
              :ref="(el) => (chipEls[i] = el)"
              class="funnel-hud-chip"
              :class="[hudSide(i), { active: selected === i }]"
              :style="{ '--hud-accent': stageCss(i), '--hud-delay': (4 - i) * 0.12 + 's' }"
              role="button" tabindex="0"
              :aria-label="'选中阶段 ' + st.label"
              @click="selectStage(i)"
              @keydown.enter.space.prevent="selectStage(i)">
              <span class="hud-name">{{ st.label }}</span>
              <span class="hud-count">{{ st.count }}</span>
            </div>
          </div>
          <!-- Static fallback (reduced motion / WebGL unavailable) -->
          <div v-if="!webglOK" class="funnel-fallback">
            <div v-for="(st, i) in FUNNEL_STEPS" :key="'fb' + i" class="fb-row">
              <div class="fb-disc" :class="{ active: selected === i }"
                :style="{ width: fbWidth(i) + '%', background: fbBg(i), borderColor: stageCss(i) }"
                role="button" tabindex="0" :aria-label="'选中阶段 ' + st.label"
                @click="selectStage(i)" @keydown.enter.space.prevent="selectStage(i)">
                <b>{{ st.count }}</b><span>{{ st.label }}</span>
              </div>
            </div>
          </div>
        </div>
        <!-- Insight panel (right) -->
        <div class="funnel-insight-panel" v-if="sel" :key="selected">
          <div class="insight-panel-inner">
            <!-- Header: label + badges -->
            <div class="insight-header">
              <span class="insight-dot" :style="{ background: selAccent }"></span>
              <span class="insight-label-main">{{ sel.label }}</span>
              <span v-if="sel.bottleneck" class="badge-bottleneck">瓶颈</span>
              <span v-else class="health-badge" :class="'health-' + sel.health">{{ healthLabel(sel.health) }}</span>
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
                  <span class="wow-abs">{{ wowArrow(sel.wowUp) }}{{ sel.spark ? ((Math.round((sel.spark[sel.spark.length - 1] - sel.spark[0]) / sel.spark[0] * 100) || 0) + '%') : '' }}</span>
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
            <!-- Sparkline -->
            <div class="insight-chart-block">
              <div class="block-label">近 7 天趋势</div>
              <svg viewBox="0 0 180 48" class="insight-spark-lg">
                <polyline :points="sparkPath(sel.spark, 180, 44, 4)" fill="none" :stroke="selAccent" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
                <polygon :points="sparkFillPath(sel.spark, 180, 44, 4)" :fill="selAccent" opacity="0.08"/>
                <text v-if="sel.spark" :x="4" :y="12" fill="var(--c-sub)" font-size="8">{{ Math.max(...sel.spark) }}</text>
                <text v-if="sel.spark" :x="4" :y="43" fill="var(--c-sub)" font-size="8">{{ Math.min(...sel.spark) }}</text>
              </svg>
            </div>
            <!-- Conversion chain -->
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
      <div v-for="(step, i) in FUNNEL_STEPS" :key="'step' + i"
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
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import * as THREE from 'three';
import { FUNNEL_STEPS } from '../data/dashboard.js';

const router = useRouter();

// ---------- state ----------
const selected = ref(3); // default: bottleneck stage (Offer)
const webglOK = ref(true);
const cardEl = ref(null);
const vizWrap = ref(null);
const chipEls = ref([]);
const lineEls = ref([]);
const dotEls = ref([]);

const sel = computed(() => FUNNEL_STEPS[selected.value] || FUNNEL_STEPS[0]);
const selAccent = computed(() => accentColor(sel.value));

function accentColor(step) { return step.color || 'var(--c-primary)'; }
function selectStage(i) { selected.value = i; }

// Semantic stage palette (hex for WebGL, css strings for HUD)
const STAGE_HEX = FUNNEL_STEPS.map((s, i) => {
  if (i === FUNNEL_STEPS.length - 1) return 0x22c55e; // 入职 green
  if (s.bottleneck || s.health === 'risk') return 0xef4444;
  if (s.health === 'watch') return 0xf59e0b;
  return 0x4f6ef7;
});
const STAGE_CSS = STAGE_HEX.map((h) => '#' + h.toString(16).padStart(6, '0'));
function stageCss(i) { return STAGE_CSS[i]; }
function hudSide(i) { return i % 2 === 0 ? 'left' : 'right'; }

// ---------- fallback helpers ----------
const FB_WIDTHS = [100, 82, 64, 48, 34];
function fbWidth(i) { return FB_WIDTHS[i]; }
function fbBg(i) {
  const hex = STAGE_HEX[i];
  const r = (hex >> 16) & 255, g = (hex >> 8) & 255, b = hex & 255;
  return `rgba(${r},${g},${b},0.16)`;
}

// ---------- insight helpers (kept from legacy dashboard) ----------
function chainHeight(i) {
  const maxCount = Math.max(...FUNNEL_STEPS.map((s) => s.count));
  return Math.round((FUNNEL_STEPS[i].count / maxCount) * 100);
}
function sparkPath(spark, w, h, pad) {
  w = w || 140; h = h || 28; pad = pad || 2;
  if (!spark || !spark.length) return pad + ',' + (h / 2) + ' ' + (w - pad) + ',' + (h / 2);
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
  if (!spark || !spark.length) return pad + ',' + h + ' ' + (w - pad) + ',' + h + ' ' + (w - pad) + ',' + (h + 2) + ' ' + pad + ',' + (h + 2);
  const pts = sparkPath(spark, w, h, pad).split(' ').map((p) => p.split(',').map(Number));
  const last = pts[pts.length - 1];
  const first = pts[0];
  return `${first[0]},${h} ${pts.map((p) => `${p[0]},${p[1]}`).join(' ')} ${last[0]},${h}`;
}
function healthLabel(h) {
  const map = { good: '健康', watch: '关注', risk: '风险' };
  return map[h] || h;
}
function wowArrow(up) { return up ? '▲' : '▼'; }

// ---------- Three.js scene ----------
let renderer = null;
let scene = null;
let camera = null;
let coneGroup = null;
let raycaster = null;
let pointerNDC = null;
let discs = []; // { mesh, mat, ringMat, glowMat, baseY, r, lift, reveal }
let coneFillers = []; // { mesh, mat, reveal }
let helixStrands = []; // { geo, phase }
let orbitLights = [];
let rafId = 0;
let revealStart = -1;
let io = null;
let ro = null;
let scrollP = 0;
let ptrX = 0;
let ptrY = 0;
let destroyed = false;

const DISC_RADII = [1.6, 1.32, 1.04, 0.78, 0.54];
const DISC_Y = [1.55, 0.78, 0.0, -0.78, -1.55];
const HELIX_N = 150;
const HELIX_TURNS = 3;

function easeOutCubic(t) { return 1 - Math.pow(1 - t, 3); }

function circlePoints(r, y) {
  const pts = [];
  for (let i = 0; i < 64; i++) {
    const a = (i / 64) * Math.PI * 2;
    pts.push(new THREE.Vector3(Math.cos(a) * r, y, Math.sin(a) * r));
  }
  return pts;
}

function initThree() {
  const wrap = vizWrap.value;
  const w = wrap.clientWidth || 600;
  const h = wrap.clientHeight || 520;

  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  renderer.setSize(w, h, false); // CSS keeps canvas at 100% of the wrap
  renderer.setClearColor(0xf4f7fc, 0);
  renderer.domElement.classList.add('funnel-three-canvas');
  wrap.appendChild(renderer.domElement);

  scene = new THREE.Scene();
  scene.fog = new THREE.Fog(0xf4f7fc, 10, 18);
  camera = new THREE.PerspectiveCamera(38, w / h, 0.1, 50);
  camera.position.set(0, 1.4, 8.2);
  camera.lookAt(0, -0.05, 0);

  // Lighting: ambient + key (project blue) + rim (cyan) orbiting
  scene.add(new THREE.AmbientLight(0x8fa3d9, 0.55));
  const keyLight = new THREE.PointLight(0x4f6ef7, 42, 0, 1.5);
  const rimLight = new THREE.PointLight(0x22d3ee, 28, 0, 1.5);
  const fillLight = new THREE.DirectionalLight(0xdce6ff, 0.55);
  fillLight.position.set(2, 6, 4);
  scene.add(keyLight, rimLight, fillLight);
  orbitLights = [keyLight, rimLight];

  coneGroup = new THREE.Group();
  scene.add(coneGroup);

  // Frustum side fillers between rings (rendered first, behind rings)
  const coneFillers = [];
  for (let i = 0; i < DISC_RADII.length - 1; i++) {
    const r = DISC_RADII[i];
    const nextR = DISC_RADII[i + 1];
    const tube = 0.085 - i * 0.007;
    const nextTube = 0.085 - (i + 1) * 0.007;
    const topY = DISC_Y[i] - 1.4;
    const bottomY = DISC_Y[i + 1] - 1.4;
    const height = Math.abs(bottomY - topY);
    const topR = (r + tube) * 0.55;
    const bottomR = (nextR + nextTube) * 0.55;
    const fillGeo = new THREE.CylinderGeometry(topR, bottomR, height, 64, 1, true);
    const fillMat = new THREE.MeshPhysicalMaterial({
      color: STAGE_HEX[i],
      transparent: true,
      opacity: 0.0,
      roughness: 0.22,
      metalness: 0.05,
      clearcoat: 0.6,
      clearcoatRoughness: 0.2,
      transmission: 0.35,
      thickness: 0.2,
      side: THREE.DoubleSide,
      depthWrite: false,
    });
    const filler = new THREE.Mesh(fillGeo, fillMat);
    filler.position.y = (topY + bottomY) / 2;
    filler.userData.stage = i;
    coneGroup.add(filler);
    coneFillers.push({ mesh: filler, mat: fillMat, reveal: 0 });
  }

  // 5 advanced glass rings, stacked top → bottom
  DISC_RADII.forEach((r, i) => {
    const tube = 0.085 - i * 0.007;
    const geo = new THREE.TorusGeometry(r, tube, 18, 120);
    const mat = new THREE.MeshPhysicalMaterial({
      color: STAGE_HEX[i],
      transparent: true,
      opacity: 0.0,
      roughness: 0.08,
      metalness: 0.12,
      clearcoat: 1,
      clearcoatRoughness: 0.12,
      transmission: 0.55,
      thickness: 0.4,
      ior: 1.55,
      emissive: STAGE_HEX[i],
      emissiveIntensity: 0.18,
      depthWrite: false,
    });
    const mesh = new THREE.Mesh(geo, mat);
    mesh.position.y = DISC_Y[i] - 1.4;
    mesh.rotation.x = Math.PI / 2;
    mesh.scale.setScalar(0.55);
    mesh.userData.stage = i;

    // selection ring (kept hidden)
    const ringGeo = new THREE.TorusGeometry(r + tube * 2.2, 0.025, 12, 96);
    const ringMat = new THREE.MeshBasicMaterial({ color: STAGE_HEX[i], transparent: true, opacity: 0, blending: THREE.AdditiveBlending, depthWrite: false });
    const ring = new THREE.Mesh(ringGeo, ringMat);
    ring.rotation.x = Math.PI / 2;
    ring.userData.stage = i;
    mesh.add(ring);

    coneGroup.add(mesh);
    discs.push({ mesh, mat, ringMat, glowMat: null, baseY: DISC_Y[i], r, lift: 0, reveal: 0 });
  });

  // Double-helix particle streams (phase offset π) — more visible
  for (let s = 0; s < 2; s++) {
    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.BufferAttribute(new Float32Array(HELIX_N * 3), 3));
    const mat = new THREE.PointsMaterial({
      color: s === 0 ? 0x315efb : 0x36bffa,
      size: 0.075,
      transparent: true,
      opacity: 1.0,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
      sizeAttenuation: true,
    });
    coneGroup.add(new THREE.Points(geo, mat));
    helixStrands.push({ geo, phase: s * Math.PI });
  }

  raycaster = new THREE.Raycaster();
  pointerNDC = new THREE.Vector2();

  renderer.domElement.addEventListener('click', onCanvasClick);
  wrap.addEventListener('pointermove', onPointerMove);

  ro = new ResizeObserver(onResize);
  ro.observe(wrap);

  rafId = requestAnimationFrame(tick);
}

function updateHelix(time) {
  helixStrands.forEach((hx) => {
    const arr = hx.geo.attributes.position.array;
    for (let j = 0; j < HELIX_N; j++) {
      const t = (j / HELIX_N + time * 0.045) % 1;
      const ang = t * HELIX_TURNS * Math.PI * 2 + hx.phase;
      const y = 2.1 + t * -4.2; // flows top → bottom
      const r = 1.95 + t * (0.7 - 1.95);
      arr[j * 3] = Math.cos(ang) * r;
      arr[j * 3 + 1] = y;
      arr[j * 3 + 2] = Math.sin(ang) * r;
    }
    hx.geo.attributes.position.needsUpdate = true;
  });
}

function updateDiscs(now) {
  if (revealStart >= 0) {
    const el = now - revealStart;
    discs.forEach((d, i) => {
      const order = discs.length - 1 - i; // bottom → top stagger
      const local = Math.min(1, Math.max(0, (el - order * 140) / 850));
      d.reveal = easeOutCubic(local);
    });
    pillars.forEach((p, i) => {
      const order = pillars.length - 1 - i;
      const local = Math.min(1, Math.max(0, (el - order * 140 - 200) / 850));
      p.reveal = easeOutCubic(local);
    });
  }
  discs.forEach((d, i) => {
    const isSel = i === selected.value;
    d.lift += ((isSel ? 0.24 : 0) - d.lift) * 0.07;
    d.mat.emissiveIntensity += ((isSel ? 0.65 : 0.15) - d.mat.emissiveIntensity) * 0.08;
    d.ringMat.opacity += (0 - d.ringMat.opacity) * 0.1; // selection ring hidden
    d.glowMat.opacity += ((isSel ? 0.75 : 0.28) - d.glowMat.opacity) * 0.08;
    const rv = d.reveal;
    d.mesh.position.y = d.baseY - (1 - rv) * 1.4 + d.lift;
    d.mesh.scale.setScalar(0.55 + 0.45 * rv);
    d.mat.opacity = 0.68 * rv;
  });
  pillars.forEach((p) => {
    p.mat.opacity = 0.45 * p.reveal;
  });
}

const hudVec = new THREE.Vector3();
const hudRight = new THREE.Vector3();
function updateHUD() {
  const wrap = vizWrap.value;
  if (!wrap || !camera) return;
  const w = wrap.clientWidth;
  const h = wrap.clientHeight;
  if (!w || !h) return;
  const e = camera.matrixWorld.elements;
  hudRight.set(e[0], e[1], e[2]).normalize();
  discs.forEach((d, i) => {
    const chip = chipEls.value[i];
    const line = lineEls.value[i];
    const dot = dotEls.value[i];
    if (!chip || !line || !dot) return;
    // disc centre → screen
    hudVec.set(0, d.mesh.position.y, 0).project(camera);
    const cx = (hudVec.x * 0.5 + 0.5) * w;
    const cy = (-hudVec.y * 0.5 + 0.5) * h;
    // disc edge (screen-space, along camera-right) → screen
    const side = i % 2 === 0 ? -1 : 1;
    hudVec.copy(hudRight).multiplyScalar(side * d.r * d.mesh.scale.x);
    hudVec.y += d.mesh.position.y;
    hudVec.project(camera);
    const ex = (hudVec.x * 0.5 + 0.5) * w;
    const ey = (-hudVec.y * 0.5 + 0.5) * h;
    const ax = side < 0 ? 118 : w - 118;
    chip.style.top = cy + 'px';
    line.setAttribute('x1', ax);
    line.setAttribute('y1', cy);
    line.setAttribute('x2', ex);
    line.setAttribute('y2', ey);
    dot.setAttribute('cx', ex);
    dot.setAttribute('cy', ey);
  });
}

function tick(nowMs) {
  if (destroyed) return;
  rafId = requestAnimationFrame(tick);
  const time = nowMs / 1000;

  coneGroup.rotation.y = time * 0.12;

  // orbiting dynamic lights (elliptical paths)
  orbitLights[0].position.set(Math.cos(time * 0.55) * 3.8, 2.3 + Math.sin(time * 0.3) * 0.5, Math.sin(time * 0.55) * 2.6);
  orbitLights[1].position.set(Math.cos(-time * 0.4 + Math.PI) * 3.2, -1.3, Math.sin(-time * 0.4 + Math.PI) * 3.0);

  updateHelix(time);
  updateDiscs(nowMs);

  // scroll-driven depth + pointer parallax (lerped)
  const targetZ = 8.2 - scrollP * 1.6;
  const targetY = 1.0 + scrollP * 1.1 - ptrY * 0.4;
  const targetX = ptrX * 0.7;
  camera.position.z += (targetZ - camera.position.z) * 0.05;
  camera.position.y += (targetY - camera.position.y) * 0.05;
  camera.position.x += (targetX - camera.position.x) * 0.05;
  camera.lookAt(0, -0.05, 0);

  renderer.render(scene, camera);
  updateHUD();
}

// ---------- events ----------
function onCanvasClick(e) {
  const rect = renderer.domElement.getBoundingClientRect();
  pointerNDC.set(((e.clientX - rect.left) / rect.width) * 2 - 1, -((e.clientY - rect.top) / rect.height) * 2 + 1);
  raycaster.setFromCamera(pointerNDC, camera);
  const hits = raycaster.intersectObjects(coneGroup.children, true);
  const hit = hits.find((hh) => hh.object.userData.stage !== undefined);
  if (hit) selectStage(hit.object.userData.stage);
}

function onPointerMove(e) {
  const wrap = vizWrap.value;
  if (!wrap) return;
  const rect = wrap.getBoundingClientRect();
  ptrX = ((e.clientX - rect.left) / rect.width - 0.5) * 2;
  ptrY = ((e.clientY - rect.top) / rect.height - 0.5) * 2;
  // hover cursor feedback
  if (!renderer) return;
  pointerNDC.set(ptrX, -ptrY);
  raycaster.setFromCamera(pointerNDC, camera);
  const hits = raycaster.intersectObjects(coneGroup.children, true);
  const hover = hits.some((hh) => hh.object.userData.stage !== undefined);
  renderer.domElement.style.cursor = hover ? 'pointer' : 'default';
}

function onScroll() {
  if (!cardEl.value) return;
  const rect = cardEl.value.getBoundingClientRect();
  const vh = window.innerHeight || 1;
  scrollP = Math.min(1, Math.max(0, (vh - rect.top) / (vh + rect.height)));
}

function onResize() {
  const wrap = vizWrap.value;
  if (!wrap || !renderer || !camera) return;
  const w = wrap.clientWidth;
  const h = wrap.clientHeight;
  if (!w || !h) return;
  camera.aspect = w / h;
  camera.updateProjectionMatrix();
  renderer.setSize(w, h, false);
  onScroll();
}

function disposeThree() {
  cancelAnimationFrame(rafId);
  if (io) { io.disconnect(); io = null; }
  if (ro) { ro.disconnect(); ro = null; }
  window.removeEventListener('scroll', onScroll);
  window.removeEventListener('resize', onScroll);
  if (vizWrap.value) vizWrap.value.removeEventListener('pointermove', onPointerMove);
  if (renderer) {
    renderer.domElement.removeEventListener('click', onCanvasClick);
    if (scene) {
      scene.traverse((o) => {
        if (o.geometry) o.geometry.dispose();
        if (o.material) {
          (Array.isArray(o.material) ? o.material : [o.material]).forEach((m) => m.dispose());
        }
      });
    }
    renderer.dispose();
    if (renderer.forceContextLoss) renderer.forceContextLoss();
    renderer.domElement.remove();
    renderer = null;
    scene = null;
    camera = null;
  }
}

onMounted(() => {
  const mq = window.matchMedia('(prefers-reduced-motion: reduce)');
  if (mq.matches) {
    webglOK.value = false;
    return;
  }
  try {
    initThree();
  } catch (err) {
    disposeThree();
    webglOK.value = false;
    return;
  }
  // Scroll narrative: passive listeners only, never hijack scrolling
  window.addEventListener('scroll', onScroll, { passive: true });
  window.addEventListener('resize', onScroll, { passive: true });
  onScroll();
  // Layer-by-layer reveal when the card enters the viewport
  io = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting && revealStart < 0) {
        revealStart = performance.now();
        cardEl.value && cardEl.value.classList.add('funnel-revealed');
        io.disconnect();
      }
    });
  }, { threshold: 0.25 });
  io.observe(cardEl.value);
});

onUnmounted(() => {
  destroyed = true;
  disposeThree();
});
</script>

<style scoped>
.funnel-hero-card {
  position: relative;
  background:
    radial-gradient(ellipse at 50% 0%, rgba(73, 104, 255, 0.08), transparent 60%),
    linear-gradient(180deg, #F8FAFF 0%, #F4F7FC 100%);
  border: 1px solid #E1E6EF;
  overflow: hidden;
}

/* 底部网格纹理，透明度约 4% */
.funnel-hero-card::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  opacity: 0.04;
  background-image:
    linear-gradient(to right, #315EFB 1px, transparent 1px),
    linear-gradient(to bottom, #315EFB 1px, transparent 1px);
  background-size: 28px 28px;
  mask-image: linear-gradient(to top, rgba(0,0,0,1) 0%, rgba(0,0,0,0) 55%);
  -webkit-mask-image: linear-gradient(to top, rgba(0,0,0,1) 0%, rgba(0,0,0,0) 55%);
}
.funnel-hero-title { position: relative; z-index: 2; }
.funnel-hero-body { position: relative; z-index: 1; }
.funnel-viz-row {
  display: flex;
  gap: 20px;
  align-items: stretch;
  padding: 8px 0 0;
}

/* — 3D scene area — */
.funnel-viz-area {
  flex: 1 1 auto;
  min-width: 0;
  position: relative;
}
.funnel-three-wrap {
  position: relative;
  height: 520px;
  border-radius: 14px;
  overflow: hidden;
  background: transparent;
  border: 1px solid rgba(79, 110, 247, 0.18);
}
:deep(.funnel-three-canvas) {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  display: block;
}

/* — HUD overlay — */
.funnel-hud-lines {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 2;
}
.funnel-hud-lines line {
  stroke-width: 1;
  opacity: 0;
  transition: opacity .5s ease;
}
.funnel-hud-lines circle {
  opacity: 0;
  transition: opacity .5s ease;
}
.funnel-revealed .funnel-hud-lines line { opacity: 0.45; }
.funnel-revealed .funnel-hud-lines circle { opacity: 0.9; }
.funnel-hud-lines line.active { opacity: 0.9; stroke-width: 1.5; }

.funnel-hud-chip {
  position: absolute;
  top: 0;
  width: 108px;
  transform: translateY(-50%) scale(1);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  padding: 5px 10px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(79, 110, 247, 0.18);
  border-left: 2px solid var(--hud-accent, var(--c-primary));
  box-shadow: 0 2px 8px rgba(49, 94, 251, 0.08);
  cursor: pointer;
  z-index: 3;
  opacity: 0;
  transition: opacity .5s ease var(--hud-delay, 0s), transform .2s ease, box-shadow .2s ease, border-color .2s, background .2s;
}
.funnel-hud-chip.left { left: 10px; }
.funnel-hud-chip.right { right: 10px; }
.funnel-revealed .funnel-hud-chip { opacity: 1; }
.funnel-hud-chip:hover {
  transform: translateY(-50%) scale(1.04);
  background: rgba(255, 255, 255, 0.95);
  border-color: var(--hud-accent, var(--c-primary));
  box-shadow: 0 6px 16px rgba(49, 94, 251, 0.16);
  z-index: 4;
}
.funnel-hud-chip.active {
  transform: translateY(-50%) scale(1.04);
  background: #FFFFFF;
  border: 1px solid var(--hud-accent, var(--c-primary));
  border-left-width: 3px;
  box-shadow: 0 8px 20px rgba(49, 94, 251, 0.2);
  z-index: 4;
}
.hud-name {
  font-size: 11px;
  font-weight: 600;
  color: var(--c-text);
  white-space: nowrap;
}
.hud-count {
  font-size: 13px;
  font-weight: 800;
  color: var(--c-text);
  font-variant-numeric: tabular-nums;
}

/* — Static fallback — */
.funnel-fallback {
  height: 520px;
  border-radius: 14px;
  background: linear-gradient(180deg, #F8FAFF 0%, #F4F7FC 100%);
  border: 1px solid rgba(79, 110, 247, 0.18);
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 14px;
  padding: 24px 20px;
}
.fb-row { display: flex; justify-content: center; }
.fb-disc {
  height: 64px;
  border-radius: 50%;
  border: 1.5px solid;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  color: var(--c-text);
}
.fb-disc b { font-size: 16px; font-variant-numeric: tabular-nums; color: var(--c-text); }
.fb-disc span { font-size: 12px; }
.fb-disc.active { background: rgba(79, 110, 247, 0.18) !important; }

/* ===== Insight panel — layered glass ===== */
.funnel-insight-panel {
  flex: 0 1 400px;
  min-width: 260px;
  max-width: 400px;
  display: flex;
  align-items: stretch;
}
.insight-panel-inner {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 13px;
  padding: 16px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(79, 110, 247, 0.14);
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
.health-good { background: rgba(34, 197, 94, 0.15); color: var(--c-done); }
.health-watch { background: rgba(245, 158, 11, 0.15); color: var(--c-warn); }
.health-risk { background: rgba(239, 68, 68, 0.15); color: var(--c-reject); }

/* Metric cards row */
.insight-metrics-row { display: flex; gap: 10px; }
.insight-metric-card {
  flex: 1;
  background: rgba(255, 255, 255, 0.55);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
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

/* WoW + dwell row */
.insight-detail-row {
  display: flex;
  gap: 16px;
  padding: 6px 0;
  border-bottom: 1px solid var(--c-border-light);
}
.insight-detail-item { flex: 1; }
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
.wow-abs { font-size: 10px; font-weight: 600; opacity: 0.6; }

/* Sparkline block */
.insight-chart-block {
  background: rgba(255, 255, 255, 0.55);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
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
.insight-spark-lg { width: 100%; height: 48px; display: block; }

/* Conversion chain bars */
.insight-chain {
  background: rgba(255, 255, 255, 0.55);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
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
  box-shadow: 0 0 8px rgba(79, 110, 247, 0.15);
}
.chain-label { font-size: 9px; color: var(--c-sub); white-space: nowrap; }
.chain-val {
  font-size: 11px;
  font-weight: 700;
  color: var(--c-text);
  font-variant-numeric: tabular-nums;
}

/* Insight note */
.insight-note-block {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 13px;
  color: var(--c-body);
  line-height: 1.6;
  padding: 10px 12px;
  background: rgba(79, 110, 247, 0.04);
  border-radius: 8px;
  border-left: 3px solid var(--c-primary);
}

/* CTA */
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
  box-shadow: 0 4px 16px rgba(79, 110, 247, 0.2);
}

/* Bottom stepper chips */
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
  background: rgba(79, 110, 247, 0.06);
}
.viz-funnel-step.funnel-step-chip.active { font-weight: 700; }
.step-chip-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.step-chip-count { font-weight: 700; font-variant-numeric: tabular-nums; color: var(--c-text); }

/* — Reduced motion — */
@media (prefers-reduced-motion: reduce) {
  .insight-panel-inner { animation: none !important; }
  .funnel-hud-chip,
  .funnel-hud-lines line,
  .funnel-hud-lines circle { transition: none !important; }
  .viz-funnel-step.funnel-step-chip { transition: none !important; }
}

/* — Mobile — */
@media (max-width: 780px) {
  .funnel-title-hint { display: none; }
  .funnel-viz-row { flex-direction: column; }
  .funnel-three-wrap { height: 380px; }
  .funnel-fallback { height: 380px; gap: 8px; }
  .fb-disc { height: 48px; }
  .funnel-insight-panel { max-width: none; flex: 1 1 auto; }
  .insight-metrics-row { flex-wrap: wrap; }
  .insight-metric-card { min-width: 80px; }
  .funnel-hud-chip { width: 92px; padding: 4px 8px; }
  .hud-name { font-size: 10px; }
  .hud-count { font-size: 12px; }
  .funnel-stepper { gap: 6px; }
  .viz-funnel-step.funnel-step-chip { padding: 5px 10px; font-size: 11px; }
}
</style>
