<template>
  <div
    data-slot="chat-message"
    :data-role="role"
    :data-status="status"
  >
    <!-- AI avatar (left) -->
    <div v-if="role === 'ai'" data-slot="chat-message-avatar" aria-hidden="true">
      <span data-slot="chat-message-avatar-text">{{ aiLabel }}</span>
    </div>

    <div data-slot="chat-message-body">
      <div data-slot="chat-message-bubble">
        <!-- Loading state: skeleton -->
        <AiSkeleton v-if="status === 'loading'" variant="text" :lines="3" />

        <!-- Streaming state: dots + shimmer -->
        <template v-else-if="status === 'streaming'">
          <AiSkeleton variant="dots" />
          <div v-if="streamingContent" data-slot="chat-message-shimmer">{{ streamingContent }}</div>
        </template>

        <!-- Error state -->
        <div v-else-if="status === 'error'" data-slot="chat-message-error">
          <svg viewBox="0 0 24 24" style="width:16px;height:16px;flex-shrink:0" aria-hidden="true">
            <circle cx="12" cy="12" r="10" stroke="var(--c-reject)" stroke-width="2" fill="none"/>
            <line x1="12" y1="8" x2="12" y2="12" stroke="var(--c-reject)" stroke-width="2" stroke-linecap="round"/>
            <line x1="12" y1="16" x2="12.01" y2="16" stroke="var(--c-reject)" stroke-width="2" stroke-linecap="round"/>
          </svg>
          <span><slot name="error">生成失败，请重试</slot></span>
        </div>

        <!-- Complete / ready: render content -->
        <template v-else>
          <div data-slot="chat-message-content">
            <slot />
          </div>
        </template>
      </div>

      <!-- Timestamp -->
      <div v-if="timestamp" data-slot="chat-message-timestamp">{{ timestamp }}</div>

      <!-- Actions bar (copy, regenerate, etc.) -->
      <div v-if="status === 'complete' && $slots.actions" data-slot="chat-message-actions">
        <slot name="actions" />
      </div>
    </div>

    <!-- User avatar (right) -->
    <div v-if="role === 'user'" data-slot="chat-message-avatar" aria-hidden="true">
      <span data-slot="chat-message-avatar-text">{{ userInitial }}</span>
    </div>
  </div>
</template>

<script setup>
import AiSkeleton from './AiSkeleton.vue';

defineProps({
  role: {
    type: String,
    default: 'ai',
    validator: (v) => ['user', 'ai'].includes(v),
  },
  status: {
    type: String,
    default: 'complete',
    validator: (v) => ['loading', 'streaming', 'complete', 'error'].includes(v),
  },
  streamingContent: { type: String, default: '' },
  timestamp: { type: String, default: '' },
  userInitial: { type: String, default: 'HR' },
  aiLabel: { type: String, default: 'AI' },
});
</script>

<style scoped>
[data-slot="chat-message"] {
  display: flex;
  gap: 12px;
  padding: 12px 0;
  align-items: flex-start;
  max-width: 100%;
}
[data-slot="chat-message"][data-role="user"] {
  flex-direction: row-reverse;
}

/* Avatar */
[data-slot="chat-message-avatar"] {
  width: 30px;
  height: 30px;
  border-radius: var(--radius-sm, 6px);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 700;
}
[data-slot="chat-message"][data-role="ai"] [data-slot="chat-message-avatar"] {
  background: var(--c-primary-subtle, rgba(79,110,247,0.08));
  color: var(--c-primary, #4F6EF7);
}
[data-slot="chat-message"][data-role="user"] [data-slot="chat-message-avatar"] {
  background: var(--c-bg, #F6F8FB);
  color: var(--c-body, #5B6475);
}

/* Body */
[data-slot="chat-message-body"] {
  flex: 1;
  min-width: 0;
}

/* Bubble */
[data-slot="chat-message-bubble"] {
  border-radius: var(--radius-lg, 12px);
  padding: 14px 18px;
  font-size: var(--fs-body, 14px);
  line-height: 1.7;
  color: var(--c-text, #172033);
}
[data-slot="chat-message"][data-role="ai"] [data-slot="chat-message-bubble"] {
  background: #F0F4FF;
  border-top-left-radius: var(--radius-sm, 6px);
}
[data-slot="chat-message"][data-role="user"] [data-slot="chat-message-bubble"] {
  background: var(--c-bg, #F6F8FB);
  border-top-right-radius: var(--radius-sm, 6px);
}
[data-slot="chat-message"][data-status="error"] [data-slot="chat-message-bubble"] {
  background: #FEF2F2;
}

/* Error */
[data-slot="chat-message-error"] {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--c-reject, #EF4444);
  font-size: var(--fs-caption, 12px);
}

/* Streaming shimmer */
[data-slot="chat-message-shimmer"] {
  display: block;
  margin-top: 8px;
  white-space: pre-wrap;
  word-break: break-word;
}

/* Timestamp */
[data-slot="chat-message-timestamp"] {
  font-size: 11px;
  color: var(--c-sub, #8C95A6);
  margin-top: 4px;
  padding: 0 4px;
}

/* Actions */
[data-slot="chat-message-actions"] {
  display: flex;
  gap: 6px;
  margin-top: 6px;
  padding: 0 4px;
}
</style>
