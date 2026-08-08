<template>
  <div data-slot="ai-workspace">
    <AiConversation>
      <AiChatMessage role="ai" status="complete">
        <p>根据候选人简历和岗位 JD，AI 自动生成针对性面试问题。选择上下文信息后开始生成。</p>
      </AiChatMessage>
      <AiThinking
        v-if="interviewProcHasRun"
        :steps="interviewProcSteps"
        :active="interviewProcActive"
        title="处理过程"
        done-text="处理完成 · 点击展开"
      />
      <AiChatMessage v-if="interviewLoading" role="ai" status="loading" />
      <AiChatMessage v-if="interviewError && !interviewLoading" role="ai" status="error">
        <template #error>{{ interviewError }}</template>
      </AiChatMessage>
      <template v-if="interviewQuestions.length && !interviewLoading">
        <AiChatMessage role="ai" status="complete">
          <ol data-slot="ai-question-list">
            <li v-for="(q, qi) in interviewQuestions" :key="qi" data-slot="ai-question-item">
              <div data-slot="ai-question-text">{{ q.question }}</div>
              <div data-slot="ai-question-meta">
                <StatusBadge type="progress">{{ q.dimension }}</StatusBadge>
                <button class="btn btn-text btn-sm" @click="toggleHint(qi)" :aria-expanded="expandedHints[qi] ? 'true' : 'false'">
                  {{ expandedHints[qi] ? '收起提示' : '回答提示' }}
                  <svg viewBox="0 0 24 24" style="width:12px;height:12px;stroke:currentColor;fill:none;stroke-width:2;stroke-linecap:round;transition:transform .2s" :style="{ transform: expandedHints[qi] ? 'rotate(180deg)' : '' }"><polyline points="6 9 12 15 18 9"/></svg>
                </button>
              </div>
              <div v-if="expandedHints[qi]" data-slot="ai-question-hint">{{ Array.isArray(q.expected_answer_hints) ? q.expected_answer_hints.join('；') : q.expected_answer_hints }}</div>
            </li>
          </ol>
        </AiChatMessage>
        <AiDisclaimer />
      </template>
    </AiConversation>
    <div data-slot="ai-input-area">
      <div style="display:flex;gap:8px;margin-bottom:8px;flex-wrap:wrap">
        <select v-model="interviewForm.candidateId" style="flex:1;min-width:130px;padding:7px 10px;border:1px solid var(--c-border);border-radius:var(--radius-sm);font-size:13px;font-family:inherit;background:var(--c-card);color:var(--c-body)" aria-label="选择候选人">
          <option value="">选择候选人</option>
          <option v-for="c in candidates" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
        <select v-model="interviewForm.demandId" style="flex:1;min-width:130px;padding:7px 10px;border:1px solid var(--c-border);border-radius:var(--radius-sm);font-size:13px;font-family:inherit;background:var(--c-card);color:var(--c-body)" aria-label="选择岗位">
          <option value="">选择岗位</option>
          <option v-for="d in demands" :key="d.id" :value="d.id">{{ d.name }}</option>
        </select>
        <select v-model="interviewForm.round" style="width:90px;padding:7px 10px;border:1px solid var(--c-border);border-radius:var(--radius-sm);font-size:13px;font-family:inherit;background:var(--c-card);color:var(--c-body)" aria-label="面试轮次">
          <option v-for="rnd in interviewRounds" :key="rnd.value" :value="rnd.value">{{ rnd.label }}</option>
        </select>
      </div>
      <button class="btn btn-primary" style="width:100%" :disabled="!interviewForm.candidateId || !interviewForm.demandId || interviewLoading" @click="generateQuestions">
        <AiSkeleton v-if="interviewLoading" variant="spinner" />
        {{ interviewLoading ? '生成中...' : '生成面试问题' }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, inject } from 'vue';
import AiConversation from '../../components/ai/AiConversation.vue';
import AiThinking from '../../components/ai/AiThinking.vue';
import AiChatMessage from '../../components/ai/AiChatMessage.vue';
import AiSkeleton from '../../components/ai/AiSkeleton.vue';
import AiDisclaimer from '../../components/ai/AiDisclaimer.vue';
import StatusBadge from '../../components/StatusBadge.vue';
import { runInterviewQuestions } from '../../api/ai.js';
import { useProcessingSteps } from '../../composables/useProcessingSteps.js';

const showToast = inject('showToast');
const candidates = inject('aiCandidates');
const demands = inject('aiDemands');

const interviewRounds = [{ value: 'first', label: '初试' }, { value: 'second', label: '复试' }, { value: 'final', label: '终面' }];
const interviewForm = reactive({ candidateId: '', demandId: '', round: 'first' });
const interviewQuestions = ref([]);
const interviewLoading = ref(false);
const interviewError = ref('');
const expandedHints = ref({});

// 分步处理过程（诚实标注为「处理过程」，非真实 AI 思考链）
const {
  steps: interviewProcSteps,
  active: interviewProcActive,
  hasRun: interviewProcHasRun,
  start: interviewProcStart,
  finish: interviewProcFinish,
} = useProcessingSteps(['读取候选人画像', '分析岗位 JD', '生成面试问题', '校验维度覆盖']);

async function generateQuestions() {
  if (!interviewForm.candidateId || !interviewForm.demandId) return;
  interviewError.value = ''; interviewLoading.value = true;
  interviewProcStart();
  try { const r = await runInterviewQuestions({ candidate_id: interviewForm.candidateId, demand_id: interviewForm.demandId, round: interviewForm.round }); interviewQuestions.value = r.questions || []; expandedHints.value = {}; showToast('生成 ' + interviewQuestions.value.length + ' 个问题'); }
  catch (e) { interviewError.value = e.message || '生成失败，请重试'; showToast(interviewError.value); }
  finally { interviewProcFinish(); interviewLoading.value = false; }
}
function toggleHint(i) { expandedHints.value = { ...expandedHints.value, [i]: !expandedHints.value[i] }; }
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

/* ===== Interview ===== */
[data-slot="ai-question-list"] { padding-left:18px }
[data-slot="ai-question-item"] { padding:12px 0;border-bottom:1px solid var(--c-border-light) }
[data-slot="ai-question-item"]:last-child { border-bottom:none }
[data-slot="ai-question-text"] { font-size:14px;color:var(--c-text);font-weight:600;line-height:1.6;margin-bottom:6px }
[data-slot="ai-question-meta"] { display:flex;align-items:center;gap:8px }
[data-slot="ai-question-hint"] { margin-top:8px;padding:10px 12px;background:var(--c-surface-elevated);border-radius:var(--radius-sm);border:1px solid var(--c-border-light);font-size:12px;color:var(--c-body);line-height:1.7 }

/* Focus visible */
input:focus-visible, select:focus-visible { outline:2px solid var(--c-primary);outline-offset:1px }

/* ===== Mobile (≤768px) ===== */
@media (max-width: 768px) {
  [data-slot="ai-question-text"] { font-size: 13px; }
  [data-slot="ai-question-item"] { padding: 10px 0; }
  [data-slot="ai-question-hint"] { font-size: 11px; padding: 8px 10px; }
}
</style>
