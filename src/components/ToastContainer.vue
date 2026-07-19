<template>
  <Teleport to="body">
    <div class="toast-container" role="status" aria-live="polite" aria-label="通知提示">
      <TransitionGroup name="toast-slide">
        <div
          v-for="t in toasts"
          :key="t.id"
          class="toast-item"
          :class="[`toast--${t.type}`]"
          @mouseenter="pauseDismiss(t)"
          @mouseleave="resumeDismiss(t)"
        >
          <div class="toast-content">
            <span class="toast-icon" aria-hidden="true" v-html="iconMap[t.type]"></span>
            <span class="toast-message">{{ t.message }}</span>
          </div>
          <button
            class="toast-close"
            aria-label="关闭通知"
            @click="removeToast(t.id)"
          >✕</button>
          <div
            v-if="t.duration > 0"
            class="toast-progress"
            :style="{ animationDuration: t.duration + 'ms' }"
            :class="{ 'toast-progress--paused': t._paused }"
          ></div>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<script setup>
import { useToast } from '../composables/useToast.js';

const { toasts, removeToast } = useToast();

const iconMap = {
  success: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>',
  error: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>',
  warning: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>',
  info: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>',
};

function pauseDismiss(t) {
  t._paused = true;
}

function resumeDismiss(t) {
  t._paused = false;
}
</script>

<style scoped>
.toast-container {
  position: fixed;
  top: 16px;
  right: 16px;
  z-index: 9999;
  display: flex;
  flex-direction: column;
  gap: 8px;
  pointer-events: none;
  max-width: 400px;
  width: 100%;
}

.toast-item {
  pointer-events: auto;
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px 16px;
  background: #FFFFFF;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12), 0 1px 4px rgba(0, 0, 0, 0.06);
  border-left: 4px solid transparent;
  position: relative;
  overflow: hidden;
  min-height: 48px;
}

.toast-content {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.toast-icon {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  margin-top: 1px;
}

.toast-message {
  font-size: 14px;
  line-height: 1.5;
  color: #172033;
  word-break: break-word;
}

.toast-close {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: #8C95A6;
  font-size: 12px;
  cursor: pointer;
  border-radius: 4px;
  padding: 0;
  transition: color 0.15s, background 0.15s;
}

.toast-close:hover {
  color: #172033;
  background: #EFF1F5;
}

.toast-progress {
  position: absolute;
  bottom: 0;
  left: 0;
  height: 3px;
  background: currentColor;
  opacity: 0.25;
  animation: toast-progress-shrink linear forwards;
}

.toast-progress--paused {
  animation-play-state: paused;
}

/* Type-based colors */
.toast--success { border-left-color: #22C55E; }
.toast--success .toast-icon { background: #22C55E; }
.toast--success .toast-progress { color: #22C55E; }

.toast--error { border-left-color: #EF4444; }
.toast--error .toast-icon { background: #EF4444; }
.toast--error .toast-progress { color: #EF4444; }

.toast--warning { border-left-color: #F59E0B; }
.toast--warning .toast-icon { background: #F59E0B; }
.toast--warning .toast-progress { color: #F59E0B; }

.toast--info { border-left-color: #4F6EF7; }
.toast--info .toast-icon { background: #4F6EF7; }
.toast--info .toast-progress { color: #4F6EF7; }

/* Slide-in animation */
.toast-slide-enter-active {
  animation: toast-in 0.3s ease-out;
}

.toast-slide-leave-active {
  animation: toast-out 0.2s ease-in forwards;
}

@keyframes toast-in {
  from {
    transform: translateX(120%);
    opacity: 0;
  }
  to {
    transform: translateX(0);
    opacity: 1;
  }
}

@keyframes toast-out {
  from {
    transform: translateX(0);
    opacity: 1;
  }
  to {
    transform: translateX(120%);
    opacity: 0;
  }
}

@keyframes toast-progress-shrink {
  from { width: 100%; }
  to { width: 0%; }
}

/* prefers-reduced-motion */
@media (prefers-reduced-motion: reduce) {
  .toast-slide-enter-active,
  .toast-slide-leave-active {
    animation: none;
  }

  .toast-slide-enter-active {
    opacity: 0;
  }

  .toast-slide-enter-to {
    opacity: 1;
    transition: opacity 0.15s;
  }

  .toast-slide-leave-active {
    opacity: 1;
  }

  .toast-slide-leave-to {
    opacity: 0;
    transition: opacity 0.15s;
  }

  .toast-progress {
    animation: none;
  }
}
</style>
