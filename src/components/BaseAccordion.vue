<template>
  <div class="accordion" :class="{ open: isOpen }">
    <div class="accordion-header" role="button" :tabindex="0" :aria-expanded="isOpen"
      data-accordion-enhanced="true"
      @click="toggle" @keydown.enter.prevent="toggle" @keydown.space.prevent="toggle">
      {{ title }} <span class="accordion-arrow">▾</span>
    </div>
    <div class="accordion-body" v-show="isOpen">
      <slot></slot>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';

const props = defineProps({
  title: { type: String, required: true },
  open: { type: Boolean, default: false },
});

const isOpen = ref(props.open);

defineExpose({ isOpen });

function toggle(){
  isOpen.value = !isOpen.value;
}
</script>
