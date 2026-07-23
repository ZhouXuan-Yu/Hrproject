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
    <div class="table-wrap data-region">
      <DataLoadingOverlay :visible="loading" />
      <table v-if="filteredDemands.length > 0">
        <thead><tr>
          <th>需求编号</th><th>岗位</th><th>部门</th><th>HC</th><th>紧急度</th><th>提交人</th>
          <th style="min-width:220px">审批进度 / 招聘进展</th><th>状态</th><th>操作</th>
        </tr></thead>
        <tbody>
          <tr v-for="d in filteredDemands" :key="d.id" :style="{ opacity: d.status === 'draft' ? 0.6 : 1 }">
            <td><b>{{ d.id }}</b></td>
            <td v-if="d.status === 'open'">
              <a href="/recruit-demand-detail" class="position-link" @click.prevent="goDetail(d)">{{ d.position }}</a>
            </td>
            <td v-else>{{ d.position }}</td>
            <td>{{ d.dept }}</td>
            <td>{{ d.hc }}</td>
            <td><StatusBadge :type="d.urgencyType">{{ d.urgencyLabel }}</StatusBadge></td>
            <td>{{ d.submitter }}</td>
            <td>
              <div v-if="d.approvalNodes.length" class="approval-mini" style="margin-bottom:4px">
                <template v-for="(node, ni) in d.approvalNodes" :key="ni">
                  <span class="am-node" :class="node.state" :title="node.opinion || ''">
                    {{ node.label }}<template v-if="node.actor"> · {{ node.actor }}</template><template v-if="node.date">（{{ node.date }}）</template>
                  </span>
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
              <button class="btn btn-outline btn-sm" @click="goDetail(d)">查看详情</button>
              <button class="btn btn-outline btn-sm" :disabled="!canEdit(d)" @click="openEditModal(d)">编辑</button>
              <button class="btn btn-ghost btn-sm" :disabled="!canDelete(d)" style="color:var(--c-reject,#d4380d)" @click="removeDemand(d)">删除</button>
              <button class="btn btn-ghost btn-sm" @click="openMoreOps(d)">更多</button>
            </td>
          </tr>
        </tbody>
      </table>
      <EmptyState
        v-else-if="!loading"
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
          <div class="form-group">
            <div class="jd-label-row">
              <label>岗位说明</label>
              <button class="btn btn-outline btn-sm" @click="generateDemandJd" :disabled="jdLoading || !form.position || !form.dept">
                {{ jdLoading ? '生成中...' : 'AI 生成岗位说明' }}
              </button>
            </div>
            <textarea v-model="form.desc" style="width:100%;min-height:78px;padding:10px;border:1px solid var(--c-border);border-radius:6px;font-size:13px;box-sizing:border-box" placeholder="说明核心职责、必备技能和补充要求"></textarea>
            <div v-if="jdLoading" class="jd-thinking">
              <b>思考中</b><span>正在结合部门、岗位和补充要求生成 Markdown JD 草稿...</span>
            </div>
            <div v-if="jdDraft" class="jd-preview-box">
              <div class="jd-preview-head">
                <b>AI 生成预览</b>
                <div>
                  <button class="btn btn-outline btn-sm" @click="jdEditMode = !jdEditMode">{{ jdEditMode ? '预览' : '编辑' }}</button>
                  <button class="btn btn-primary btn-sm" @click="applyJdToDesc">写入岗位说明</button>
                </div>
              </div>
              <textarea v-if="jdEditMode" v-model="jdDraft" class="jd-edit-area"></textarea>
              <div v-else class="jd-markdown"><AiMarkdown :content="jdDraft" /></div>
            </div>
          </div>
          <div class="modal-actions">
            <button class="btn btn-ghost btn-sm" @click="closeModal">取消</button>
            <button class="btn btn-outline btn-sm" @click="saveDraft">保存草稿</button>
            <button class="btn btn-primary btn-sm" @click="submitApproval">提交审批</button>
          </div>
        </div>
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="showMoreModal" class="modal-overlay open" @click.self="closeMoreOps">
        <div class="modal-box" style="width:460px">
          <h3>更多操作</h3>
          <p style="font-size:13px;color:var(--c-sub);margin-top:-4px">
            {{ moreDemand?.id }} · {{ moreDemand?.position }}
          </p>
          <div class="more-action-list">
            <button class="more-action" :disabled="moreDemand?.status !== 'approval'" @click="approveDemandFromMore">
              <b>审批通过</b><span>按当前登录身份审批当前节点，管理员会记录为代审批</span>
            </button>
            <button class="more-action" :disabled="moreDemand?.status !== 'approval'" @click="rejectDemandFromMore">
              <b>驳回需求</b><span>退回本条需求，保留审批意见</span>
            </button>
            <button class="more-action" :disabled="moreDemand?.status !== 'open'" @click="closeDemandFromMore">
              <b>关闭需求</b><span>用于招聘完成或停止招聘的岗位</span>
            </button>
            <button class="more-action" :disabled="!canDelete(moreDemand)" @click="deleteDemandFromMore">
              <b>删除需求</b><span>仅草稿、驳回、取消且无进行中面试/Offer时可删</span>
            </button>
          </div>
          <div class="modal-actions" style="margin-top:16px">
            <button class="btn btn-ghost btn-sm" @click="closeMoreOps">关闭</button>
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
import { HR_DEPARTMENTS } from '../composables/useMockData.js';
import { fetchDemands, createDemand, updateDemand, deleteDemand, submitForApproval, approveDemandApi, rejectDemandApi, fetchDemandDetail } from '../api/demand.js';
import { api } from '../api/index.js';
import { useToast } from '../composables/useToast.js';
import { useAppError } from '../composables/useAppError.js';
import StatCardRow from '../components/StatCardRow.vue';
import EmptyState from '../components/EmptyState.vue';
import DataLoadingOverlay from '../components/DataLoadingOverlay.vue';
import AiMarkdown from '../components/ai/AiMarkdown.vue';
import { KPI_ICONS } from '../components/kpiIcons.js';
import { runJdGenerate } from '../api/ai.js';

const router = useRouter();
const { toast } = useToast();
const { handleError } = useAppError();
const apiDemands = ref(null);
const loading = ref(true);
const loadError = ref('');

async function loadFromApi() {
  loading.value = true;
  loadError.value = '';
  try {
    const res = await fetchDemands({ pageSize: 100 });
    apiDemands.value = res;
  } catch (e) {
    loadError.value = e.message || '需求数据加载失败';
    apiDemands.value = { data: [], total: 0 };
    console.warn('[RecruitDemand] API fetch failed:', e);
  } finally {
    loading.value = false;
  }
}
const departments = HR_DEPARTMENTS;

// Filters
const filters = reactive({ search: '', status: 'all', urgency: 'all' });

const filteredDemands = computed(() => {
  const list = apiDemands.value?.data || [];
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
  demandList.value.forEach(d => { if (counts[d.status] !== undefined) counts[d.status]++; });
  return counts;
});

const demandList = computed(() => apiDemands.value?.data || []);
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
const showMoreModal = ref(false);
const moreDemand = ref(null);
const editingId = ref('');
const form = reactive({ dept: '技术部', position: '', hc: 1, urgency: '普通', salary: '', date: '', desc: '' });
const jdLoading = ref(false);
const jdDraft = ref('');
const jdEditMode = ref(false);

function canEdit(d) {
  return !!d && ['draft', 'rejected'].includes(d.status);
}

function canDelete(d) {
  return !!d && ['draft', 'rejected', 'cancelled'].includes(d.status);
}

function openCreateModal(){
  editingId.value = '';
  Object.assign(form, { dept: '技术部', position: '', hc: 1, urgency: '普通', salary: '', date: '', desc: '' });
  jdDraft.value = '';
  jdEditMode.value = false;
  showModal.value = true;
}

async function openEditModal(d){
  if (!canEdit(d)) {
    toast.info('该状态暂不允许编辑，请在更多操作中关闭或查看详情');
    return;
  }
  editingId.value = d.id;
  jdDraft.value = '';
  jdEditMode.value = false;
  Object.assign(form, { dept: d.dept, position: d.position, hc: d.hc, urgency: d.urgencyLabel || '普通', salary: d.salary || '', date: d.date || '', desc: d.desc || '' });
  showModal.value = true;
  // 回填完整字段（列表数据可能缺 salary/date/desc）
  try {
    const detail = await fetchDemandDetail(d.id);
    if (detail && editingId.value === d.id) {
      Object.assign(form, {
        dept: detail.dept || form.dept,
        position: detail.position || form.position,
        hc: detail.hc || form.hc,
        urgency: detail.urgency || form.urgency,
        salary: (detail.salary && detail.salary !== '面议') ? detail.salary : form.salary,
        date: detail.date || form.date,
        desc: detail.description || form.desc,
      });
    }
  } catch (e) {
    console.warn('[RecruitDemand] fetch detail for edit failed:', e);
  }
}

function closeModal(){ showModal.value = false; }

async function generateDemandJd() {
  if (!form.position || !form.dept) {
    toast.warning('请先填写岗位和部门');
    return;
  }
  jdLoading.value = true;
  try {
    const result = await runJdGenerate({
      position: form.position,
      department: form.dept,
      level: '高级',
      requirements: form.desc || `HC ${form.hc}人，紧急度${form.urgency}，薪资${form.salary || '面议'}`,
      style: 'internal_approval',
    });
    jdDraft.value = result.jd_text || [
      '## 岗位概述',
      `${form.dept}拟招聘${form.position}，计划 HC ${form.hc} 人。`,
      '',
      '## 核心职责',
      ...(result.responsibilities || []).map((x, i) => `${i + 1}. ${x}`),
      '',
      '## 任职要求',
      ...((result.required_skills || []).map((s) => `- ${s.name || s}：${s.description || ''}`)),
    ].join('\n');
    jdEditMode.value = false;
    toast.success('JD 草稿生成完成，请预览确认后写入');
  } catch (e) {
    handleError(e, 'RecruitDemand.generateDemandJd');
  } finally {
    jdLoading.value = false;
  }
}

function applyJdToDesc() {
  form.desc = jdDraft.value;
  toast.success('已写入岗位说明，可继续人工修改');
}

function buildPayload(){
  return {
    dept: form.dept,
    position: form.position,
    hc: form.hc,
    urgency: form.urgency,
    salary: form.salary,
    date: form.date,
    desc: form.desc,
  };
}

async function saveDraft(){
  if (!form.position) { toast.warning('请填写岗位名称'); return; }
  try {
    if (editingId.value) {
      await updateDemand(editingId.value, buildPayload());
      toast.success('草稿已保存：' + editingId.value);
    } else {
      const res = await createDemand(buildPayload());
      toast.success('草稿已创建，需求编号：' + (res?.id || ''));
    }
    await loadFromApi();
  } catch (e) {
    handleError(e, 'RecruitDemand.saveDraft');
  }
  closeModal();
}

async function submitApproval(){
  if (!form.position) { toast.warning('请填写岗位名称'); return; }
  try {
    let id = editingId.value;
    if (id) {
      // 编辑场景：先保存修改，再提交审批
      await updateDemand(id, buildPayload());
    } else {
      const res = await createDemand(buildPayload());
      id = res?.id;
      if (!id) throw new Error('创建需求失败：未返回需求编号');
    }
    await submitForApproval(id);
    toast.success('已提交审批，需求编号：' + id);
    await loadFromApi(); // refresh list
  } catch (e) {
    handleError(e, 'RecruitDemand.submitApproval');
  }
  closeModal();
}

async function approveDemand(d) {
  if (!confirm(`确认审批通过 "${d.id} ${d.position}"？`)) return;
  // 找到当前待审批层级（state === 'current'），缺省退回第一个未完成节点
  const nodes = d.approvalNodes || [];
  let level = null;
  for (let i = 0; i < nodes.length; i++) {
    if (nodes[i].state === 'current') { level = nodes[i].level || (i + 1); break; }
  }
  if (!level) {
    for (let i = 0; i < nodes.length; i++) {
      if (nodes[i].state !== 'done') { level = nodes[i].level || (i + 1); break; }
    }
  }
  if (!level) { toast.warning('该需求没有待审批节点'); return; }
  try {
    await approveDemandApi(d.id, { level, opinion: '批准' });
    toast.success(`审批通过（${nodes[level - 1]?.label || '层级' + level}）：` + d.id);
    await loadFromApi();
  } catch (e) {
    toast.error(e?.message || '审批失败');
    handleError(e, 'RecruitDemand.approveDemand');
  }
}

function currentApprovalLevel(d) {
  const nodes = d?.approvalNodes || [];
  let level = null;
  for (let i = 0; i < nodes.length; i++) {
    if (nodes[i].state === 'current') { level = nodes[i].level || (i + 1); break; }
  }
  if (!level) {
    for (let i = 0; i < nodes.length; i++) {
      if (nodes[i].state !== 'done') { level = nodes[i].level || (i + 1); break; }
    }
  }
  return level;
}

function openMoreOps(d) {
  moreDemand.value = d;
  showMoreModal.value = true;
}

function closeMoreOps() {
  showMoreModal.value = false;
  moreDemand.value = null;
}

async function approveDemandFromMore() {
  if (!moreDemand.value) return;
  await approveDemand(moreDemand.value);
  closeMoreOps();
}

async function rejectDemandFromMore() {
  const d = moreDemand.value;
  if (!d) return;
  const level = currentApprovalLevel(d);
  if (!level) { toast.warning('该需求没有待审批节点，无法驳回'); return; }
  try {
    await rejectDemandApi(d.id, { level, opinion: '不合适' });
    toast.info('已驳回：' + d.id);
    closeMoreOps();
    await loadFromApi();
  } catch (e) {
    handleError(e, 'RecruitDemand.rejectDemandFromMore');
  }
}

async function closeDemandFromMore() {
  const d = moreDemand.value;
  if (!d) return;
  try {
    await api.post(`/demand/${d.id}/close`);
    toast.info('已关闭：' + d.id);
    closeMoreOps();
    await loadFromApi();
  } catch (e) {
    handleError(e, 'RecruitDemand.closeDemandFromMore');
  }
}

async function deleteDemandFromMore() {
  const d = moreDemand.value;
  if (!d) return;
  closeMoreOps();
  await removeDemand(d);
}

function goDetail(d){ router.push({ path: '/recruit-demand-detail', query: { id: d.id } }); }

async function removeDemand(d) {
  if (!confirm(`确认删除需求 "${d.id} ${d.position}"？删除后不可恢复。`)) return;
  try {
    await deleteDemand(d.id);
    toast.success('已删除：' + d.id);
    await loadFromApi();
  } catch (e) {
    toast.error(e?.message || '删除失败');
    handleError(e, 'RecruitDemand.removeDemand');
  }
}

onMounted(() => {
  loadFromApi();
});
</script>

<style scoped>
.row-actions { white-space: nowrap; }
.data-region { position: relative; min-height: 220px; }
.row-actions .btn { display: inline-flex; margin-right: 4px; }
.position-link { font-weight: 600; color: var(--c-primary); text-decoration: none; }
.linked-cnt { color: var(--c-primary); font-weight: 600; margin-left: 4px; }
.btn:disabled { opacity: .45; cursor: not-allowed; }
.more-action-list { display: grid; gap: 10px; margin-top: 14px; }
.more-action {
  width: 100%; text-align: left; border: 1px solid var(--c-border); border-radius: 8px;
  background: var(--c-card); padding: 12px 14px; cursor: pointer; color: var(--c-text);
}
.more-action b { display: block; font-size: 14px; margin-bottom: 4px; }
.more-action span { display: block; font-size: 12px; color: var(--c-sub); line-height: 1.5; }
.more-action:hover:not(:disabled) { border-color: var(--c-primary); background: var(--c-primary-subtle); }
.more-action:disabled { opacity: .45; cursor: not-allowed; }
.jd-label-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 6px; }
.jd-thinking { margin-top: 8px; padding: 10px 12px; border: 1px solid var(--c-border); border-radius: 8px; background: var(--c-bg); font-size: 12px; color: var(--c-sub); }
.jd-thinking b { color: var(--c-text); margin-right: 8px; }
.jd-thinking::before { content: ""; display: inline-block; width: 12px; height: 12px; margin-right: 8px; border: 2px solid var(--c-border); border-top-color: var(--c-primary); border-radius: 50%; vertical-align: -2px; animation: spin .8s linear infinite; }
.jd-preview-box { margin-top: 10px; padding: 12px; border: 1px solid var(--c-border); border-radius: 8px; background: var(--c-card); }
.jd-preview-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 10px; }
.jd-preview-head > div { display: flex; gap: 8px; }
.jd-edit-area { width: 100%; min-height: 180px; box-sizing: border-box; padding: 10px; border: 1px solid var(--c-border); border-radius: 6px; font-size: 13px; line-height: 1.7; }
.jd-markdown { max-height: 220px; overflow: auto; padding: 10px; border-radius: 6px; background: var(--c-bg); font-size: 13px; line-height: 1.7; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
