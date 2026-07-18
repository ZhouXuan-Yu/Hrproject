<template>
  <div data-slot="chain-of-thought" :data-expanded="isExpanded ? 'true' : 'false'">
    <!-- Header toggle -->
    <button
      data-slot="chain-of-thought-header"
      type="button"
      :aria-expanded="isExpanded ? 'true' : 'false'"
      :aria-label="isExpanded ? '折叠思考过程' : '展开思考过程'"
      @click="toggleExpanded"
    >
      <span data-slot="chain-of-thought-header-icon">
        <svg viewBox="0 0 24 24" style="width:16px;height:16px;stroke:currentColor;fill:none;stroke-width:2;stroke-linecap:round;stroke-linejoin:round" aria-hidden="true">
          <circle cx="12" cy="12" r="10"/>
          <path d="M12 6v6l4 2"/>
        </svg>
      </span>
      <span data-slot="chain-of-thought-header-text">AI 思考过程</span>
      <span
        data-slot="chain-of-thought-chevron"
        aria-hidden="true"
      >
        <svg viewBox="0 0 24 24" style="width:14px;height:14px;stroke:currentColor;fill:none;stroke-width:2.5;stroke-linecap:round;stroke-linejoin:round">
          <polyline points="6 9 12 15 18 9"/>
        </svg>
      </span>
    </button>

    <!-- Steps list -->
    <div
      v-show="isExpanded"
      data-slot="chain-of-thought-body"
      role="list"
    >
      <div
        v-for="(step, idx) in steps"
        :key="idx"
        data-slot="chain-of-thought-step"
        :data-status="step.status"
        role="listitem"
      >
        <!-- Step indicator -->
        <span data-slot="chain-of-thought-step-indicator" aria-hidden="true">
          <!-- Done: green check -->
          <svg v-if="step.status === 'done'" viewBox="0 0 24 24" style="width:14px;height:14px;stroke:var(--c-done, #22C55E);fill:none;stroke-width:2.5;stroke-linecap:round;stroke-linejoin:round">
            <polyline points="20 6 9 17 4 12"/>
          </svg>
          <!-- Running: pulsating dot -->
          <span
            v-else-if="step.status === 'running'"
            data-slot="chain-of-thought-step-dot-running"
          ></span>
          <!-- Pending: gray dot -->
          <span
            v-else
            data-slot="chain-of-thought-step-dot-pending"
          ></span>
        </span>

        <!-- Step content -->
        <div data-slot="chain-of-thought-step-body">
          <!-- Label -->
          <span
            data-slot="chain-of-thought-step-label"
            :data-status="step.status"
          >
            <span
              v-if="step.status === 'running' && streaming"
              data-slot="chain-of-thought-step-shimmer"
            >{{ step.label }}</span>
            <template v-else>{{ step.label }}</template>
          </span>

          <!-- Detail content (shown when done) -->
          <div
            v-if="step.status === 'done' && step.content"
            data-slot="chain-of-thought-step-content"
          >{{ step.content }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue';

const props = defineProps({
  steps: {
    type: Array,
    default: () => [],
  },
  expanded: {
    type: Boolean,
    default: true,
  },
  streaming: {
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

function toggleExpanded() {
  isExpanded.value = !isExpanded.value;
  emit('update:expanded', isExpanded.value);
}
</script>

<style scoped>
/* Root */
[data-slot="chain-of-thought"] {
  border: 1px solid var(--c-border, #E1E6EF);
  border-radius: var(--radius, 8px);
  background: var(--c-card, #FFFFFF);
  overflow: hidden;
}

/* Header */
[data-slot="chain-of-thought-header"] {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px 14px;
  background: none;
  border: none;
  font-family: inherit;
  font-size: var(--fs-caption, 12px);
  color: var(--c-sub, #8C95A6);
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;
}
[data-slot="chain-of-thought-header"]:hover {
  background: var(--c-surface-elevated, #F9FAFC);
}
[data-slot="chain-of-thought-header"]:focus-visible {
  outline: 2px solid var(--c-primary, #4F6EF7);
  outline-offset: -2px;
  border-radius: var(--radius, 8px);
}

[data-slot="chain-of-thought-header-icon"] {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  color: var(--c-sub, #8C95A6);
}

[data-slot="chain-of-thought-header-text"] {
  font-size: var(--fs-caption, 12px);
  color: var(--c-sub, #8C95A6);
}

/* Chevron */
[data-slot="chain-of-thought-chevron"] {
  display: flex;
  align-items: center;
  margin-left: auto;
  transition: transform 0.2s ease;
  color: var(--c-sub, #8C95A6);
}
[data-slot="chain-of-thought"][data-expanded="true"] [data-slot="chain-of-thought-chevron"] {
  transform: rotate(180deg);
}

/* Body */
[data-slot="chain-of-thought-body"] {
  padding: 6px 14px 12px;
}

/* Step */
[data-slot="chain-of-thought-step"] {
  display: flex;
  gap: 8px;
  padding: 6px 0;
  font-variant-numeric: tabular-nums;
}
[data-slot="chain-of-thought-step"][data-status="pending"] {
  opacity: 0.45;
}

/* Step indicator */
[data-slot="chain-of-thought-step-indicator"] {
  display: flex;
  align-items: flex-start;
  flex-shrink: 0;
  width: 16px;
  padding-top: 3px;
}

/* Pending dot */
[data-slot="chain-of-thought-step-dot-pending"] {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--c-sub, #8C95A6);
  display: block;
  margin-top: 3px;
}

/* Running dot */
[data-slot="chain-of-thought-step-dot-running"] {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--c-primary, #4F6EF7);
  display: block;
  margin-top: 2px;
  animation: cot-pulse 1.2s ease-in-out infinite;
}
@keyframes cot-pulse {
  0%, 100% { opacity: 0.4; transform: scale(0.75); }
  50% { opacity: 1; transform: scale(1.1); }
}

/* Step body */
[data-slot="chain-of-thought-step-body"] {
  flex: 1;
  min-width: 0;
}

/* Step label */
[data-slot="chain-of-thought-step-label"] {
  font-size: var(--fs-caption, 12px);
  line-height: 1.5;
  color: var(--c-body, #5B6475);
  display: block;
}
[data-slot="chain-of-thought-step-label"][data-status="done"] {
  color: var(--c-done, #22C55E);
}

/* TextShimmer */
[data-slot="chain-of-thought-step-shimmer"] {
  display: inline-block;
  background: linear-gradient(
    90deg,
    var(--c-body, #5B6475) 0%,
    var(--c-primary, #4F6EF7) 40%,
    var(--c-body, #5B6475) 80%
  );
  background-size: 200% 100%;
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  animation: cot-shimmer 2s ease-in-out infinite;
}
@keyframes cot-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* Step content */
[data-slot="chain-of-thought-step-content"] {
  margin-top: 4px;
  font-size: var(--fs-caption, 12px);
  line-height: 1.55;
  color: var(--c-sub, #8C95A6);
  white-space: pre-wrap;
  word-break: break-word;
  padding-left: 0;
}

@media (prefers-reduced-motion: reduce) {
  [data-slot="chain-of-thought-step-dot-running"] {
    animation: none;
    opacity: 1;
    transform: scale(1);
  }
  [data-slot="chain-of-thought-step-shimmer"] {
    animation: none;
    -webkit-text-fill-color: var(--c-body, #5B6475);
  }
  [data-slot="chain-of-thought-chevron"] {
    transition: none;
  }
}
</style>
