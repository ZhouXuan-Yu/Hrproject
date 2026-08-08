<template>
  <Teleport to="body">
    <div class="modal-overlay open" @click.self="close">
      <div class="modal-box" role="dialog" :aria-modal="true" :aria-label="title || ''">
        <!-- Header -->
        <div class="modal-header" v-if="$slots.header || title">
          <slot name="header">
            <h3 class="modal-title">{{ title }}</h3>
          </slot>
          <button class="drawer-close" @click="close" aria-label="关闭">&times;</button>
        </div>
        <!-- No-header fallback close button -->
        <button v-if="!$slots.header && !title" class="drawer-close drawer-close--float" @click="close" aria-label="关闭">&times;</button>

        <!-- Body -->
        <div class="modal-body">
          <slot></slot>
        </div>

        <!-- Footer -->
        <div class="modal-footer" v-if="$slots.footer">
          <slot name="footer"></slot>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
defineProps({
  title: { type: String, default: '' },
});

const emit = defineEmits(['close']);

function close() {
  emit('close');
}
</script>

<style scoped>
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.modal-header h3 {
  margin-bottom: 0;
}
.modal-body {
  margin-bottom: 16px;
}
.modal-body:last-child {
  margin-bottom: 0;
}
.modal-footer {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 20px;
}
.drawer-close--float {
  position: absolute;
  top: 12px;
  right: 12px;
}
</style>
