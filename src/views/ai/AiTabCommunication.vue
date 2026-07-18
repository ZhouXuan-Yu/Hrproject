<template>
  <div data-slot="ai-workspace">
    <div data-slot="ai-conversation">
      <AiChatMessage role="ai" status="complete">
        <p>根据候选人信息和沟通场景，AI 生成沟通话术草稿和建议。所有联系动作必须由 HR 人工确认后执行。</p>
      </AiChatMessage>
      <AiChatMessage v-if="chatLoading" role="ai" status="loading" />
      <AiChatMessage v-if="chatError && !chatLoading" role="ai" status="error">
        <template #error>{{ chatError }}</template>
      </AiChatMessage>
      <template v-if="chatResult && !chatLoading">
        <AiChatMessage role="ai" status="complete">
          <div data-slot="ai-jd-section"><div data-slot="ai-jd-section-title">话术草稿</div><div data-slot="ai-draft-text">{{ chatResult.draft }}</div></div>
          <div v-if="chatResult.suggestions?.length" data-slot="ai-jd-section"><div data-slot="ai-jd-section-title">沟通建议</div><ul data-slot="ai-jd-list"><li v-for="(s, si) in chatResult.suggestions" :key="'sg'+si">{{ s }}</li></ul></div>
        </AiChatMessage>
        <AiDisclaimer />
      </template>
    </div>
    <div data-slot="ai-input-area">
      <div style="display:flex;gap:8px;margin-bottom:8px;flex-wrap:wrap">
        <select v-model="chatForm.candidateId" style="flex:1;min-width:130px;padding:7px 10px;border:1px solid var(--c-border);border-radius:var(--radius-sm);font-size:13px;font-family:inherit;background:var(--c-card);color:var(--c-body)" aria-label="选择候选人">
          <option value="">选择候选人</option>
          <option v-for="c in candidates" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
        <select v-model="chatForm.channel" style="flex:1;min-width:100px;padding:7px 10px;border:1px solid var(--c-border);border-radius:var(--radius-sm);font-size:13px;font-family:inherit;background:var(--c-card);color:var(--c-body)" aria-label="沟通渠道">
          <option value="">选择渠道</option>
          <option value="phone">电话</option><option value="email">邮件</option><option value="feishu">飞书</option>
        </select>
        <select v-model="chatForm.purpose" style="flex:1;min-width:120px;padding:7px 10px;border:1px solid var(--c-border);border-radius:var(--radius-sm);font-size:13px;font-family:inherit;background:var(--c-card);color:var(--c-body)" aria-label="沟通目的">
          <option value="">选择目的</option>
          <option v-for="p in purposes" :key="p.value" :value="p.value">{{ p.label }}</option>
        </select>
      </div>
      <button class="btn btn-primary" style="width:100%" :disabled="!chatForm.candidateId || !chatForm.channel || !chatForm.purpose || chatLoading" @click="generateDraft">
        <AiSkeleton v-if="chatLoading" variant="spinner" />
        {{ chatLoading ? '生成中...' : '生成沟通话术' }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, inject } from 'vue';
import AiChatMessage from '../../components/ai/AiChatMessage.vue';
import AiSkeleton from '../../components/ai/AiSkeleton.vue';
import AiDisclaimer from '../../components/ai/AiDisclaimer.vue';
import { runCommunicationDraft } from '../../api/ai.js';

const showToast = inject('showToast');
const candidates = inject('aiCandidates');

const purposes = [
  { value: 'first_contact', label: '初次联系' }, { value: 'interview_invite', label: '面试邀请' },
  { value: 'offer_notice', label: 'Offer通知' }, { value: 'follow_up', label: '跟进' },
];
const chatForm = reactive({ candidateId: '', channel: '', purpose: '', context: '' });
const chatResult = ref(null);
const chatLoading = ref(false);
const chatError = ref('');

async function generateDraft() {
  if (!chatForm.candidateId || !chatForm.channel || !chatForm.purpose) return;
  chatError.value = ''; chatLoading.value = true;
  try { const c = candidates.value.find(x => x.id === chatForm.candidateId); chatResult.value = await runCommunicationDraft({ candidate_name: c?.name || '', channel: chatForm.channel, purpose: chatForm.purpose, context: chatForm.context }); showToast('话术生成完成'); }
  catch (e) { chatError.value = e.message || '话术生成失败，请重试'; showToast(chatError.value); }
  finally { chatLoading.value = false; }
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

/* ===== Communication ===== */
[data-slot="ai-draft-text"] { font-size:13px;color:var(--c-body);background:var(--c-surface-elevated);padding:14px;border-radius:var(--radius-sm);border:1px solid var(--c-border-light);white-space:pre-wrap;line-height:1.8 }

/* Focus visible */
input:focus-visible, select:focus-visible { outline:2px solid var(--c-primary);outline-offset:1px }

/* ===== Mobile (≤768px) ===== */
@media (max-width: 768px) {
  [data-slot="ai-draft-text"] {
    font-size: 12px;
    padding: 10px;
  }
}
</style>
