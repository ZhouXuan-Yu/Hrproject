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
                :disabled="!channelAvailable(ch.key)"
                :title="channelDisabledReason(ch.key)"
                @click="selectedChannel = ch.key"
              >
                <span v-html="ch.icon"></span>
                {{ ch.label }}
              </button>
            </div>
          </div>

          <!-- 联系方式详情（简历识别提取） -->
          <div class="ct-contact-card" v-if="contactLoading">
            <span class="ct-loading">正在读取简历识别的联系方式...</span>
          </div>
          <div class="ct-contact-card" v-else-if="selectedChannel === 'phone'">
            <template v-if="contact.mobile">
              <div class="ct-contact-row">
                <span class="ct-contact-label">手机号</span>
                <span class="ct-contact-value">{{ contact.mobile }}</span>
                <button class="btn btn-text btn-sm" @click="copyText(contact.mobile, '手机号')">复制</button>
              </div>
              <div class="ct-contact-hint">来自简历识别提取，点击「拨打并记录」唤起系统拨号。</div>
            </template>
            <div class="ct-contact-empty" v-else>简历中未识别到手机号，可查看简历原件手动补录。</div>
          </div>
          <div class="ct-contact-card" v-else-if="selectedChannel === 'email'">
            <template v-if="contact.email">
              <div class="ct-contact-row">
                <span class="ct-contact-label">邮箱</span>
                <span class="ct-contact-value">{{ contact.email }}</span>
                <button class="btn btn-text btn-sm" @click="copyText(contact.email, '邮箱')">复制</button>
              </div>
              <div class="ct-contact-hint">来自简历识别提取，点击「发送并记录」会通过后端 SMTP 真实发送。</div>
              <textarea v-model="draftText" class="ct-draft" rows="5" placeholder="填写或粘贴要发送给候选人的邮件内容"></textarea>
            </template>
            <div class="ct-contact-empty" v-else>简历中未识别到邮箱，可查看简历原件手动补录。</div>
          </div>
          <div class="ct-contact-card" v-else>
            <div class="ct-contact-hint">
              飞书发送会优先按候选人邮箱/手机号检索 open_id；也可先在配置页完成飞书应用配置。
            </div>
            <textarea v-model="draftText" class="ct-draft" rows="5" placeholder="填写或粘贴要通过飞书发送给候选人的内容"></textarea>
          </div>
          <div v-if="sendResult.visible" class="ct-send-result" :class="{ ok: sendResult.sent, fail: !sendResult.sent }">
            <b>{{ sendResult.sent ? '发送成功' : '发送失败' }}</b>
            <span>{{ sendResult.message }}</span>
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
          <template v-if="!isBatch && selectedChannel === 'phone' && contact.mobile">
            <a class="btn btn-primary btn-sm ct-action-link" :href="`tel:${contact.mobile}`" @click="recordAction('电话')">拨打并记录</a>
          </template>
          <button v-else-if="sendResult.visible" class="btn btn-primary btn-sm" @click="close">确定</button>
          <button v-else class="btn btn-primary btn-sm" :disabled="isSubmitting" @click="submit">
            {{ isBatch ? '记录批量联系' : (selectedChannel === 'phone' ? '记录联系' : '发送并记录') }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, computed, watch } from 'vue';
import { recordTalentContact, batchContactCandidates, fetchCandidateContact, sendTalentContact } from '../api/talent.js';
import { useToast } from '../composables/useToast.js';

const props = defineProps({
  visible: { type: Boolean, default: false },
  candidateName: { type: String, default: '' },
  candidateId: { type: String, default: '' },
  selected: { type: Array, default: () => [] },
  isBatch: { type: Boolean, default: false },
});

const emit = defineEmits(['close', 'done']);
const toast = useToast();

const selectedChannel = ref('phone');
const isSubmitting = ref(false);
const contact = ref({ mobile: '', email: '' });
const contactLoading = ref(false);
const draftText = ref('');
const sendResult = ref({ visible: false, sent: false, message: '' });

const channels = [
  { key: 'phone', label: '电话', icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"/></svg>' },
  { key: 'email', label: '邮件', icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/><polyline points="22,6 12,13 2,6"/></svg>' },
  { key: 'feishu', label: '飞书', icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>' },
];

function channelAvailable(key) {
  if (key === 'phone') return !!contact.value.mobile;
  if (key === 'email') return !!contact.value.email;
  return true;
}

function channelDisabledReason(key) {
  if (channelAvailable(key)) return '';
  return key === 'phone' ? '简历中未识别到手机号' : '简历中未识别到邮箱';
}

// 打开弹窗时拉取简历识别提取的联系方式
watch(() => props.visible, async (v) => {
  if (!v || props.isBatch || !props.candidateId) return;
  contactLoading.value = true;
  contact.value = { mobile: '', email: '' };
  try {
    const res = await fetchCandidateContact(props.candidateId);
    const data = (res && res.data) ? res.data : res;
    contact.value = { mobile: data?.mobile || '', email: data?.email || '' };
    // 默认选中第一个可用渠道
    if (!contact.value.mobile && contact.value.email) selectedChannel.value = 'email';
    else selectedChannel.value = 'phone';
  } catch (e) {
    console.warn('[ContactModal] fetch contact failed:', e);
    contact.value = { mobile: '', email: '' };
  } finally {
    contactLoading.value = false;
  }
  draftText.value = `${props.candidateName || '候选人'}您好，关于您应聘岗位的后续沟通，想和您确认一下近期安排。`;
  sendResult.value = { visible: false, sent: false, message: '' };
});

async function copyText(text, label) {
  try {
    await navigator.clipboard.writeText(text);
    toast.success(`${label}已复制`);
  } catch {
    toast.warning('复制失败，请手动选择复制');
  }
}

function close() {
  if (!isSubmitting.value) emit('close');
}

// 电话：外部动作由 tel: 链接触发，这里同步记录联系
async function recordAction(methodLabel) {
  try {
    await recordTalentContact(props.candidateId, methodLabel);
    emit('done', { channel: selectedChannel.value, names: [props.candidateName] });
  } catch (e) {
    console.warn('[ContactModal] record failed:', e);
    emit('done', { channel: selectedChannel.value, names: [props.candidateName], fallback: true });
  }
}

async function submit() {
  isSubmitting.value = true;
  const methodLabel = { phone: '电话', email: '邮件', feishu: '飞书' }[selectedChannel.value] || '系统记录';
  try {
    if (props.isBatch) {
      await batchContactCandidates(props.selected, methodLabel);
    } else if (selectedChannel.value === 'email' || selectedChannel.value === 'feishu') {
      const result = await sendTalentContact({
        candidateId: props.candidateId,
        channel: selectedChannel.value,
        method: methodLabel,
        draft: draftText.value,
        subject: 'XX公司招聘沟通',
      });
      const ok = !!(result?.sent || result?.send?.sent);
      sendResult.value = {
        visible: true,
        sent: ok,
        message: ok
          ? `${methodLabel}已发送至 ${result?.send?.recipient || contact.value.email || props.candidateName}`
          : (result?.send?.message || result?.message || '请检查邮箱/飞书配置'),
      };
      return;
    } else {
      await recordTalentContact(props.candidateId, methodLabel);
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
.ct-channel-btn:disabled { opacity: 0.45; cursor: not-allowed; }
.ct-channel-btn:disabled:hover { border-color: var(--c-border); color: var(--c-body); }
.ct-contact-card { margin-top: 14px; padding: 12px 14px; border: 1px solid var(--e-border-soft, #EFF3F8); border-radius: 8px; background: var(--e-surface-soft, #F8FAFD); }
.ct-contact-row { display: flex; align-items: center; gap: 10px; }
.ct-contact-label { font-size: 12px; color: var(--c-sub); flex-shrink: 0; }
.ct-contact-value { font-size: 15px; font-weight: 700; color: var(--c-text); font-variant-numeric: tabular-nums; letter-spacing: 0.3px; }
.ct-contact-hint { margin-top: 8px; font-size: 12px; color: var(--c-sub); line-height: 1.5; }
.ct-contact-empty { font-size: 13px; color: var(--c-warn); }
.ct-loading { font-size: 12px; color: var(--c-sub); }
.ct-draft { width: 100%; margin-top: 10px; padding: 9px 10px; border: 1px solid var(--c-border); border-radius: 8px; resize: vertical; font-size: 13px; line-height: 1.7; box-sizing: border-box; font-family: inherit; color: var(--c-text); background: var(--c-card); }
.ct-send-result { margin-top: 10px; padding: 9px 12px; border-radius: 8px; border: 1px solid var(--c-border); display: flex; gap: 8px; align-items: center; font-size: 12px; line-height: 1.5; }
.ct-send-result.ok { color: var(--c-done); border-color: rgba(34,197,94,.25); background: rgba(34,197,94,.08); }
.ct-send-result.fail { color: var(--c-reject); border-color: rgba(239,68,68,.25); background: rgba(239,68,68,.08); }
.ct-action-link { text-decoration: none; }
.ct-actions { display: flex; gap: 8px; justify-content: flex-end; align-items: center; padding: 12px 20px; border-top: 1px solid var(--e-border-soft, #EFF3F8); }
.btn { display: inline-flex; align-items: center; gap: 5px; padding: 6px 14px; border-radius: 6px; font-size: 13px; font-weight: 600; cursor: pointer; border: none; }
.btn-primary { background: var(--c-primary); color: #fff; }
.btn-ghost { background: transparent; border: none; color: var(--c-sub); }
.btn:disabled { opacity: 0.45; cursor: not-allowed; }
</style>
