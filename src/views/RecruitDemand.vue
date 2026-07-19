<template>
  <WorkbenchLayout title="需求管理" :breadcrumb="{ text: '招聘管理', href: '/recruit-dashboard' }">
    <template #topbar-actions>
      <button class="btn btn-primary btn-sm" @click="openCreateModal">+ 新建需求</button>
    </template>

    <!-- 需求状态统计卡 -->
    <StatCardRow :cards="statCards" :active-key="filters.status" clickable @select="onStatSelect" />
    <section class="hero-page-summary" style="display:none" aria-hidden="true"></section>

    <div class="permission-bar" style="margin-bottom:14px">
      <svg viewBox="0 0 24 24" style="width:14px;height:14px;vertical-align:-2px;stroke:var(--c-sub);fill:none;stroke-width:2;stroke-linecap:round"><circle cx="12" cy="12" r="10"/><path d="M12 16v-4"/><path d="M12 8h.01"/></svg>
      一个表格看全部：审批进度 + 招聘进展 + 岗位匹配，点击「查看详情」进入完整页面
    </div>

    <!-- Filter bar -->
    <div class="filter-bar">
      <input id="demandSearch" type="text" v-model="filters.search" placeholder="搜索需求编号 / 部门 / 岗位..." @input="applyFilters">
      <select id="demandStatus" v-model="filters.status" @change="applyFilters"><option value="all">全部状态</option><option value="draft">草稿</option><option value="approval">审批中</option><option value="open">招聘中</option><option value="closed">已关闭</option></select>
      <select id="demandUrgency" v-model="filters.urgency" @change="applyFilters"><option value="all">全部紧急度</option><option value="very">非常紧急</option><option value="high">紧急</option><option value="normal">普通</option></select>
      <button class="filter-reset btn btn-ghost btn-sm" @click="resetFilters">重置筛选</button>
      <span style="flex:1"></span>
      <span id="demandFilterCount" style="font-size:11px;color:var(--c-sub)">共 {{ filteredDemands.length }} 条需求</span>
    </div>

    <!-- Table -->
    <div class="table-wrap">
      <table v-if="filteredDemands.length > 0">
        <thead><tr>
          <th>需求编号</th><th>岗位</th><th>部门</th><th>HC</th><th>紧急度</th><th>提交人</th>
          <th style="min-width:220px">审批进度 / 招聘进展</th><th>状态</th><th>操作</th>
        </tr></thead>
        <tbody>
          <tr v-for="d in filteredDemands" :key="d.id" :style="{ opacity: d.status === 'draft' ? 0.6 : 1 }">
            <td><b>{{ d.id }}</b></td>
            <td v-if="d.status === 'open'">
              <a href="/recruit-demand-detail" class="position-link" @click.prevent="goDetail">{{ d.position }}</a>
            </td>
            <td v-else>{{ d.position }}</td>
            <td>{{ d.dept }}</td>
            <td>{{ d.hc }}</td>
            <td><StatusBadge :type="d.urgencyType">{{ d.urgencyLabel }}</StatusBadge></td>
            <td>{{ d.submitter }}</td>
            <td>
              <div v-if="d.approvalNodes.length" class="approval-mini" style="margin-bottom:4px">
                <template v-for="(node, ni) in d.approvalNodes" :key="ni">
                  <span class="am-node" :class="node.state">{{ node.label }}</span>
                  <span v-if="ni < d.approvalNodes.length - 1" class="am-arrow">→</span>
                </template>
              </div>
              <span v-if="d.status === 'draft'" style="font-size:11px;color:var(--c-sub)">未提交审批</span>
              <div v-if="d.status === 'open'" style="font-size:11px;color:var(--c-sub)">
                直接投递 <b style="color:var(--c-text)">{{ d.directApply }}</b> ·
                系统推荐 <b style="color:var(--c-done)">{{ d.systemRecommend }}</b> ·
                内部匹配 <b style="color:var(--c-done)">{{ d.internalMatch }}人</b>
                <template v-if="d.internalNames.length">（{{ d.internalNames.join(' · ') }}）</template>
                · 面试中 <b style="color:var(--c-progress)">{{ d.interviewing }}</b>
                <span v-if="d.linkedCount" class="linked-cnt">+人才库{{ d.linkedCount }}人</span>
              </div>
            </td>
            <td><StatusBadge :type="d.statusType">{{ d.statusLabel }}</StatusBadge></td>
            <td class="row-actions">
              <button class="btn btn-outline btn-sm" @click="goDetail">查看详情</button>
              <button v-if="d.status === 'approval'" class="btn btn-primary btn-sm" @click="approveDemand(d.id)">同意</button>
              <button v-if="d.status === 'draft'" class="btn btn-outline btn-sm" @click="openEditModal(d)">编辑</button>
              <button class="btn btn-ghost btn-sm" @click="moreOps(d.id)">更多</button>
            </td>
          </tr>
        </tbody>
      </table>
      <EmptyState
        v-else
        title="暂无匹配的需求"
        description="当前筛选条件下没有找到招聘需求，请调整筛选条件或新建需求"
        action-label="+ 新建需求"
        @action="openCreateModal"
      />
      <div class="table-count">共 {{ filteredDemands.length }} 条需求 · {{ statusCounts.approval }} 条审批中 · {{ statusCounts.open }} 条招聘中 · {{ statusCounts.closed }} 条已关闭 · {{ statusCounts.draft }} 条草稿</div>
    </div>

    <!-- Create/Edit Modal -->
    <Teleport to="body">
      <div id="demandModal" class="modal-overlay" :class="{ open: showModal }" v-if="showModal" @click.self="closeModal">
        <div class="modal-box" style="width:560px" role="dialog" aria-modal="true">
          <h3>{{ editingId ? '编辑招聘需求 · ' + editingId : '新建招聘需求' }}</h3>
          <div class="form-row">
            <div class="form-group"><label>部门 *</label><select v-model="form.dept"><option v-for="d in departments" :key="d" :value="d">{{ d }}</option></select></div>
            <div class="form-group"><label>岗位 *</label><input id="newDemandPosition" type="text" v-model="form.position" placeholder="例如：高级Java工程师"></div>
          </div>
          <div class="form-row">
            <div class="form-group"><label>HC 人数 *</label><input type="number" v-model.number="form.hc" min="1"></div>
            <div class="form-group"><label>紧急度</label><select v-model="form.urgency"><option>普通</option><option>紧急</option><option>非常紧急</option></select></div>
          </div>
          <div class="form-row">
            <div class="form-group"><label>薪资范围</label><input type="text" v-model="form.salary" placeholder="15K-25K"></div>
            <div class="form-group"><label>期望到岗</label><input type="date" v-model="form.date"></div>
          </div>
          <div class="form-group"><label>岗位说明</label><textarea v-model="form.desc" style="width:100%;min-height:78px;padding:10px;border:1px solid var(--c-border);border-radius:6px;font-size:13px;box-sizing:border-box" placeholder="说明核心职责、必备技能和补充要求"></textarea></div>
          <div class="modal-actions">
            <button class="btn btn-ghost btn-sm" @click="closeModal">取消</button>
            <button class="btn btn-outline btn-sm" @click="saveDraft">保存草稿</button>
            <button class="btn btn-primary btn-sm" @click="submitApproval">提交审批</button>
          </div>
        </div>
      </div>
    </Teleport>
  </WorkbenchLayout>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import WorkbenchLayout from '../layouts/WorkbenchLayout.vue';
import { DEMANDS, getLinkedCount } from '../data/demand.js';
import { HR_DEPARTMENTS } from '../composables/useMockData.js';
import { fetchDemands, createDemand, submitForApproval, approveDemandApi, rejectDemandApi } from '../api/demand.js';
import { api } from '../api/index.js';
import { useToast } from '../composables/useToast.js';
import { useAppError } from '../composables/useAppError.js';
import StatCardRow from '../components/StatCardRow.vue';
import EmptyState from '../components/EmptyState.vue';
import { KPI_ICONS } from '../components/kpiIcons.js';

const router = useRouter();
const { toast } = useToast();
const { handleError } = useAppError();
const apiDemands = ref(null);
const demands = ref(DEMANDS.map(d => ({ ...d, linkedCount: getLinkedCount(d.id) })));

async function loadFromApi() {
  try {
    const res = await fetchDemands();
    apiDemands.value = res;
  } catch (e) {
    console.warn('[RecruitDemand] API fetch failed, using mock data:', e);
  }
}
const departments = HR_DEPARTMENTS;

// Filters
const filters = reactive({ search: '', status: 'all', urgency: 'all' });

const filteredDemands = computed(() => {
  const list = apiDemands.value?.data || demands.value;
  return list.filter(d => {
    if (filters.status !== 'all' && d.status !== filters.status) return false;
    if (filters.urgency !== 'all' && d.urgency !== filters.urgency) return false;
    if (filters.search) {
      const q = filters.search.toLowerCase();
      const text = [d.id, d.position, d.dept, d.submitter].join(' ').toLowerCase();
      if (!text.includes(q)) return false;
    }
    return true;
  });
});

const statusCounts = computed(() => {
  const counts = { approval: 0, open: 0, closed: 0, draft: 0 };
  demands.value.forEach(d => { if (counts[d.status] !== undefined) counts[d.status]++; });
  return counts;
});

const demandList = computed(() => apiDemands.value?.data || demands.value);
const statCards = computed(() => {
  const cnt = (st) => demandList.value.filter(d => d.status === st).length;
  return [
    { key: 'all', label: '全部需求', value: demandList.value.length, hint: '含各状态', icon: KPI_ICONS.fileText },
    { key: 'open', label: '招聘中', value: cnt('open'), hint: '进展中', icon: KPI_ICONS.briefcase },
    { key: 'approval', label: '待审批', value: cnt('approval'), hint: '审批流程中', icon: KPI_ICONS.clock },
    { key: 'closed', label: '已关闭', value: cnt('closed'), hint: '本期完成', icon: KPI_ICONS.check },
  ];
});
function onStatSelect(c) { filters.status = c.key; applyFilters(); }

function applyFilters(){}
function resetFilters(){
  filters.search = '';
  filters.status = 'all';
  filters.urgency = 'all';
}

// Modal
const showModal = ref(false);
const editingId = ref('');
const form = reactive({ dept: '技术部', position: '', hc: 1, urgency: '普通', salary: '', date: '', desc: '' });

function openCreateModal(){
  editingId.value = '';
  Object.assign(form, { dept: '技术部', position: '', hc: 1, urgency: '普通', salary: '', date: '', desc: '' });
  showModal.value = true;
}

function openEditModal(d){
  editingId.value = d.id;
  Object.assign(form, { dept: d.dept, position: d.position, hc: d.hc, urgency: d.urgencyLabel, salary: '', date: '', desc: '' });
  showModal.value = true;
}

function closeModal(){ showModal.value = false; }

function saveDraft(){
  if (!form.position) { toast.warning('请填写岗位名称'); return; }
  submitApproval(); // save draft == submit in this flow
}

async function submitApproval(){
  const payload = {
    dept: form.dept,
    position: form.position,
    hc: form.hc,
    urgency: form.urgency,
    salary: form.salary,
    date: form.date,
    desc: form.desc,
  };
  try {
    const res = await createDemand(payload);
    const id = res?.id || '[sample] DM2026070010';
    toast.success('已提交审批，需求编号：' + id);
    await loadFromApi(); // refresh list
  } catch (e) {
    handleError(e, 'RecruitDemand.submitApproval');
  }
  closeModal();
}

async function approveDemand(d) {
  if (!confirm(`确认审批通过 "${d.id} ${d.position}"？`)) return;
  try {
    await approveDemandApi(d.id, { level: 1, opinion: '批准' });
    toast.success('审批通过：' + d.id);
    await loadFromApi();
  } catch (e) { handleError(e, 'RecruitDemand.approveDemand'); }
}

async function moreOps(d) {
  const action = prompt(`需求 ${d.id} - 更多操作:\n1. 驳回\n2. 关闭\n3. 取消`, '');
  if (!action) return;
  try {
    if (action === '驳回' || action === '1') {
      await rejectDemandApi(d.id, { level: 1, opinion: '不合适' });
      toast.info('已驳回：' + d.id);
    } else if (action === '关闭' || action === '2') {
      await api.post(`/demand/${d.id}/close`);
      toast.info('已关闭：' + d.id);
    } else {
      toast.info('操作完成：' + d.id);
    }
    await loadFromApi();
  } catch (e) { handleError(e, 'RecruitDemand.moreOps'); }
}

function goDetail(){ router.push('/recruit-demand-detail'); }

onMounted(() => {
  loadFromApi();
});
</script>

<style scoped>
.row-actions { white-space: nowrap; }
.row-actions .btn { display: inline-flex; margin-right: 4px; }
.position-link { font-weight: 600; color: var(--c-primary); text-decoration: none; }
.linked-cnt { color: var(--c-primary); font-weight: 600; margin-left: 4px; }
</style>
