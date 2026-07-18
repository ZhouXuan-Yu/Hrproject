<template>
  <span class="st" :class="statusClass"><slot></slot></span>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  type: {
    type: String,
    default: 'draft',
    validator: v => ['warn','progress','done','reject','draft'].includes(v),
  },
});

const statusClass = computed(() => ({
  'st-warn': props.type === 'warn',
  'st-progress': props.type === 'progress',
  'st-done': props.type === 'done',
  'st-reject': props.type === 'reject',
  'st-draft': props.type === 'draft',
}));
</script>
