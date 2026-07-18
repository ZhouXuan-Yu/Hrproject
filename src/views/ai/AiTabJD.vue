<template>
  <div data-slot="ai-workspace">
    <!-- Conversation area -->
    <div data-slot="ai-conversation">
      <!-- AI intro -->
      <AiChatMessage role="ai" status="complete">
        <p>输入岗位信息，我帮你生成结构化的 JD 草稿，包括岗位职责、必备技能、加分项和任职资格。所有内容需人工审核确认后使用。</p>
      </AiChatMessage>

      <!-- Loading -->
      <AiChatMessage v-if="jdLoading && !jdStreamContent" role="ai" status="loading" />

      <!-- Streaming content (shown during SSE, before final parse) -->
      <AiChatMessage
        v-if="jdLoading && jdStreamContent"
        role="ai"
        status="streaming"
        :streamingContent="jdStreamContent"
      />

      <!-- Error -->
      <AiChatMessage v-if="jdError && !jdLoading" role="ai" status="error">
        <template #error>{{ jdError }}</template>
      </AiChatMessage>

      <!-- Result -->
      <template v-if="jdResult && !jdLoading">
        <AiChatMessage role="ai" status="complete">
          <div data-slot="ai-jd-result">
            <div data-slot="ai-jd-header">
              <h4>{{ jdResult.position }}</h4>
              <span data-slot="ai-jd-dept">{{ jdResult.department }}</span>
            </div>
            <div data-slot="ai-jd-section">
              <div data-slot="ai-jd-section-title">岗位职责</div>
              <ol data-slot="ai-jd-list">
                <li v-for="(r, i) in jdResult.responsibilities" :key="'r'+i">{{ r }}</li>
              </ol>
            </div>
            <div data-slot="ai-jd-section">
              <div data-slot="ai-jd-section-title">必备技能</div>
              <div data-slot="ai-jd-skill-table">
                <div v-for="s in jdResult.required_skills" :key="s.name" data-slot="ai-jd-skill-row">
                  <span data-slot="ai-jd-skill-name">{{ s.name }}</span>
                  <StatusBadge :type="s.weight === '必须' ? 'done' : 'progress'">{{ s.weight }}</StatusBadge>
                  <span data-slot="ai-jd-skill-desc">{{ s.description }}</span>
                </div>
              </div>
            </div>
            <div v-if="jdResult.plus_skills?.length" data-slot="ai-jd-section">
              <div data-slot="ai-jd-section-title">加分项</div>
              <ul data-slot="ai-jd-list">
                <li v-for="s in jdResult.plus_skills" :key="s.name">{{ s.name }} — {{ s.description }}</li>
              </ul>
            </div>
            <div data-slot="ai-jd-section">
              <div data-slot="ai-jd-section-title">任职资格</div>
              <div data-slot="ai-jd-info-grid">
                <div v-for="(v, k) in jdResult.qualifications" :key="k" data-slot="ai-jd-info-item">
                  <span data-slot="ai-jd-info-label">{{ qualLabels[k] || k }}</span>
                  <span data-slot="ai-jd-info-value">{{ v }}</span>
                </div>
              </div>
            </div>
          </div>
        </AiChatMessage>
        <AiDisclaimer />
      </template>
    </div>

    <!-- Input area -->
    <div data-slot="ai-input-area">
      <div style="display:flex;gap:8px;margin-bottom:8px;flex-wrap:wrap">
        <input v-model="jdForm.position" placeholder="岗位名称 (必填)" style="flex:1;min-width:140px;padding:7px 10px;border:1px solid var(--c-border);border-radius:var(--radius-sm);font-size:13px;font-family:inherit;background:var(--c-card);color:var(--c-text)" aria-label="岗位名称">
        <select v-model="jdForm.department" style="flex:1;min-width:120px;padding:7px 10px;border:1px solid var(--c-border);border-radius:var(--radius-sm);font-size:13px;font-family:inherit;background:var(--c-card);color:var(--c-body)" aria-label="部门">
          <option value="">部门 (必填)</option>
          <option v-for="d in departments" :key="d" :value="d">{{ d }}</option>
        </select>
        <select v-model="jdForm.level" style="width:100px;padding:7px 10px;border:1px solid var(--c-border);border-radius:var(--radius-sm);font-size:13px;font-family:inherit;background:var(--c-card);color:var(--c-body)" aria-label="职级">
          <option v-for="lv in levels" :key="lv" :value="lv">{{ lv }}</option>
        </select>
      </div>
      <AiPromptInput
        v-model="jdForm.requirements"
        :status="jdStatus"
        :disabled="!jdForm.position || !jdForm.department"
        placeholder="描述岗位核心要求 (选填)，例如：5年Java经验、大厂背景、熟悉微服务架构..."
        hint=""
        layout="compact"
        aria-label="JD 草稿需求描述"
        @submit="generateJd"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, inject } from 'vue';
import AiChatMessage from '../../components/ai/AiChatMessage.vue';
import AiPromptInput from '../../components/ai/AiPromptInput.vue';
import AiSkeleton from '../../components/ai/AiSkeleton.vue';
import AiDisclaimer from '../../components/ai/AiDisclaimer.vue';
import StatusBadge from '../../components/StatusBadge.vue';
import { MOCK_DEPARTMENTS } from '../../data/ai.js';
import { runJdGenerate } from '../../api/ai.js';
import { useStreaming } from '../../composables/useStreaming.js';

const showToast = inject('showToast');

const levels = ['初级', '中级', '高级', '资深', '专家'];
const qualLabels = { education: '学历', experience: '经验', industry: '行业', soft: '软技能' };
const departments = MOCK_DEPARTMENTS;

const jdForm = reactive({ position: '', department: '', level: '高级', requirements: '' });
const jdResult = ref(null);
const jdLoading = ref(false);
const jdError = ref('');
const jdStatus = computed(() => jdLoading.value ? 'submitted' : (jdError.value ? 'error' : 'ready'));

// Streaming (SSE) — tries streaming first, falls back to blocking
const {
  content: jdStreamContent,
  thinking: jdThinking,
  isStreaming: jdStreaming,
  error: jdStreamError,
  result: jdStreamResult,
  start: startJdStream,
  stop: stopJdStream,
} = useStreaming();

// Watch for streaming completion and set jdResult
watch([jdStreamContent, jdStreamResult, jdStreamError], () => {
  if (jdStreamError.value) {
    jdError.value = jdStreamError.value;
    jdLoading.value = false;
  }
  if (!jdStreaming.value && jdStreamContent.value) {
    // Parse the markdown stream into structured JD
    jdResult.value = {
      jd_text: jdStreamContent.value,
      position: jdForm.position,
      department: jdForm.department,
      // Fallback structure if streaming didn't provide result keys
      responsibilities: jdStreamResult.value?.responsibilities || ['（AI 流式生成的内容，请查看上方 Markdown）'],
      required_skills: jdStreamResult.value?.required_skills || [],
      plus_skills: jdStreamResult.value?.plus_skills || [],
      qualifications: jdStreamResult.value?.qualifications || {},
      disclaimer: jdStreamResult.value?.disclaimer || '此内容由AI生成，请人工审核确认后使用',
    };
    jdLoading.value = false;
  }
});

// Watch: clear error on input change
watch(() => jdForm.requirements, () => { if (jdError.value) jdError.value = ''; });

// Override generateJd to use streaming
async function generateJd() {
  if (!jdForm.position || !jdForm.department) return;
  jdError.value = ''; jdLoading.value = true;
  try {
    await startJdStream('jd-generate', { ...jdForm });
    showToast('JD 草稿生成完成');
  } catch (e) {
    // Fallback to blocking API
    jdLoading.value = true;
    try {
      jdResult.value = await runJdGenerate({ ...jdForm });
      showToast('JD 草稿生成完成');
    } catch (e2) {
      jdError.value = e2.message || '生成失败，请重试';
      showToast(jdError.value);
    }
    jdLoading.value = false;
  }
}
</script>

<style scoped>
/* ===== AI Workspace layout (conversation + fixed input) ===== */
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

/* ===== JD Result sections ===== */
[data-slot="ai-jd-result"] { font-size: 13px; }
[data-slot="ai-jd-header"] { display:flex;align-items:center;gap:10px;margin-bottom:16px;padding-bottom:12px;border-bottom:1px solid var(--c-border) }
[data-slot="ai-jd-header"] h4 { font-size:16px;font-weight:700;color:var(--c-text);margin:0 }
[data-slot="ai-jd-dept"] { font-size:11px;color:var(--c-sub);background:var(--c-surface-elevated);padding:2px 10px;border-radius:10px;border:1px solid var(--c-border) }
[data-slot="ai-jd-section"] { margin-bottom:16px }
[data-slot="ai-jd-section-title"] { font-size:12px;font-weight:700;color:var(--c-text);margin-bottom:8px;display:flex;align-items:center;gap:6px }
[data-slot="ai-jd-section-title"]::before { content:'';width:3px;height:12px;background:var(--c-primary);border-radius:2px }
[data-slot="ai-jd-list"] { padding-left:16px;font-size:13px;color:var(--c-body);line-height:2 }
[data-slot="ai-jd-skill-table"] { border:1px solid var(--c-border);border-radius:var(--radius-sm);overflow:hidden }
[data-slot="ai-jd-skill-row"] { display:flex;align-items:center;gap:8px;padding:8px 12px;border-bottom:1px solid var(--c-border-light);font-size:12px }
[data-slot="ai-jd-skill-row"]:last-child { border-bottom:none }
[data-slot="ai-jd-skill-name"] { font-weight:600;color:var(--c-text);min-width:70px }
[data-slot="ai-jd-skill-desc"] { color:var(--c-sub);flex:1 }
[data-slot="ai-jd-info-grid"] { display:grid;grid-template-columns:1fr 1fr;gap:6px }
[data-slot="ai-jd-info-item"] { display:flex;gap:6px;padding:6px 0;font-size:12px }
[data-slot="ai-jd-info-label"] { color:var(--c-sub);min-width:40px;flex-shrink:0 }
[data-slot="ai-jd-info-value"] { color:var(--c-text);font-weight:500 }

/* Focus visible */
input:focus-visible, select:focus-visible { outline:2px solid var(--c-primary);outline-offset:1px }

/* ===== Mobile (≤768px) ===== */
@media (max-width: 768px) {
  [data-slot="ai-jd-header"] { flex-direction: column; align-items: flex-start; gap: 6px; }
  [data-slot="ai-jd-header"] h4 { font-size: 14px; }
  [data-slot="ai-jd-info-grid"] { grid-template-columns: 1fr; }
  [data-slot="ai-jd-skill-row"] { flex-wrap: wrap; gap: 4px; }
  [data-slot="ai-jd-skill-name"] { min-width: auto; }
  [data-slot="ai-input-area"] input,
  [data-slot="ai-input-area"] select { min-width: 100%; }
}
</style>
