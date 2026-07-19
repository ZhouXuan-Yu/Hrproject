<template>
  <div class="legacy-page-shell" :data-route="routeId">
    <ToastContainer />

    <!-- Offline banner -->
    <div v-if="!isOnline" class="offline-banner" role="alert">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <line x1="1" y1="1" x2="23" y2="23"/><path d="M16.72 11.06A10.94 10.94 0 0 1 19 12.55"/><path d="M5 12.55a10.94 10.94 0 0 1 5.17-2.39"/><path d="M10.71 5.05A16 16 0 0 1 22.56 9"/><path d="M1.42 9a15.91 15.91 0 0 1 4.7-2.88"/><path d="M8.53 16.11a6 6 0 0 1 6.95 0"/><line x1="12" y1="20" x2="12.01" y2="20"/>
      </svg>
      <span>网络连接已断开，部分功能不可用</span>
    </div>

    <!-- Sidebar -->
    <aside class="sidebar" id="sidebar" role="navigation" aria-label="招聘模块导航">
      <div class="logo">
        <div class="logo-icon">HR</div>
        <div class="logo-text">招聘管理系统<span>Recruitment v0.1</span></div>
      </div>
      <nav v-if="visibleMenus.length">
        <div class="nav-main-menu open">
          <div class="nav-main-label" @click="toggleNav">招聘管理 <span class="nav-arrow">▾</span></div>
          <div class="nav-flyout">
            <a
              v-for="item in visibleMenus"
              :key="item.id"
              :href="item.href"
              class="nav-flyout-item"
              :class="{ active: item.id === routeId || (routeId === 'recruit-demand-detail' && item.id === 'recruit-demand') }"
              :aria-current="item.id === routeId ? 'page' : undefined"
            ><span class="nav-dot"></span>{{ item.label }}</a>
          </div>
        </div>
      </nav>
      <div v-else style="padding:40px 18px;text-align:center;font-size:12px;color:#5a6180">
        暂无招聘模块权限<br><br>请联系管理员开通
      </div>
      <div class="user-info">
        <div class="avatar">{{ userInitial }}</div>
        <div>
          {{ user }}
          <span class="role-badge" :class="roleBadgeClass">{{ roleLabel }}</span>
        </div>
        <a class="logout" href="/login" @click.prevent="doLogout">退出</a>
      </div>
    </aside>

    <!-- Main content -->
    <main class="content">
      <header class="topbar" role="banner" v-if="title">
        <h1>{{ title }}</h1>
        <div class="breadcrumb" v-if="breadcrumb">
          <a :href="breadcrumb.href">{{ breadcrumb.text }}</a> / {{ title }}
        </div>
        <div class="spacer"></div>
        <div class="topbar-actions">
          <button
            id="commandTrigger"
            type="button"
            class="command-trigger"
            aria-label="打开快速跳转"
            title="快速跳转"
            @click="openCommandPalette"
          ><span>快速跳转</span><kbd>Ctrl K</kbd></button>
          <slot name="topbar-actions"></slot>
        </div>
      </header>
      <div class="content-body" role="main" tabindex="-1">
        <slot></slot>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { getRole, getUser, getVisibleMenus, ROLE_LABELS, ROLE_CLASS } from '../composables/useAuth.js';
import { useOnline } from '../composables/useOnline.js';
import ToastContainer from '../components/ToastContainer.vue';

const props = defineProps({
  title: { type: String, default: '' },
  breadcrumb: { type: Object, default: null },
});

const router = useRouter();
const route = useRoute();
const navOpen = ref(true);

const routeId = computed(() => route.path.replace(/^\//, '') || 'recruit-dashboard');
const { isOnline } = useOnline();
const role = computed(() => getRole());
const user = computed(() => getUser());
const userInitial = computed(() => user.value.charAt(0).toUpperCase());
const visibleMenus = computed(() => getVisibleMenus(role.value));
const roleLabel = computed(() => ROLE_LABELS[role.value] || role.value);
const roleBadgeClass = computed(() => ROLE_CLASS[role.value] || '');

function toggleNav(){
  navOpen.value = !navOpen.value;
}

function doLogout(){
  localStorage.clear();
  sessionStorage.clear();
  router.push('/login');
}

function openCommandPalette(){
  // Trigger Ctrl+K equivalent — handled by app.js command palette
  const event = new KeyboardEvent('keydown', { key: 'k', ctrlKey: true, bubbles: true });
  document.dispatchEvent(event);
}
</script>

<style scoped>
.legacy-page-shell {
  display: flex;
  min-height: 100vh;
  width: 100%;
}
/* Sidebar sticky positioning already handled by style.css (.sidebar, .content, .content-body) */

/* Offline banner */
.offline-banner {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 9000;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 8px 16px;
  background: #FEF3C7;
  color: #92400E;
  font-size: 13px;
  font-weight: 600;
  border-bottom: 1px solid #FDE68A;
}
</style>
