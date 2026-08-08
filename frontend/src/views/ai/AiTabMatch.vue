<template>
  <div data-slot="ai-workspace">
    <AiConversation>
      <AiChatMessage role="ai" status="complete">
        <p>选择候选人和岗位，系统会基于简历画像和岗位 JD 进行多维匹配打分，并给出详细理由。</p>
      </AiChatMessage>

      <!-- Thinking panel (思考 + 实时生成过程都在面板内) -->
      <AiThinking
        v-if="matchThinkingUsed"
        :thinking="matchThinkingDisplay"
        :active="matchLoading"
        title="思考过程"
        done-text="思考完成 · 点击展开"
      />

      <AiChatMessage v-if="matchLoading && !matchStreamContent" role="ai" status="loading" />

      <AiChatMessage v-if="matchError && !matchLoading" role="ai" status="error">
        <template #error>{{ matchError }}</template>
      </AiChatMessage>
      <template v-if="matchResult && !matchLoading">
        <AiChatMessage role="ai" status="complete">
          <div data-slot="ai-match-result">
            <div data-slot="ai-match-score-row">
              <div data-slot="ai-match-big-score">
                <span data-slot="ai-match-score-num">{{ matchResult.overall_score }}</span>
                <span data-slot="ai-match-score-label">综合匹配得分</span>
              </div>
              <div data-slot="ai-match-breakdown">
                <div data-slot="ai-match-item"><span data-slot="ai-match-item-label">画像分</span><div class="progress-bar"><div class="progress-fill" :style="{ width: matchResult.profile_score + '%' }"></div></div><span data-slot="ai-match-item-val">{{ matchResult.profile_score }}</span></div>
                <div data-slot="ai-match-item"><span data-slot="ai-match-item-label">匹配分</span><div class="progress-bar"><div class="progress-fill" :style="{ width: matchResult.match_score + '%' }"></div></div><span data-slot="ai-match-item-val">{{ matchResult.match_score }}</span></div>
                <div data-slot="ai-match-item"><span data-slot="ai-match-item-label">综合等级</span><StatusBadge :type="matchResult.grade === 'A' || matchResult.grade === 'S' ? 'done' : matchResult.grade === 'B' ? 'progress' : 'warn'">{{ matchResult.grade }} 级</StatusBadge></div>
              </div>
            </div>
            <div data-slot="ai-jd-section"><div data-slot="ai-jd-section-title">匹配优势</div><ul data-slot="ai-jd-list"><li v-for="(s, i) in matchResult.strengths" :key="'s'+i">{{ s }}</li></ul></div>
            <div v-if="matchResult.missing_skills?.length" data-slot="ai-jd-section"><div data-slot="ai-jd-section-title">待补足技能</div><div data-slot="ai-jd-skill-table"><div v-for="ms in matchResult.missing_skills" :key="ms.skill" data-slot="ai-jd-skill-row"><span data-slot="ai-jd-skill-name">{{ ms.skill }}</span><StatusBadge :type="ms.importance === '加分项' ? 'draft' : 'warn'">{{ ms.importance }}</StatusBadge><span data-slot="ai-jd-skill-desc">{{ ms.note }}</span></div></div></div>
            <div data-slot="ai-jd-section"><div data-slot="ai-jd-section-title">详细理由</div><ul data-slot="ai-jd-list"><li v-for="(r, i) in matchResult.reasons" :key="'mr'+i">{{ r }}</li></ul></div>
          </div>
        </AiChatMessage>
        <AiDisclaimer />
      </template>
    </AiConversation>
    <div data-slot="ai-input-area">
      <div style="display:flex;gap:8px;margin-bottom:8px">
        <select v-model="matchForm.candidateId" style="flex:1;padding:7px 10px;border:1px solid var(--c-border);border-radius:var(--radius-sm);font-size:13px;font-family:inherit;background:var(--c-card);color:var(--c-body)" aria-label="选择候选人">
          <option value="">选择候选人</option>
          <option v-for="c in candidates" :key="c.id" :value="c.id">{{ c.name }} — {{ c.title }}</option>
        </select>
        <select v-model="matchForm.demandId" style="flex:1;padding:7px 10px;border:1px solid var(--c-border);border-radius:var(--radius-sm);font-size:13px;font-family:inherit;background:var(--c-card);color:var(--c-body)" aria-label="选择岗位">
          <option value="">选择岗位</option>
          <option v-for="d in demands" :key="d.id" :value="d.id">{{ d.name }} · {{ d.dept }}</option>
        </select>
      </div>
      <button class="btn btn-primary" style="width:100%" :disabled="!matchForm.candidateId || !matchForm.demandId || matchLoading" @click="runMatch">
        <AiSkeleton v-if="matchLoading" variant="spinner" />
        {{ matchLoading ? '匹配中...' : '开始匹配' }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, inject } from 'vue';
import AiConversation from '../../components/ai/AiConversation.vue';
import AiThinking from '../../components/ai/AiThinking.vue';
import AiChatMessage from '../../components/ai/AiChatMessage.vue';
import AiSkeleton from '../../components/ai/AiSkeleton.vue';
import AiDisclaimer from '../../components/ai/AiDisclaimer.vue';
import StatusBadge from '../../components/StatusBadge.vue';
import { runMatch as apiRunMatch } from '../../api/ai.js';
import { useStreaming } from '../../composables/useStreaming.js';

const showToast = inject('showToast');
const candidates = inject('aiCandidates');
const demands = inject('aiDemands');

// Streaming (SSE) for match — 流式优先，结构化缺失时用阻塞式 API 兜底补齐
const {
  content: matchStreamContent,
  thinking: matchThinking,
  isStreaming: matchStreaming,
  error: matchStreamError,
  result: matchStreamResult,
  start: startMatchStream,
  stop: stopMatchStream,
} = useStreaming();

const matchForm = reactive({ candidateId: '', demandId: '' });
const matchResult = ref(null);
const matchLoading = ref(false);
const matchError = ref('');
const matchThinkingUsed = ref(false);

// 思考面板显示内容：静态思考提示 + 实时生成流（流式原文即生成过程）
const matchThinkingDisplay = computed(() => {
  const parts = [];
  if (matchThinking.value) parts.push(matchThinking.value);
  if (matchStreamContent.value) parts.push(matchStreamContent.value);
  return parts.join('\n');
});

// --- 结构化字段归一化（兼容后端返回字符串数组的旧格式） ---
function _nonEmptyArr(v) { return Array.isArray(v) && v.length > 0; }

function _asStrList(list) {
  if (!_nonEmptyArr(list)) return [];
  return list
    .map((x) => (typeof x === 'string' ? x : (x?.skill || x?.name || '')))
    .filter(Boolean);
}

function _asMissingRows(list) {
  if (!_nonEmptyArr(list)) return [];
  return list
    .map((ms) => (typeof ms === 'string'
      ? { skill: ms, importance: '待补足', note: '' }
      : { skill: ms?.skill || '', importance: ms?.importance || '待补足', note: ms?.note || '' }))
    .filter((ms) => ms.skill);
}

async function runMatch() {
  if (!matchForm.candidateId || !matchForm.demandId) return;
  matchError.value = ''; matchLoading.value = true; matchResult.value = null; matchThinkingUsed.value = true;

  // 1) 流式匹配（useStreaming 内部捕获异常，不会 reject）
  await startMatchStream('match', { candidate_id: matchForm.candidateId, demand_id: matchForm.demandId });
  if (matchStreamError.value) { matchError.value = matchStreamError.value; matchLoading.value = false; return; }

  // 2) 结构化字段缺失（流式解析失败）时，调用阻塞式 API 补齐
  const sr = matchStreamResult.value || {};
  const incomplete = sr.profile_score == null || sr.match_score == null || !sr.grade || !_nonEmptyArr(sr.reasons);
  let structured = sr;
  if (incomplete) {
    try {
      const blocking = await apiRunMatch({ candidate_id: matchForm.candidateId, demand_id: matchForm.demandId });
      structured = {
        profile_score: sr.profile_score ?? blocking.profile_score,
        match_score: sr.match_score ?? blocking.match_score,
        overall_score: sr.overall_score ?? blocking.overall_score,
        grade: sr.grade || blocking.grade,
        reasons: _nonEmptyArr(sr.reasons) ? sr.reasons : blocking.reasons,
        missing_skills: _nonEmptyArr(sr.missing_skills) ? sr.missing_skills : blocking.missing_skills,
        strengths: _nonEmptyArr(sr.strengths) ? sr.strengths : blocking.strengths,
        disclaimer: sr.disclaimer || blocking.disclaimer,
      };
    } catch (_e) { /* 保留流式已拿到的部分 */ }
  }

  // 3) 汇总结果
  const profileScore = structured.profile_score ?? 0;
  const matchScore = structured.match_score ?? 0;
  matchResult.value = {
    overall_score: structured.overall_score ?? Math.round((profileScore + matchScore) / 2),
    profile_score: profileScore,
    match_score: matchScore,
    grade: structured.grade || 'B',
    reasons: _asStrList(structured.reasons),
    missing_skills: _asMissingRows(structured.missing_skills),
    strengths: _asStrList(structured.strengths),
    disclaimer: structured.disclaimer || '此内容由AI生成，请人工审核确认后使用',
  };
  matchLoading.value = false;
  showToast('匹配完成');
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

/* ===== JD Result shared sections (used by match too) ===== */
[data-slot="ai-jd-section"] { margin-bottom:16px }
[data-slot="ai-jd-section-title"] { font-size:12px;font-weight:700;color:var(--c-text);margin-bottom:8px;display:flex;align-items:center;gap:6px }
[data-slot="ai-jd-section-title"]::before { content:'';width:3px;height:12px;background:var(--c-primary);border-radius:2px }
[data-slot="ai-jd-list"] { padding-left:16px;font-size:13px;color:var(--c-body);line-height:2 }
[data-slot="ai-jd-skill-table"] { border:1px solid var(--c-border);border-radius:var(--radius-sm);overflow:hidden }
[data-slot="ai-jd-skill-row"] { display:flex;align-items:center;gap:8px;padding:8px 12px;border-bottom:1px solid var(--c-border-light);font-size:12px }
[data-slot="ai-jd-skill-row"]:last-child { border-bottom:none }
[data-slot="ai-jd-skill-name"] { font-weight:600;color:var(--c-text);min-width:70px }
[data-slot="ai-jd-skill-desc"] { color:var(--c-sub);flex:1 }

/* ===== Match result ===== */
[data-slot="ai-match-result"] { font-size:13px }
[data-slot="ai-match-score-row"] { display:flex;gap:24px;align-items:flex-start;margin-bottom:20px;padding-bottom:16px;border-bottom:1px solid var(--c-border) }
[data-slot="ai-match-big-score"] { text-align:center;min-width:100px }
[data-slot="ai-match-score-num"] { display:block;font-size:42px;font-weight:900;color:var(--c-primary);font-variant-numeric:tabular-nums;line-height:1 }
[data-slot="ai-match-score-label"] { font-size:11px;color:var(--c-sub);margin-top:2px;display:block }
[data-slot="ai-match-breakdown"] { flex:1 }
[data-slot="ai-match-item"] { display:flex;align-items:center;gap:10px;margin-bottom:8px;font-size:12px }
[data-slot="ai-match-item-label"] { color:var(--c-sub);min-width:48px }
[data-slot="ai-match-item-val"] { font-weight:700;color:var(--c-text);font-variant-numeric:tabular-nums;min-width:24px }

.progress-bar { flex:1;height:8px;background:var(--c-border-light);border-radius:4px;overflow:hidden }
.progress-fill { height:100%;background:var(--c-primary);border-radius:4px;transition:width .6s ease }

@media (prefers-reduced-motion: reduce) {
  .progress-fill { transition: none; }
}

/* Focus visible */
input:focus-visible, select:focus-visible { outline:2px solid var(--c-primary);outline-offset:1px }

/* ===== Mobile (≤768px) ===== */
@media (max-width: 768px) {
  [data-slot="ai-match-score-row"] {
    flex-direction: column;
    gap: 16px;
    align-items: center;
  }
  [data-slot="ai-match-big-score"] {
    min-width: auto;
  }
  [data-slot="ai-match-score-num"] {
    font-size: 36px;
  }
  [data-slot="ai-match-breakdown"] {
    width: 100%;
  }
}
</style>
