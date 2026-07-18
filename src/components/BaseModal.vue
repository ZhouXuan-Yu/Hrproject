<template>
  <Teleport to="body">
    <div class="modal-overlay" :class="{ open: visible }" v-if="visible || isAnimating" @click.self="close">
      <div class="modal-box" role="dialog" :aria-modal="true" :aria-label="title">
        <slot></slot>
        <div class="modal-actions" v-if="$slots.actions">
          <slot name="actions"></slot>
        </div>
        <button class="drawer-close" @click="close" aria-label="关闭" style="position:absolute;top:12px;right:12px">✕</button>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue';

const props = defineProps({
  visible: { type: Boolean, default: false },
  title: { type: String, default: '' },
});

const emit = defineEmits(['close']);
const isAnimating = ref(false);

function close(){
  emit('close');
}

watch(() => props.visible, (v) => {
  if (v) {
    isAnimating.value = true;
    nextTick(() => {
      const overlay = document.querySelector('.modal-overlay.open');
      if (overlay) overlay.classList.add('open');
    });
  } else {
    isAnimating.value = false;
  }
});
</script>
