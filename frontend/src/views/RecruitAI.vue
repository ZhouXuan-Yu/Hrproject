<template>
  <WorkbenchLayout title="招聘辅助中心" :breadcrumb="{ text: '招聘管理', href: '/recruit-dashboard' }">
    <!-- 辅助能力统计卡（hero-summary-card 同款） -->
    <StatCardRow :cards="statCards" />

    <div class="permission-bar">
      本页面仅<b>系统管理员</b>可见 · 集中所有<b>用户主动发起</b>的招聘辅助能力 · 流程内嵌的简历解析、匹配评分、联系话术辅助已嵌入各业务页面
    </div>

    <!-- 6 Tab navigation -->
    <div class="tabs" role="tablist" aria-label="招聘辅助功能">
      <button v-for="tab in tabs" :key="tab.id" class="tab"
        :id="'tab-btn-' + tab.id"
        :class="{ active: activeTab === tab.id }"
        :tabindex="activeTab === tab.id ? 0 : -1"
        :aria-selected="activeTab === tab.id ? 'true' : 'false'"
        role="tab" @click="activeTab = tab.id"
        @keydown.left.prevent="focusPrevTab"
        @keydown.right.prevent="focusNextTab"
        @keydown.home.prevent="activeTab = tabs[0].id"
        @keydown.end.prevent="activeTab = tabs[tabs.length - 1].id"
      >{{ tab.number }} {{ tab.title }}</button>
    </div>

    <!-- Dynamic tab content with KeepAlive -->
    <KeepAlive>
      <component :is="activeComponent" :key="activeTab" />
    </KeepAlive>

    <!-- Toast notification (replaces alert) -->
    <Teleport to="body">
      <div v-if="toast.show" data-slot="ai-toast" :class="{ visible: toast.show }">{{ toast.text }}</div>
    </Teleport>

    <!-- Embedded AI capabilities table -->
    <div style="margin-top:32px">
      <div class="section-label" style="font-size:14px;margin-bottom:12px">已嵌入各业务页面的辅助能力（流程内触发，不在此页面操作）</div>
      <div class="table-wrap data-region">
        <DataLoadingOverlay :visible="referenceLoading" />
        <table><thead><tr><th>辅助能力</th><th>所属页面</th><th>触发方式</th><th>Dify 工作流</th><th>状态</th></tr></thead>
          <tbody><tr v-for="(item, i) in embeddedAI" :key="i">
            <td v-html="item.ability"></td><td>{{ item.page }}</td><td>{{ item.trigger }}</td><td>{{ item.workflow }}</td>
            <td><StatusBadge :type="item.status">{{ statusLabel(item.status) }}</StatusBadge></td>
          </tr></tbody>
        </table>
      </div>
    </div>

    <!-- API architecture info -->
    <div class="permission-bar" style="margin-top:20px">
      <b>API 调用规则</b>：所有 AI 能力统一走 <b>后端代理转发</b>（前端 → Flask 后端 → DeepSeek），前端不直连 LLM。<br>
      <b>AI 工作流</b>：JD 草稿生成 · 语义简历搜索 · 人岗匹配 · 面试问题生成 · 沟通话术 · 招聘报表分析<br>
      <b>数据存储</b>：解析结果 → <code>t_hr_resume</code> · 匹配分 → <code>t_hr_resume_match</code> · 画像标签 → <code>t_hr_candidate</code><br>
      <b>免责声明</b>：所有 AI 生成内容仅供辅助参考，关键动作必须经过人工确认
    </div>
  </WorkbenchLayout>
</template>

<script setup>
import { ref, reactive, computed, onBeforeUnmount, onMounted, provide } from 'vue';
import WorkbenchLayout from '../layouts/WorkbenchLayout.vue';
import StatusBadge from '../components/StatusBadge.vue';
import { AI_TABS, EMBEDDED_AI } from '../data/ai.js';
import { fetchDemands } from '../api/demand.js';
import { fetchTalent } from '../api/talent.js';
import StatCardRow from '../components/StatCardRow.vue';
import { KPI_ICONS } from '../components/kpiIcons.js';
import DataLoadingOverlay from '../components/DataLoadingOverlay.vue';

import AiTabJD from './ai/AiTabJD.vue';
import AiTabSearch from './ai/AiTabSearch.vue';
import AiTabMatch from './ai/AiTabMatch.vue';
import AiTabInterview from './ai/AiTabInterview.vue';
import AiTabReport from './ai/AiTabReport.vue';
import AiTabCommunication from './ai/AiTabCommunication.vue';

// --- Tab system ---
const tabs = AI_TABS;
const activeTab = ref('jd');
const embeddedAI = ref(EMBEDDED_AI);

// 顶部辅助能力统计卡（hero-summary-card 同款，按嵌入能力状态统计）
const statCards = computed(() => {
  const cnt = (st) => embeddedAI.value.filter(a => a.status === st).length;
  return [
    { key: 'all', label: '辅助能力', value: embeddedAI.value.length, hint: '流程内嵌', icon: KPI_ICONS.settings },
    { key: 'done', label: '已上线', value: cnt('done'), hint: '可直接使用', icon: KPI_ICONS.check },
    { key: 'warn', label: '进行中', value: cnt('warn'), hint: '需新增工作流', icon: KPI_ICONS.clock },
    { key: 'draft', label: '待规划', value: cnt('draft'), hint: '需复核评估', icon: KPI_ICONS.bell },
  ];
});

const tabMap = {
  jd: AiTabJD,
  search: AiTabSearch,
  match: AiTabMatch,
  interview: AiTabInterview,
  report: AiTabReport,
  chat: AiTabCommunication,
};
const activeComponent = computed(() => tabMap[activeTab.value]);

// --- Shared data (provided to tab children) ---
const candidates = ref([]);
const demands = ref([]);
const referenceLoading = ref(true);
const referenceLoadError = ref('');

provide('aiCandidates', candidates);
provide('aiDemands', demands);
provide('aiReferenceLoading', referenceLoading);

// --- Toast (shared) ---
const toast = reactive({ show: false, text: '', timer: null });
function showToast(text) {
  toast.text = text; toast.show = true;
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => { toast.show = false; }, 2500);
}
provide('showToast', showToast);
onBeforeUnmount(() => { clearTimeout(toast.timer); });

// --- Load real data from backend on mount ---
async function loadBackendData() {
  referenceLoading.value = true;
  referenceLoadError.value = '';
  try {
    const [demandRes, talentRes] = await Promise.all([
      fetchDemands({ page: 1, pageSize: 50 }),
      fetchTalent({ tab: 'external', page: 1, pageSize: 50 }),
    ]);
    if (Array.isArray(demandRes.data)) {
      const dl = demandRes.data;
      demands.value = dl.map(d => ({
        id: d.id || d.demand_id,
        name: d.position || d.position_id || d.position_name,
        dept: d.dept || d.department || '—',
        status: d.status || d.demand_status || '—',
      }));
    }
    if (Array.isArray(talentRes.ext)) {
      const tl = talentRes.ext;
      candidates.value = tl.map(c => ({
        id: c.id,
        name: c.name || c.candidate_name,
        title: c.position_hint || c.last_position || '候选人',
        dept: c.dept || c.department || '—',
        company: c.company || c.source_channel || '—',
        years: c.workYears || c.work_years,
        edu: c.edu || c.edu_level,
      }));
    }
  } catch (e) {
    referenceLoadError.value = e.message || 'AI 辅助引用数据加载失败';
    candidates.value = [];
    demands.value = [];
    console.warn('[RecruitAI] Backend data load failed:', e.message);
  } finally {
    referenceLoading.value = false;
  }
}
onMounted(loadBackendData);

// --- Shared utilities ---
function statusLabel(s) { return { done: '一期开发', warn: '二期', draft: '远期' }[s] || s; }

// --- Keyboard navigation for tabs ---
function focusPrevTab() {
  const idx = tabs.findIndex(t => t.id === activeTab.value);
  const prev = tabs[idx > 0 ? idx - 1 : tabs.length - 1];
  activeTab.value = prev.id;
  document.getElementById('tab-btn-' + prev.id)?.focus();
}
function focusNextTab() {
  const idx = tabs.findIndex(t => t.id === activeTab.value);
  const next = tabs[idx < tabs.length - 1 ? idx + 1 : 0];
  activeTab.value = next.id;
  document.getElementById('tab-btn-' + next.id)?.focus();
}
</script>

<style scoped>
/* ===== Toast ===== */
[data-slot="ai-toast"] {
  position: fixed;
  bottom: 32px;
  left: 50%;
  transform: translateX(-50%) translateY(20px);
  background: var(--c-sidebar,#1E293B);
  color: #fff;
  padding: 10px 24px;
  border-radius: 8px;
  font-size: 13px;
  z-index: 10000;
  opacity: 0;
  transition: opacity .25s ease, transform .25s ease;
  pointer-events: none;
}
[data-slot="ai-toast"].visible {
  opacity: 1;
  transform: translateX(-50%) translateY(0);
}

/* ===== Shared ===== */
.permission-bar { font-size:var(--fs-caption);color:var(--c-sub);background:var(--c-surface-elevated);padding:10px 16px;border-radius:var(--radius);border:1px solid var(--c-border);line-height:1.8;margin-bottom:var(--gap) }
.card { background:var(--c-card);border-radius:var(--radius);padding:20px;border:1px solid var(--c-border) }
.data-region { position: relative; min-height: 160px; }
.card-title { font-size:15px;font-weight:700;margin-bottom:16px;color:var(--c-text);display:flex;align-items:center;gap:8px }
.card-subtitle { font-size:11px;color:var(--c-sub);font-weight:400;margin-left:8px }
.section-label { font-size:14px;font-weight:600;color:var(--c-text) }

@media (prefers-reduced-motion: reduce) {
  [data-slot="ai-toast"] { transition: none; }
}

/* ===== Mobile (≤768px) ===== */
@media (max-width: 768px) {
  .tabs {
    flex-wrap: wrap;
    gap: 4px;
    border-bottom-width: 1px;
  }
  .tab {
    font-size: 12px;
    padding: 6px 12px;
  }
  .card {
    padding: 16px;
  }
  .permission-bar {
    padding: 8px 12px;
    font-size: 11px;
  }
  .table-wrap {
    overflow-x: auto;
  }
}
</style>
