<template>
  <Teleport to="body">
    <div v-if="visible" class="modal-overlay" @click.self="handleClose">
      <div class="modal-box offer-modal" role="dialog" aria-modal="true" aria-label="发送Offer">
        <!-- Header -->
        <div class="offer-header">
          <div class="offer-header-left">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/><polyline points="22,6 12,13 2,6"/></svg>
            <h3>发送 Offer</h3>
          </div>
          <button class="drawer-close" @click="handleClose" aria-label="关闭">✕</button>
        </div>

        <!-- Body -->
        <div class="offer-body">
          <!-- Candidate info -->
          <div class="offer-meta">
            <div class="meta-item">
              <span class="meta-label">候选人</span>
              <span class="meta-value">{{ candidate?.name || '—' }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">应聘岗位</span>
              <span class="meta-value">{{ demand?.position || '—' }}</span>
            </div>
          </div>

          <!-- Salary structure -->
          <div class="form-section">
            <h4 class="section-title">薪资结构</h4>
            <div class="form-grid">
              <div class="form-field">
                <label for="offer-base" class="form-label">基本月薪 (元) <span class="required">*</span></label>
                <input id="offer-base" v-model.number="form.baseSalary" type="number" class="form-input" placeholder="15000" min="0" />
              </div>
              <div class="form-field">
                <label for="offer-bonus" class="form-label">年终奖 (月)</label>
                <input id="offer-bonus" v-model.number="form.bonusMonths" type="number" class="form-input" placeholder="3" min="0" step="0.5" />
              </div>
              <div class="form-field">
                <label for="offer-allowance" class="form-label">补贴 (元/月)</label>
                <input id="offer-allowance" v-model.number="form.allowance" type="number" class="form-input" placeholder="800" min="0" />
              </div>
              <div class="form-field">
                <label for="offer-stock" class="form-label">期权/股票</label>
                <input id="offer-stock" v-model="form.stock" type="text" class="form-input" placeholder="如：5000股/4年" />
              </div>
            </div>
          </div>

          <!-- Job details -->
          <div class="form-section">
            <h4 class="section-title">入职信息</h4>
            <div class="form-grid">
              <div class="form-field">
                <label for="offer-probation" class="form-label">试用期 <span class="required">*</span></label>
                <select id="offer-probation" v-model="form.probationMonths" class="form-select">
                  <option :value="3">3 个月</option>
                  <option :value="6">6 个月</option>
                </select>
              </div>
              <div class="form-field">
                <label for="offer-entry-date" class="form-label">预计入职日期 <span class="required">*</span></label>
                <input id="offer-entry-date" v-model="form.entryDate" type="date" class="form-input" />
              </div>
              <div class="form-field">
                <label for="offer-deadline" class="form-label">Offer 截止日 <span class="required">*</span></label>
                <input id="offer-deadline" v-model="form.validDeadline" type="date" class="form-input" />
              </div>
              <div class="form-field">
                <label for="offer-position" class="form-label">入职岗位</label>
                <input id="offer-position" v-model="form.positionTitle" type="text" class="form-input" placeholder="岗位名称" />
              </div>
            </div>
          </div>

          <!-- Offer content -->
          <div class="form-section">
            <h4 class="section-title">Offer 正文</h4>
            <textarea
              v-model="form.content"
              class="form-textarea"
              rows="4"
              placeholder="尊敬的候选人，非常高兴通知您..."
            ></textarea>
          </div>
        </div>

        <!-- Footer -->
        <div class="offer-actions">
          <button class="btn btn-ghost" @click="handleClose">保存草稿</button>
          <button class="btn btn-primary" :disabled="submitting || !isValid" @click="handleSubmit">
            <span v-if="submitting" class="spinner"></span>
            {{ submitting ? '发送中...' : '发送 Offer' }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue';
import { api } from '../api/index.js';
import { useToast } from '../composables/useToast.js';

const props = defineProps({
  visible: { type: Boolean, default: false },
  candidate: { type: Object, default: () => ({ name: '', id: '' }) },
  demand: { type: Object, default: () => ({ position: '', id: '' }) },
  processId: { type: [String, Number], default: null },
  resumeId: { type: [String, Number], default: null },
  // 面试预约ID（如 INT0004）：传入时走面试联动接口，发 Offer 同时推进流程状态
  bookId: { type: String, default: '' },
});

const emit = defineEmits(['close', 'success']);

const submitting = ref(false);
const { toast } = useToast();

const form = reactive({
  baseSalary: 15000,
  bonusMonths: 3,
  allowance: 800,
  stock: '',
  probationMonths: 3,
  entryDate: '',
  validDeadline: '',
  positionTitle: '',
  content: '',
});

const isValid = computed(() => {
  return form.baseSalary > 0 && form.entryDate && form.validDeadline;
});

function handleClose() {
  emit('close');
}

function resetForm() {
  form.baseSalary = 15000;
  form.bonusMonths = 3;
  form.allowance = 800;
  form.stock = '';
  form.probationMonths = 3;
  form.entryDate = '';
  form.validDeadline = '';
  form.positionTitle = props.demand?.position || '';
  form.content = '';
}

async function handleSubmit() {
  if (!isValid.value) return;
  submitting.value = true;

  const salaryJson = {
    base: form.baseSalary,
    bonus: form.bonusMonths,
    allowance: form.allowance,
    stock: form.stock,
    probationMonths: form.probationMonths,
    annualTotal: form.baseSalary * (12 + form.bonusMonths) + form.allowance * 12,
  };

  try {
    // 面试页入口：走面试联动接口（创建+发送+流程状态推进+候选人确认邮件一体）
    if (props.bookId) {
      const resp = await api.post(`/interview/${encodeURIComponent(props.bookId)}/offer`, {
        offer_content: form.content || generateDefaultContent(),
        salary_json: salaryJson,
        valid_deadline: form.validDeadline,
      });
      const data = resp.data || {};
      submitting.value = false;
      emit('success', { id: data.id, name: props.candidate?.name, emailSent: data.emailSent, emailMsg: data.emailMsg });
      resetForm();
      return;
    }
    if (!props.resumeId || !props.demand?.id) {
      submitting.value = false;
      toast.error('该面试记录未关联候选人或岗位，无法发送 Offer');
      return;
    }
    const payload = {
      resumeId: props.resumeId,
      processId: props.processId || 0,
      demandId: props.demand.id,
      salaryJson,
      validDeadline: form.validDeadline,
      offerContent: form.content || generateDefaultContent(),
      sendUserId: 2,
    };
    const resp = await api.post('/hire/offer/create', payload);
    const data = resp.data || {};
    // 创建只是草稿，紧接着调用 send 才真正发出（含候选人确认邮件）
    if (data.id) {
      try {
        const sendResp = await api.post(`/hire/offer/${encodeURIComponent(data.id)}/send`);
        const sd = sendResp.data || {};
        submitting.value = false;
        emit('success', { id: data.id, name: props.candidate?.name, emailSent: sd.emailSent, emailMsg: sd.emailMsg });
        resetForm();
        return;
      } catch (sendErr) {
        console.warn('[OfferModal] send failed:', sendErr);
        submitting.value = false;
        // Offer 已存在为草稿，明确告知用户后续在哪里继续
        toast.warning(`Offer 已保存为草稿（${data.id}），但发送失败：${sendErr.message || '未知错误'}。可在 Offer 列表中重新发送`);
        emit('close');
        resetForm();
        return;
      }
    }
    submitting.value = false;
    emit('success', { id: data.id, name: props.candidate?.name });
    resetForm();
  } catch (e) {
    console.warn('[OfferModal] create failed:', e);
    submitting.value = false;
    toast.error('发送 Offer 失败: ' + (e.message || '网络错误'));
  }
}

function generateDefaultContent() {
  const total = form.baseSalary * (12 + form.bonusMonths) + form.allowance * 12;
  return `尊敬的 ${props.candidate?.name}：

非常高兴通知您，您已通过我司面试考核，现正式向您发出录用通知。

岗位：${form.positionTitle || props.demand?.position || '待定'}
基本月薪：¥${form.baseSalary.toLocaleString()} × ${12 + form.bonusMonths} 薪
年度总包：约 ¥${total.toLocaleString()}
试用期：${form.probationMonths} 个月
预计入职日期：${form.entryDate}

请在 ${form.validDeadline} 前确认回复。期待您的加入！`;
}

watch(
  () => props.visible,
  (v) => {
    if (v) {
      resetForm();
      if (!form.positionTitle) form.positionTitle = props.demand?.position || '';
      // Default dates
      const today = new Date();
      const entry = new Date(today);
      entry.setDate(today.getDate() + 14);
      form.entryDate = entry.toISOString().split('T')[0];
      const deadline = new Date(today);
      deadline.setDate(today.getDate() + 7);
      form.validDeadline = deadline.toISOString().split('T')[0];
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
.offer-modal {
  width: 640px;
  max-width: 92vw;
  max-height: 85vh;
  display: flex;
  flex-direction: column;
  border-radius: 10px;
  border: 1px solid var(--e-border, #E1E6EF);
  background: var(--e-surface, #FFFFFF);
  box-shadow: var(--e-shadow-modal, 0 24px 80px rgba(15,23,42,0.2));
  animation: slideUp 0.22s cubic-bezier(0.16, 1, 0.3, 1);
}

/* ===== Header ===== */
.offer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-bottom: 1px solid var(--e-border-soft, #EFF3F8);
  flex-shrink: 0;
}

.offer-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--e-primary, #4F6EF7);
}

.offer-header-left h3 {
  font-size: 16px;
  font-weight: 700;
  color: var(--e-text, #172033);
  margin: 0;
}

.drawer-close {
  width: 32px;
  height: 32px;
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
}

.drawer-close:hover {
  background: var(--e-border, #E1E6EF);
  color: var(--e-text, #172033);
}

/* ===== Meta bar ===== */
.offer-meta {
  display: flex;
  gap: 24px;
  padding: 14px 24px;
  background: var(--e-surface-soft, #F9FAFC);
  border-bottom: 1px solid var(--e-border-soft, #EFF3F8);
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.meta-label { color: var(--e-muted, #5B6475); font-weight: 500; }
.meta-value { color: var(--e-text, #172033); font-weight: 600; }

/* ===== Body ===== */
.offer-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
}

.form-section { margin-bottom: 20px; }

.section-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--e-text, #172033);
  margin: 0 0 12px 0;
  padding-left: 10px;
  border-left: 3px solid var(--e-primary, #4F6EF7);
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 16px;
}

.form-field { display: flex; flex-direction: column; gap: 4px; }

.form-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--e-muted, #5B6475);
}

.required { color: var(--e-reject, #EF4444); }

.form-select, .form-input, .form-textarea {
  padding: 8px 10px;
  border: 1px solid var(--e-border, #E1E6EF);
  border-radius: 6px;
  font-size: 13px;
  font-family: inherit;
  background: var(--e-surface, #FFFFFF);
  color: var(--e-text, #172033);
  transition: border-color 0.15s, box-shadow 0.15s;
}

.form-select:focus-visible, .form-input:focus-visible, .form-textarea:focus-visible {
  border-color: var(--e-primary, #4F6EF7);
  outline: none;
  box-shadow: 0 0 0 3px rgba(79, 110, 247, 0.22);
}

.form-textarea {
  width: 100%;
  resize: vertical;
  min-height: 80px;
}

/* ===== Footer ===== */
.offer-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  padding: 12px 24px;
  border-top: 1px solid var(--e-border-soft, #EFF3F8);
  flex-shrink: 0;
}

/* ===== Buttons ===== */
.btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 7px 16px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  border: none;
  transition: background 0.15s, transform 0.1s;
  white-space: nowrap;
}

.btn:active { transform: scale(0.98); }

.btn-primary {
  background: var(--e-primary, #4F6EF7);
  color: #FFFFFF;
}

.btn-primary:hover:not(:disabled) { background: #6B84FF; }
.btn-primary:active:not(:disabled) { background: #3D54D4; }

.btn-ghost {
  background: var(--e-surface-soft, #F2F5FA);
  border: 1px solid var(--e-border, #E1E6EF);
  color: var(--e-muted, #5B6475);
}

.btn-ghost:hover {
  background: var(--e-border-soft, #EFF3F8);
  color: var(--e-text, #172033);
}

.btn:disabled { opacity: 0.45; pointer-events: none; cursor: not-allowed; }

.spinner {
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255,255,255,0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

/* ===== Animations ===== */
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
@keyframes slideUp {
  from { opacity: 0; transform: translateY(12px) scale(0.98); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}
@keyframes spin { to { transform: rotate(360deg); } }

@media (prefers-reduced-motion: reduce) {
  .modal-overlay, .offer-modal, .btn, .drawer-close {
    transition: none; animation: none;
  }
}
</style>
