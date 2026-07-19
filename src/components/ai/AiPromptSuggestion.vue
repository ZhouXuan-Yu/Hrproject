<template>
  <div data-slot="prompt-suggestion" :data-variant="variant">
    <!-- Card variant: grid -->
    <div
      v-if="variant === 'card'"
      data-slot="prompt-suggestion-grid"
      :style="gridStyle"
    >
      <button
        v-for="(item, idx) in suggestions"
        :key="idx"
        data-slot="prompt-suggestion-item"
        class="suggestion-card"
        type="button"
        :aria-label="'快速开始：' + item.title"
        @click="$emit('select', item.action)"
      >
        <span data-slot="prompt-suggestion-item-title">{{ item.title }}</span>
        <span v-if="item.description" data-slot="prompt-suggestion-item-desc">{{ item.description }}</span>
      </button>
    </div>

    <!-- Pill variant: horizontal row -->
    <div
      v-else
      data-slot="prompt-suggestion-row"
    >
      <button
        v-for="(item, idx) in suggestions"
        :key="idx"
        data-slot="prompt-suggestion-item"
        class="suggestion-pill"
        type="button"
        :aria-label="'快速开始：' + item.title"
        @click="$emit('select', item.action)"
      >
        {{ item.title }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  suggestions: {
    type: Array,
    default: () => [],
  },
  variant: {
    type: String,
    default: 'card',
    validator: (v) => ['pill', 'card'].includes(v),
  },
  columns: {
    type: Number,
    default: 2,
  },
});

defineEmits(['select']);

const gridStyle = computed(() => ({
  gridTemplateColumns: `repeat(${props.columns}, 1fr)`,
}));
</script>

<style scoped>
/* Root */
[data-slot="prompt-suggestion"] {
  width: 100%;
}

/* Grid for cards */
[data-slot="prompt-suggestion-grid"] {
  display: grid;
  gap: 12px;
}

/* Row for pills */
[data-slot="prompt-suggestion-row"] {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

/* ---- Card variant ---- */
.suggestion-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  background: var(--c-card, #FFFFFF);
  border: 1px solid var(--c-border, #E1E6EF);
  border-radius: var(--radius, 8px);
  text-align: left;
  font-family: inherit;
  cursor: pointer;
  transition: border-color 0.2s ease, transform 0.15s ease;
  width: 100%;
}
.suggestion-card:hover {
  border-color: var(--c-primary, #4F6EF7);
  transform: scale(1.01);
}
.suggestion-card:active {
  transform: scale(0.985);
}
.suggestion-card:focus-visible {
  outline: 2px solid var(--c-primary, #4F6EF7);
  outline-offset: 2px;
  border-radius: var(--radius, 8px);
}

[data-slot="prompt-suggestion-item-title"] {
  font-size: var(--fs-body, 14px);
  font-weight: 600;
  color: var(--c-text, #172033);
  line-height: 1.4;
}

[data-slot="prompt-suggestion-item-desc"] {
  font-size: var(--fs-caption, 12px);
  color: var(--c-sub, #8C95A6);
  line-height: 1.5;
}

/* ---- Pill variant ---- */
.suggestion-pill {
  display: inline-flex;
  align-items: center;
  padding: 7px 16px;
  background: var(--c-card, #FFFFFF);
  border: 1px solid var(--c-border, #E1E6EF);
  border-radius: var(--radius-sm, 6px);
  font-family: inherit;
  font-size: var(--fs-body, 14px);
  color: var(--c-body, #5B6475);
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
  white-space: nowrap;
}
.suggestion-pill:hover {
  background: var(--c-primary-subtle, rgba(79,110,247,0.08));
  border-color: var(--c-primary, #4F6EF7);
}
.suggestion-pill:active {
  transform: scale(0.97);
}
.suggestion-pill:focus-visible {
  outline: 2px solid var(--c-primary, #4F6EF7);
  outline-offset: 2px;
  border-radius: var(--radius-sm, 6px);
}

/* Reduced motion */
@media (prefers-reduced-motion: reduce) {
  .suggestion-card,
  .suggestion-pill {
    transition: none;
  }
  .suggestion-card:hover,
  .suggestion-pill:hover {
    transform: none;
  }
  .suggestion-card:active,
  .suggestion-pill:active {
    transform: none;
  }
}

@media (max-width: 768px) {
  [data-slot="prompt-suggestion"] {
    grid-template-columns: 1fr !important;
    gap: 8px;
  }
  [data-slot="prompt-suggestion-item"] {
    padding: 10px 14px;
    font-size: 12px;
  }
}
</style>
