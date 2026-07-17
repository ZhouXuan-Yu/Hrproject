<template>
  <div class="legacy-page-shell" :aria-busy="loading ? 'true' : 'false'">
    <div v-if="loading" class="legacy-state" role="status" aria-live="polite">
      <div class="legacy-state__skeleton"></div>
      <div class="legacy-state__text">页面加载中</div>
    </div>
    <div v-else-if="errorMessage" class="legacy-state legacy-state--error" role="alert">
      <strong>页面加载失败</strong>
      <span>{{ errorMessage }}</span>
      <button type="button" @click="loadLegacyPage">重试</button>
    </div>
    <div v-show="!loading && !errorMessage" ref="host" class="legacy-page"></div>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

const props = defineProps({
  page: { type: String, required: true },
  title: { type: String, required: true },
});

const router = useRouter();
const host = ref(null);
const loading = ref(false);
const errorMessage = ref('');
let cleanupFns = [];

const htmlToRoute = {
  'login.html': '/login',
  'recruit-dashboard.html': '/recruit-dashboard',
  'recruit-demand.html': '/recruit-demand',
  'recruit-demand-detail.html': '/recruit-demand-detail',
  'recruit-talent.html': '/recruit-talent',
  'recruit-interview.html': '/recruit-interview',
  'recruit-ai.html': '/recruit-ai',
  'recruit-config.html': '/recruit-config',
};

function transformLegacySource(source) {
  return Object.entries(htmlToRoute).reduce(
    (text, [legacy, route]) => text.split(legacy).join(route),
    source,
  );
}

function normalizeLegacyLinks(root) {
  root.querySelectorAll('a[href]').forEach((link) => {
    const href = link.getAttribute('href');
    if (htmlToRoute[href]) link.setAttribute('href', htmlToRoute[href]);
  });
}

function installClickBridge(root) {
  const onClick = (event) => {
    const link = event.target.closest('a[href]');
    if (!link) return;
    const href = link.getAttribute('href');
    if (!href || href.startsWith('#') || href.startsWith('http') || href.startsWith('mailto:')) return;
    if (href.startsWith('/')) {
      event.preventDefault();
      router.push(href);
    }
  };

  root.addEventListener('click', onClick);
  cleanupFns.push(() => root.removeEventListener('click', onClick));
}

function runInlineScripts(scripts) {
  scripts.forEach((source) => {
    const script = document.createElement('script');
    script.text = transformLegacySource(source);
    document.body.appendChild(script);
    script.remove();
  });
}

async function loadLegacyPage() {
  cleanupFns.forEach((fn) => fn());
  cleanupFns = [];
  loading.value = true;
  errorMessage.value = '';

  try {
    const response = await fetch(`/legacy/${props.page}.html`, { cache: 'no-store' });
    if (!response.ok) throw new Error(`无法加载页面: ${props.page}`);

    const rawHtml = await response.text();
    const parser = new DOMParser();
    const documentSnapshot = parser.parseFromString(rawHtml, 'text/html');
    const scripts = Array.from(documentSnapshot.querySelectorAll('script:not([src])')).map((script) => script.textContent || '');
    documentSnapshot.querySelectorAll('script').forEach((script) => script.remove());

    host.value.innerHTML = documentSnapshot.body.innerHTML;
    normalizeLegacyLinks(host.value);
    installClickBridge(host.value);

    await nextTick();
    runInlineScripts(scripts);
    normalizeLegacyLinks(host.value);
  } catch (error) {
    host.value.innerHTML = '';
    errorMessage.value = error instanceof Error ? error.message : '未知错误';
  } finally {
    loading.value = false;
  }
}

onMounted(loadLegacyPage);
watch(() => props.page, loadLegacyPage);
onBeforeUnmount(() => cleanupFns.forEach((fn) => fn()));
</script>

<style>
.legacy-page-shell,
.legacy-page {
  display: flex;
  min-height: 100vh;
  width: 100%;
}
.legacy-state {
  width: 100%;
  min-height: 100vh;
  display: grid;
  place-items: center;
  gap: 12px;
  background: #f6f8fb;
  color: #5b6475;
  font: 14px/1.5 Inter, "PingFang SC", "Microsoft YaHei", system-ui, sans-serif;
}
.legacy-state__skeleton {
  width: min(720px, calc(100vw - 48px));
  height: 220px;
  border-radius: 8px;
  border: 1px solid #e1e6ef;
  background:
    linear-gradient(90deg, transparent, rgba(79, 110, 247, .08), transparent),
    linear-gradient(#fff, #f9fafc);
  background-size: 240px 100%, 100% 100%;
  animation: legacy-shimmer 1.2s linear infinite;
}
.legacy-state--error {
  color: #172033;
}
.legacy-state--error span {
  color: #5b6475;
}
.legacy-state--error button {
  height: 36px;
  border: 1px solid #4f6ef7;
  border-radius: 8px;
  background: #4f6ef7;
  color: #fff;
  padding: 0 14px;
  cursor: pointer;
}
@keyframes legacy-shimmer {
  from { background-position: -240px 0, 0 0; }
  to { background-position: 720px 0, 0 0; }
}
@media (prefers-reduced-motion: reduce) {
  .legacy-state__skeleton {
    animation: none;
  }
}
</style>
