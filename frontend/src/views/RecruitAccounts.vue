<template>
  <WorkbenchLayout title="账号管理" :breadcrumb="{ text: '招聘管理', href: '/recruit-dashboard' }">
    <template #topbar-actions>
      <span class="admin-only">仅管理员可见</span>
    </template>

    <BaseAccordion title="账号管理" :open="true">
      <div class="accordion-desc">管理系统登录账号，包括开通工号、编辑、角色分配、停用/启用、重置密码。仅管理员可操作。</div>
    </BaseAccordion>

    <div style="display:flex;gap:8px;margin-bottom:12px;flex-wrap:wrap;align-items:center;position:relative;z-index:1">
      <span style="font-weight:600;font-size:14px">账号列表</span>
      <input type="text" v-model="userSearch" placeholder="搜索..." style="width:180px" @keyup.enter="loadUsers">
      <select v-model="userRoleFilter" @change="loadUsers" style="width:100px">
        <option value="">全部角色</option>
        <option value="admin">管理员</option><option value="hr">HR</option><option value="dept_head">部门负责人</option>
        <option value="director">总监</option><option value="employee">员工</option><option value="interviewer">面试官</option>
      </select>
      <span style="flex:1"></span>
      <button class="btn btn-primary btn-sm" type="button" @click="openUserCreate">开通工号</button>
    </div>

    <div class="table-wrap data-region" style="margin-bottom:12px">
      <table><thead><tr>
        <th>工号</th><th>姓名</th><th>部门</th><th>岗位</th><th>角色</th><th>手机号</th><th>状态</th><th style="width:140px">操作</th>
      </tr></thead><tbody>
        <tr v-for="u in userList" :key="u.userId" :class="u.status === 0 ? 'row-archived' : ''">
          <td>{{ u.employeeNo || u.username }}</td><td>{{ u.realName }}</td><td>{{ u.deptName }}</td><td>{{ u.positionName }}</td>
          <td><StatusBadge :type="u.roleCode === 'admin' ? 'done' : (u.roleCode === 'hr' ? 'progress' : 'draft')">{{ roleLabel(u.roleCode) }}</StatusBadge></td>
          <td>{{ u.mobile || '—' }}</td>
          <td><span :style="{color: u.status === 1 ? 'var(--success)' : 'var(--error)', fontWeight:600}">{{ u.statusLabel }}</span></td>
          <td class="row-actions">
            <a href="#" class="btn btn-text btn-sm" @click.prevent="openUserEdit(u)">编辑</a>
            <a href="#" class="btn btn-text btn-sm" @click.prevent="toggleUser(u)">{{ u.status === 1 ? '停用' : '启用' }}</a>
            <a href="#" class="btn btn-text btn-sm" @click.prevent="resetPwd(u)">重置密码</a>
            <a href="#" class="btn btn-text btn-sm" style="color:var(--error)" @click.prevent="deleteUserAccount(u)">删除</a>
          </td>
        </tr></tbody>
      </table>
    </div>
    <div class="table-count" style="margin-bottom:16px">共 {{ userTotal }} 个账号</div>
    <div v-if="userResetMsg" :style="{marginTop:'-8px',marginBottom:'16px',fontSize:'13px',color:userResetOk?'var(--success)':'var(--error)'}">{{ userResetMsg }}</div>

    <!-- 开通工号 / 编辑账号 弹窗 -->
    <BaseModal v-if="userDialogVisible" :title="userEditingId ? '编辑账号' : '开通工号'" @close="closeUserDialog">
      <div class="form-grid">
        <!-- 姓名：带待开通候选人下拉 -->
        <div class="form-group" style="position:relative">
          <label>姓名 <span style="color:var(--error)">*</span></label>
          <input type="text" v-model="userForm.realName" placeholder="输入姓名或从下方选择"
            @focus="showPendingDrop = true" @input="filterPendingCandidates"
            @blur="onNameBlur">
          <!-- 待开通候选人下拉 -->
          <div v-if="showPendingDrop && filteredPending.length" class="pending-dropdown">
            <div class="pending-drop-hint">待开通工号的候选人（点击自动填充）</div>
            <div v-for="p in filteredPending" :key="p.candidateId" class="pending-drop-item"
              @mousedown.prevent="selectPendingCandidate(p)">
              <span class="pending-drop-name">{{ p.candidateName }}</span>
              <span class="pending-drop-info">{{ p.positionName || '—' }} · {{ p.deptName || '—' }}</span>
              <span class="pending-drop-status">{{ p.statusLabel }}</span>
            </div>
          </div>
        </div>
        <div class="form-group"><label>工号 <span v-if="!userEditingId" style="color:var(--text-muted);font-weight:400">（留空自动生成）</span></label><input type="text" v-model="userForm.employeeNo" :disabled="!!userEditingId" placeholder="留空自动生成 年份+序号 如20260001"></div>
        <div class="form-group"><label>手机号</label><input type="text" v-model="userForm.mobile" placeholder="11位手机号"></div>
        <div class="form-group"><label>邮箱</label><input type="text" v-model="userForm.email" placeholder="选填"></div>
        <div class="form-group"><label>角色</label><select v-model="userForm.roleCode">
          <option v-for="r in roleOptions" :key="r.value" :value="r.value">{{ r.label }}</option>
        </select></div>
        <div class="form-group"><label>部门</label><select v-model="userForm.deptId"><option value="">— 请选择 —</option><option v-for="d in deptOptions" :key="d.id" :value="d.id">{{ d.name }}</option></select></div>
        <div class="form-group"><label>岗位</label><select v-model="userForm.positionId"><option value="">— 请选择 —</option><option v-for="p in positionOptions" :key="p.id" :value="p.id">{{ p.name }}</option></select></div>
      </div>
      <div v-if="!userEditingId && pendingList.length" style="margin-top:12px;padding:10px 12px;background:var(--surface-elevated);border:1px solid var(--border);border-radius:8px">
        <div style="font-size:12px;color:var(--text-muted);margin-bottom:6px">待开通工号（勾选后自动填充，可多选，可修改）</div>
        <div v-for="p in pendingList.slice(0, 5)" :key="p.candidateId" style="display:flex;align-items:center;gap:8px;padding:4px 0;font-size:13px">
          <input type="checkbox" :checked="batchPending.includes(p)" @change="toggleBatchPending(p)">
          <span>{{ p.candidateName }}</span>
          <span style="color:var(--text-muted);font-size:12px">{{ p.positionName || '—' }}</span>
          <span :style="{color: p.processStatus === 8 ? 'var(--success)' : 'var(--warning)',fontSize:'12px'}">{{ p.statusLabel }}</span>
        </div>
        <div v-if="pendingList.length > 5" style="font-size:12px;color:var(--text-muted);margin-top:4px">... 还有 {{ pendingList.length - 5 }} 人</div>
        <div style="margin-top:8px" v-if="batchPending.length">
          <button class="btn btn-outline btn-sm" type="button" @click="fillBatchPending">批量填充选中（{{ batchPending.length }}人）</button>
        </div>
      </div>
      <div v-if="userFormError" style="color:var(--error);font-size:13px;margin-top:8px">{{ userFormError }}</div>
      <div v-if="userCreateMsg" style="color:var(--success);font-size:13px;margin-top:8px;font-weight:600">{{ userCreateMsg }}</div>
      <template #footer>
        <button class="btn btn-outline btn-sm" @click="closeUserDialog">取消</button>
        <button class="btn btn-primary btn-sm" :disabled="userSaving" @click="saveUser">{{ userSaving ? '保存中...' : (userEditingId ? '保存' : '开通') }}</button>
      </template>
    </BaseModal>

    <ConfirmDialog
      v-bind="confirmDialog"
      @confirm="onConfirmDialogConfirm"
      @cancel="onConfirmDialogCancel"
    />
  </WorkbenchLayout>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue';
import WorkbenchLayout from '../layouts/WorkbenchLayout.vue';
import StatusBadge from '../components/StatusBadge.vue';
import ConfirmDialog from '../components/ConfirmDialog.vue';
import { useConfirmDialog } from '../composables/useConfirmDialog.js';
import { fetchUsers, createUser, updateUser, toggleUserStatus, deleteUser, resetUserPassword, fetchPositions, fetchPendingAccounts } from '../api/auth.js';
import { setAuth, getUserId } from '../composables/useAuth.js';

const userList = ref([]);
const userTotal = ref(0);
const userSearch = ref('');
const userRoleFilter = ref('');
const userDialogVisible = ref(false);
const userEditingId = ref(null);
const userSaving = ref(false);
const userFormError = ref('');
const userCreateMsg = ref('');
const userResetMsg = ref('');
const userResetOk = ref(false);
const userForm = reactive({ employeeNo: '', realName: '', mobile: '', email: '', roleCode: 'employee', deptId: '', positionId: '' });
const deptOptions = ref([]);
const positionOptions = ref([]);
const roleOptions = [
  { value: 'admin', label: '管理员' }, { value: 'hr', label: 'HR专员' },
  { value: 'dept_head', label: '部门负责人' }, { value: 'director', label: '总监' },
  { value: 'employee', label: '基层员工' }, { value: 'interviewer', label: '面试官' },
];

const { confirmDialog, askConfirm, onConfirmDialogConfirm, onConfirmDialogCancel } = useConfirmDialog();

// 待开通候选人
const pendingList = ref([]);
const showPendingDrop = ref(false);
const batchPending = ref([]);

const filteredPending = computed(() => {
  const kw = (userForm.realName || '').trim().toLowerCase();
  if (!kw) return pendingList.value;
  return pendingList.value.filter(p => p.candidateName.toLowerCase().includes(kw));
});

function roleLabel(code) {
  const m = { admin:'管理员', hr:'HR专员', dept_head:'部门负责人', director:'总监', employee:'基层员工', interviewer:'面试官' };
  return m[code] || code;
}

async function loadPending() {
  try { pendingList.value = await fetchPendingAccounts() || []; }
  catch (e) { console.warn('loadPending failed:', e); }
}

function filterPendingCandidates() { showPendingDrop.value = true; }

function onNameBlur() {
  setTimeout(() => { showPendingDrop.value = false; }, 200);
}

function selectPendingCandidate(p) {
  userForm.realName = p.candidateName;
  userForm.mobile = p.mobile || '';
  userForm.email = p.email || '';
  userForm.deptId = p.deptId || '';
  userForm.positionId = p.positionId || '';
  showPendingDrop.value = false;
}

function toggleBatchPending(p) {
  const idx = batchPending.value.indexOf(p);
  if (idx >= 0) batchPending.value.splice(idx, 1);
  else batchPending.value.push(p);
}

function fillBatchPending() {
  if (batchPending.value.length === 0) return;
  // Fill first one into the form, then create subsequent ones via batch
  const first = batchPending.value[0];
  userForm.realName = first.candidateName;
  userForm.mobile = first.mobile || '';
  userForm.email = first.email || '';
  userForm.deptId = first.deptId || '';
  userForm.positionId = first.positionId || '';
  // Queue remaining for batch creation after save
  if (batchPending.value.length > 1) {
    _batchQueue = batchPending.value.slice(1);
  }
  batchPending.value = [];
}

let _batchQueue = [];

async function loadUsers() {
  try {
    const params = { keyword: userSearch.value, pageSize: 50 };
    if (userRoleFilter.value) params.role = userRoleFilter.value;
    const r = await fetchUsers(params);
    userList.value = r.items || [];
    userTotal.value = r.total ?? 0;
  } catch (e) { console.warn('loadUsers failed:', e); }
}

async function loadDeptAndPosition() {
  try {
    const { fetchDepartments } = await import('../api/config.js');
    deptOptions.value = Array.isArray(await fetchDepartments()) ? await fetchDepartments() : [];
  } catch { deptOptions.value = []; }
  try {
    positionOptions.value = Array.isArray(await fetchPositions()) ? await fetchPositions() : [];
  } catch { positionOptions.value = []; }
}

function openUserCreate() {
  userEditingId.value = null;
  userFormError.value = '';
  userCreateMsg.value = '';
  batchPending.value = [];
  _batchQueue = [];
  Object.assign(userForm, { employeeNo:'', realName:'', mobile:'', email:'', roleCode:'employee', deptId:'', positionId:'' });
  userDialogVisible.value = true;
  loadPending();
}

function openUserEdit(u) {
  userEditingId.value = u.userId;
  userFormError.value = '';
  userCreateMsg.value = '';
  batchPending.value = [];
  _batchQueue = [];
  Object.assign(userForm, {
    employeeNo: u.employeeNo || u.username, realName: u.realName, mobile: u.mobile || '',
    email: u.email || '', roleCode: u.roleCode,
    deptId: u.deptId || '', positionId: u.positionId || '',
  });
  userDialogVisible.value = true;
}

function closeUserDialog() {
  userDialogVisible.value = false;
  showPendingDrop.value = false;
  userCreateMsg.value = '';
}

async function saveUser() {
  userFormError.value = '';
  if (!userForm.realName) { userFormError.value = '请填写姓名'; return; }
  userSaving.value = true;
  try {
    const payload = {
      realName: userForm.realName, mobile: userForm.mobile, email: userForm.email,
      roleCode: userForm.roleCode, deptId: userForm.deptId || null, positionId: userForm.positionId || null,
    };
    if (userEditingId.value) {
      await updateUser(userEditingId.value, payload);
      // 如果修改的是当前登录用户，更新侧边栏缓存
      const curId = getUserId();
      if (curId && String(userEditingId.value) === String(curId)) {
        localStorage.setItem('hr_user', payload.realName);
        setAuth(payload.realName, localStorage.getItem('hr_role'), curId);
      }
      userDialogVisible.value = false;
    } else {
      if (userForm.employeeNo) payload.employeeNo = userForm.employeeNo;
      await createUser(payload);

      // 批量队列
      if (_batchQueue.length) {
        const { batchCreateUsers } = await import('../api/auth.js');
        const batchUsers = _batchQueue.map(p => ({
          employeeNo: '', realName: p.candidateName, mobile: p.mobile || '',
          email: p.email || '', roleCode: 'employee',
          deptId: p.deptId || null, positionId: p.positionId || null,
        }));
        await batchCreateUsers(batchUsers);
        _batchQueue = [];
        batchPending.value = [];
        userCreateMsg.value = '账号已开通（含批量），初始密码均为 123456，请告知用户首次登录后修改';
      } else {
        userCreateMsg.value = '账号已开通，初始密码为 123456，请告知用户首次登录后修改';
      }
      // 短暂显示提示后关闭
      setTimeout(() => { userDialogVisible.value = false; }, 2000);
    }
    await loadUsers();
  } catch (e) { userFormError.value = e.message || '保存失败'; }
  finally { userSaving.value = false; }
}

async function toggleUser(u) {
  const actionLabel = u.status === 1 ? '停用' : '启用';
  const ok = await askConfirm({
    title: `${actionLabel}账号`,
    message: `确定${actionLabel}「${u.realName}」的账号吗？`,
    detail: u.status === 1 ? '停用后该账号将无法登录系统。' : '启用后该账号将恢复登录权限。',
  });
  if (!ok) return;
  try { await toggleUserStatus(u.userId); await loadUsers(); }
  catch (e) { alert(e.message || '操作失败'); }
}

async function deleteUserAccount(u) {
  const ok = await askConfirm({
    title: '删除账号',
    message: `确定删除「${u.realName}」的账号吗？`,
    detail: '删除后该账号将被永久移除。如果账号有审批记录等关联数据，将无法删除，建议先停用。',
    type: 'danger',
    confirmText: '删除',
  });
  if (!ok) return;
  try {
    await deleteUser(u.userId);
    await loadUsers();
  } catch (e) {
    alert(e?.response?.data?.message || e.message || '删除失败');
  }
}

async function resetPwd(u) {
  const ok = await askConfirm({
    title: '重置密码',
    message: `确定重置「${u.realName}」的登录密码吗？`,
    detail: '重置后将生成新的随机密码，请告知用户及时修改。',
  });
  if (!ok) return;
  try {
    const r = await resetUserPassword(u.userId);
    userResetOk.value = true;
    userResetMsg.value = `密码已重置为：${r.newPassword}（请告知用户及时修改）`;
    setTimeout(() => { userResetMsg.value = ''; }, 15000);
  } catch (e) { userResetOk.value = false; userResetMsg.value = e.message || '重置失败'; }
}

onMounted(() => {
  loadUsers();
  loadDeptAndPosition();
});
</script>

<style scoped>
.pending-dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  z-index: 100;
  background: var(--surface-overlay, #fff);
  border: 1px solid var(--border);
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0,0,0,.1);
  max-height: 240px;
  overflow-y: auto;
  margin-top: 2px;
}

.pending-drop-hint {
  padding: 6px 10px;
  font-size: 11px;
  color: var(--text-muted);
  border-bottom: 1px solid var(--border-light);
}

.pending-drop-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  cursor: pointer;
  font-size: 13px;
}

.pending-drop-item:hover {
  background: var(--bg-hover, #EEF1FA);
}

.pending-drop-name {
  font-weight: 600;
  min-width: 60px;
}

.pending-drop-info {
  color: var(--text-muted);
  font-size: 12px;
  flex: 1;
}

.pending-drop-status {
  font-size: 11px;
  font-weight: 600;
}

</style>
