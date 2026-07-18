<template>
  <div data-slot="chat-source">
    <template v-for="(src, idx) in visibleSources" :key="idx">
      <a
        v-if="src.href"
        data-slot="chat-source-item"
        :href="src.href"
        target="_blank"
        rel="noopener noreferrer"
        :aria-label="'来源：' + src.title"
      >
        <span data-slot="chat-source-icon" aria-hidden="true">
          <!-- Globe for url -->
          <svg
            v-if="src.type === 'url'"
            viewBox="0 0 24 24"
            style="width:14px;height:14px;stroke:currentColor;fill:none;stroke-width:2;stroke-linecap:round;stroke-linejoin:round"
          >
            <circle cx="12" cy="12" r="10"/>
            <line x1="2" y1="12" x2="22" y2="12"/>
            <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/>
          </svg>
          <!-- File for doc -->
          <svg
            v-else
            viewBox="0 0 24 24"
            style="width:14px;height:14px;stroke:currentColor;fill:none;stroke-width:2;stroke-linecap:round;stroke-linejoin:round"
          >
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
            <polyline points="14 2 14 8 20 8"/>
            <line x1="16" y1="13" x2="8" y2="13"/>
            <line x1="16" y1="17" x2="8" y2="17"/>
          </svg>
        </span>
        <span data-slot="chat-source-title">{{ src.title }}</span>
      </a>
      <span
        v-else
        data-slot="chat-source-item"
        :aria-label="'来源：' + src.title"
      >
        <span data-slot="chat-source-icon" aria-hidden="true">
          <!-- Globe for url -->
          <svg
            v-if="src.type === 'url'"
            viewBox="0 0 24 24"
            style="width:14px;height:14px;stroke:currentColor;fill:none;stroke-width:2;stroke-linecap:round;stroke-linejoin:round"
          >
            <circle cx="12" cy="12" r="10"/>
            <line x1="2" y1="12" x2="22" y2="12"/>
            <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/>
          </svg>
          <!-- File for doc -->
          <svg
            v-else
            viewBox="0 0 24 24"
            style="width:14px;height:14px;stroke:currentColor;fill:none;stroke-width:2;stroke-linecap:round;stroke-linejoin:round"
          >
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
            <polyline points="14 2 14 8 20 8"/>
            <line x1="16" y1="13" x2="8" y2="13"/>
            <line x1="16" y1="17" x2="8" y2="17"/>
          </svg>
        </span>
        <span data-slot="chat-source-title">{{ src.title }}</span>
      </span>
    </template>
    <span
      v-if="overflowCount > 0"
      data-slot="chat-source-overflow"
      :aria-label="'还有 ' + overflowCount + ' 个来源'"
    >
      +{{ overflowCount }} more
    </span>
  </div>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  sources: {
    type: Array,
    default: () => [],
  },
});

const MAX_VISIBLE = 4;

const visibleSources = computed(() => props.sources.slice(0, MAX_VISIBLE));
const overflowCount = computed(() => Math.max(0, props.sources.length - MAX_VISIBLE));
</script>

<style scoped>
[data-slot="chat-source"] {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

[data-slot="chat-source-item"] {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  background: var(--c-surface-elevated, #F9FAFC);
  border: 1px solid var(--c-border-light, #EFF1F5);
  border-radius: var(--radius-sm, 6px);
  font-size: var(--fs-caption, 12px);
  color: var(--c-body, #5B6475);
  text-decoration: none;
  transition: border-color 0.15s, background 0.15s;
  line-height: 1.4;
}

a[data-slot="chat-source-item"] {
  color: var(--c-primary, #4F6EF7);
}

a[data-slot="chat-source-item"]:hover {
  background: var(--c-primary-subtle, rgba(79,110,247,0.08));
  border-color: var(--c-primary, #4F6EF7);
}

a[data-slot="chat-source-item"]:focus-visible {
  outline: 2px solid var(--c-primary, #4F6EF7);
  outline-offset: 2px;
  border-radius: var(--radius-sm, 6px);
}

[data-slot="chat-source-icon"] {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  color: var(--c-sub, #8C95A6);
}

a[data-slot="chat-source-item"] [data-slot="chat-source-icon"] {
  color: var(--c-primary, #4F6EF7);
}

[data-slot="chat-source-title"] {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 160px;
}

[data-slot="chat-source-overflow"] {
  font-size: var(--fs-caption, 12px);
  color: var(--c-sub, #8C95A6);
  padding: 4px 6px;
}

@media (prefers-reduced-motion: reduce) {
  [data-slot="chat-source-item"] {
    transition: none;
  }
}
</style>
