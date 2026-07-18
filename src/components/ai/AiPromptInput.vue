<template>
  <div
    data-slot="prompt-input"
    :data-status="status"
    :data-layout="layout"
    :data-disabled="disabled ? '' : undefined"
  >
    <div data-slot="prompt-input-shell" @click="focusTextarea">
      <div data-slot="prompt-input-content">
        <textarea
          ref="textareaRef"
          data-slot="prompt-input-textarea"
          :value="modelValue"
          :placeholder="placeholder"
          :disabled="disabled"
          :rows="layout === 'compact' ? 1 : 2"
          :aria-label="ariaLabel"
          @input="onInput"
          @keydown.enter.exact.prevent="handleSubmit"
          @keydown.escape="handleStop"
        ></textarea>
      </div>
      <div data-slot="prompt-input-toolbar">
        <div data-slot="prompt-input-toolbar-start">
          <slot name="toolbar-start" />
        </div>
        <div data-slot="prompt-input-toolbar-end">
          <button
            data-slot="prompt-input-send"
            :data-status="status"
            :disabled="status === 'ready' && !modelValue.trim()"
            :aria-label="sendAriaLabel"
            type="button"
            @click="status === 'streaming' || status === 'submitted' ? handleStop() : handleSubmit()"
          >
            <!-- submitted: spinner -->
            <AiSkeleton v-if="status === 'submitted'" variant="spinner" />
            <!-- streaming: stop square -->
            <svg v-else-if="status === 'streaming'" viewBox="0 0 24 24" style="width:14px;height:14px" aria-hidden="true">
              <rect x="6" y="6" width="12" height="12" rx="2" fill="currentColor"/>
            </svg>
            <!-- error: X -->
            <svg v-else-if="status === 'error'" viewBox="0 0 24 24" style="width:16px;height:16px;stroke:currentColor;fill:none;stroke-width:2;stroke-linecap:round;stroke-linejoin:round" aria-hidden="true">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
            <!-- ready: arrow up -->
            <svg v-else viewBox="0 0 24 24" style="width:16px;height:16px;stroke:currentColor;fill:none;stroke-width:2.5;stroke-linecap:round;stroke-linejoin:round" aria-hidden="true">
              <line x1="12" y1="19" x2="12" y2="5"/><polyline points="5 12 12 5 19 12"/>
            </svg>
          </button>
        </div>
      </div>
    </div>
    <div v-if="hint" data-slot="prompt-input-footer">{{ hint }}</div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue';
import AiSkeleton from './AiSkeleton.vue';

const props = defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '输入您的问题...' },
  disabled: { type: Boolean, default: false },
  status: {
    type: String,
    default: 'ready',
    validator: (v) => ['ready', 'submitted', 'streaming', 'error'].includes(v),
  },
  hint: { type: String, default: '' },
  layout: {
    type: String,
    default: 'stacked',
    validator: (v) => ['stacked', 'compact', 'inline'].includes(v),
  },
  ariaLabel: { type: String, default: '消息输入' },
});

const emit = defineEmits(['update:modelValue', 'submit', 'stop']);
const textareaRef = ref(null);

const sendAriaLabel = computed(() => {
  if (props.status === 'streaming') return '停止生成';
  if (props.status === 'submitted') return '生成中';
  return '发送';
});

function onInput(e) {
  emit('update:modelValue', e.target.value);
  autosize(e.target);
}

function handleSubmit() {
  if (props.status !== 'ready') return;
  emit('submit');
}

function handleStop() {
  emit('stop');
}

function focusTextarea(e) {
  // Don't steal focus from buttons
  if (e.target.closest('button, a, [role="button"]')) return;
  textareaRef.value?.focus();
}

function autosize(el) {
  el.style.height = 'auto';
  el.style.height = Math.min(el.scrollHeight, 200) + 'px';
}

defineExpose({ focus: () => textareaRef.value?.focus() });
</script>

<style scoped>
[data-slot="prompt-input"] {
  border: 1px solid var(--c-border, #E1E6EF);
  border-radius: var(--radius-lg, 12px);
  background: var(--c-card, #FFFFFF);
  transition: border-color 0.15s;
}
[data-slot="prompt-input"]:focus-within {
  border-color: var(--c-primary, #4F6EF7);
}
[data-slot="prompt-input"][data-status="error"] {
  border-color: var(--c-reject, #EF4444);
}

[data-slot="prompt-input-shell"] {
  padding: 12px 16px;
  cursor: text;
}

[data-slot="prompt-input-textarea"] {
  width: 100%;
  border: none;
  outline: none;
  resize: none;
  font-family: inherit;
  font-size: var(--fs-body, 14px);
  color: var(--c-text, #172033);
  background: transparent;
  line-height: 1.6;
  min-height: 44px;
}
[data-slot="prompt-input-textarea"]::placeholder {
  color: var(--c-sub, #8C95A6);
}
[data-slot="prompt-input-textarea"]:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

[data-slot="prompt-input-toolbar"] {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px 12px;
  border-top: 1px solid var(--c-border-light, #EFF1F5);
}
[data-slot="prompt-input-toolbar-start"],
[data-slot="prompt-input-toolbar-end"] {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* Send button */
[data-slot="prompt-input-send"] {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: var(--radius-sm, 6px);
  border: none;
  background: var(--c-primary, #4F6EF7);
  color: #fff;
  cursor: pointer;
  transition: background 0.15s, transform 0.1s;
  flex-shrink: 0;
}
[data-slot="prompt-input-send"]:hover:not(:disabled) {
  background: var(--c-primary-hover, #6B84FF);
}
[data-slot="prompt-input-send"]:active:not(:disabled) {
  transform: scale(0.95);
}
[data-slot="prompt-input-send"]:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
[data-slot="prompt-input-send"][data-status="streaming"] {
  background: var(--c-reject, #EF4444);
}
[data-slot="prompt-input-send"][data-status="error"] {
  background: var(--c-reject, #EF4444);
}

[data-slot="prompt-input-footer"] {
  padding: 6px 16px 10px;
  font-size: var(--fs-caption, 12px);
  color: var(--c-sub, #8C95A6);
}

/* Compact variant */
[data-slot="prompt-input"][data-layout="compact"] [data-slot="prompt-input-shell"] {
  padding: 8px 12px;
}
[data-slot="prompt-input"][data-layout="compact"] [data-slot="prompt-input-textarea"] {
  min-height: 36px;
}
[data-slot="prompt-input"][data-layout="compact"] [data-slot="prompt-input-toolbar"] {
  padding: 6px 12px 8px;
}

/* Focus visible */
[data-slot="prompt-input-send"]:focus-visible {
  outline: 2px solid var(--c-primary, #4F6EF7);
  outline-offset: 2px;
}

@media (prefers-reduced-motion: reduce) {
  [data-slot="prompt-input-send"]:active {
    transform: none;
  }
}
</style>
