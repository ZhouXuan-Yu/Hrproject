<template>
  <div class="boss-integration">
    <div v-if="compact" class="boss-compact">
      <span class="boss-status" :class="statusState">
        <svg width="10" height="10" viewBox="0 0 24 24" fill="currentColor" style="vertical-align:-1px;margin-right:4px">
          <circle cx="12" cy="12" r="10"/>
        </svg>
        BOSS 直聘 {{ statusState === 'connected' ? '已连接' : '未连接' }}
      </span>
      <button v-if="statusState !== 'connected'" class="btn btn-outline btn-sm" @click="handleLogin" :disabled="loginLoading">
        {{ loginLoading ? '连接中...' : '连接' }}
      </button>
      <button v-else class="btn btn-ghost btn-sm" @click="statusState = 'disconnected'">断开</button>
    </div>

    <!-- Login modal -->
    <Teleport to="body">
      <div v-if="showBossLoginModal" class="boss-modal-overlay" @click.self="dismissBossLoginModal">
        <div class="boss-modal-box" role="dialog" aria-modal="true" aria-label="BOSS直聘登录">
          <div class="boss-modal-header">
            <h3>BOSS直聘连接</h3>
            <button class="boss-modal-close" @click="dismissBossLoginModal" aria-label="关闭">&#10005;</button>
          </div>
          <div class="boss-modal-body">
            <div v-if="loginLoading" class="boss-loading-state">
              <div class="boss-spinner"></div>
              <p>正在连接 BOSS 直聘...</p>
              <p class="boss-hint">请在打开的浏览器窗口中完成扫码登录</p>
            </div>
            <div v-else-if="loginError" class="boss-error-state">
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#EF4444" stroke-width="1.5" stroke-linecap="round">
                <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
              </svg>
              <p class="boss-error-msg">{{ loginError }}</p>
              <button class="btn btn-primary btn-sm" @click="handleLogin">重试</button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { api } from '../api/index.js';
import { useToast } from '../composables/useToast.js';

defineProps({
  compact: { type: Boolean, default: false },
});

const { toast } = useToast();

const statusState = ref('disconnected');
const showBossLoginModal = ref(false);
const loginLoading = ref(false);
const loginError = ref('');
let loginTimeoutId = null;

function handleLogin() {
  if (loginTimeoutId) return;
  showBossLoginModal.value = true;
  loginLoading.value = true;
  loginError.value = '';
  loginTimeoutId = setTimeout(() => {
    loginError.value = '登录超时（120s）。请确认 boss-cli 已安装且未运行其他浏览器实例。';
    loginLoading.value = false;
    loginTimeoutId = null;
  }, 120_000);
  api.post('/boss/login')
    .then((res) => {
      const data = res?.data || res || {};
      if (data?.logged_in === true || data?.success === true || data?.status === 'connected') {
        statusState.value = 'connected';
        showBossLoginModal.value = false;
        toast.success('BOSS直聘已连接成功');
      } else {
        loginError.value = data?.message || data?.error || '登录未成功，请手动扫码后在 Boss 浏览器中完成登录';
      }
    })
    .catch((e) => {
      loginError.value = e?.message || e?.error || '登录请求失败，请检查后端 /api/boss/login 端点';
    })
    .finally(() => {
      loginLoading.value = false;
      clearTimeout(loginTimeoutId);
      loginTimeoutId = null;
    });
}

function dismissBossLoginModal() {
  showBossLoginModal.value = false;
  loginError.value = '';
  if (loginTimeoutId) {
    clearTimeout(loginTimeoutId);
    loginTimeoutId = null;
  }
  loginLoading.value = false;
}
</script>

<style scoped>
.boss-integration { font-size: 13px; }
.boss-compact { display: flex; align-items: center; gap: 8px; }
.boss-status { display: inline-flex; align-items: center; padding: 2px 10px; border-radius: 4px; background: var(--c-bg-alt, #f0f2f5); font-size: 12px; }
.boss-status.connected { color: var(--c-done, #22C55E); }
.boss-status.disconnected { color: var(--c-draft, #9CA3AF); }

/* Modal overlay */
.boss-modal-overlay {
  position: fixed; inset: 0;
  background: rgba(15,23,42,0.38);
  backdrop-filter: blur(2px);
  z-index: 500;
  display: flex; align-items: center; justify-content: center;
}
.boss-modal-box {
  width: 380px; max-width: 90vw;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 24px 80px rgba(15,23,42,0.2);
}
.boss-modal-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 20px; border-bottom: 1px solid #EFF3F8;
}
.boss-modal-header h3 { margin: 0; font-size: 16px; font-weight: 700; color: #172033; }
.boss-modal-close {
  width: 28px; height: 28px; border-radius: 6px;
  border: none; background: transparent;
  color: #8C95A6; cursor: pointer; font-size: 14px;
}
.boss-modal-close:hover { background: #F2F5FA; color: #172033; }
.boss-modal-body { padding: 32px 20px; text-align: center; }
.boss-loading-state p { margin: 8px 0 0; font-size: 14px; color: #172033; }
.boss-hint { font-size: 12px !important; color: #8C95A6 !important; margin-top: 4px !important; }
.boss-spinner {
  width: 32px; height: 32px; border: 3px solid #E1E6EF;
  border-top-color: #4F6EF7; border-radius: 50%;
  margin: 0 auto 12px; animation: boss-spin 0.8s linear infinite;
}
@keyframes boss-spin { to { transform: rotate(360deg); } }
.boss-error-state p { margin: 12px 0; font-size: 13px; color: #EF4444; line-height: 1.6; }
.boss-error-msg { word-break: break-word; }
</style>
