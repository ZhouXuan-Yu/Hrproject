<template>
  <div v-if="visible" class="mock-banner" role="alert">
    <span class="mock-banner-icon">ⓘ</span>
    <span class="mock-banner-text">当前数据为演示数据，{{ message }}</span>
    <button class="mock-banner-close" @click="visible = false" aria-label="关闭提示">×</button>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';

const props = defineProps({
  message: { type: String, default: '后端服务未连接或数据库不可用。' },
  autoDismiss: { type: Boolean, default: false },
  dismissAfter: { type: Number, default: 60000 },
});

const visible = ref(true);

onMounted(() => {
  if (props.autoDismiss) {
    setTimeout(() => { visible.value = false; }, props.dismissAfter);
  }
});
</script>

<style scoped>
.mock-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  margin-bottom: 12px;
  background: #FFF8E1;
  border: 1px solid #FFE082;
  border-radius: 6px;
  font-size: 12px;
  color: #795548;
}
.mock-banner-icon { font-size: 14px; flex-shrink: 0; }
.mock-banner-text { flex: 1; }
.mock-banner-close {
  background: none;
  border: none;
  font-size: 16px;
  cursor: pointer;
  color: #795548;
  padding: 0 4px;
  line-height: 1;
}
</style>
