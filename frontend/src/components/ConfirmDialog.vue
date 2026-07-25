<template>
  <Teleport to="body">
    <div v-if="visible" class="confirm-overlay" role="presentation" @click.self="cancel">
      <div class="confirm-box" role="dialog" aria-modal="true" :aria-label="title">
        <div class="confirm-head">
          <div class="confirm-icon" :class="type">{{ iconText }}</div>
          <div>
            <h3>{{ title }}</h3>
            <p v-if="message">{{ message }}</p>
          </div>
        </div>
        <div v-if="detail" class="confirm-detail">{{ detail }}</div>
        <div class="confirm-actions">
          <button v-if="showCancel" class="btn btn-ghost btn-sm" :disabled="busy" @click="cancel">{{ cancelText }}</button>
          <button class="btn btn-primary btn-sm" :class="{ danger: type === 'danger' }" :disabled="busy" @click="confirm">
            {{ busy ? busyText : confirmText }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  visible: { type: Boolean, default: false },
  title: { type: String, default: '确认操作' },
  message: { type: String, default: '' },
  detail: { type: String, default: '' },
  type: { type: String, default: 'info' },
  confirmText: { type: String, default: '确定' },
  cancelText: { type: String, default: '取消' },
  busyText: { type: String, default: '处理中...' },
  busy: { type: Boolean, default: false },
  showCancel: { type: Boolean, default: true },
});

const emit = defineEmits(['confirm', 'cancel']);

const iconText = computed(() => props.type === 'danger' ? '!' : props.type === 'warning' ? 'i' : '✓');

function confirm() {
  emit('confirm');
}

function cancel() {
  if (!props.busy) emit('cancel');
}
</script>

<style scoped>
.confirm-overlay {
  position: fixed;
  inset: 0;
  z-index: 1200;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(15, 23, 42, 0.38);
  backdrop-filter: blur(4px);
}
.confirm-box {
  width: 420px;
  max-width: 92vw;
  border: 1px solid var(--c-border);
  border-radius: 10px;
  background: var(--c-card);
  box-shadow: 0 24px 80px rgba(15, 23, 42, 0.22);
  padding: 18px;
}
.confirm-head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.confirm-icon {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-weight: 800;
  color: var(--c-primary);
  background: var(--c-primary-subtle);
}
.confirm-icon.danger { color: var(--c-reject); background: rgba(239, 68, 68, 0.1); }
.confirm-icon.warning { color: var(--c-warn); background: rgba(245, 158, 11, 0.12); }
.confirm-head h3 {
  margin: 0;
  font-size: 16px;
  color: var(--c-text);
}
.confirm-head p {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--c-body);
  line-height: 1.6;
}
.confirm-detail {
  margin-top: 12px;
  padding: 10px 12px;
  border: 1px solid var(--c-border);
  border-radius: 8px;
  background: var(--c-bg);
  color: var(--c-sub);
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
}
.confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}
.btn-primary.danger { background: var(--c-reject); }
</style>
