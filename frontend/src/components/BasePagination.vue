<template>
  <div class="pagination-bar" v-if="totalPages > 0">
    <span class="pg-info">共 {{ total }} 条</span>

    <select class="pg-size" :value="pageSize" @change="onSizeChange">
      <option v-for="s in sizeOptions" :key="s" :value="s">{{ s }} 条/页</option>
    </select>

    <button class="pg-btn" :disabled="currentPage <= 1" @click="goTo(currentPage - 1)">上一页</button>

    <template v-for="(p, i) in visiblePages" :key="i">
      <span v-if="p === '...'" class="pg-ellipsis">...</span>
      <button v-else class="pg-btn pg-num" :class="{ active: p === currentPage }" @click="goTo(p)">{{ p }}</button>
    </template>

    <button class="pg-btn" :disabled="currentPage >= totalPages" @click="goTo(currentPage + 1)">下一页</button>

    <span class="pg-jump-label">跳至</span>
    <input
      class="pg-jump-input"
      :value="jumpValue"
      @input="onJumpInput"
      @keyup.enter="doJump"
      @blur="doJump"
    />
    <span class="pg-jump-label">页</span>
    <button class="pg-btn" @click="doJump">确定</button>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue';

const props = defineProps({
  currentPage: { type: Number, default: 1 },
  totalPages: { type: Number, default: 0 },
  total: { type: Number, default: 0 },
  pageSize: { type: Number, default: 20 },
  sizeOptions: { type: Array, default: () => [10, 20, 50, 100] },
});

const emit = defineEmits(['update:currentPage', 'update:pageSize']);

const jumpValue = ref(String(props.currentPage));

watch(() => props.currentPage, (v) => { jumpValue.value = String(v); });

const visiblePages = computed(() => {
  const tp = props.totalPages;
  const cp = props.currentPage;
  if (tp <= 7) {
    const arr = [];
    for (let i = 1; i <= tp; i++) arr.push(i);
    return arr;
  }
  const pages = [1];
  const delta = 2;
  const left = Math.max(2, cp - delta);
  const right = Math.min(tp - 1, cp + delta);
  if (left > 2) pages.push('...');
  for (let i = left; i <= right; i++) pages.push(i);
  if (right < tp - 1) pages.push('...');
  pages.push(tp);
  return pages;
});

function goTo(p) {
  const num = parseInt(p, 10);
  if (isNaN(num) || num < 1 || num > props.totalPages) return;
  emit('update:currentPage', num);
}

function onJumpInput(e) {
  jumpValue.value = e.target.value;
}

function doJump() {
  const n = parseInt(jumpValue.value, 10);
  if (isNaN(n)) { jumpValue.value = String(props.currentPage); return; }
  goTo(n);
}

function onSizeChange(e) {
  emit('update:pageSize', parseInt(e.target.value, 10));
  emit('update:currentPage', 1);
}
</script>

<style scoped>
.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 14px 0 4px;
  font-size: 13px;
  color: var(--c-body);
  flex-wrap: wrap;
}
.pg-info { color: var(--c-sub); font-variant-numeric: tabular-nums; margin-right: 4px; }
.pg-size {
  padding: 4px 8px; border: 1px solid var(--c-border); border-radius: 6px;
  font-size: 12px; background: var(--c-card); color: var(--c-text); cursor: pointer;
  margin-right: 8px;
}
.pg-btn {
  min-width: 32px; height: 30px; padding: 0 8px;
  border: 1px solid var(--c-border); border-radius: 6px;
  background: var(--c-card); color: var(--c-body);
  font-size: 13px; cursor: pointer; font-variant-numeric: tabular-nums;
  transition: all .15s;
}
.pg-btn:hover:not(:disabled) { border-color: var(--c-primary); color: var(--c-primary); }
.pg-btn:disabled { opacity: .4; cursor: not-allowed; }
.pg-btn.active { background: var(--c-primary); color: #fff; border-color: var(--c-primary); font-weight: 700; }
.pg-ellipsis { padding: 0 4px; color: var(--c-sub); }
.pg-jump-label { color: var(--c-sub); font-size: 12px; }
.pg-jump-input {
  width: 46px; height: 28px; text-align: center; font-size: 13px;
  border: 1px solid var(--c-border); border-radius: 6px; padding: 0 4px;
  font-variant-numeric: tabular-nums;
}
@media (max-width: 640px) {
  .pg-info, .pg-jump-label { display: none; }
}
</style>
