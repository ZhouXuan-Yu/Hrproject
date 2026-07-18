<template>
  <div data-slot="chat-tool" :data-status="status" :data-expanded="isExpanded ? 'true' : 'false'">
    <!-- Header -->
    <button
      data-slot="chat-tool-header"
      type="button"
      :aria-expanded="isExpanded ? 'true' : 'false'"
      :aria-label="'工具 ' + name + ' ' + statusLabel"
      @click="toggleExpanded"
    >
      <!-- Tool icon -->
      <span data-slot="chat-tool-icon" aria-hidden="true">
        <svg
          v-if="status === 'running'"
          viewBox="0 0 24 24"
          style="width:16px;height:16px;stroke:var(--c-primary, #4F6EF7);fill:none;stroke-width:2;stroke-linecap:round;stroke-linejoin:round"
        >
          <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/>
        </svg>
        <svg
          v-else-if="status === 'done'"
          viewBox="0 0 24 24"
          style="width:16px;height:16px;stroke:var(--c-done, #22C55E);fill:none;stroke-width:2;stroke-linecap:round;stroke-linejoin:round"
        >
          <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/>
        </svg>
        <svg
          v-else
          viewBox="0 0 24 24"
          style="width:16px;height:16px;stroke:var(--c-reject, #EF4444);fill:none;stroke-width:2;stroke-linecap:round;stroke-linejoin:round"
        >
          <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/>
        </svg>
      </span>

      <!-- Tool name -->
      <span data-slot="chat-tool-name">{{ name }}</span>

      <!-- Status badge -->
      <span data-slot="chat-tool-status" :data-status="status">
        <template v-if="status === 'running'">
          <AiSkeleton variant="pulse" />
          <span>运行中</span>
        </template>
        <template v-else-if="status === 'done'">
          <svg
            viewBox="0 0 24 24"
            style="width:12px;height:12px;stroke:var(--c-done, #22C55E);fill:none;stroke-width:2.5;stroke-linecap:round;stroke-linejoin:round"
            aria-hidden="true"
          >
            <polyline points="20 6 9 17 4 12"/>
          </svg>
          <span>完成</span>
        </template>
        <template v-else>
          <svg
            viewBox="0 0 24 24"
            style="width:12px;height:12px;stroke:var(--c-reject, #EF4444);fill:none;stroke-width:2.5;stroke-linecap:round;stroke-linejoin:round"
            aria-hidden="true"
          >
            <line x1="18" y1="6" x2="6" y2="18"/>
            <line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
          <span>错误</span>
        </template>
      </span>

      <!-- Chevron -->
      <span
        data-slot="chat-tool-chevron"
        aria-hidden="true"
      >
        <svg
          viewBox="0 0 24 24"
          style="width:14px;height:14px;stroke:currentColor;fill:none;stroke-width:2.5;stroke-linecap:round;stroke-linejoin:round"
        >
          <polyline points="6 9 12 15 18 9"/>
        </svg>
      </span>
    </button>

    <!-- Collapsible body -->
    <div
      v-show="isExpanded"
      data-slot="chat-tool-body"
    >
      <!-- Input section -->
      <div v-if="input" data-slot="chat-tool-section">
        <div data-slot="chat-tool-section-label">输入</div>
        <div data-slot="chat-tool-section-content">{{ input }}</div>
      </div>

      <!-- Output section -->
      <div v-if="status === 'done' && output" data-slot="chat-tool-section">
        <div data-slot="chat-tool-section-label">输出</div>
        <div data-slot="chat-tool-section-content">{{ output }}</div>
      </div>

      <!-- Error section -->
      <div v-if="status === 'error' && errorMessage" data-slot="chat-tool-section" data-error="true">
        <div data-slot="chat-tool-section-label">错误信息</div>
        <div data-slot="chat-tool-section-content">{{ errorMessage }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue';
import AiSkeleton from './AiSkeleton.vue';

const props = defineProps({
  name: {
    type: String,
    required: true,
  },
  status: {
    type: String,
    default: 'running',
    validator: (v) => ['running', 'done', 'error'].includes(v),
  },
  input: {
    type: String,
    default: '',
  },
  output: {
    type: String,
    default: '',
  },
  errorMessage: {
    type: String,
    default: '',
  },
  expanded: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(['update:expanded']);

const isExpanded = ref(props.expanded);

watch(
  () => props.expanded,
  (val) => {
    isExpanded.value = val;
  }
);

// Auto-expand when done or error
watch(
  () => props.status,
  (val) => {
    if (val === 'done' || val === 'error') {
      isExpanded.value = true;
      emit('update:expanded', true);
    }
  }
);

const statusLabel = computed(() => {
  switch (props.status) {
    case 'running': return '运行中';
    case 'done': return '完成';
    case 'error': return '错误';
    default: return '';
  }
});

function toggleExpanded() {
  isExpanded.value = !isExpanded.value;
  emit('update:expanded', isExpanded.value);
}
</script>

<style scoped>
/* Root */
[data-slot="chat-tool"] {
  border: 1px solid var(--c-border, #E1E6EF);
  border-radius: var(--radius, 8px);
  background: var(--c-card, #FFFFFF);
  overflow: hidden;
}
[data-slot="chat-tool"][data-status="running"] {
  border-color: var(--c-primary-subtle, rgba(79,110,247,0.08));
}
[data-slot="chat-tool"][data-status="error"] {
  border-color: var(--c-reject, #EF4444);
}

/* Header */
[data-slot="chat-tool-header"] {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px 14px;
  background: none;
  border: none;
  font-family: inherit;
  font-size: var(--fs-caption, 12px);
  color: var(--c-body, #5B6475);
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;
}
[data-slot="chat-tool-header"]:hover {
  background: var(--c-surface-elevated, #F9FAFC);
}
[data-slot="chat-tool-header"]:focus-visible {
  outline: 2px solid var(--c-primary, #4F6EF7);
  outline-offset: -2px;
  border-radius: var(--radius, 8px);
}

/* Tool icon */
[data-slot="chat-tool-icon"] {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

/* Tool name */
[data-slot="chat-tool-name"] {
  font-size: var(--fs-body, 14px);
  font-weight: 600;
  color: var(--c-text, #172033);
  flex: 0 1 auto;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Status badge */
[data-slot="chat-tool-status"] {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: var(--radius-sm, 6px);
  font-size: 11px;
  font-weight: 500;
  flex-shrink: 0;
}
[data-slot="chat-tool-status"][data-status="running"] {
  background: var(--c-primary-subtle, rgba(79,110,247,0.08));
  color: var(--c-primary, #4F6EF7);
}
[data-slot="chat-tool-status"][data-status="done"] {
  background: rgba(34,197,94,0.08);
  color: var(--c-done, #22C55E);
}
[data-slot="chat-tool-status"][data-status="error"] {
  background: rgba(239,68,68,0.08);
  color: var(--c-reject, #EF4444);
}

/* Chevron */
[data-slot="chat-tool-chevron"] {
  display: flex;
  align-items: center;
  margin-left: auto;
  transition: transform 0.2s ease;
  color: var(--c-sub, #8C95A6);
}
[data-slot="chat-tool"][data-expanded="true"] [data-slot="chat-tool-chevron"] {
  transform: rotate(180deg);
}

/* Body */
[data-slot="chat-tool-body"] {
  padding: 6px 14px 14px;
  border-top: 1px solid var(--c-border-light, #EFF1F5);
}

/* Sections */
[data-slot="chat-tool-section"] {
  margin-top: 10px;
}
[data-slot="chat-tool-section"][data-error="true"] [data-slot="chat-tool-section-content"] {
  color: var(--c-reject, #EF4444);
}

[data-slot="chat-tool-section-label"] {
  font-size: 11px;
  font-weight: 700;
  color: var(--c-sub, #8C95A6);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin-bottom: 4px;
}

[data-slot="chat-tool-section-content"] {
  font-size: var(--fs-caption, 12px);
  line-height: 1.6;
  color: var(--c-body, #5B6475);
  white-space: pre-wrap;
  word-break: break-word;
  background: var(--c-surface-elevated, #F9FAFC);
  border-radius: var(--radius-sm, 6px);
  padding: 10px 12px;
  font-family: 'SF Mono', 'Cascadia Code', 'Consolas', monospace;
}

/* Reduced motion */
@media (prefers-reduced-motion: reduce) {
  [data-slot="chat-tool-chevron"] {
    transition: none;
  }
}
</style>
