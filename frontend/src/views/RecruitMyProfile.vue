<template>
  <WorkbenchLayout title="我的档案" :breadcrumb="{ text: '个人中心', href: '/my-profile' }">
    <div class="profile-page">
      <DataLoadingOverlay :visible="loading" />
      <div v-if="error" class="error-banner">{{ error }}</div>
      <template v-if="profile">
        <section class="profile-header">
          <div>
            <div class="eyebrow">员工档案</div>
            <h2>{{ profile.name }}</h2>
            <p>{{ profile.id }} · {{ profile.dept }} · {{ profile.pos }}</p>
          </div>
          <StatusBadge :type="profile.transfer ? 'done' : 'warn'">
            {{ profile.transfer ? '可调岗' : '不可调岗' }}
          </StatusBadge>
        </section>
        <section class="profile-grid">
          <div class="profile-item"><span>综合评估</span><strong>{{ profile.grade }} · {{ profile.score }}</strong></div>
          <div class="profile-item"><span>绩效</span><strong>{{ profile.perf }}</strong></div>
          <div class="profile-item"><span>工龄</span><strong>{{ profile.years }}</strong></div>
          <div class="profile-item"><span>最近晋升</span><strong>{{ profile.lastPromote }}</strong></div>
        </section>
        <div v-if="profile.restrictReason" class="notice">调岗限制：{{ profile.restrictReason }}</div>
        <p class="readonly-note">以上信息来自员工档案，仅支持查看。如需修改，请联系 HR。</p>
      </template>
      <EmptyState v-else-if="!loading" title="暂无员工档案" description="当前账号尚未关联员工档案，请联系 HR 管理员" />
    </div>
  </WorkbenchLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import WorkbenchLayout from '../layouts/WorkbenchLayout.vue';
import DataLoadingOverlay from '../components/DataLoadingOverlay.vue';
import EmptyState from '../components/EmptyState.vue';
import { fetchMyEmployeeProfile } from '../api/talent.js';

const profile = ref(null);
const loading = ref(true);
const error = ref('');

onMounted(async () => {
  try {
    profile.value = await fetchMyEmployeeProfile();
  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || '本人档案加载失败';
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
.profile-page { position: relative; min-height: 260px; }
.profile-header { display:flex; align-items:center; justify-content:space-between; padding:24px; background:var(--c-card); border:1px solid var(--c-border); border-radius:8px; }
.eyebrow { color:var(--c-sub); font-size:12px; margin-bottom:8px; }
h2 { margin:0; font-size:22px; color:var(--c-text); }
.profile-header p { margin:8px 0 0; color:var(--c-sub); font-size:13px; }
.profile-grid { display:grid; grid-template-columns:repeat(4, 1fr); gap:12px; margin-top:12px; }
.profile-item { padding:18px; background:var(--c-card); border:1px solid var(--c-border); border-radius:8px; }
.profile-item span { display:block; color:var(--c-sub); font-size:12px; margin-bottom:8px; }
.profile-item strong { color:var(--c-text); font-size:18px; }
.notice { margin-top:12px; padding:12px 16px; color:var(--c-warn); background:#FFF8E1; border:1px solid #FDE68A; border-radius:8px; font-size:13px; }
.readonly-note { margin:16px 0; color:var(--c-sub); font-size:12px; }
@media (max-width: 720px) { .profile-grid { grid-template-columns:repeat(2, 1fr); } .profile-header { gap:16px; align-items:flex-start; } }
</style>
