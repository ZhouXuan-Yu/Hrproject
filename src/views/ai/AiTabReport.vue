<template>
  <div data-slot="ai-workspace">
    <div data-slot="ai-conversation">
      <AiChatMessage role="ai" status="complete">
        <p>AI 分析招聘数据，标注异常趋势和关键洞察，并给出改进建议。选择报表类型开始分析。</p>
      </AiChatMessage>
      <AiChatMessage v-if="reportLoading" role="ai" status="loading" />
      <AiChatMessage v-if="reportError && !reportLoading" role="ai" status="error">
        <template #error>{{ reportError }}</template>
      </AiChatMessage>
      <template v-if="reportResult && !reportLoading">
        <AiChatMessage role="ai" status="complete">
          <div data-slot="ai-jd-section"><div data-slot="ai-jd-section-title">综合分析</div><p data-slot="ai-report-summary">{{ reportResult.summary }}</p></div>
          <div data-slot="ai-jd-section"><div data-slot="ai-jd-section-title">关键洞察</div><ul data-slot="ai-jd-list"><li v-for="(ins, i) in reportResult.insights" :key="'ins'+i">{{ ins }}</li></ul></div>
          <div v-if="reportResult.anomalies?.length" data-slot="ai-jd-section"><div data-slot="ai-jd-section-title">异常标注</div><ul data-slot="ai-report-anomalies"><li v-for="(a, i) in reportResult.anomalies" :key="'an'+i">{{ a }}</li></ul></div>
          <div data-slot="ai-jd-section"><div data-slot="ai-jd-section-title">改进建议</div><ul data-slot="ai-jd-list"><li v-for="(r, i) in reportResult.recommendations" :key="'rc'+i">{{ r }}</li></ul></div>
        </AiChatMessage>
        <AiDisclaimer />
      </template>
    </div>
    <div data-slot="ai-input-area">
      <div style="display:flex;gap:8px;margin-bottom:8px">
        <select v-model="reportForm.type" style="flex:1;padding:7px 10px;border:1px solid var(--c-border);border-radius:var(--radius-sm);font-size:13px;font-family:inherit;background:var(--c-card);color:var(--c-body)" aria-label="报告类型">
          <option v-for="rt in reportTypes" :key="rt.value" :value="rt.value">{{ rt.label }}</option>
        </select>
      </div>
      <button class="btn btn-primary" style="width:100%" :disabled="reportLoading" @click="generateReport">
        <AiSkeleton v-if="reportLoading" variant="spinner" />
        {{ reportLoading ? '生成中...' : '生成分析报告' }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, inject } from 'vue';
import AiChatMessage from '../../components/ai/AiChatMessage.vue';
import AiSkeleton from '../../components/ai/AiSkeleton.vue';
import AiDisclaimer from '../../components/ai/AiDisclaimer.vue';
import { runReportAnalysis } from '../../api/ai.js';
import { useClipboard } from '../../composables/useClipboard.js';

const showToast = inject('showToast');
const { copy, copied } = useClipboard();

const reportTypes = [
  { value: 'funnel', label: '招聘漏斗' }, { value: 'channel', label: '渠道效果' },
  { value: 'cycle', label: '招聘周期' }, { value: 'offer', label: 'Offer分析' }, { value: 'interviewer', label: '面试官统计' },
];
const reportForm = reactive({ type: 'funnel' });
const reportResult = ref(null);
const reportLoading = ref(false);
const reportError = ref('');

async function generateReport() {
  reportError.value = ''; reportLoading.value = true;
  try { reportResult.value = await runReportAnalysis({ type: reportForm.type, params: {} }); showToast('分析报告生成完成'); }
  catch (e) { reportError.value = e.message || '报告生成失败，请重试'; showToast(reportError.value); }
  finally { reportLoading.value = false; }
}
</script>

<style scoped>
/* ===== AI Workspace layout ===== */
[data-slot="ai-workspace"] {
  display: flex;
  flex-direction: column;
  min-height: 420px;
  max-height: calc(100vh - 280px);
}
[data-slot="ai-conversation"] {
  flex: 1;
  overflow-y: auto;
  padding: 0 4px;
}
[data-slot="ai-input-area"] {
  padding-top: 12px;
  border-top: 1px solid var(--c-border-light);
  flex-shrink: 0;
}

/* ===== JD Section styles (shared) ===== */
[data-slot="ai-jd-section"] { margin-bottom:16px }
[data-slot="ai-jd-section-title"] { font-size:12px;font-weight:700;color:var(--c-text);margin-bottom:8px;display:flex;align-items:center;gap:6px }
[data-slot="ai-jd-section-title"]::before { content:'';width:3px;height:12px;background:var(--c-primary);border-radius:2px }
[data-slot="ai-jd-list"] { padding-left:16px;font-size:13px;color:var(--c-body);line-height:2 }

/* ===== Report ===== */
[data-slot="ai-report-summary"] { font-size:13px;line-height:1.9;color:var(--c-body) }
[data-slot="ai-report-anomalies"] { padding-left:16px;font-size:13px;line-height:2 }
[data-slot="ai-report-anomalies"] li { color:var(--c-warn) }
[data-slot="ai-report-anomalies"] li::marker { color:var(--c-warn) }

/* Focus visible */
input:focus-visible, select:focus-visible { outline:2px solid var(--c-primary);outline-offset:1px }

/* ===== Mobile (≤768px) ===== */
@media (max-width: 768px) {
  [data-slot="ai-report-summary"] { font-size: 12px; line-height: 1.7; }
}
</style>
