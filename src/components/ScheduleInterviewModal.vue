<template>
  <Teleport to="body">
    <div
      v-if="visible"
      class="modal-overlay"
      @click.self="handleClose"
    >
      <div
        class="modal-box schedule-modal"
        role="dialog"
        aria-modal="true"
        aria-label="安排面试"
        @keydown.escape="handleClose"
      >
        <!-- Header -->
        <div class="schedule-header">
          <div class="schedule-header-left">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
            <h3>安排面试</h3>
          </div>
          <button class="drawer-close" @click="handleClose" aria-label="关闭">✕</button>
        </div>

        <!-- Candidate & Demand info -->
        <div class="schedule-meta">
          <div class="meta-item">
            <span class="meta-label">候选人</span>
            <span class="meta-value">{{ candidate?.name || '—' }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">应聘岗位</span>
            <span class="meta-value">{{ demand?.position || '—' }}</span>
          </div>
        </div>

        <!-- Rounds -->
        <div class="schedule-body">
          <div
            v-for="(round, idx) in rounds"
            :key="round.key"
            class="round-card"
          >
            <div class="round-header">
              <span class="round-title">第 {{ idx + 1 }} 轮</span>
              <button
                v-if="rounds.length > 1"
                class="btn-text-danger btn-sm round-delete"
                @click="removeRound(idx)"
                aria-label="删除此轮"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
                删除
              </button>
            </div>

            <div class="form-grid">
              <!-- Interviewer -->
              <div class="form-field">
                <label :for="`interviewer-${round.key}`" class="form-label">
                  面试官 <span class="required">*</span>
                </label>
                <select
                  :id="`interviewer-${round.key}`"
                  v-model="round.interviewer"
                  class="form-select"
                >
                  <option value="" disabled>请选择面试官</option>
                  <option v-for="iv in interviewers" :key="iv.id" :value="iv.id">
                    {{ iv.name }}
                  </option>
                </select>
              </div>

              <!-- Mode -->
              <div class="form-field">
                <label :for="`mode-${round.key}`" class="form-label">面试方式</label>
                <select
                  :id="`mode-${round.key}`"
                  v-model="round.mode"
                  class="form-select"
                  @change="onModeChange(round)"
                >
                  <option value="" disabled>请选择面试方式</option>
                  <option v-for="m in modes" :key="m.id" :value="m.id">
                    {{ m.label }}
                  </option>
                </select>
              </div>

              <!-- Date -->
              <div class="form-field">
                <label :for="`date-${round.key}`" class="form-label">
                  面试日期 <span class="required">*</span>
                </label>
                <input
                  :id="`date-${round.key}`"
                  v-model="round.date"
                  type="date"
                  class="form-input"
                />
              </div>

              <!-- Time -->
              <div class="form-field">
                <label :for="`time-${round.key}`" class="form-label">
                  面试时间 <span class="required">*</span>
                </label>
                <input
                  :id="`time-${round.key}`"
                  v-model="round.time"
                  type="time"
                  class="form-input"
                />
              </div>

              <!-- Address (only when mode = 4 = offline) -->
              <div v-if="round.mode === '4'" class="form-field form-field-wide">
                <label :for="`address-${round.key}`" class="form-label">面试地址</label>
                <input
                  :id="`address-${round.key}`"
                  v-model="round.address"
                  type="text"
                  class="form-input"
                  placeholder="请输入面试地址"
                />
              </div>
            </div>
          </div>

          <!-- Add round button -->
          <button class="btn-add-round" @click="addRound">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            + 添加轮次
          </button>
        </div>

        <!-- Footer actions -->
        <div class="schedule-actions">
          <button class="btn btn-ghost" @click="handleClose">取消</button>
          <button
            class="btn btn-primary"
            :disabled="submitting || !isValid"
            @click="handleSubmit"
          >
            <span v-if="submitting" class="spinner"></span>
            {{ submitting ? '提交中...' : '确认安排' }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue';
import { createInterview } from '../api/interview.js';

const props = defineProps({
  visible: { type: Boolean, default: false },
  candidate: { type: Object, default: () => ({ name: '', id: '' }) },
  demand: { type: Object, default: () => ({ position: '', id: '' }) },
});

const emit = defineEmits(['close', 'success']);

const interviewers = [
  { id: '1', name: '张HR' },
  { id: '2', name: '李面试官' },
  { id: '3', name: '王面试官' },
];

const modes = [
  { id: '1', label: '飞书视频' },
  { id: '2', label: '腾讯会议' },
  { id: '3', label: '其他线上' },
  { id: '4', label: '线下' },
];

let keyCounter = 0;
function nextKey() {
  return ++keyCounter;
}

function createEmptyRound() {
  return {
    key: nextKey(),
    interviewer: '',
    mode: '',
    date: '',
    time: '',
    address: '',
  };
}

const rounds = ref([createEmptyRound()]);
const submitting = ref(false);

const isValid = computed(() => {
  return rounds.value.every((r) => r.interviewer && r.date && r.time);
});

function onModeChange(round) {
  if (round.mode !== '4') {
    round.address = '';
  }
}

function addRound() {
  rounds.value.push(createEmptyRound());
}

function removeRound(idx) {
  if (rounds.value.length <= 1) return;
  rounds.value.splice(idx, 1);
}

function handleClose() {
  emit('close');
}

function resetForm() {
  rounds.value = [createEmptyRound()];
  keyCounter = 0;
}

async function handleSubmit() {
  if (!isValid.value) return;
  submitting.value = true;

  const interviewerMap = {};
  interviewers.forEach((iv) => { interviewerMap[iv.id] = iv.name; });
  const modeMap = {};
  modes.forEach((m) => { modeMap[m.id] = m.label; });

  try {
    const results = [];
    for (let i = 0; i < rounds.value.length; i++) {
      const r = rounds.value[i];
      const payload = {
        candidate_id: props.candidate?.id || '',
        candidate: props.candidate?.name || '',
        position: props.demand?.position || '',
        demand_id: props.demand?.id || '',
        round: i + 1,
        total_rounds: rounds.value.length,
        interviewer_id: r.interviewer,
        interviewer: interviewerMap[r.interviewer] || '',
        mode_id: r.mode,
        mode: modeMap[r.mode] || '',
        date: r.date,
        time: r.time,
        address: r.address || '',
      };
      const res = await createInterview(payload);
      results.push(res);
    }
    submitting.value = false;
    emit('success', { rounds: rounds.value.length, results });
    resetForm();
  } catch (e) {
    console.warn('[ScheduleInterviewModal] createInterview failed:', e);
    submitting.value = false;
    // keep modal open so user can retry
  }
}

watch(
  () => props.visible,
  (v) => {
    if (v) {
      resetForm();
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
.schedule-modal {
  width: 600px;
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

.schedule-modal:focus-visible {
  outline: none;
  box-shadow: var(--e-shadow-modal, 0 24px 80px rgba(15,23,42,0.2)), var(--e-focus, 0 0 0 3px rgba(79,110,247,0.22));
}

/* ===== Header ===== */
.schedule-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-bottom: 1px solid var(--e-border-soft, #EFF3F8);
  flex-shrink: 0;
  min-height: 56px;
}

.schedule-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--e-primary, #4F6EF7);
}

.schedule-header-left svg {
  flex-shrink: 0;
}

.schedule-header-left h3 {
  font-size: 16px;
  font-weight: 700;
  color: var(--e-text, #172033);
  margin: 0;
}

/* ===== Close button ===== */
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
  flex-shrink: 0;
}

.drawer-close:hover {
  background: var(--e-border, #E1E6EF);
  color: var(--e-text, #172033);
}

/* ===== Meta info bar ===== */
.schedule-meta {
  display: flex;
  gap: 24px;
  padding: 14px 24px;
  background: var(--e-surface-soft, #F9FAFC);
  border-bottom: 1px solid var(--e-border-soft, #EFF3F8);
  flex-shrink: 0;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.meta-label {
  color: var(--e-muted, #5B6475);
  font-weight: 500;
}

.meta-value {
  color: var(--e-text, #172033);
  font-weight: 600;
}

/* ===== Body / rounds ===== */
.schedule-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 24px 12px;
}

.schedule-body::-webkit-scrollbar {
  width: 4px;
}

.schedule-body::-webkit-scrollbar-thumb {
  background: #d1d5db;
  border-radius: 2px;
}

/* ===== Round card ===== */
.round-card {
  border: 1px solid var(--e-border, #E1E6EF);
  border-radius: var(--radius, 8px);
  padding: 16px 18px;
  margin-bottom: 12px;
  background: var(--e-surface, #FFFFFF);
  transition: border-color 0.15s;
}

.round-card:focus-within {
  border-color: var(--e-primary, #4F6EF7);
}

.round-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.round-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--e-text, #172033);
}

.round-title::before {
  content: '';
  display: inline-block;
  width: 3px;
  height: 14px;
  background: var(--e-primary, #4F6EF7);
  border-radius: 2px;
  margin-right: 8px;
  vertical-align: middle;
}

/* ===== Form grid ===== */
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 16px;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.form-field-wide {
  grid-column: 1 / -1;
}

.form-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--e-muted, #5B6475);
}

.required {
  color: var(--e-reject, #EF4444);
}

.form-select,
.form-input {
  height: 36px;
  padding: 0 10px;
  border: 1px solid var(--e-border, #E1E6EF);
  border-radius: var(--radius-sm, 6px);
  font-size: 13px;
  font-family: inherit;
  background: var(--e-surface, #FFFFFF);
  color: var(--e-text, #172033);
  transition: border-color 0.15s, box-shadow 0.15s;
}

.form-select:focus-visible,
.form-input:focus-visible {
  border-color: var(--e-primary, #4F6EF7);
  outline: none;
  box-shadow: var(--e-focus, 0 0 0 3px rgba(79, 110, 247, 0.22));
}

.form-select option {
  color: var(--e-text, #172033);
}

.form-input::placeholder {
  color: var(--e-subtle, #8C95A6);
}

/* ===== Add round button ===== */
.btn-add-round {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: 1px dashed var(--e-border, #E1E6EF);
  border-radius: var(--radius-sm, 6px);
  background: transparent;
  color: var(--e-primary, #4F6EF7);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
  width: 100%;
  justify-content: center;
}

.btn-add-round:hover {
  background: var(--e-primary-subtle, rgba(79, 110, 247, 0.1));
  border-color: var(--e-primary, #4F6EF7);
}

/* ===== Actions footer ===== */
.schedule-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  padding: 12px 24px;
  border-top: 1px solid var(--e-border-soft, #EFF3F8);
  background: var(--e-surface, #FFFFFF);
  flex-shrink: 0;
}

/* ===== Button overrides ===== */
.btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 7px 16px;
  border-radius: var(--radius-sm, 6px);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  border: none;
  text-decoration: none;
  transition: background 0.15s, transform 0.1s, box-shadow 0.15s;
  white-space: nowrap;
  line-height: 1.4;
}

.btn:active {
  transform: scale(0.98);
}

.btn-primary {
  background: var(--e-primary, #4F6EF7);
  color: #FFFFFF;
}

.btn-primary:hover:not(:disabled) {
  background: var(--e-primary-hover, #6B84FF);
  box-shadow: 0 0 0 3px var(--e-primary-subtle, rgba(79, 110, 247, 0.1));
}

.btn-primary:active:not(:disabled) {
  background: var(--e-primary-active, #3D54D4);
}

.btn-ghost {
  background: var(--e-surface-soft, #F2F5FA);
  border: 1px solid var(--e-border, #E1E6EF);
  color: var(--e-muted, #5B6475);
}

.btn-ghost:hover {
  background: var(--e-border-soft, #EFF3F8);
  color: var(--e-text, #172033);
}

.btn:disabled {
  opacity: 0.45;
  pointer-events: none;
  cursor: not-allowed;
}

/* Delete round button */
.btn-text-danger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: transparent;
  color: var(--e-reject, #EF4444);
  border: none;
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
  padding: 4px 8px;
  border-radius: var(--radius-sm, 6px);
  transition: background 0.15s;
}

.btn-text-danger:hover {
  background: rgba(239, 68, 68, 0.06);
}

.btn-sm {
  padding: 4px 10px;
  font-size: 11px;
}

/* ===== Spinner ===== */
.spinner {
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #FFFFFF;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

/* ===== Animations ===== */
@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(12px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ===== Reduced motion ===== */
@media (prefers-reduced-motion: reduce) {
  .modal-overlay,
  .schedule-modal,
  .btn,
  .drawer-close,
  .round-card,
  .btn-add-round,
  .form-select,
  .form-input {
    transition: none;
    animation: none;
  }

  .schedule-modal {
    opacity: 1;
    transform: none;
  }

  .modal-overlay {
    opacity: 1;
  }
}
</style>
