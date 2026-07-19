<template>
  <Teleport to="body">
    <div v-if="visible" class="modal-overlay" @click.self="close">
      <div class="modal-box contact-modal" role="dialog" aria-modal="true" aria-label="联系候选人">
        <div class="ct-header">
          <div class="ct-header-left">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="var(--c-primary)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"/></svg>
            <h3>联系<template v-if="isBatch"> · 共 {{ selected.length }} 人</template></h3>
          </div>
          <button class="drawer-close" @click="close" aria-label="关闭">✕</button>
        </div>

        <div class="ct-body" v-if="!isBatch">
          <div class="ct-info">
            <span class="ct-label">候选人</span><span class="ct-value">{{ candidateName }}</span>
          </div>
          <div style="margin-top:16px">
            <div style="font-size:13px;font-weight:700;color:var(--c-text);margin-bottom:8px">选择联系渠道</div>
            <div style="display:flex;gap:10px;flex-wrap:wrap">
              <button v-for="ch in channels" :key="ch.key"
                class="ct-channel-btn" :class="{ active: selectedChannel === ch.key }"
                @click="selectedChannel = ch.key"
              >
                <span v-html="ch.icon"></span>
                {{ ch.label }}
              </button>
            </div>
          </div>
        </div>

        <div class="ct-body" v-else>
          <div style="font-size:13px;color:var(--c-body);margin-bottom:8px">将批量联系以下候选人：</div>
          <div v-for="n in selected" :key="n" style="padding:4px 0;font-size:13px;color:var(--c-text)">{{ n }}</div>
          <div style="margin-top:12px;font-size:12px;color:var(--c-sub)">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="vertical-align:-1px"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
            系统仅辅助记录联系动作，实际沟通需通过电话/邮件/飞书完成。
          </div>
        </div>

        <div class="ct-actions">
          <span v-if="isSubmitting" style="font-size:12px;color:var(--c-sub)">提交中...</span>
          <button class="btn btn-ghost btn-sm" @click="close">取消</button>
          <button class="btn btn-primary btn-sm" :disabled="isSubmitting" @click="submit">
            {{ isBatch ? '记录批量联系' : '发起联系并记录' }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, computed } from 'vue';
import { recordTalentContact, batchContactCandidates } from '../api/talent.js';

const props = defineProps({
  visible: { type: Boolean, default: false },
  candidateName: { type: String, default: '' },
  candidateId: { type: String, default: '' },
  selected: { type: Array, default: () => [] },
  isBatch: { type: Boolean, default: false },
});

const emit = defineEmits(['close', 'done']);

const selectedChannel = ref('phone');
const isSubmitting = ref(false);

const channels = [
  { key: 'phone', label: '电话', icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"/></svg>' },
  { key: 'email', label: '邮件', icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/><polyline points="22,6 12,13 2,6"/></svg>' },
  { key: 'feishu', label: '飞书', icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>' },
];

function close() {
  if (!isSubmitting.value) emit('close');
}

async function submit() {
  isSubmitting.value = true;
  try {
    if (props.isBatch) {
      await batchContactCandidates(props.selected, selectedChannel.value);
    } else {
      await recordTalentContact(props.candidateId, selectedChannel.value);
    }
    emit('done', { channel: selectedChannel.value, names: props.isBatch ? props.selected : [props.candidateName] });
  } catch (e) {
    console.warn('[ContactModal] API failed:', e);
    // Still emit done — frontend can show a fallback message
    emit('done', { channel: selectedChannel.value, names: props.isBatch ? props.selected : [props.candidateName], fallback: true });
  } finally {
    isSubmitting.value = false;
  }
}
</script>

<style scoped>
.modal-overlay { position: fixed; inset: 0; background: rgba(15,23,42,0.38); backdrop-filter: blur(2px); z-index: 300; display: flex; align-items: center; justify-content: center; }
.contact-modal { width: 440px; max-width: 92vw; max-height: 85vh; display: flex; flex-direction: column; border-radius: 10px; border: 1px solid var(--e-border, #E1E6EF); background: var(--e-surface, #FFFFFF); box-shadow: 0 24px 80px rgba(15,23,42,0.2); }
.ct-header { display: flex; align-items: center; justify-content: space-between; padding: 14px 20px; border-bottom: 1px solid var(--e-border-soft, #EFF3F8); }
.ct-header-left { display: flex; align-items: center; gap: 8px; }
.ct-header-left h3 { font-size: 16px; font-weight: 700; color: var(--c-text); margin: 0; }
.drawer-close { width: 32px; height: 32px; border-radius: 50%; border: none; background: var(--e-surface-soft, #F2F5FA); color: var(--c-sub); cursor: pointer; font-size: 16px; display: flex; align-items: center; justify-content: center; }
.drawer-close:hover { background: var(--c-border); }
.ct-body { padding: 16px 20px; }
.ct-info { display: flex; gap: 12px; font-size: 13px; }
.ct-label { color: var(--c-sub); font-weight: 500; }
.ct-value { color: var(--c-text); font-weight: 600; }
.ct-channel-btn { display: flex; align-items: center; gap: 6px; padding: 10px 16px; border: 1.5px solid var(--c-border); border-radius: 8px; background: transparent; font-size: 13px; font-weight: 600; cursor: pointer; transition: all 0.15s; color: var(--c-body); }
.ct-channel-btn:hover { border-color: var(--c-primary); color: var(--c-primary); }
.ct-channel-btn.active { border-color: var(--c-primary); background: var(--c-primary-subtle); color: var(--c-primary); }
.ct-actions { display: flex; gap: 8px; justify-content: flex-end; align-items: center; padding: 12px 20px; border-top: 1px solid var(--e-border-soft, #EFF3F8); }
.btn { display: inline-flex; align-items: center; gap: 5px; padding: 6px 14px; border-radius: 6px; font-size: 13px; font-weight: 600; cursor: pointer; border: none; }
.btn-primary { background: var(--c-primary); color: #fff; }
.btn-ghost { background: transparent; border: none; color: var(--c-sub); }
.btn:disabled { opacity: 0.45; cursor: not-allowed; }
</style>
