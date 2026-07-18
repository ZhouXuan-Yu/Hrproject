<template>
  <div data-slot="chat-conversation">
    <div
      ref="scrollContainerRef"
      data-slot="chat-conversation-content"
      @scroll="onScroll"
    >
      <slot></slot>
    </div>

    <!-- Scroll to bottom button -->
    <Transition name="ct-scroll-btn">
      <button
        v-if="showScrollButton"
        data-slot="chat-conversation-scroll-button"
        type="button"
        aria-label="滚动到底部"
        @click="scrollToBottom(true)"
      >
        <svg viewBox="0 0 24 24" style="width:16px;height:16px;stroke:currentColor;fill:none;stroke-width:2.5;stroke-linecap:round;stroke-linejoin:round" aria-hidden="true">
          <polyline points="6 9 12 15 18 9"/>
        </svg>
      </button>
    </Transition>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue';

const props = defineProps({
  streaming: { type: Boolean, default: false },
});

const scrollContainerRef = ref(null);
const showScrollButton = ref(false);
const isUserNearBottom = ref(true);

let resizeObserver = null;
let mutationObserver = null;
let scrollTimer = null;

function isNearBottom() {
  const el = scrollContainerRef.value;
  if (!el) return true;
  return el.scrollHeight - el.scrollTop - el.clientHeight < 60;
}

function onScroll() {
  const near = isNearBottom();
  isUserNearBottom.value = near;
  showScrollButton.value = !near;
}

function scrollToBottom(smooth = false) {
  const el = scrollContainerRef.value;
  if (!el) return;
  nextTick(() => {
    if (!scrollContainerRef.value) return;
    scrollContainerRef.value.scrollTo({
      top: scrollContainerRef.value.scrollHeight,
      behavior: smooth ? 'smooth' : 'instant',
    });
  });
}

function onContentChange() {
  if (isUserNearBottom.value) {
    scrollToBottom(false);
  }
}

function setupObservers() {
  const el = scrollContainerRef.value;
  if (!el) return;

  // ResizeObserver on the content container
  resizeObserver = new ResizeObserver(() => {
    if (isUserNearBottom.value) {
      scrollToBottom(false);
    }
  });
  resizeObserver.observe(el);

  // MutationObserver as fallback for slot content changes
  mutationObserver = new MutationObserver(() => {
    if (isUserNearBottom.value) {
      scrollToBottom(false);
    }
  });
  mutationObserver.observe(el, {
    childList: true,
    subtree: true,
    characterData: true,
  });
}

function teardownObservers() {
  if (resizeObserver) {
    resizeObserver.disconnect();
    resizeObserver = null;
  }
  if (mutationObserver) {
    mutationObserver.disconnect();
    mutationObserver = null;
  }
  if (scrollTimer) {
    clearTimeout(scrollTimer);
    scrollTimer = null;
  }
}

// More aggressive auto-scroll while streaming
watch(
  () => props.streaming,
  (streaming) => {
    if (streaming) {
      // Poll for scroll while streaming
      const poll = () => {
        if (!props.streaming) return;
        if (isUserNearBottom.value) {
          scrollToBottom(false);
        }
        scrollTimer = setTimeout(poll, 150);
      };
      poll();
    } else {
      if (scrollTimer) {
        clearTimeout(scrollTimer);
        scrollTimer = null;
      }
    }
  }
);

onMounted(() => {
  setupObservers();
  nextTick(() => scrollToBottom(false));
});

onBeforeUnmount(() => {
  teardownObservers();
});
</script>

<style scoped>
[data-slot="chat-conversation"] {
  position: relative;
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

[data-slot="chat-conversation-content"] {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  scroll-behavior: auto;
  overscroll-behavior: contain;

  /* Hide scrollbar when not hovering/scrolling */
  scrollbar-width: thin;
  scrollbar-color: transparent transparent;
}
[data-slot="chat-conversation-content"]:hover {
  scrollbar-color: var(--c-border-light, #EFF1F5) transparent;
}
[data-slot="chat-conversation-content"]::-webkit-scrollbar {
  width: 5px;
}
[data-slot="chat-conversation-content"]::-webkit-scrollbar-track {
  background: transparent;
}
[data-slot="chat-conversation-content"]::-webkit-scrollbar-thumb {
  background: transparent;
  border-radius: 10px;
}
[data-slot="chat-conversation-content"]:hover::-webkit-scrollbar-thumb {
  background: var(--c-border-light, #EFF1F5);
}

/* Scroll button */
[data-slot="chat-conversation-scroll-button"] {
  position: sticky;
  bottom: 12px;
  align-self: center;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 1px solid var(--c-border, #E1E6EF);
  background: var(--c-card, #FFFFFF);
  color: var(--c-sub, #8C95A6);
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  transition: background 0.15s, color 0.15s, transform 0.1s;
  z-index: 10;
}
[data-slot="chat-conversation-scroll-button"]:hover {
  background: var(--c-primary-subtle, rgba(79,110,247,0.08));
  color: var(--c-primary, #4F6EF7);
}
[data-slot="chat-conversation-scroll-button"]:active {
  transform: scale(0.92);
}
[data-slot="chat-conversation-scroll-button"]:focus-visible {
  outline: 2px solid var(--c-primary, #4F6EF7);
  outline-offset: 2px;
}

/* Transition: fade + slight move */
.ct-scroll-btn-enter-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.ct-scroll-btn-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.ct-scroll-btn-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.ct-scroll-btn-leave-to {
  opacity: 0;
  transform: translateY(4px);
}

@media (prefers-reduced-motion: reduce) {
  [data-slot="chat-conversation-content"] {
    scroll-behavior: auto;
  }
  [data-slot="chat-conversation-scroll-button"]:active {
    transform: none;
  }
  .ct-scroll-btn-enter-active,
  .ct-scroll-btn-leave-active {
    transition: none;
  }
}
</style>
