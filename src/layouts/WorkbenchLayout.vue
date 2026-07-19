<template>
  <div class="legacy-page-shell" :data-route="routeId">
    <ToastContainer />

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

const props = defineProps({
  title: { type: String, default: '' },
  breadcrumb: { type: Object, default: null },
});

const router = useRouter();
const route = useRoute();
const navOpen = ref(true);

const routeId = computed(() => route.path.replace(/^\//, '') || 'recruit-dashboard');
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
</style>
