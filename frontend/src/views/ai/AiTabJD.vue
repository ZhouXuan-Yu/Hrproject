<template>
  <div data-slot="ai-workspace">
    <!-- Conversation area (auto-follow scroll + back-to-bottom) -->
    <AiConversation>
      <!-- AI intro -->
      <AiChatMessage role="ai" status="complete">
        <p>输入岗位信息，我帮你生成结构化的 JD 草稿，包括岗位职责、必备技能、加分项和任职资格。所有内容需人工审核确认后使用。</p>
      </AiChatMessage>

      <!-- Thinking panel (思考 + 实时生成过程都在面板内) -->
      <AiThinking
        v-if="jdThinkingUsed"
        :thinking="jdThinkingDisplay"
        :active="jdLoading"
        title="思考过程"
        done-text="思考完成 · 点击展开"
      />

      <!-- Loading（流式内容开始前） -->
      <AiChatMessage v-if="jdLoading && !jdStreamContent" role="ai" status="loading" />

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
            <div v-if="jdResult.jd_text" data-slot="ai-jd-section">
              <div data-slot="ai-jd-section-title">JD 全文</div>
              <div data-slot="ai-jd-fulltext">
                <AiMarkdown :content="jdResult.jd_text" />
              </div>
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
    </AiConversation>

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
      <div style="display:flex;gap:8px;margin-bottom:8px;flex-wrap:wrap">
        <input v-model="jdForm.salary" placeholder="薪资范围 (如 15K-25K)" style="flex:1;min-width:120px;padding:7px 10px;border:1px solid var(--c-border);border-radius:var(--radius-sm);font-size:13px;font-family:inherit;background:var(--c-card);color:var(--c-text)">
        <input v-model="jdForm.workCity" placeholder="工作城市" style="flex:1;min-width:100px;padding:7px 10px;border:1px solid var(--c-border);border-radius:var(--radius-sm);font-size:13px;font-family:inherit;background:var(--c-card);color:var(--c-text)">
        <select v-model="jdForm.eduMin" style="width:90px;padding:7px 10px;border:1px solid var(--c-border);border-radius:var(--radius-sm);font-size:13px;font-family:inherit;background:var(--c-card)">
          <option value="大专">大专</option><option value="本科">本科</option><option value="硕士">硕士</option><option value="不限">不限</option>
        </select>
        <select v-model="jdForm.expMin" style="width:100px;padding:7px 10px;border:1px solid var(--c-border);border-radius:var(--radius-sm);font-size:13px;font-family:inherit;background:var(--c-card)">
          <option value="应届">应届</option><option value="1年">1年</option><option value="3年">3年</option><option value="5年">5年</option><option value="不限">不限</option>
        </select>
        <select v-model="jdForm.headcount" style="width:70px;padding:7px 10px;border:1px solid var(--c-border);border-radius:var(--radius-sm);font-size:13px;font-family:inherit;background:var(--c-card)">
          <option :value="1">1人</option><option :value="2">2人</option><option :value="3">3人</option><option :value="5">5人</option>
        </select>
      </div>
      <AiPromptInput
        v-model="jdForm.requirements"
        :status="jdStatus"
        :disabled="!jdForm.position"
        :require-value="false"
        placeholder="补充要求 (选填)，例如：大厂背景、熟悉微服务架构..."
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
import AiConversation from '../../components/ai/AiConversation.vue';
import AiThinking from '../../components/ai/AiThinking.vue';
import AiChatMessage from '../../components/ai/AiChatMessage.vue';
import AiPromptInput from '../../components/ai/AiPromptInput.vue';
import AiMarkdown from '../../components/ai/AiMarkdown.vue';
import AiDisclaimer from '../../components/ai/AiDisclaimer.vue';
import StatusBadge from '../../components/StatusBadge.vue';
import { runJdGenerate } from '../../api/ai.js';
import { useStreaming } from '../../composables/useStreaming.js';
import { fetchDepartments } from '../../api/config.js';

const showToast = inject('showToast');

const levels = ['初级', '中级', '高级', '资深', '专家'];
const qualLabels = { education: '学历', experience: '经验', industry: '行业', soft: '软技能', others: '其他' };
const departments = ref([]);

async function loadDepartments() {
  try {
    const list = await fetchDepartments();
    departments.value = (list || []).map(d => d.deptName || d.name || d);
  } catch (_) {
    departments.value = [];
  }
}

const jdForm = reactive({
  position: '', department: '', level: '中级', requirements: '',
  salary: '', workCity: '', eduMin: '本科', expMin: '3年', headcount: 1,
});
const jdResult = ref(null);
const jdLoading = ref(false);
const jdError = ref('');
const jdThinkingUsed = ref(false);
const jdStatus = computed(() => jdLoading.value ? 'submitted' : (jdError.value ? 'error' : 'ready'));

// 思考面板显示内容：静态思考提示 + 实时生成流（流式原文即生成过程）
const jdThinkingDisplay = computed(() => {
  const parts = [];
  if (jdThinking.value) parts.push(jdThinking.value);
  if (jdStreamContent.value) parts.push(jdStreamContent.value);
  return parts.join('\n');
});

// Streaming (SSE) — 流式优先，结构化缺失时用阻塞式 API 兜底补齐
const {
  content: jdStreamContent,
  thinking: jdThinking,
  isStreaming: jdStreaming,
  error: jdStreamError,
  result: jdStreamResult,
  start: startJdStream,
  stop: stopJdStream,
} = useStreaming();

// --- 结构化字段归一化（兼容后端返回字符串数组的旧格式） ---
function _nonEmptyArr(v) { return Array.isArray(v) && v.length > 0; }

function _asStrList(list) {
  if (!_nonEmptyArr(list)) return [];
  return list
    .map((x) => (typeof x === 'string' ? x : (x?.name || x?.skill || '')))
    .filter(Boolean);
}

function _asSkillRows(list, defaultWeight) {
  if (!_nonEmptyArr(list)) return [];
  return list
    .map((s) => (typeof s === 'string'
      ? { name: s, weight: defaultWeight, description: '' }
      : { name: s?.name || '', weight: s?.weight || defaultWeight, description: s?.description || '' }))
    .filter((s) => s.name);
}

function _asPlusRows(list) {
  if (!_nonEmptyArr(list)) return [];
  return list
    .map((s) => (typeof s === 'string'
      ? { name: s, description: '' }
      : { name: s?.name || '', description: s?.description || '' }))
    .filter((s) => s.name);
}

function _asQual(q) {
  if (!q || typeof q !== 'object') return {};
  const out = {};
  for (const [k, v] of Object.entries(q)) {
    const val = Array.isArray(v) ? v.join('；') : String(v ?? '');
    if (val) out[k] = val;
  }
  return out;
}

function _hasQual(q) { return q && typeof q === 'object' && Object.keys(q).length > 0; }

async function generateJd() {
  if (!jdForm.position) return;
  jdError.value = ''; jdLoading.value = true; jdResult.value = null; jdThinkingUsed.value = true;

  // 1) 流式生成（useStreaming 内部捕获异常，不会 reject）
  await startJdStream('jd-generate', { ...jdForm });
  if (jdStreamError.value) { jdError.value = jdStreamError.value; jdLoading.value = false; return; }

  // 2) 结构化字段缺失（流式解析失败）时，调用阻塞式 API 补齐；
  //    流式的 Markdown 正文仍保留展示
  const sr = jdStreamResult.value || {};
  const incomplete = !_nonEmptyArr(sr.responsibilities) || !_nonEmptyArr(sr.required_skills);
  let structured = sr;
  if (incomplete) {
    try {
      const blocking = await runJdGenerate({ ...jdForm });
      structured = {
        responsibilities: _nonEmptyArr(sr.responsibilities) ? sr.responsibilities : blocking.responsibilities,
        required_skills: _nonEmptyArr(sr.required_skills) ? sr.required_skills : blocking.required_skills,
        plus_skills: _nonEmptyArr(sr.plus_skills) ? sr.plus_skills : blocking.plus_skills,
        qualifications: _hasQual(sr.qualifications) ? sr.qualifications : blocking.qualifications,
        jd_text: sr.jd_text || blocking.jd_text,
        disclaimer: sr.disclaimer || blocking.disclaimer,
      };
    } catch (_e) { /* 保留流式已拿到的部分 */ }
  }

  // 3) 汇总结果
  let jdText = jdStreamContent.value || structured.jd_text || '';
  // 流式正文若是纯 JSON（新提示词的标准输出），展示结构化 jd_text 字段
  if (jdText.trim().startsWith('{')) jdText = structured.jd_text || '';

  jdResult.value = {
    jd_text: jdText,
    position: jdForm.position,
    department: jdForm.department,
    responsibilities: _asStrList(structured.responsibilities),
    required_skills: _asSkillRows(structured.required_skills, '必须'),
    plus_skills: _asPlusRows(structured.plus_skills),
    qualifications: _asQual(structured.qualifications),
    disclaimer: structured.disclaimer || '此内容由AI生成，请人工审核确认后使用',
  };
  jdLoading.value = false;
  showToast('JD 草稿生成完成');
}

// Watch: clear error on input change
watch(() => jdForm.requirements, () => { if (jdError.value) jdError.value = ''; });
loadDepartments();
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
[data-slot="ai-jd-fulltext"] { padding:12px 14px;background:var(--c-surface-elevated);border:1px solid var(--c-border-light);border-radius:var(--radius-sm);max-height:280px;overflow-y:auto }
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
  [data-slot="ai-jd-fulltext"] { max-height: 200px; padding: 10px 12px; }
  [data-slot="ai-input-area"] input,
  [data-slot="ai-input-area"] select { min-width: 100%; }
}
</style>
