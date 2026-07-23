<template>
  <Teleport to="body">
    <div v-if="visible" class="modal-overlay" @click.self="handleClose" @keydown.escape="handleClose">
      <div class="modal-box comm-modal" role="dialog" aria-modal="true" aria-label="联系候选人">
        <!-- Header -->
        <div class="comm-header">
          <div class="comm-header-left">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
            </svg>
            <h3>联系候选人</h3>
          </div>
          <button class="drawer-close" @click="handleClose" aria-label="关闭">✕</button>
        </div>

        <!-- Candidate info -->
        <div class="comm-meta">
          <div class="meta-item"><span class="meta-label">候选人</span><span class="meta-value">{{ candidate?.name || '—' }}</span></div>
          <div class="meta-item"><span class="meta-label">应聘岗位</span><span class="meta-value">{{ demand?.position || '—' }}</span></div>
        </div>

        <!-- Body -->
        <div class="comm-body">
          <!-- Channel selector -->
          <div class="form-section">
            <h4 class="section-title">选择联系方式</h4>
            <div class="channel-cards">
              <label
                v-for="ch in channelOptions"
                :key="ch.id"
                class="channel-card"
                :class="{ active: channel === ch.id }"
              >
                <input type="radio" :value="ch.id" v-model="channel" class="sr-only" />
                <span class="channel-icon" v-html="ch.icon"></span>
                <span class="channel-label">{{ ch.label }}</span>
              </label>
            </div>
            <div class="contact-channel-detail">
              <div v-if="channel === 'phone'">
                <span class="contact-label">电话号码</span>
                <b>{{ contactInfo.mobile || '未填写' }}</b>
              </div>
              <div v-else-if="channel === 'email'">
                <span class="contact-label">邮箱</span>
                <b>{{ contactInfo.email || '未填写' }}</b>
                <button class="btn btn-outline btn-sm" :disabled="!contactInfo.email || draftLoading || sending" @click="sendCurrentChannel">
                  {{ sending && channel === 'email' ? '发送中...' : (sent.email ? '已发送' : '发送') }}
                </button>
              </div>
              <div v-else>
                <span class="contact-label">飞书</span>
                <b>{{ contactInfo.feishu || contactInfo.mobile || '未绑定飞书' }}</b>
                <button class="btn btn-outline btn-sm" :disabled="draftLoading || sending" @click="sendCurrentChannel">
                  {{ sending && channel === 'feishu' ? '发送中...' : (sent.feishu ? '已发送' : '发送') }}
                </button>
              </div>
            </div>
          </div>

          <!-- Context -->
          <div class="form-section">
            <h4 class="section-title">沟通目的</h4>
            <select v-model="purpose" class="form-select full-width">
              <option value="initial">初次联系</option>
              <option value="followup">跟进面试</option>
              <option value="offer">Offer沟通</option>
              <option value="info">收集补充信息</option>
              <option value="other">其他</option>
            </select>
          </div>

          <!-- AI draft area -->
          <div class="form-section">
            <h4 class="section-title">
              沟通话术草稿
              <span class="ai-badge">AI 辅助生成</span>
              <button class="btn-text" @click="generateDraft" :disabled="draftLoading">
                {{ draftLoading ? '生成中...' : '重新生成' }}
              </button>
            </h4>
            <div v-if="draftError" class="draft-error">{{ draftError }}</div>
            <div v-if="draftLoading" class="draft-loading">
              <span class="skeleton-line" style="width:80%"></span>
              <span class="skeleton-line" style="width:95%"></span>
              <span class="skeleton-line" style="width:60%"></span>
              <span class="skeleton-line" style="width:72%"></span>
            </div>
            <textarea
              v-else
              v-model="draftText"
              class="form-textarea"
              rows="8"
              placeholder="AI 将根据候选人信息和沟通目的生成联系话术草稿..."
            ></textarea>
          </div>

          <!-- Disclaimer -->
          <div class="ai-disclaimer">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
            此内容由AI生成，请人工审核确认后使用。实际联系需通过电话/邮件/飞书由HR手动完成。
          </div>
        </div>

        <!-- Footer -->
        <div class="comm-actions">
          <button class="btn btn-ghost" @click="handleClose">取消</button>
          <button class="btn btn-outline" @click="copyDraft" :disabled="!draftText">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
            {{ copied ? '已复制' : '复制话术' }}
          </button>
          <button class="btn btn-primary" @click="confirmContact">
            记录联系
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, computed, watch } from 'vue';
import { runCommunicationDraft } from '../api/ai.js';
import { fetchCandidateContact, sendTalentContact } from '../api/talent.js';
import { useToast } from '../composables/useToast.js';

const props = defineProps({
  visible: { type: Boolean, default: false },
  candidate: { type: Object, default: () => ({ name: '', id: '' }) },
  demand: { type: Object, default: () => ({ position: '', id: '' }) },
});

const emit = defineEmits(['close', 'success']);

const channel = ref('phone');
const purpose = ref('initial');
const draftText = ref('');
const draftLoading = ref(false);
const draftError = ref('');
const copied = ref(false);
const contactInfo = ref({ mobile: '', email: '', feishu: '' });
const sent = ref({ email: false, feishu: false });
const sending = ref(false);
let draftSeq = 0;
const { toast } = useToast();

const channelOptions = [
  { id: 'phone', label: '电话', icon: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"/></svg>' },
  { id: 'email', label: '邮件', icon: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/><polyline points="22,6 12,13 2,6"/></svg>' },
  { id: 'feishu', label: '飞书', icon: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>' },
];

const purposeLabels = { initial: '初次联系', followup: '跟进面试', offer: 'Offer沟通', info: '收集补充信息', other: '其他' };
const channelLabels = { phone: '电话', email: '邮件', feishu: '飞书' };

async function generateDraft() {
  const seq = ++draftSeq;
  draftLoading.value = true;
  draftError.value = '';
  try {
    const result = await runCommunicationDraft({
      candidate_name: props.candidate?.name || '候选人',
      position: props.demand?.position || '招聘岗位',
      channel: channelLabels[channel.value] || '电话',
      purpose: purposeLabels[purpose.value] || '初次联系',
    });
    // Discard result if modal was closed/reopened since this request began
    if (seq !== draftSeq) return;
    // Extract draft from API response (handle various shapes)
    const text =
      result?.draft ||
      result?.content ||
      result?.data?.draft ||
      result?.data?.content ||
      (Array.isArray(result?.drafts) ? result.drafts.join('\n\n') : null) ||
      '暂未生成话术，请手动填写。';
    draftText.value = typeof text === 'string' ? text : JSON.stringify(text, null, 2);
  } catch (e) {
    if (seq !== draftSeq) return;
    console.warn('[CommunicationModal] draft generation failed:', e);
    draftError.value = '生成失败：' + (e.message || '网络错误');
    // Use fallback placeholder text
    if (!draftText.value) {
      draftText.value = `【${channelLabels[channel.value]}沟通草稿】\n\n${props.candidate?.name || '候选人'} 您好，\n\n关于「${props.demand?.position || '招聘岗位'}」岗位的${purposeLabels[purpose.value] || '沟通'}：\n\n[请根据实际情况补充具体内容]\n\n如有任何问题，欢迎随时联系。`;
    }
  } finally {
    if (seq === draftSeq) draftLoading.value = false;
  }
}

async function loadContactInfo() {
  const id = props.candidate?.id || '';
  contactInfo.value = { mobile: '', email: '', feishu: '' };
  if (!id) return;
  try {
    const info = await fetchCandidateContact(id);
    contactInfo.value = {
      mobile: info?.mobile || '',
      email: info?.email || '',
      feishu: info?.feishu || '',
    };
  } catch (e) {
    console.warn('[CommunicationModal] contact info failed:', e);
  }
}

async function sendCurrentChannel() {
  if (!draftText.value) await generateDraft();
  if (!props.candidate?.id) {
    toast.error('候选人未关联真实档案，无法发送');
    return;
  }
  sending.value = true;
  try {
    const result = await sendTalentContact({
      candidateId: props.candidate.id,
      channel: channel.value,
      method: channelLabels[channel.value] || channel.value,
      draft: draftText.value,
      subject: `${props.demand?.position || '招聘岗位'} 沟通`,
      position: props.demand?.position || '',
      demandId: props.demand?.id || '',
      feishu: contactInfo.value.feishu || '',
    });
    const ok = !!(result?.sent || result?.send?.sent);
    const message = result?.send?.message || result?.message || '';
    if (channel.value === 'email') sent.value.email = ok;
    if (channel.value === 'feishu') sent.value.feishu = ok;
    if (ok) {
      toast.success((channelLabels[channel.value] || '消息') + '已真实发送');
    } else {
      toast.error('发送失败：' + (message || '请检查邮箱/飞书配置'));
    }
  } catch (e) {
    toast.error('发送失败：' + (e.message || '未知错误'));
  } finally {
    sending.value = false;
  }
}
async function copyDraft() {
  if (!draftText.value) return;
  try {
    await navigator.clipboard.writeText(draftText.value);
    copied.value = true;
    setTimeout(() => { copied.value = false; }, 2000);
  } catch (e) {
    // Fallback: select text and tell user to copy manually
    const ta = document.querySelector('.comm-modal .form-textarea');
    if (ta) { ta.select(); ta.setSelectionRange(0, 99999); }
    toast.warning('自动复制失败，请按 Ctrl+C 手动复制话术');
  }
}

function confirmContact() {
  emit('success', {
    candidate: props.candidate?.name || '',
    channel: channelLabels[channel.value],
    purpose: purposeLabels[purpose.value],
    draft: draftText.value,
    timestamp: new Date().toISOString(),
  });
}

function handleClose() {
  emit('close');
}

function resetForm() {
  channel.value = 'phone';
  purpose.value = 'initial';
  draftText.value = '';
  draftError.value = '';
  copied.value = false;
  sent.value = { email: false, feishu: false };
  sending.value = false;
}

watch(
  () => props.visible,
  async (v) => {
    if (v) {
      resetForm();
      await loadContactInfo();
      // Auto-generate draft on open
      await generateDraft();
    }
  }
);
</script>

<style scoped>
/* ===== Overlay ===== */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.38);
  backdrop-filter: blur(2px);
  z-index: 300;
  display: flex;
  align-items: center;
  justify-content: center;
  animation: fadeIn 0.18s ease;
}

/* ===== Modal Box ===== */
.comm-modal {
  width: 560px;
  max-width: 92vw;
  max-height: 85vh;
  display: flex;
  flex-direction: column;
  border-radius: 10px;
  border: 1px solid var(--e-border, #E1E6EF);
  background: var(--e-surface, #FFFFFF);
  box-shadow: var(--e-shadow-modal, 0 24px 80px rgba(15,23,42,0.2), 0 0 0 1px rgba(15,23,42,0.08));
  animation: slideUp 0.22s cubic-bezier(0.16, 1, 0.3, 1);
}

/* ===== Header ===== */
.comm-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-bottom: 1px solid var(--e-border-soft, #EFF3F8);
  flex-shrink: 0;
}

.comm-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--e-primary, #4F6EF7);
}

.comm-header-left h3 {
  font-size: 16px;
  font-weight: 700;
  color: var(--e-text, #172033);
  margin: 0;
}

.drawer-close {
  width: 32px; height: 32px;
  border-radius: 50%;
  border: none;
  background: var(--e-surface-soft, #F2F5FA);
  color: var(--e-muted, #5B6475);
  cursor: pointer;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
  flex-shrink: 0;
}
.drawer-close:hover { background: var(--e-border, #E1E6EF); color: var(--e-text, #172033); }

/* ===== Meta bar ===== */
.comm-meta {
  display: flex; gap: 24px;
  padding: 14px 24px;
  background: var(--e-surface-soft, #F9FAFC);
  border-bottom: 1px solid var(--e-border-soft, #EFF3F8);
  flex-shrink: 0;
}

.meta-item { display: flex; align-items: center; gap: 6px; font-size: 13px; }
.meta-label { color: var(--e-muted, #5B6475); font-weight: 500; }
.meta-value { color: var(--e-text, #172033); font-weight: 600; }

/* ===== Body ===== */
.comm-body {
  flex: 1; overflow-y: auto; padding: 16px 24px 12px;
}
.comm-body::-webkit-scrollbar { width: 4px; }
.comm-body::-webkit-scrollbar-thumb { background: #d1d5db; border-radius: 2px; }

.form-section { margin-bottom: 16px; }

.section-title {
  font-size: 13px; font-weight: 700;
  color: var(--e-text, #172033);
  margin: 0 0 10px;
  display: flex; align-items: center; gap: 8px;
}

.ai-badge {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 10px;
  font-size: 10px;
  font-weight: 700;
  color: var(--e-primary, #4F6EF7);
  background: var(--e-primary-subtle, rgba(79, 110, 247, 0.1));
  line-height: 1.6;
}

.btn-text {
  background: none; border: none;
  color: var(--e-primary, #4F6EF7);
  font-size: 12px; font-weight: 600;
  cursor: pointer;
  margin-left: auto;
  padding: 2px 6px;
  border-radius: 4px;
}
.btn-text:hover { background: var(--e-primary-subtle, rgba(79, 110, 247, 0.08)); }
.btn-text:disabled { opacity: 0.5; cursor: not-allowed; }

/* ===== Channel cards ===== */
.channel-cards { display: flex; gap: 10px; }
.channel-card {
  flex: 1;
  display: flex; flex-direction: column; align-items: center; gap: 6px;
  padding: 14px 8px;
  border: 2px solid var(--e-border, #E1E6EF);
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}
.channel-card:hover { border-color: var(--e-primary, #4F6EF7); background: var(--e-primary-subtle, rgba(79, 110, 247, 0.04)); }
.channel-card.active {
  border-color: var(--e-primary, #4F6EF7);
  background: var(--e-primary-subtle, rgba(79, 110, 247, 0.1));
}
.channel-icon { color: var(--e-muted, #5B6475); transition: color 0.15s; }
.channel-card.active .channel-icon { color: var(--e-primary, #4F6EF7); }
.channel-label { font-size: 13px; font-weight: 600; color: var(--e-text, #172033); }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0,0,0,0); }
.contact-channel-detail {
  margin-top: 10px; padding: 10px 12px; border: 1px solid var(--e-border-soft, #EFF3F8);
  border-radius: 8px; background: var(--e-surface-soft, #F9FAFC); font-size: 13px;
}
.contact-channel-detail > div { display: flex; align-items: center; gap: 10px; }
.contact-label { color: var(--e-muted, #5B6475); font-weight: 600; }
.contact-channel-detail b { color: var(--e-text, #172033); margin-right: auto; }

/* ===== Form elements ===== */
.form-select { height: 36px; padding: 0 10px; border: 1px solid var(--e-border, #E1E6EF); border-radius: 6px; font-size: 13px; font-family: inherit; background: var(--e-surface, #fff); color: var(--e-text, #172033); }
.form-select:focus-visible { border-color: var(--e-primary, #4F6EF7); outline: none; box-shadow: 0 0 0 3px rgba(79,110,247,0.22); }
.full-width { width: 100%; }

.form-textarea {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--e-border, #E1E6EF);
  border-radius: 6px;
  font-size: 13px;
  font-family: inherit;
  line-height: 1.7;
  background: var(--e-surface, #fff);
  color: var(--e-text, #172033);
  resize: vertical;
  box-sizing: border-box;
}
.form-textarea:focus-visible { border-color: var(--e-primary, #4F6EF7); outline: none; box-shadow: 0 0 0 3px rgba(79,110,247,0.22); }

/* ===== AI disclaimer ===== */
.ai-disclaimer {
  display: flex; align-items: flex-start; gap: 6px;
  padding: 10px 14px;
  background: #FFF9E6;
  border: 1px solid #FDE68A;
  border-radius: 6px;
  font-size: 12px;
  color: #92400E;
  line-height: 1.6;
  margin-top: 2px;
}
.ai-disclaimer svg { flex-shrink: 0; margin-top: 1px; color: #D97706; }

/* ===== Skeleton loading ===== */
.draft-loading { display: flex; flex-direction: column; gap: 8px; padding: 12px; }
.skeleton-line { height: 14px; border-radius: 4px; background: linear-gradient(90deg, #E1E6EF 25%, #F0F2F5 50%, #E1E6EF 75%); background-size: 200% 100%; animation: shimmer 1.5s infinite; }
@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }
.draft-error { color: var(--e-reject, #EF4444); font-size: 12px; margin-bottom: 8px; padding: 8px 12px; background: #FEF2F2; border-radius: 6px; }

/* ===== Actions footer ===== */
.comm-actions {
  display: flex; gap: 8px; justify-content: flex-end;
  padding: 12px 24px;
  border-top: 1px solid var(--e-border-soft, #EFF3F8);
  background: var(--e-surface, #FFFFFF);
  flex-shrink: 0;
}

/* ===== Button overrides ===== */
.btn {
  display: inline-flex; align-items: center; gap: 5px;
  padding: 7px 16px; border-radius: 6px;
  font-size: 13px; font-weight: 600;
  cursor: pointer; border: none;
  text-decoration: none;
  transition: background 0.15s, transform 0.1s, box-shadow 0.15s;
  white-space: nowrap; line-height: 1.4;
}
.btn:active { transform: scale(0.98); }
.btn-primary { background: var(--e-primary, #4F6EF7); color: #fff; }
.btn-primary:hover:not(:disabled) { background: var(--e-primary-hover, #6B84FF); }
.btn-ghost { background: var(--e-surface-soft, #F2F5FA); border: 1px solid var(--e-border, #E1E6EF); color: var(--e-muted, #5B6475); }
.btn-ghost:hover { background: var(--e-border-soft, #EFF3F8); color: var(--e-text, #172033); }
.btn-outline { background: transparent; border: 1px solid var(--e-border, #E1E6EF); color: var(--e-text, #172033); }
.btn-outline:hover { background: var(--e-surface-soft, #F2F5FA); border-color: var(--e-primary, #4F6EF7); }
.btn:disabled { opacity: 0.45; pointer-events: none; cursor: not-allowed; }
.btn-sm { padding: 4px 10px; font-size: 11px; }

/* ===== Animations ===== */
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
@keyframes slideUp { from { opacity: 0; transform: translateY(12px) scale(0.98); } to { opacity: 1; transform: translateY(0) scale(1); } }

@media (prefers-reduced-motion: reduce) {
  .modal-overlay, .comm-modal, .btn, .drawer-close, .channel-card { transition: none; animation: none; }
  .comm-modal { opacity: 1; transform: none; }
}
</style>
