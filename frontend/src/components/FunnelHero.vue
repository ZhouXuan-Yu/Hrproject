<template>
  <div ref="cardEl" class="card funnel-hero-card" style="margin-bottom:12px" data-viz-enhanced="funnel">
    <div class="card-title funnel-hero-title">
      <svg viewBox="0 0 24 24" style="width:18px;height:18px"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>
      招聘全漏斗
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
import { ref, onMounted, onUnmounted } from 'vue';
import * as THREE from 'three';
import { FUNNEL_STEPS } from '../data/dashboard.js';

// ---------- state ----------
const selected = ref(3); // default: bottleneck stage (Offer)
const webglOK = ref(true);
const cardEl = ref(null);
const vizWrap = ref(null);
const chipEls = ref([]);
const lineEls = ref([]);
const dotEls = ref([]);

function accentColor(step) { return step.color || 'var(--c-primary)'; }
function selectStage(i) { selected.value = i; }

// Semantic stage palette — cohesive blue progression with green for hire
const STAGE_HEX = [
  0x7AA7FF, // 收简历   — light sky
  0x4F7BFF, // 筛选通过 — bright blue
  0x315EFB, // 面试     — primary blue
  0x6C63FF, // Offer    — purple-blue accent
  0x22A06B, // 入职     — success green
];
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

// ---------- Three.js scene ----------
let renderer = null;
let scene = null;
let camera = null;
let coneGroup = null;
let raycaster = null;
let pointerNDC = null;
let rings = []; // { mesh, mat, baseY, r, lift, reveal }
let bands = []; // { mesh, mat, reveal } — frustum fill bands between rings
let helixStrands = []; // { geo, phase }
let orbitLights = [];
let rafId = 0;
let revealStart = -1;
let io = null;
let visIo = null;
let ro = null;
let scrollP = 0;
let ptrX = 0;
let ptrY = 0;
let destroyed = false;
let visible = false;

// Linear taper so the CylinderGeometry silhouette passes exactly through each ring
const DISC_RADII = [1.6, 1.335, 1.07, 0.805, 0.54];
const DISC_Y = [1.55, 0.78, 0.0, -0.78, -1.55];
const HELIX_N = 150;
const HELIX_TURNS = 3;

function easeOutCubic(t) { return 1 - Math.pow(1 - t, 3); }

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
  camera = new THREE.PerspectiveCamera(38, w / h, 0.1, 50);
  camera.position.set(0, 0.55, 8.2);
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

  // ---- Frustum fill bands: one solid glass band per stage gap ----
  // Short open cylinders render reliably; together they form the cone body,
  // each band a different stage color so the layers read distinctly.
  for (let i = 0; i < DISC_RADII.length - 1; i++) {
    const topR = DISC_RADII[i];
    const bottomR = DISC_RADII[i + 1];
    const topY = DISC_Y[i];
    const bottomY = DISC_Y[i + 1];
    const height = topY - bottomY;
    const geo = new THREE.CylinderGeometry(topR, bottomR, height, 48, 1, true);
    const mat = new THREE.MeshStandardMaterial({
      color: STAGE_HEX[i],
      transparent: true,
      opacity: 0.0,
      roughness: 0.25,
      metalness: 0.05,
      emissive: STAGE_HEX[i],
      emissiveIntensity: 0.08,
      side: THREE.DoubleSide,
      depthWrite: false,
    });
    const mesh = new THREE.Mesh(geo, mat);
    mesh.position.y = (topY + bottomY) / 2;
    mesh.scale.set(0.55, 1, 0.55);
    mesh.userData.stage = i;
    mesh.renderOrder = 0;
    coneGroup.add(mesh);
    bands.push({ mesh, mat, reveal: 0 });
  }

  // ---- 5 thin outline rings: the exact boundary of each fill band ----
  const RING_TUBE = 0.028;
  DISC_RADII.forEach((r, i) => {
    const geo = new THREE.TorusGeometry(r, RING_TUBE, 16, 48);
    const mat = new THREE.MeshStandardMaterial({
      color: STAGE_HEX[i],
      transparent: true,
      opacity: 0.0,
      roughness: 0.18,
      metalness: 0.15,
      emissive: STAGE_HEX[i],
      emissiveIntensity: 0.22,
      depthWrite: false,
    });
    const mesh = new THREE.Mesh(geo, mat);
    mesh.position.y = DISC_Y[i] - 1.4;
    mesh.rotation.x = Math.PI / 2;
    mesh.scale.setScalar(0.55);
    mesh.userData.stage = i;
    mesh.renderOrder = 2;

    coneGroup.add(mesh);
    rings.push({ mesh, mat, baseY: DISC_Y[i], r, lift: 0, reveal: 0 });
  });

  // Triple particle streams orbiting OUTSIDE the cone, top → bottom
  const streamColors = [0x315efb, 0x36bffa, 0x6c63ff];
  for (let s = 0; s < 3; s++) {
    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.BufferAttribute(new Float32Array(HELIX_N * 3), 3));
    const mat = new THREE.PointsMaterial({
      color: streamColors[s],
      size: 0.07,
      transparent: true,
      opacity: 0.95,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
      sizeAttenuation: true,
    });
    coneGroup.add(new THREE.Points(geo, mat));
    helixStrands.push({ geo, phase: s * (Math.PI * 2 / 3) });
  }

  raycaster = new THREE.Raycaster();
  pointerNDC = new THREE.Vector2();

  renderer.domElement.addEventListener('click', onCanvasClick);
  wrap.addEventListener('pointermove', onPointerMove);

  ro = new ResizeObserver(onResize);
  ro.observe(wrap);

  rafId = requestAnimationFrame(tick);
}

// Cone silhouette radius at parameter t (0 = top, 1 = bottom)
function coneRadiusAt(t) {
  const n = DISC_RADII.length - 1;
  const x = Math.min(n - 1e-6, Math.max(0, t * n));
  const i = Math.floor(x);
  const f = x - i;
  return DISC_RADII[i] + (DISC_RADII[i + 1] - DISC_RADII[i]) * f;
}

function updateHelix(time) {
  const topY = DISC_Y[0];
  const bottomY = DISC_Y[DISC_Y.length - 1];
  helixStrands.forEach((hx) => {
    const arr = hx.geo.attributes.position.array;
    for (let j = 0; j < HELIX_N; j++) {
      const t = (j / HELIX_N + time * 0.05) % 1;
      const ang = t * HELIX_TURNS * Math.PI * 2 + hx.phase;
      const y = topY + t * (bottomY - topY);
      // orbit just outside the cone surface, wider at the top
      const offset = 0.22 * (1 - t) + 0.14 * t;
      const r = coneRadiusAt(t) + offset + Math.sin(ang * 2 + time * 1.4) * 0.02;
      arr[j * 3] = Math.cos(ang) * r;
      arr[j * 3 + 1] = y;
      arr[j * 3 + 2] = Math.sin(ang) * r;
    }
    hx.geo.attributes.position.needsUpdate = true;
  });
}

function updateScene(now) {
  if (revealStart >= 0) {
    const el = now - revealStart;
    rings.forEach((d, i) => {
      const order = rings.length - 1 - i; // bottom → top stagger
      const local = Math.min(1, Math.max(0, (el - order * 140) / 850));
      d.reveal = easeOutCubic(local);
    });
    bands.forEach((b, i) => {
      const order = bands.length - 1 - i;
      const local = Math.min(1, Math.max(0, (el - order * 140 - 60) / 850));
      b.reveal = easeOutCubic(local);
    });
  }
  rings.forEach((d, i) => {
    const isSel = i === selected.value;
    d.lift += ((isSel ? 0.18 : 0) - d.lift) * 0.07;
    d.mat.emissiveIntensity += ((isSel ? 0.6 : 0.22) - d.mat.emissiveIntensity) * 0.08;
    const rv = d.reveal;
    d.mesh.position.y = d.baseY - (1 - rv) * 1.4 + d.lift;
    d.mesh.scale.setScalar(0.55 + 0.45 * rv);
    d.mat.opacity = (isSel ? 0.95 : 0.8) * rv;
  });
  bands.forEach((b) => {
    const rv = b.reveal;
    const s = 0.55 + 0.45 * rv;
    b.mesh.scale.set(s, 1, s);
    b.mat.opacity = 0.55 * rv;
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
  rings.forEach((d, i) => {
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
    const ax = side < 0 ? 56 : w - 56;
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
  if (!visible) { rafId = 0; return; }
  rafId = requestAnimationFrame(tick);
  const time = nowMs / 1000;

  coneGroup.rotation.y = time * 0.12;

  // orbiting dynamic lights (elliptical paths)
  orbitLights[0].position.set(Math.cos(time * 0.55) * 3.8, 2.3 + Math.sin(time * 0.3) * 0.5, Math.sin(time * 0.55) * 2.6);
  orbitLights[1].position.set(Math.cos(-time * 0.4 + Math.PI) * 3.2, -1.3, Math.sin(-time * 0.4 + Math.PI) * 3.0);

  updateHelix(time);
  updateScene(nowMs);

  // scroll-driven depth + pointer parallax (lerped)
  const targetZ = 8.2 - scrollP * 1.6;
  const targetY = 0.55 + scrollP * 0.6 - ptrY * 0.4;
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
  if (visIo) { visIo.disconnect(); visIo = null; }
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
  // Pause rAF when off-screen, resume when visible
  const visIoLocal = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      visible = entry.isIntersecting;
      if (visible && !destroyed && rafId === 0) {
        rafId = requestAnimationFrame(tick);
      }
    });
  }, { threshold: 0 });
  visIoLocal.observe(cardEl.value);
  visIo = visIoLocal;
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
  display: block;
  padding: 8px 0 0;
}

/* — 3D scene area — */
.funnel-viz-area {
  width: 100%;
  position: relative;
}
.funnel-three-wrap {
  position: relative;
  height: 560px;
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
  background: rgba(255, 255, 255, 0.85);
  border: 1px solid rgba(79, 110, 247, 0.18);
  border-left: 2px solid var(--hud-accent, var(--c-primary));
  box-shadow: 0 2px 8px rgba(49, 94, 251, 0.08);
  cursor: pointer;
  z-index: 3;
  opacity: 0;
  transition: opacity .5s ease var(--hud-delay, 0s), transform .2s ease, box-shadow .2s ease, border-color .2s, background .2s;
}
.funnel-hud-chip.left { left: 8px; }
.funnel-hud-chip.right { right: 8px; }
.funnel-revealed .funnel-hud-chip { opacity: 1; }
.funnel-hud-chip:hover {
  transform: translateY(-50%) scale(1.04);
  background: rgba(255, 255, 255, 0.95);
  border-color: var(--hud-accent, var(--c-primary));
  box-shadow: 0 6px 16px rgba(49, 94, 251, 0.16);
  z-index: 4;
}
@keyframes hudPulse {
  0%, 100% { box-shadow: 0 8px 20px rgba(49, 94, 251, 0.2); }
  50% { box-shadow: 0 8px 26px rgba(49, 94, 251, 0.34); }
}
.funnel-hud-chip.active {
  transform: translateY(-50%) scale(1.04);
  background: #FFFFFF;
  border: 1px solid var(--hud-accent, var(--c-primary));
  border-left-width: 3px;
  box-shadow: 0 8px 20px rgba(49, 94, 251, 0.2);
  animation: hudPulse 2.2s ease-in-out infinite;
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
  .funnel-hud-chip,
  .funnel-hud-lines line,
  .funnel-hud-lines circle { transition: none !important; }
  .viz-funnel-step.funnel-step-chip { transition: none !important; }
}

/* — Mobile — */
@media (max-width: 780px) {
  .funnel-three-wrap { height: 440px; }
  .funnel-fallback { height: 440px; gap: 8px; }
  .fb-disc { height: 48px; }
  .funnel-hud-chip { width: 92px; padding: 4px 8px; }
  .hud-name { font-size: 10px; }
  .hud-count { font-size: 12px; }
  .funnel-stepper { gap: 6px; }
  .viz-funnel-step.funnel-step-chip { padding: 5px 10px; font-size: 11px; }
}
</style>
